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

class SpendRepository(private val spendDao: SpendDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "SpendRepository"
    private var syncListener: ListenerRegistration? = null

    fun getAllSpends(userId: String): Flow<List<Spend>> = spendDao.getAllSpends(userId)

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
        spendDao.insertSpend(spend)
        syncToFirestore(spend)
    }

    suspend fun delete(spend: Spend) {
        spendDao.deleteSpend(spend)
        removeFromFirestore(spend)
    }

    suspend fun deleteByUuid(uuid: String, userId: String) {
        spendDao.deleteSpendByUuid(uuid)
        try {
            firestore.collection("users")
                .document(userId)
                .collection("spends")
                .document(uuid)
                .delete()
                .await()
            Log.d(TAG, "Successfully removed spend from Firestore by UUID: $uuid")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from Firestore by UUID: ${e.message}", e)
        }
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
