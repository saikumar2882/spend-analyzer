package com.alpha.spendtracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alpha.spendtracker.data.ChatDao
import com.alpha.spendtracker.data.NotesDao
import com.alpha.spendtracker.data.RecurringBillDao
import com.alpha.spendtracker.data.SpendDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
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
    private val notesDao: NotesDao
) : CoroutineWorker(appContext, workerParams) {

    private companion object {
        const val TAG = "SyncWorker"

        /**
         * How many documents to read, and to upload, at a time.
         *
         * This worker used to issue a `get()` then a `set()` per row, awaiting each in turn — two
         * sequential round trips per record, for seven collections. A user with a thousand spends
         * meant thousands of serial calls inside a job WorkManager will kill at ten minutes, so
         * large accounts simply never finished a sync. Reads now come from one query per collection
         * and the uploads run in bounded parallel batches.
         */
        const val UPLOAD_CONCURRENCY = 25
    }

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(userId)

        return try {
            // Every collection uses the same last-write-wins gate: upload when the local row is at
            // least as new as the remote copy. `>=` rather than a strict `>`, and a missing remote
            // updatedAt counting as "upload", so legacy/partial cloud docs (written before fields
            // like `notes`/`category` existed, or whose updatedAt collides with the local baseline)
            // get re-uploaded with the complete local record.
            //
            // Soft-delete tombstones (deleted=true) are included on purpose: they are how a
            // deletion performed while this device was offline reaches other devices, and the LWW
            // check keeps this device from overwriting a newer tombstone with its stale live copy.
            upload(userDoc.collection("spends"), spendDao.getAllSpendsForSync(userId).first(), { it.uuid }, { it.updatedAt })
            upload(userDoc.collection("recurring_bills"), recurringBillDao.getAllRecurringBillsForSync(userId).first(), { it.uuid }, { it.updatedAt })
            upload(userDoc.collection("chat_messages"), chatDao.getChatMessagesForSync(userId).first(), { it.uuid }, { it.updatedAt })
            upload(userDoc.collection("history"), spendDao.getAllHistoryForSync(userId).first(), { it.historyUuid }, { it.updatedAt })
            upload(userDoc.collection("notes"), notesDao.getAllNotesForSync(userId).first(), { it.uuid }, { it.updatedAt })
            upload(userDoc.collection("note_entries"), notesDao.getAllNoteEntriesForSync(userId).first(), { it.uuid }, { it.updatedAt })
            upload(userDoc.collection("note_history"), notesDao.getNoteHistoryForSync(userId).first(), { it.historyUuid }, { it.updatedAt })

            Log.d(TAG, "Background sync successful for all collections for user: $userId")
            Result.success()
        } catch (e: CancellationException) {
            // WorkManager stopping this worker arrives as cancellation. Rethrow rather than
            // reporting it as a sync failure — a broad catch here reported "failed" and asked for
            // a retry every time the system simply stopped the job.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Uploads the rows of [local] that win last-write-wins against their remote copies.
     *
     * Remote `updatedAt` values come from one query for the whole collection rather than a `get()`
     * per row. Firestore charges and bills a read either way, but a single query is one round trip
     * instead of N.
     */
    private suspend fun <T : Any> upload(
        collection: CollectionReference,
        local: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long
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

        // Bounded parallelism rather than one big awaitAll: a few thousand simultaneous writes
        // would swamp the connection pool and time the job out just as surely as doing them serially.
        stale.chunked(UPLOAD_CONCURRENCY).forEach { chunk ->
            coroutineScope {
                chunk.map { row ->
                    async { collection.document(id(row)).set(row).await() }
                }.awaitAll()
            }
        }
        Log.d(TAG, "${collection.id}: uploaded ${stale.size}/${local.size}")
    }
}
