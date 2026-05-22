/**
 * Repository class that abstracts access to the spend data sources.
 */
package com.alpha.spendtracker.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class SpendRepository(private val spendDao: SpendDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "SpendRepository"

    fun getAllSpends(userId: String): Flow<List<Spend>> = spendDao.getAllSpends(userId)

    suspend fun insert(spend: Spend) {
        spendDao.insertSpend(spend)
        syncToFirestore(spend)
    }

    suspend fun delete(spend: Spend) {
        spendDao.deleteSpend(spend)
        removeFromFirestore(spend)
    }

    suspend fun deleteById(id: Int, userId: String) {
        // To delete from Firestore by ID, we'd need to know the UUID.
        // For simplicity, we'll mostly use the delete(spend) method which has the UUID.
        spendDao.deleteSpendById(id)
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
        try {
            firestore.collection("users")
                .document(spend.userId)
                .collection("spends")
                .document(spend.uuid)
                .delete()
                .await()
            Log.d(TAG, "Successfully removed spend from Firestore: ${spend.uuid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from Firestore: ${e.message}", e)
        }
    }
}
