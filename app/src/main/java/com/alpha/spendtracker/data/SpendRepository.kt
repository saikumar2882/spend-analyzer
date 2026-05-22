/**
 * Repository class that abstracts access to the spend data sources.
 */
package com.alpha.spendtracker.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class SpendRepository(private val spendDao: SpendDao) {

    private val firestore = FirebaseFirestore.getInstance()

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
        } catch (e: Exception) {
            // Log or handle error
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
        } catch (e: Exception) {
            // Log or handle error
        }
    }
}
