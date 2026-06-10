package com.alpha.spendtracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
    private val spendDao: SpendDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val firestore = FirebaseFirestore.getInstance()

        return try {
            val localSpends = spendDao.getAllSpends(userId).first()
            
            // For simplicity, we sync all local records to Firestore. 
            // In a real production app, you'd use a 'lastSync' timestamp or 'isDirty' flag.
            localSpends.forEach { spend ->
                firestore.collection("users")
                    .document(userId)
                    .collection("spends")
                    .document(spend.uuid)
                    .set(spend)
                    .await()
            }
            
            Log.d("SyncWorker", "Background sync successful for user: $userId")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed: ${e.message}")
            Result.retry()
        }
    }
}
