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
            // 1. Sync Spends — last-write-wins: overwrite the cloud copy when the local row
            // is at least as new as what is already in Firestore. We use >= (not strict >)
            // and treat a missing remote updatedAt as "upload" so that legacy/partial cloud
            // docs (written before fields like `notes`/`category` existed, or whose updatedAt
            // collides with the local baseline) are re-uploaded with the complete local
            // record — fixing fields such as the person's name dropping out of sync.
            // Soft-delete tombstones (deleted=true) are included on purpose: they are how a
            // deletion performed while this device was offline reaches other devices, and
            // the LWW check below keeps this device from overwriting a newer tombstone
            // with its stale live copy (which used to resurrect deleted records).
            val localSpends = spendDao.getAllSpendsForSync(userId).first()
            localSpends.forEach { spend ->
                val docRef = userDoc.collection("spends").document(spend.uuid)
                val remoteUpdatedAt = docRef.get().await().getLong("updatedAt")
                if (remoteUpdatedAt == null || spend.updatedAt >= remoteUpdatedAt) {
                    docRef.set(spend).await()
                }
            }

            // 2. Sync Recurring Bills — last-write-wins (>= for the same reasons as above),
            // tombstones included so bill deletions propagate too.
            val localBills = recurringBillDao.getAllRecurringBillsForSync(userId).first()
            localBills.forEach { bill ->
                val docRef = userDoc.collection("recurring_bills").document(bill.uuid)
                val remoteUpdatedAt = docRef.get().await().getLong("updatedAt")
                if (remoteUpdatedAt == null || bill.updatedAt >= remoteUpdatedAt) {
                    docRef.set(bill).await()
                }
            }

            // 3. Sync Chat Messages — same LWW gate; a blind set() here used to overwrite
            // remote tombstones with this device's stale live copy.
            val localMessages = chatDao.getChatMessagesForSync(userId).first()
            localMessages.forEach { msg ->
                val docRef = userDoc.collection("chat_messages").document(msg.uuid)
                val remoteUpdatedAt = docRef.get().await().getLong("updatedAt")
                if (remoteUpdatedAt == null || msg.updatedAt >= remoteUpdatedAt) {
                    docRef.set(msg).await()
                }
            }

            // 4. Sync History — same LWW gate, tombstones included.
            val localHistory = spendDao.getAllHistoryForSync(userId).first()
            localHistory.forEach { h ->
                val docRef = userDoc.collection("history").document(h.historyUuid)
                val remoteUpdatedAt = docRef.get().await().getLong("updatedAt")
                if (remoteUpdatedAt == null || h.updatedAt >= remoteUpdatedAt) {
                    docRef.set(h).await()
                }
            }

            Log.d("SyncWorker", "Background sync successful for all collections for user: $userId")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed: ${e.message}")
            Result.retry()
        }
    }
}
