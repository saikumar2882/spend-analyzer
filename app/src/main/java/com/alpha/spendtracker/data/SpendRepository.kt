/**
 * Repository class that abstracts access to the spend data sources.
 */
package com.alpha.spendtracker.data

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SpendRepository(
    private val spendDao: SpendDao,
    private val recurringBillDao: RecurringBillDao,
    private val chatDao: ChatDao
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val tag = "SpendRepository"
    private val syncListeners = mutableListOf<ListenerRegistration>()

    fun getAllSpends(userId: String): Flow<List<Spend>> = spendDao.getAllSpends(userId)

    fun getHistory(userId: String, type: String): Flow<List<SpendHistory>> = spendDao.getHistory(userId, type)

    /**
     * Starts bi-directional sync with Firestore.
     * Listens for changes in the cloud and updates the local database accordingly.
     */
    fun startSync(userId: String, scope: CoroutineScope) {
        stopSync()
        
        val userDoc = firestore.collection("users").document(userId)

        // 1. Spends Listener
        syncListeners.add(userDoc.collection("spends")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Spends listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val spend = change.document.toObject(Spend::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> spendDao.insertSpend(spend)
                            DocumentChange.Type.REMOVED -> spendDao.deleteSpend(spend)
                        }
                    }
                }
            })

        // 2. Recurring Bills Listener
        syncListeners.add(userDoc.collection("recurring_bills")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Bills listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val bill = change.document.toObject(RecurringBill::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> recurringBillDao.insertRecurringBill(bill)
                            DocumentChange.Type.REMOVED -> recurringBillDao.deleteRecurringBill(bill)
                        }
                    }
                }
            })

        // 3. History Listener
        syncListeners.add(userDoc.collection("history")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "History listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val history = change.document.toObject(SpendHistory::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> spendDao.insertHistory(history)
                            DocumentChange.Type.REMOVED -> spendDao.deleteHistoryByUuid(history.historyUuid)
                        }
                    }
                }
            })

        // 4. Chat Messages Listener
        syncListeners.add(userDoc.collection("chat_messages")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Chat listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val message = change.document.toObject(ChatMessage::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> chatDao.insertMessage(message)
                            DocumentChange.Type.REMOVED -> chatDao.deleteMessageByUuid(message.uuid)
                        }
                    }
                }
            })
    }

    /**
     * Stops all real-time sync listeners.
     */
    fun stopSync() {
        syncListeners.forEach { it.remove() }
        syncListeners.clear()
    }

    suspend fun insert(spend: Spend) {
        // For updates, we log the previous state
        val existing = spendDao.getSpendByUuid(spend.uuid)
        if (existing != null && (existing.amount != spend.amount || existing.notes != spend.notes || existing.purpose != spend.purpose)) {
            val history = SpendHistory(
                historyUuid = java.util.UUID.randomUUID().toString(),
                spendUuid = existing.uuid,
                userId = existing.userId,
                appName = existing.appName,
                amount = existing.amount,
                purpose = existing.purpose,
                category = existing.category,
                timestamp = existing.timestamp,
                notes = existing.notes,
                historyType = HistoryType.UPDATED,
                recordedAt = System.currentTimeMillis()
            )
            spendDao.insertHistory(history)
            syncHistoryToFirestore(history)
        }

        spendDao.insertSpend(spend)
        syncToFirestore(spend)
    }

    suspend fun delete(spend: Spend) {
        // Move to history
        val history = SpendHistory(
            historyUuid = java.util.UUID.randomUUID().toString(),
            spendUuid = spend.uuid,
            userId = spend.userId,
            appName = spend.appName,
            amount = spend.amount,
            purpose = spend.purpose,
            category = spend.category,
            timestamp = spend.timestamp,
            notes = spend.notes,
            historyType = HistoryType.DELETED,
            recordedAt = System.currentTimeMillis()
        )
        spendDao.insertHistory(history)
        syncHistoryToFirestore(history)

        spendDao.deleteSpend(spend)
        removeFromFirestore(spend)
    }

    suspend fun restoreFromHistory(history: SpendHistory) {
        val spend = Spend(
            uuid = history.spendUuid,
            userId = history.userId,
            appName = history.appName,
            amount = history.amount,
            purpose = history.purpose,
            category = history.category,
            timestamp = history.timestamp,
            notes = history.notes
        )
        spendDao.insertSpend(spend)
        syncToFirestore(spend)
        
        spendDao.deleteHistoryByUuid(history.historyUuid)
        removeHistoryFromFirestore(history)
    }

    suspend fun permanentlyDeleteHistory(history: SpendHistory) {
        spendDao.deleteHistoryByUuid(history.historyUuid)
        removeHistoryFromFirestore(history)
    }

    suspend fun clearHistory(userId: String, type: String) {
        spendDao.deleteHistoryByType(userId, type)
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("history")
                .whereEqualTo("historyType", type)
                .get()
                .await()
            
            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Error clearing history from Firestore: ${e.message}")
        }
    }

    suspend fun cleanupOldHistory(userId: String, days: Int = 30) {
        val threshold = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        spendDao.deleteOldHistory(threshold)
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("history")
                .whereLessThan("recordedAt", threshold)
                .get()
                .await()
            
            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up old history from Firestore: ${e.message}")
        }
    }

    suspend fun cleanupOldChatMessages(userId: String, hours: Int = 12) {
        val threshold = System.currentTimeMillis() - (hours.toLong() * 60 * 60 * 1000)
        chatDao.deleteOldMessages(threshold)
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("chat_messages")
                .whereLessThan("timestamp", threshold)
                .get()
                .await()
            
            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up old chat messages from Firestore: ${e.message}")
        }
    }

    suspend fun deleteByUuid(uuid: String, userId: String) {
        val existing = spendDao.getSpendByUuid(uuid)
        if (existing != null) {
            delete(existing)
        } else {
            spendDao.deleteSpendByUuid(uuid)
            removeFromFirestoreByUuid(uuid, userId)
        }
    }

    fun getAllRecurringBills(userId: String): Flow<List<RecurringBill>> = recurringBillDao.getAllRecurringBills(userId)

    suspend fun insertRecurringBill(bill: RecurringBill) {
        recurringBillDao.insertRecurringBill(bill)
        syncRecurringBillToFirestore(bill)
    }

    suspend fun deleteRecurringBill(bill: RecurringBill) {
        recurringBillDao.deleteRecurringBill(bill)
        removeRecurringBillFromFirestore(bill)
    }

    suspend fun getBillsDueOn(day: Int): List<RecurringBill> = recurringBillDao.getBillsDueOn(day)

    suspend fun findMatchingSpend(userId: String, appName: String, purpose: String, startTime: Long, endTime: Long): Spend? =
        recurringBillDao.findMatchingSpend(userId, appName, purpose, startTime, endTime)

    private suspend fun syncRecurringBillToFirestore(bill: RecurringBill) {
        try {
            firestore.collection("users")
                .document(bill.userId)
                .collection("recurring_bills")
                .document(bill.uuid)
                .set(bill)
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error syncing recurring bill to Firestore: ${e.message}")
        }
    }

    private suspend fun removeRecurringBillFromFirestore(bill: RecurringBill) {
        try {
            firestore.collection("users")
                .document(bill.userId)
                .collection("recurring_bills")
                .document(bill.uuid)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error removing recurring bill from Firestore: ${e.message}")
        }
    }

    suspend fun updateRecurringBill(bill: RecurringBill) {
        recurringBillDao.insertRecurringBill(bill)
        syncRecurringBillToFirestore(bill)
    }

    private suspend fun syncToFirestore(spend: Spend) {
        try {
            firestore.collection("users")
                .document(spend.userId)
                .collection("spends")
                .document(spend.uuid)
                .set(spend)
                .await()
            Log.d(tag, "Successfully synced spend to Firestore: ${spend.uuid}")
        } catch (e: Exception) {
            Log.e(tag, "Error syncing to Firestore: ${e.message}", e)
        }
    }

    private suspend fun removeFromFirestore(spend: Spend) {
        removeFromFirestoreByUuid(spend.uuid, spend.userId)
    }

    private suspend fun removeFromFirestoreByUuid(uuid: String, userId: String) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("spends")
                .document(uuid)
                .delete()
                .await()
            Log.d(tag, "Successfully removed spend from Firestore: $uuid")
        } catch (e: Exception) {
            Log.e(tag, "Error removing from Firestore: ${e.message}", e)
        }
    }

    private suspend fun syncHistoryToFirestore(history: SpendHistory) {
        try {
            firestore.collection("users")
                .document(history.userId)
                .collection("history")
                .document(history.historyUuid)
                .set(history)
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error syncing history to Firestore: ${e.message}")
        }
    }

    private suspend fun removeHistoryFromFirestore(history: SpendHistory) {
        try {
            firestore.collection("users")
                .document(history.userId)
                .collection("history")
                .document(history.historyUuid)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error removing history from Firestore: ${e.message}")
        }
    }

    suspend fun insertChatMessage(message: ChatMessage) {
        chatDao.insertMessage(message)
        syncChatMessageToFirestore(message)
    }

    private suspend fun syncChatMessageToFirestore(message: ChatMessage) {
        try {
            firestore.collection("users")
                .document(message.userId)
                .collection("chat_messages")
                .document(message.uuid)
                .set(message)
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error syncing chat message to Firestore: ${e.message}")
        }
    }

    suspend fun deleteChatMessage(message: ChatMessage) {
        chatDao.deleteMessageByUuid(message.uuid)
        removeChatMessageFromFirestore(message)
    }

    private suspend fun removeChatMessageFromFirestore(message: ChatMessage) {
        try {
            firestore.collection("users")
                .document(message.userId)
                .collection("chat_messages")
                .document(message.uuid)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error removing chat message from Firestore: ${e.message}")
        }
    }
}
