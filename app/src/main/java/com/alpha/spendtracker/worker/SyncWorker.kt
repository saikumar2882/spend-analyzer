package com.alpha.spendtracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.ChatMessage
import com.alpha.spendtracker.data.ChatDao
import com.alpha.spendtracker.data.Note
import com.alpha.spendtracker.data.NoteEntry
import com.alpha.spendtracker.data.NoteHistory
import com.alpha.spendtracker.data.NotesDao
import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.data.RecurringBillDao
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendDao
import com.alpha.spendtracker.data.SpendHistory
import com.alpha.spendtracker.util.CloudEnvelope
import com.alpha.spendtracker.util.EncryptionUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val spendDao: SpendDao,
    private val recurringBillDao: RecurringBillDao,
    private val chatDao: ChatDao,
    private val notesDao: NotesDao,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val moshi: Moshi
) : CoroutineWorker(appContext, workerParams) {

    private companion object {
        const val TAG = "SyncWorker"

        /**
         * How many documents to read, and to upload, at a time.
         * Bounded parallelism prevents swamping the connection pool.
         */
        const val UPLOAD_CONCURRENCY = 25
    }

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(userId)

        val localFlag = try {
            aiPreferencesRepository.aiPreferencesFlow.first().isE2eEncryptionEnabled
        } catch (_: Exception) {
            false
        }

        // The account's setting decides, not this device's cached flag. The cache is only refreshed
        // while the app is open, so a device that was closed when another device flipped encryption
        // would otherwise re-upload every row in the old mode and undo the change in the cloud. If
        // the setting can't be read (offline, not created yet) the cache is the best information left.
        val isE2E = try {
            val setting = userDoc.collection("profile").document("security")
                .get(Source.SERVER).await()
            if (setting.exists()) {
                (setting.getBoolean("e2eeEnabled") == true).also { enabled ->
                    if (enabled != localFlag) aiPreferencesRepository.updateE2eEncryption(enabled)
                }
            } else {
                localFlag
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            localFlag
        }

        return try {
            upload(userDoc.collection("spends"), spendDao.getAllSpendsForSync(userId).first(), { it.uuid }, { it.updatedAt }, Spend::class.java, userId, isE2E)
            upload(userDoc.collection("recurring_bills"), recurringBillDao.getAllRecurringBillsForSync(userId).first(), { it.uuid }, { it.updatedAt }, RecurringBill::class.java, userId, isE2E)
            upload(userDoc.collection("chat_messages"), chatDao.getChatMessagesForSync(userId).first(), { it.uuid }, { it.updatedAt }, ChatMessage::class.java, userId, isE2E)
            upload(userDoc.collection("history"), spendDao.getAllHistoryForSync(userId).first(), { it.historyUuid }, { it.updatedAt }, SpendHistory::class.java, userId, isE2E)
            upload(userDoc.collection("notes"), notesDao.getAllNotesForSync(userId).first(), { it.uuid }, { it.updatedAt }, Note::class.java, userId, isE2E)
            upload(userDoc.collection("note_entries"), notesDao.getAllNoteEntriesForSync(userId).first(), { it.uuid }, { it.updatedAt }, NoteEntry::class.java, userId, isE2E)
            upload(userDoc.collection("note_history"), notesDao.getNoteHistoryForSync(userId).first(), { it.historyUuid }, { it.updatedAt }, NoteHistory::class.java, userId, isE2E)

            try {
                val currentBudget = aiPreferencesRepository.aiPreferencesFlow.first().monthlyBudget
                if (currentBudget > 0.0) {
                    userDoc.collection("profile").document("settings")
                        .set(mapOf("monthlyBudget" to currentBudget, "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Syncing monthly budget in background worker failed: ${e.message}")
            }

            Log.d(TAG, "Background sync successful for all collections (E2EE=$isE2E) for user: $userId")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Uploads the rows of [local] that win last-write-wins against their remote copies.
     * Supports E2EE encryption if [isE2E] is enabled.
     */
    private suspend fun <T : Any> upload(
        collection: CollectionReference,
        local: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long,
        type: Class<T>,
        userId: String,
        isE2E: Boolean
    ) {
        if (local.isEmpty()) return

        val remoteUpdatedAt = collection.get().await()
            .documents
            .associate { it.id to it.getLong("updatedAt") }

        val stale = local.filter { row ->
            val remote = remoteUpdatedAt[id(row)]
            remote == null || updatedAt(row) >= remote
        }
        if (stale.isEmpty()) return

        val key = if (isE2E) EncryptionUtils.deriveKey(userId) else null
        val adapter = if (isE2E) moshi.adapter(type) else null

        stale.chunked(UPLOAD_CONCURRENCY).forEach { chunk ->
            coroutineScope {
                chunk.map { row ->
                    async {
                        val docId = id(row)
                        if (isE2E && key != null && adapter != null) {
                            val envelope = CloudEnvelope.seal(row, adapter, docId, userId, key)
                            collection.document(docId).set(envelope).await()
                        } else {
                            collection.document(docId).set(row).await()
                        }
                    }
                }.awaitAll()
            }
        }
        Log.d(TAG, "${collection.id}: uploaded ${stale.size}/${local.size}")
    }
}
