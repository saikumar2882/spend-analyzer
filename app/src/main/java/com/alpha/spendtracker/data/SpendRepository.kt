/**
 * Repository class that abstracts access to the spend data sources.
 */
package com.alpha.spendtracker.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class SpendRepository(
    private val spendDao: SpendDao,
    private val recurringBillDao: RecurringBillDao,
    private val chatDao: ChatDao,
    private val notesDao: NotesDao
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val tag = "SpendRepository"
    private val syncListeners = mutableListOf<ListenerRegistration>()

    private val _syncStatus = MutableStateFlow(SyncStatus())

    /**
     * Whether the push to Firestore is currently healthy. Every cloud write failure used to be
     * swallowed into Logcat, so a user could be indefinitely un-synced with the UI reporting
     * nothing. Observe this to tell them.
     */
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private companion object {
        /**
         * A Firestore batch rejects more than 500 writes. Every bulk path here builds its batch
         * from an unbounded query, so they are chunked well under the cap — a heavy user's cleanup
         * used to fail wholesale (and the failure was only logged, so nothing was ever purged).
         */
        const val BATCH_LIMIT = 450
    }

    fun getAllSpends(userId: String): Flow<List<Spend>> = spendDao.getAllSpends(userId)

    fun getHistory(userId: String, type: String): Flow<List<SpendHistory>> = spendDao.getHistory(userId, type)

    /**
     * Starts bi-directional sync with Firestore.
     * Listens for changes in the cloud and updates the local database accordingly.
     */
    fun startSync(userId: String, scope: CoroutineScope) {
        stopSync()

        val userDoc = firestore.collection("users").document(userId)

        startCollectionSync(
            userDoc, "spends", Spend::class.java, scope,
            remoteUpdatedAt = { it.updatedAt },
            localUpdatedAt = { spendDao.getSpendUpdatedAt(it.uuid) },
            upsert = { spendDao.insertSpend(it) },
            // Deletes arrive as MODIFIED with deleted=true (soft-delete tombstones). REMOVED now
            // only fires for tombstone purges and legacy hard deletes — mirror the purge locally.
            purge = { spendDao.deleteSpend(it) }
        )

        startCollectionSync(
            userDoc, "recurring_bills", RecurringBill::class.java, scope,
            remoteUpdatedAt = { it.updatedAt },
            localUpdatedAt = { recurringBillDao.getRecurringBillUpdatedAt(it.uuid) },
            upsert = { recurringBillDao.insertRecurringBill(it) },
            purge = { recurringBillDao.deleteRecurringBill(it) }
        )

        startCollectionSync(
            userDoc, "history", SpendHistory::class.java, scope,
            remoteUpdatedAt = { it.updatedAt },
            localUpdatedAt = { spendDao.getHistoryUpdatedAt(it.historyUuid) },
            upsert = { spendDao.insertHistory(it) },
            // REMOVED only fires when the 30-day cleanup purges docs.
            purge = { spendDao.deleteHistoryByUuid(it.historyUuid) }
        )

        startCollectionSync(
            userDoc, "chat_messages", ChatMessage::class.java, scope,
            remoteUpdatedAt = { it.updatedAt },
            localUpdatedAt = { chatDao.getMessageUpdatedAt(it.uuid) },
            upsert = { chatDao.insertMessage(it) },
            // REMOVED only fires when the 12-hour TTL cleanup purges docs.
            purge = { chatDao.deleteMessageByUuid(it.uuid) }
        )

        startCollectionSync(
            userDoc, "notes", Note::class.java, scope,
            remoteUpdatedAt = { it.updatedAt },
            localUpdatedAt = { notesDao.getNoteUpdatedAt(it.uuid) },
            upsert = { notesDao.insertNote(it) },
            purge = { notesDao.deleteNote(it) }
        )

        startCollectionSync(
            userDoc, "note_entries", NoteEntry::class.java, scope,
            remoteUpdatedAt = { it.updatedAt },
            localUpdatedAt = { notesDao.getNoteEntryUpdatedAt(it.uuid) },
            upsert = { notesDao.insertNoteEntry(it) },
            purge = { notesDao.deleteNoteEntry(it) }
        )

        startCollectionSync(
            userDoc, "note_history", NoteHistory::class.java, scope,
            remoteUpdatedAt = { it.updatedAt },
            localUpdatedAt = { notesDao.getNoteHistoryUpdatedAt(it.historyUuid) },
            upsert = { notesDao.insertNoteHistory(it) },
            purge = { notesDao.deleteNoteHistoryByUuid(it.historyUuid) }
        )
    }

    /**
     * Mirrors one owner-scoped Firestore collection into Room, newest write winning.
     *
     * Last-write-wins compares with `>=` rather than a strict `>` so records whose updatedAt
     * collides on the same baseline — legacy docs with no updatedAt that deserialize to 0, or
     * migration-seeded rows — still get every field populated (notably the person's name in
     * `notes` and the `category`) instead of being silently skipped. A strictly-newer local edit
     * (larger updatedAt) is still preserved.
     *
     * The whole snapshot is applied in one coroutine under a per-collection [Mutex]. Launching a
     * coroutine per document change, as this used to, let two changes to the same document run
     * concurrently and interleave between the read of the local updatedAt and the write that
     * depends on it — so the older change could win, and a delete could be undone by the live copy
     * arriving in the same batch.
     */
    private fun <T : Any> startCollectionSync(
        userDoc: DocumentReference,
        collection: String,
        type: Class<T>,
        scope: CoroutineScope,
        remoteUpdatedAt: (T) -> Long,
        localUpdatedAt: suspend (T) -> Long?,
        upsert: suspend (T) -> Unit,
        purge: suspend (T) -> Unit
    ) {
        val mutex = Mutex()
        syncListeners.add(
            userDoc.collection(collection).addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(tag, "$collection listen failed.", e)
                    return@addSnapshotListener
                }
                val changes = snapshot?.documentChanges ?: return@addSnapshotListener
                scope.launch {
                    mutex.withLock {
                        for (change in changes) {
                            val item = try {
                                change.document.toObject(type)
                            } catch (e: RuntimeException) {
                                Log.w(tag, "Skipping malformed $collection doc ${change.document.id}", e)
                                continue
                            }
                            when (change.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val local = localUpdatedAt(item)
                                    if (local == null || remoteUpdatedAt(item) >= local) upsert(item)
                                }
                                DocumentChange.Type.REMOVED -> purge(item)
                            }
                        }
                    }
                }
            }
        )
    }

    /**
     * Stops all real-time sync listeners.
     */
    fun stopSync() {
        syncListeners.forEach { it.remove() }
        syncListeners.clear()
    }

    /**
     * The error boundary for on-device work: runs [block] and converts any platform exception to a
     * [DataError] failure. Nothing above this layer sees `SQLiteException`.
     *
     * A failure here is the serious kind — the write did not land, so the user's action is lost and
     * the caller must say so instead of reporting success.
     */
    private suspend fun <T> localWrite(what: String, block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Local $what failed: ${e.message}", e)
            Result.failure(e.toDataError())
        }

    /**
     * Pushes one document to Firestore **without waiting for the server**, recording real failures
     * in [syncStatus].
     *
     * Not awaited, on purpose. Firestore queues writes in a local mutation queue while offline and
     * only completes the Task on server ack — so awaiting one suspends until connectivity returns.
     * These calls sit inside [localWrite], so an awaited push meant the enclosing `Result` never
     * came back and the UI never reported the save at all while offline, even though Room had
     * already committed it. Verified against a real offline device: Firestore logs `UNAVAILABLE`
     * internally and simply never settles the Task.
     *
     * A queued write is not a failed one, so plain "no connection" correctly raises nothing here.
     * The listener fires for genuine rejections — permission denied, quota, malformed data.
     */
    private fun firestorePush(what: String, task: Task<Void>) {
        task
            .addOnSuccessListener {
                // A server ack means the connection is healthy; clear any standing degradation.
                if (_syncStatus.value.isDegraded) _syncStatus.value = SyncStatus()
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Firestore $what failed: ${e.message}", e)
                val error = e.toDataError()
                _syncStatus.update {
                    it.copy(
                        cloudError = error,
                        degradedSince = it.degradedSince ?: System.currentTimeMillis()
                    )
                }
            }
    }

    /**
     * Runs a Firestore write. Failures are recorded in [syncStatus] rather than propagated: the
     * Room write has already landed and [SyncWorker] re-uploads on its next pass, so a transient
     * network error must not fail the user's action — but it must not vanish either, which is what
     * logging alone did.
     *
     * [CancellationException] is rethrown instead of being recorded as a failure. The broad
     * `catch (e: Exception)` this replaces swallowed it, so when the ViewModel scope was cancelled
     * mid-`await()` the coroutine carried on doing work in a scope that no longer existed.
     */
    private suspend fun firestoreWrite(what: String, block: suspend () -> Unit) {
        try {
            block()
            // Any successful write means the connection is back; clear the degraded flag.
            if (_syncStatus.value.isDegraded) _syncStatus.value = SyncStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Firestore $what failed: ${e.message}", e)
            val error = e.toDataError()
            _syncStatus.update {
                it.copy(
                    cloudError = error,
                    degradedSince = it.degradedSince ?: System.currentTimeMillis()
                )
            }
        }
    }

    /** Deletes [docs] in batches that stay under Firestore's 500-write commit cap. */
    private suspend fun batchDelete(docs: List<DocumentSnapshot>) {
        docs.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    /** Marks [docs] deleted in batches that stay under Firestore's 500-write commit cap. */
    private suspend fun batchTombstone(docs: List<DocumentSnapshot>, now: Long) {
        val fields = mapOf("deleted" to true, "updatedAt" to now)
        docs.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.update(it.reference, fields) }
            batch.commit().await()
        }
    }

    suspend fun insert(spend: Spend): Result<Unit> = localWrite("spend insert") {
        // Stamp the local mutation time so last-write-wins sync can resolve conflicts.
        val stamped = spend.copy(updatedAt = System.currentTimeMillis())
        // For updates, we log the previous state
        val existing = spendDao.getSpendByUuid(stamped.uuid)
        if (existing != null && (existing.amount != stamped.amount || existing.notes != stamped.notes || existing.purpose != stamped.purpose)) {
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
                noteUuid = existing.noteUuid,
                historyType = HistoryType.UPDATED,
                recordedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            spendDao.insertHistory(history)
            syncHistoryToFirestore(history)
        }

        spendDao.insertSpend(stamped)
        syncToFirestore(stamped)
    }

    // The active spend previously logged from [noteUuid], if any — used by
    // logNoteAsTransaction to upsert (update-in-place) instead of creating duplicates.
    suspend fun getActiveSpendByNoteUuid(userId: String, noteUuid: String): Result<Spend?> =
        localWrite("note-linked spend lookup") { spendDao.getActiveSpendByNoteUuid(userId, noteUuid) }

    suspend fun delete(spend: Spend): Result<Unit> = localWrite("spend delete") {
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
            noteUuid = spend.noteUuid,
            historyType = HistoryType.DELETED,
            recordedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        spendDao.insertHistory(history)
        syncHistoryToFirestore(history)

        // Soft delete: write a tombstone instead of removing the Firestore doc. A hard
        // delete leaves no trace for last-write-wins to compare against, so another
        // device's SyncWorker (which uploads all its local rows) would see "no remote
        // doc" and re-create the record — deletes never stuck across devices, and the
        // resurrected records read as duplicates. The tombstone rides the normal
        // ADDED/MODIFIED sync path and is filtered out of every UI query; it is purged
        // for real by cleanupOldHistory after the trash window.
        val tombstone = spend.copy(deleted = true, updatedAt = System.currentTimeMillis())
        spendDao.insertSpend(tombstone)
        syncToFirestore(tombstone)
    }

    suspend fun restoreFromHistory(history: SpendHistory): Result<Unit> = localWrite("spend restore") {
        val spend = Spend(
            uuid = history.spendUuid,
            userId = history.userId,
            appName = history.appName,
            amount = history.amount,
            purpose = history.purpose,
            category = history.category,
            timestamp = history.timestamp,
            notes = history.notes,
            noteUuid = history.noteUuid,
            // Restoring is a fresh mutation, so stamp it as the newest write. deleted is
            // explicitly cleared: the restore overwrites the tombstone left by delete(),
            // and the newer updatedAt makes the un-delete win on every device.
            updatedAt = System.currentTimeMillis(),
            deleted = false
        )
        spendDao.insertSpend(spend)
        syncToFirestore(spend)

        tombstoneHistory(history)
    }

    suspend fun permanentlyDeleteHistory(history: SpendHistory): Result<Unit> =
        localWrite("history delete") { tombstoneHistory(history) }

    // Removing a history entry is also a soft delete, for the same resurrection reason
    // as spends. The tombstone keeps recordedAt, so the regular 30-day cleanup purges it.
    private suspend fun tombstoneHistory(history: SpendHistory) {
        val tombstone = history.copy(deleted = true, updatedAt = System.currentTimeMillis())
        spendDao.insertHistory(tombstone)
        syncHistoryToFirestore(tombstone)
    }

    /**
     * Clears (tombstones) history of [type], scoped to either the lend/borrow records or the
     * regular ones so the two trash views empty independently. Tombstones in place instead of
     * deleting — a hard delete would be undone by another device's SyncWorker re-upload.
     */
    suspend fun clearHistory(userId: String, type: String, lendBorrow: Boolean): Result<Unit> = localWrite("clear history") {
        val now = System.currentTimeMillis()
        if (lendBorrow) spendDao.tombstoneLendBorrowHistoryByType(userId, type, now)
        else spendDao.tombstoneRegularHistoryByType(userId, type, now)
        firestoreWrite("clear $type history") {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("history")
                .whereEqualTo("historyType", type)
                .get()
                .await()

            // Firestore can't range/IN filter without a composite index here, so scope by
            // purpose client-side to match the local query above.
            val scoped = snapshot.documents.filter { doc ->
                val purpose = doc.getString("purpose")
                val isLendBorrow = purpose == "Lending" || purpose == "Borrowing"
                if (lendBorrow) isLendBorrow else !isLendBorrow
            }
            batchTombstone(scoped, now)
        }
    }

    suspend fun cleanupOldHistory(userId: String, days: Int = 30): Result<Unit> = localWrite("history cleanup") {
        val threshold = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        spendDao.deleteOldHistory(threshold)
        notesDao.deleteOldNoteHistory(threshold)
        // Same recordedAt-based purge for both history collections.
        for (collection in listOf("history", "note_history")) {
            firestoreWrite("cleanup old $collection") {
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection(collection)
                    .whereLessThan("recordedAt", threshold)
                    .get()
                    .await()
                batchDelete(snapshot.documents)
            }
        }
        cleanupOldTombstones(userId, threshold)
    }

    /**
     * Purges soft-delete tombstones older than the trash window. Tombstones must outlive
     * the trash entries so a device that was offline when the delete happened still sees
     * deleted=true (instead of a missing doc it would re-upload) when it comes back.
     * Covers spends, recurring bills and notes/entries — history and chat tombstones keep
     * their original recordedAt/timestamp and are purged by the existing TTL cleanups instead.
     */
    private suspend fun cleanupOldTombstones(userId: String, threshold: Long) {
        spendDao.deleteOldTombstones(threshold)
        recurringBillDao.deleteOldTombstones(threshold)
        notesDao.deleteOldNoteTombstones(threshold)
        notesDao.deleteOldNoteEntryTombstones(threshold)
        for (collection in listOf("spends", "recurring_bills", "notes", "note_entries")) {
            firestoreWrite("cleanup $collection tombstones") {
                // Equality-only query, filtered client-side on updatedAt: combining it with
                // a range clause would require a Firestore composite index.
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection(collection)
                    .whereEqualTo("deleted", true)
                    .get()
                    .await()
                batchDelete(snapshot.documents.filter { (it.getLong("updatedAt") ?: 0L) < threshold })
            }
        }
    }

    suspend fun cleanupOldChatMessages(userId: String, hours: Int = 12): Result<Unit> = localWrite("chat cleanup") {
        val threshold = System.currentTimeMillis() - (hours.toLong() * 60 * 60 * 1000)
        chatDao.deleteOldMessages(threshold)
        firestoreWrite("cleanup old chat messages") {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("chat_messages")
                .whereLessThan("timestamp", threshold)
                .get()
                .await()
            batchDelete(snapshot.documents)
        }
    }

    suspend fun deleteByUuid(uuid: String, userId: String): Result<Unit> = localWrite("spend delete by uuid") {
        val existing = spendDao.getSpendByUuid(uuid)
        if (existing != null) {
            // Already inside the boundary, so unwrap rather than nesting a Result in a Result.
            delete(existing).getOrThrow()
        } else {
            // No local row to build a tombstone from — fall back to a hard delete.
            spendDao.deleteSpendByUuid(uuid)
            removeFromFirestoreByUuid(uuid, userId)
        }
    }

    fun getAllRecurringBills(userId: String): Flow<List<RecurringBill>> = recurringBillDao.getAllRecurringBills(userId)

    suspend fun insertRecurringBill(bill: RecurringBill): Result<Unit> = localWrite("bill insert") {
        // Stamp the local mutation time so last-write-wins sync can resolve conflicts.
        val stamped = bill.copy(updatedAt = System.currentTimeMillis())
        recurringBillDao.insertRecurringBill(stamped)
        syncRecurringBillToFirestore(stamped)
    }

    suspend fun deleteRecurringBill(bill: RecurringBill): Result<Unit> = localWrite("bill delete") {
        // Soft delete, same as spends — see delete() for the resurrection rationale.
        val tombstone = bill.copy(deleted = true, updatedAt = System.currentTimeMillis())
        recurringBillDao.insertRecurringBill(tombstone)
        syncRecurringBillToFirestore(tombstone)
    }

    suspend fun getBillsDueOn(userId: String, day: Int): Result<List<RecurringBill>> =
        localWrite("due bills lookup") { recurringBillDao.getBillsDueOn(userId, day) }

    suspend fun findMatchingSpend(userId: String, appName: String, purpose: String, startTime: Long, endTime: Long): Result<Spend?> =
        localWrite("matching spend lookup") {
            spendDao.findMatchingSpend(userId, appName, purpose, startTime, endTime)
        }

    private fun syncRecurringBillToFirestore(bill: RecurringBill) {
        firestorePush(
            "recurring bill write",
            firestore.collection("users")
                .document(bill.userId)
                .collection("recurring_bills")
                .document(bill.uuid)
                .set(bill)
        )
    }

    suspend fun updateRecurringBill(bill: RecurringBill): Result<Unit> = localWrite("bill update") {
        // Stamp the local mutation time so last-write-wins sync can resolve conflicts.
        val stamped = bill.copy(updatedAt = System.currentTimeMillis())
        recurringBillDao.insertRecurringBill(stamped)
        syncRecurringBillToFirestore(stamped)
    }

    // ---- Notes ----
    // Notes and their entries live in their own tables/collections and are never mixed
    // into spend analytics. Writes go to Room first, then Firestore, all stamped with a
    // fresh updatedAt for last-write-wins — identical to the spend/bill flow above.

    fun getAllNotes(userId: String): Flow<List<Note>> = notesDao.getAllNotes(userId)

    fun getAllNoteEntries(userId: String): Flow<List<NoteEntry>> = notesDao.getAllNoteEntries(userId)

    suspend fun insertNote(note: Note): Result<Unit> = localWrite("note insert") {
        val stamped = note.copy(updatedAt = System.currentTimeMillis())
        notesDao.insertNote(stamped)
        syncNoteToFirestore(stamped)
    }

    suspend fun updateNote(note: Note): Result<Unit> = localWrite("note update") {
        val now = System.currentTimeMillis()
        // Snapshot the previous version for the Update History, but only when it actually changed.
        val existing = notesDao.getNoteByUuid(note.uuid)
        if (existing != null && (existing.title != note.title || existing.colorIndex != note.colorIndex)) {
            recordNoteHistory(existing, HistoryType.UPDATED, now)
        }
        val updated = note.copy(updatedAt = now)
        notesDao.insertNote(updated)
        syncNoteToFirestore(updated)
    }

    suspend fun deleteNote(note: Note): Result<Unit> = localWrite("note delete") {
        // Soft delete, same as spends — see delete() for the resurrection rationale.
        // Cascade the tombstone to the note's entries so they don't linger as orphans
        // that keep re-syncing after their parent note is gone. A single NOTE history record
        // represents the whole note in the Recycle Bin; restoring it brings the entries back.
        val now = System.currentTimeMillis()
        recordNoteHistory(note, HistoryType.DELETED, now)
        notesDao.getEntriesForNoteOnce(note.uuid).forEach { entry ->
            val entryTombstone = entry.copy(deleted = true, updatedAt = now)
            notesDao.insertNoteEntry(entryTombstone)
            syncNoteEntryToFirestore(entryTombstone)
        }
        val tombstone = note.copy(deleted = true, updatedAt = now)
        notesDao.insertNote(tombstone)
        syncNoteToFirestore(tombstone)
    }

    suspend fun insertNoteEntry(entry: NoteEntry): Result<Unit> = localWrite("note entry insert") {
        val stamped = entry.copy(updatedAt = System.currentTimeMillis())
        notesDao.insertNoteEntry(stamped)
        syncNoteEntryToFirestore(stamped)
    }

    suspend fun updateNoteEntry(entry: NoteEntry): Result<Unit> = localWrite("note entry update") {
        val now = System.currentTimeMillis()
        val existing = notesDao.getNoteEntryByUuid(entry.uuid)
        if (existing != null && (
                existing.label != entry.label || existing.amount != entry.amount ||
                existing.detail != entry.detail || existing.date != entry.date ||
                existing.customFields != entry.customFields)
        ) {
            recordEntryHistory(existing, HistoryType.UPDATED, now)
        }
        val updated = entry.copy(updatedAt = now)
        notesDao.insertNoteEntry(updated)
        syncNoteEntryToFirestore(updated)
    }

    suspend fun deleteNoteEntry(entry: NoteEntry): Result<Unit> = localWrite("note entry delete") {
        val now = System.currentTimeMillis()
        recordEntryHistory(entry, HistoryType.DELETED, now)
        val tombstone = entry.copy(deleted = true, updatedAt = now)
        notesDao.insertNoteEntry(tombstone)
        syncNoteEntryToFirestore(tombstone)
    }

    private fun syncNoteToFirestore(note: Note) {
        firestorePush(
            "note write",
            firestore.collection("users")
                .document(note.userId)
                .collection("notes")
                .document(note.uuid)
                .set(note)
        )
    }

    private fun syncNoteEntryToFirestore(entry: NoteEntry) {
        firestorePush(
            "note entry write",
            firestore.collection("users")
                .document(entry.userId)
                .collection("note_entries")
                .document(entry.uuid)
                .set(entry)
        )
    }

    // ---- Note history (Recycle Bin + Update History) ----

    fun getNoteHistory(userId: String, type: String): Flow<List<NoteHistory>> = notesDao.getNoteHistory(userId, type)

    private suspend fun recordNoteHistory(note: Note, type: String, now: Long) {
        val history = NoteHistory(
            historyUuid = java.util.UUID.randomUUID().toString(),
            userId = note.userId,
            itemType = NoteItemType.NOTE,
            itemUuid = note.uuid,
            title = note.title,
            colorIndex = note.colorIndex,
            itemCreatedAt = note.createdAt,
            historyType = type,
            recordedAt = now,
            updatedAt = now
        )
        notesDao.insertNoteHistory(history)
        syncNoteHistoryToFirestore(history)
    }

    private suspend fun recordEntryHistory(entry: NoteEntry, type: String, now: Long) {
        val history = NoteHistory(
            historyUuid = java.util.UUID.randomUUID().toString(),
            userId = entry.userId,
            itemType = NoteItemType.ENTRY,
            itemUuid = entry.uuid,
            noteUuid = entry.noteUuid,
            title = entry.label,
            amount = entry.amount,
            detail = entry.detail,
            date = entry.date,
            customFields = entry.customFields,
            itemCreatedAt = entry.createdAt,
            historyType = type,
            recordedAt = now,
            updatedAt = now
        )
        notesDao.insertNoteHistory(history)
        syncNoteHistoryToFirestore(history)
    }

    suspend fun restoreNoteFromHistory(history: NoteHistory): Result<Unit> = localWrite("note restore") {
        val now = System.currentTimeMillis()
        if (history.itemType == NoteItemType.NOTE) {
            val note = Note(
                uuid = history.itemUuid,
                userId = history.userId,
                title = history.title,
                colorIndex = history.colorIndex,
                createdAt = history.itemCreatedAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
                deleted = false
            )
            notesDao.insertNote(note)
            syncNoteToFirestore(note)
            // Bring back the entries that were tombstoned along with the note.
            notesDao.getAllEntriesForNote(history.itemUuid).forEach { entry ->
                if (entry.deleted) {
                    val restored = entry.copy(deleted = false, updatedAt = now)
                    notesDao.insertNoteEntry(restored)
                    syncNoteEntryToFirestore(restored)
                }
            }
        } else {
            val entry = NoteEntry(
                uuid = history.itemUuid,
                userId = history.userId,
                noteUuid = history.noteUuid,
                label = history.title,
                amount = history.amount,
                detail = history.detail,
                date = history.date,
                customFields = history.customFields,
                createdAt = history.itemCreatedAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
                deleted = false
            )
            notesDao.insertNoteEntry(entry)
            syncNoteEntryToFirestore(entry)
        }
        // Consume the history record so a restored item doesn't linger in the bin/history.
        tombstoneNoteHistory(history, now)
    }

    suspend fun permanentlyDeleteNoteHistory(history: NoteHistory): Result<Unit> =
        localWrite("note history delete") { tombstoneNoteHistory(history, System.currentTimeMillis()) }

    private suspend fun tombstoneNoteHistory(history: NoteHistory, now: Long) {
        val tombstone = history.copy(deleted = true, updatedAt = now)
        notesDao.insertNoteHistory(tombstone)
        syncNoteHistoryToFirestore(tombstone)
    }

    suspend fun clearNoteHistory(userId: String, type: String): Result<Unit> = localWrite("clear note history") {
        val now = System.currentTimeMillis()
        notesDao.tombstoneNoteHistoryByType(userId, type, now)
        firestoreWrite("clear $type note history") {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("note_history")
                .whereEqualTo("historyType", type)
                .get()
                .await()
            batchTombstone(snapshot.documents, now)
        }
    }

    private fun syncNoteHistoryToFirestore(history: NoteHistory) {
        firestorePush(
            "note history write",
            firestore.collection("users")
                .document(history.userId)
                .collection("note_history")
                .document(history.historyUuid)
                .set(history)
        )
    }

    private fun syncToFirestore(spend: Spend) {
        firestorePush(
            "spend write",
            firestore.collection("users")
                .document(spend.userId)
                .collection("spends")
                .document(spend.uuid)
                .set(spend)
        )
    }

    private fun removeFromFirestoreByUuid(uuid: String, userId: String) {
        firestorePush(
            "spend hard delete",
            firestore.collection("users")
                .document(userId)
                .collection("spends")
                .document(uuid)
                .delete()
        )
    }

    private fun syncHistoryToFirestore(history: SpendHistory) {
        firestorePush(
            "history write",
            firestore.collection("users")
                .document(history.userId)
                .collection("history")
                .document(history.historyUuid)
                .set(history)
        )
    }

    suspend fun insertChatMessage(message: ChatMessage): Result<Unit> = localWrite("chat insert") {
        // Stamp the local mutation time so last-write-wins sync can resolve conflicts.
        val stamped = message.copy(updatedAt = System.currentTimeMillis())
        chatDao.insertMessage(stamped)
        syncChatMessageToFirestore(stamped)
    }

    private fun syncChatMessageToFirestore(message: ChatMessage) {
        firestorePush(
            "chat message write",
            firestore.collection("users")
                .document(message.userId)
                .collection("chat_messages")
                .document(message.uuid)
                .set(message)
        )
    }

    suspend fun deleteChatMessage(message: ChatMessage): Result<Unit> = localWrite("chat delete") {
        // Soft delete, same as spends — see delete() for the resurrection rationale.
        val tombstone = message.copy(deleted = true, updatedAt = System.currentTimeMillis())
        chatDao.insertMessage(tombstone)
        syncChatMessageToFirestore(tombstone)
    }

    // ---- Chat reads ----
    // The AI history assistant's rate limiting is backed by these counts. They live here rather
    // than being read from an injected ChatDao in the ViewModel: a DAO is a data-layer internal,
    // and reaching past the repository for it also routed around the error boundary above.

    fun getChatMessages(userId: String): Flow<List<ChatMessage>> = chatDao.getChatMessages(userId)

    suspend fun getSessionCountSince(userId: String, since: Long): Result<Int> =
        localWrite("session count") { chatDao.getSessionCountSince(userId, since) }

    suspend fun getMessageCountInSession(userId: String, sessionId: String): Result<Int> =
        localWrite("session message count") { chatDao.getMessageCountInSession(userId, sessionId) }

    suspend fun isSessionActiveSince(userId: String, sessionId: String, since: Long): Result<Boolean> =
        localWrite("session activity check") { chatDao.isSessionActiveSince(userId, sessionId, since) }

    suspend fun getLastSessionId(userId: String): Result<String?> =
        localWrite("last session lookup") { chatDao.getLastSessionId(userId) }
}
