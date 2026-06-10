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
    private val recurringBillDao: RecurringBillDao
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "SpendRepository"
    private var syncListener: ListenerRegistration? = null

    fun getAllSpends(userId: String): Flow<List<Spend>> = spendDao.getAllSpends(userId)

    fun getHistory(userId: String, type: String): Flow<List<SpendHistory>> = spendDao.getHistory(userId, type)

    /**
     * Starts bi-directional sync with Firestore.
     * Listens for changes in the cloud and updates the local database accordingly.
     */
    fun startSync(userId: String, scope: CoroutineScope) {
        syncListener?.remove()
        syncListener = firestore.collection("users")
            .document(userId)
            .collection("spends")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val spend = change.document.toObject(Spend::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                spendDao.insertSpend(spend)
                                Log.d(TAG, "Synced from cloud: ${spend.uuid}")
                            }
                            DocumentChange.Type.REMOVED -> {
                                spendDao.deleteSpend(spend)
                                Log.d(TAG, "Removed from local due to cloud deletion: ${spend.uuid}")
                            }
                        }
                    }
                }
            }
    }

    /**
     * Stops the real-time sync listener.
     */
    fun stopSync() {
        syncListener?.remove()
        syncListener = null
    }

    suspend fun insert(spend: Spend) {
        // For updates, we log the previous state
        val existing = spendDao.getSpendByUuid(spend.uuid)
        if (existing != null && (existing.amount != spend.amount || existing.notes != spend.notes || existing.purpose != spend.purpose)) {
            val history = SpendHistory(
                spendUuid = existing.uuid,
                userId = existing.userId,
                appName = existing.appName,
                amount = existing.amount,
                purpose = existing.purpose,
                category = existing.category,
                timestamp = existing.timestamp,
                notes = existing.notes,
                historyType = HistoryType.UPDATED
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
            spendUuid = spend.uuid,
            userId = spend.userId,
            appName = spend.appName,
            amount = spend.amount,
            purpose = spend.purpose,
            category = spend.category,
            timestamp = spend.timestamp,
            notes = spend.notes,
            historyType = HistoryType.DELETED
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
        // Note: Bulk delete in Firestore would require a batch or cloud function.
        // For now, we'll let the next sync or manual check handle it if critical.
        // In a real app, we'd query and delete each document.
    }

    suspend fun cleanupOldHistory(days: Int = 30) {
        val threshold = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        spendDao.deleteOldHistory(threshold)
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
            Log.e(TAG, "Error syncing recurring bill to Firestore: ${e.message}")
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
            Log.e(TAG, "Error removing recurring bill from Firestore: ${e.message}")
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
            Log.d(TAG, "Successfully synced spend to Firestore: ${spend.uuid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to Firestore: ${e.message}", e)
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
            Log.d(TAG, "Successfully removed spend from Firestore: $uuid")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from Firestore: ${e.message}", e)
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
            Log.e(TAG, "Error syncing history to Firestore: ${e.message}")
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
            Log.e(TAG, "Error removing history from Firestore: ${e.message}")
        }
    }
}
