package com.alpha.spendtracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alpha.spendtracker.data.ChatDao
import com.alpha.spendtracker.data.RecurringBillDao
import com.alpha.spendtracker.data.SpendDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val spendDao: SpendDao,
    private val recurringBillDao: RecurringBillDao,
    private val chatDao: ChatDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(userId)

        return try {
            // 1. Sync Spends — last-write-wins: only overwrite the cloud copy when the
            // local row is strictly newer than what is already in Firestore.
            val localSpends = spendDao.getAllSpends(userId).first()
            localSpends.forEach { spend ->
                val docRef = userDoc.collection("spends").document(spend.uuid)
                val remoteUpdatedAt = docRef.get().await().getLong("updatedAt")
                if (remoteUpdatedAt == null || spend.updatedAt > remoteUpdatedAt) {
                    docRef.set(spend).await()
                }
            }

            // 2. Sync Recurring Bills — last-write-wins.
            val localBills = recurringBillDao.getAllRecurringBills(userId).first()
            localBills.forEach { bill ->
                val docRef = userDoc.collection("recurring_bills").document(bill.uuid)
                val remoteUpdatedAt = docRef.get().await().getLong("updatedAt")
                if (remoteUpdatedAt == null || bill.updatedAt > remoteUpdatedAt) {
                    docRef.set(bill).await()
                }
            }

            // 3. Sync Chat Messages
            val localMessages = chatDao.getChatMessages(userId).first()
            localMessages.forEach { msg ->
                userDoc.collection("chat_messages").document(msg.uuid).set(msg).await()
            }

            // 4. Sync History
            // Since SpendDao doesn't have a direct "getAllHistory", we sync both types
            val deletedHistory = spendDao.getHistory(userId, "DELETED").first()
            deletedHistory.forEach { h ->
                userDoc.collection("history").document(h.historyUuid).set(h).await()
            }
            val updatedHistory = spendDao.getHistory(userId, "UPDATED").first()
            updatedHistory.forEach { h ->
                userDoc.collection("history").document(h.historyUuid).set(h).await()
            }

            Log.d("SyncWorker", "Background sync successful for all collections for user: $userId")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed: ${e.message}")
            Result.retry()
        }
    }
}
