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
    private val recurringBillDao: RecurringBillDao,
    private val chatDao: ChatDao,
    private val notesDao: NotesDao
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val tag = "SpendRepository"
    private val syncListeners = mutableListOf<ListenerRegistration>()

    fun getAllSpends(userId: String): Flow<List<Spend>> = spendDao.getAllSpends(userId)

    fun getHistory(userId: String, type: String): Flow<List<SpendHistory>> = spendDao.getHistory(userId, type)

    /**
     * Starts bi-directional sync with Firestore.
     * Listens for changes in the cloud and updates the local database accordingly.
     */
    fun startSync(userId: String, scope: CoroutineScope) {
        stopSync()
        
        val userDoc = firestore.collection("users").document(userId)

        // 1. Spends Listener
        syncListeners.add(userDoc.collection("spends")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Spends listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val spend = change.document.toObject(Spend::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                // Last-write-wins: apply the cloud copy when it is at least as new
                                // as the local row. We compare with >= (not strict >) so records
                                // whose updatedAt collides on the same baseline — legacy docs with
                                // no updatedAt that deserialize to 0, or migration-seeded rows —
                                // still get every field populated (notably the person's name in
                                // `notes` and the `category`) instead of being silently skipped.
                                // A strictly-newer local edit (larger updatedAt) is still preserved.
                                val localUpdatedAt = spendDao.getSpendUpdatedAt(spend.uuid)
                                if (localUpdatedAt == null || spend.updatedAt >= localUpdatedAt) {
                                    spendDao.insertSpend(spend)
                                }
                            }
                            // Deletes arrive as MODIFIED with deleted=true (soft-delete
                            // tombstones). REMOVED now only fires for tombstone purges and
                            // legacy hard deletes — mirror the purge locally.
                            DocumentChange.Type.REMOVED -> spendDao.deleteSpend(spend)
                        }
                    }
                }
            })

        // 2. Recurring Bills Listener
        syncListeners.add(userDoc.collection("recurring_bills")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Bills listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val bill = change.document.toObject(RecurringBill::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                // Last-write-wins: apply the cloud copy when it is at least as new
                                // as the local row (>= so colliding/legacy baselines still fully
                                // populate every field, e.g. name and notes). A strictly-newer
                                // local edit is preserved.
                                val localUpdatedAt = recurringBillDao.getRecurringBillUpdatedAt(bill.uuid)
                                if (localUpdatedAt == null || bill.updatedAt >= localUpdatedAt) {
                                    recurringBillDao.insertRecurringBill(bill)
                                }
                            }
                            // Deletes arrive as MODIFIED with deleted=true; REMOVED only
                            // fires for tombstone purges and legacy hard deletes.
                            DocumentChange.Type.REMOVED -> recurringBillDao.deleteRecurringBill(bill)
                        }
                    }
                }
            })

        // 3. History Listener
        syncListeners.add(userDoc.collection("history")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "History listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val history = change.document.toObject(SpendHistory::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                // Same last-write-wins gate as spends — keeps a stale cloud
                                // copy from overwriting a newer local tombstone.
                                val localUpdatedAt = spendDao.getHistoryUpdatedAt(history.historyUuid)
                                if (localUpdatedAt == null || history.updatedAt >= localUpdatedAt) {
                                    spendDao.insertHistory(history)
                                }
                            }
                            // REMOVED only fires when the 30-day cleanup purges docs.
                            DocumentChange.Type.REMOVED -> spendDao.deleteHistoryByUuid(history.historyUuid)
                        }
                    }
                }
            })

        // 4. Chat Messages Listener
        syncListeners.add(userDoc.collection("chat_messages")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Chat listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val message = change.document.toObject(ChatMessage::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                // Same last-write-wins gate as spends — keeps a stale cloud
                                // copy from overwriting a newer local tombstone.
                                val localUpdatedAt = chatDao.getMessageUpdatedAt(message.uuid)
                                if (localUpdatedAt == null || message.updatedAt >= localUpdatedAt) {
                                    chatDao.insertMessage(message)
                                }
                            }
                            // REMOVED only fires when the 12-hour TTL cleanup purges docs.
                            DocumentChange.Type.REMOVED -> chatDao.deleteMessageByUuid(message.uuid)
                        }
                    }
                }
            })

        // 5. Notes Listener
        syncListeners.add(userDoc.collection("notes")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Notes listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val note = change.document.toObject(Note::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                // Same last-write-wins gate as spends — keeps a stale cloud
                                // copy from overwriting a newer local tombstone/edit.
                                val localUpdatedAt = notesDao.getNoteUpdatedAt(note.uuid)
                                if (localUpdatedAt == null || note.updatedAt >= localUpdatedAt) {
                                    notesDao.insertNote(note)
                                }
                            }
                            // Deletes arrive as MODIFIED with deleted=true; REMOVED only
                            // fires for tombstone purges and legacy hard deletes.
                            DocumentChange.Type.REMOVED -> notesDao.deleteNote(note)
                        }
                    }
                }
            })

        // 6. Note Entries Listener
        syncListeners.add(userDoc.collection("note_entries")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Note entries listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val entry = change.document.toObject(NoteEntry::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val localUpdatedAt = notesDao.getNoteEntryUpdatedAt(entry.uuid)
                                if (localUpdatedAt == null || entry.updatedAt >= localUpdatedAt) {
                                    notesDao.insertNoteEntry(entry)
                                }
                            }
                            DocumentChange.Type.REMOVED -> notesDao.deleteNoteEntry(entry)
                        }
                    }
                }
            })

        // 7. Note History Listener
        syncListeners.add(userDoc.collection("note_history")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { Log.w(tag, "Note history listen failed.", e); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    val history = change.document.toObject(NoteHistory::class.java)
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val localUpdatedAt = notesDao.getNoteHistoryUpdatedAt(history.historyUuid)
                                if (localUpdatedAt == null || history.updatedAt >= localUpdatedAt) {
                                    notesDao.insertNoteHistory(history)
                                }
                            }
                            // REMOVED only fires when the 30-day cleanup purges docs.
                            DocumentChange.Type.REMOVED -> notesDao.deleteNoteHistoryByUuid(history.historyUuid)
                        }
                    }
                }
            })
    }

    /**
     * Stops all real-time sync listeners.
     */
    fun stopSync() {
        syncListeners.forEach { it.remove() }
        syncListeners.clear()
    }

    suspend fun insert(spend: Spend) {
        // Stamp the local mutation time so last-write-wins sync can resolve conflicts.
        val spend = spend.copy(updatedAt = System.currentTimeMillis())
        // For updates, we log the previous state
        val existing = spendDao.getSpendByUuid(spend.uuid)
        if (existing != null && (existing.amount != spend.amount || existing.notes != spend.notes || existing.purpose != spend.purpose)) {
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
                historyType = HistoryType.UPDATED,
                recordedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
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
            historyUuid = java.util.UUID.randomUUID().toString(),
            spendUuid = spend.uuid,
            userId = spend.userId,
            appName = spend.appName,
            amount = spend.amount,
            purpose = spend.purpose,
            category = spend.category,
            timestamp = spend.timestamp,
            notes = spend.notes,
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

    suspend fun restoreFromHistory(history: SpendHistory) {
        val spend = Spend(
            uuid = history.spendUuid,
            userId = history.userId,
            appName = history.appName,
            amount = history.amount,
            purpose = history.purpose,
            category = history.category,
            timestamp = history.timestamp,
            notes = history.notes,
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

    suspend fun permanentlyDeleteHistory(history: SpendHistory) {
        tombstoneHistory(history)
    }

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
    suspend fun clearHistory(userId: String, type: String, lendBorrow: Boolean) {
        val now = System.currentTimeMillis()
        if (lendBorrow) spendDao.tombstoneLendBorrowHistoryByType(userId, type, now)
        else spendDao.tombstoneRegularHistoryByType(userId, type, now)
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("history")
                .whereEqualTo("historyType", type)
                .get()
                .await()

            // Firestore can't range/IN filter without a composite index here, so scope by
            // purpose client-side to match the local query above.
            val batch = firestore.batch()
            snapshot.documents
                .filter { doc ->
                    val purpose = doc.getString("purpose")
                    val isLendBorrow = purpose == "Lending" || purpose == "Borrowing"
                    if (lendBorrow) isLendBorrow else !isLendBorrow
                }
                .forEach { batch.update(it.reference, mapOf("deleted" to true, "updatedAt" to now)) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Error clearing history from Firestore: ${e.message}")
        }
    }

    suspend fun cleanupOldHistory(userId: String, days: Int = 30) {
        val threshold = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        spendDao.deleteOldHistory(threshold)
        notesDao.deleteOldNoteHistory(threshold)
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("history")
                .whereLessThan("recordedAt", threshold)
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up old history from Firestore: ${e.message}")
        }
        // Same recordedAt-based purge for the notes history collection.
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("note_history")
                .whereLessThan("recordedAt", threshold)
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up old note history from Firestore: ${e.message}")
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
            try {
                // Equality-only query, filtered client-side on updatedAt: combining it with
                // a range clause would require a Firestore composite index.
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection(collection)
                    .whereEqualTo("deleted", true)
                    .get()
                    .await()

                val batch = firestore.batch()
                snapshot.documents
                    .filter { (it.getLong("updatedAt") ?: 0L) < threshold }
                    .forEach { batch.delete(it.reference) }
                batch.commit().await()
            } catch (e: Exception) {
                Log.e(tag, "Error cleaning up old $collection tombstones from Firestore: ${e.message}")
            }
        }
    }

    suspend fun cleanupOldChatMessages(userId: String, hours: Int = 12) {
        val threshold = System.currentTimeMillis() - (hours.toLong() * 60 * 60 * 1000)
        chatDao.deleteOldMessages(threshold)
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("chat_messages")
                .whereLessThan("timestamp", threshold)
                .get()
                .await()
            
            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up old chat messages from Firestore: ${e.message}")
        }
    }

    suspend fun deleteByUuid(uuid: String, userId: String) {
        val existing = spendDao.getSpendByUuid(uuid)
        if (existing != null) {
            delete(existing)
        } else {
            // No local row to build a tombstone from — fall back to a hard delete.
            spendDao.deleteSpendByUuid(uuid)
            removeFromFirestoreByUuid(uuid, userId)
        }
    }

    fun getAllRecurringBills(userId: String): Flow<List<RecurringBill>> = recurringBillDao.getAllRecurringBills(userId)

    suspend fun insertRecurringBill(bill: RecurringBill) {
        // Stamp the local mutation time so last-write-wins sync can resolve conflicts.
        val bill = bill.copy(updatedAt = System.currentTimeMillis())
        recurringBillDao.insertRecurringBill(bill)
        syncRecurringBillToFirestore(bill)
    }

    suspend fun deleteRecurringBill(bill: RecurringBill) {
        // Soft delete, same as spends — see delete() for the resurrection rationale.
        val tombstone = bill.copy(deleted = true, updatedAt = System.currentTimeMillis())
        recurringBillDao.insertRecurringBill(tombstone)
        syncRecurringBillToFirestore(tombstone)
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
            Log.e(tag, "Error syncing recurring bill to Firestore: ${e.message}")
        }
    }

    suspend fun updateRecurringBill(bill: RecurringBill) {
        // Stamp the local mutation time so last-write-wins sync can resolve conflicts.
        val bill = bill.copy(updatedAt = System.currentTimeMillis())
        recurringBillDao.insertRecurringBill(bill)
        syncRecurringBillToFirestore(bill)
    }

    // ---- Notes ----
    // Notes and their entries live in their own tables/collections and are never mixed
    // into spend analytics. Writes go to Room first, then Firestore, all stamped with a
    // fresh updatedAt for last-write-wins — identical to the spend/bill flow above.

    fun getAllNotes(userId: String): Flow<List<Note>> = notesDao.getAllNotes(userId)

    fun getAllNoteEntries(userId: String): Flow<List<NoteEntry>> = notesDao.getAllNoteEntries(userId)

    suspend fun insertNote(note: Note) {
        val note = note.copy(updatedAt = System.currentTimeMillis())
        notesDao.insertNote(note)
        syncNoteToFirestore(note)
    }

    suspend fun updateNote(note: Note) {
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

    suspend fun deleteNote(note: Note) {
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

    suspend fun insertNoteEntry(entry: NoteEntry) {
        val entry = entry.copy(updatedAt = System.currentTimeMillis())
        notesDao.insertNoteEntry(entry)
        syncNoteEntryToFirestore(entry)
    }

    suspend fun updateNoteEntry(entry: NoteEntry) {
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

    suspend fun deleteNoteEntry(entry: NoteEntry) {
        val now = System.currentTimeMillis()
        recordEntryHistory(entry, HistoryType.DELETED, now)
        val tombstone = entry.copy(deleted = true, updatedAt = now)
        notesDao.insertNoteEntry(tombstone)
        syncNoteEntryToFirestore(tombstone)
    }

    private suspend fun syncNoteToFirestore(note: Note) {
        try {
            firestore.collection("users")
                .document(note.userId)
                .collection("notes")
                .document(note.uuid)
                .set(note)
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error syncing note to Firestore: ${e.message}")
        }
    }

    private suspend fun syncNoteEntryToFirestore(entry: NoteEntry) {
        try {
            firestore.collection("users")
                .document(entry.userId)
                .collection("note_entries")
                .document(entry.uuid)
                .set(entry)
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error syncing note entry to Firestore: ${e.message}")
        }
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

    suspend fun restoreNoteFromHistory(history: NoteHistory) {
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

    suspend fun permanentlyDeleteNoteHistory(history: NoteHistory) {
        tombstoneNoteHistory(history, System.currentTimeMillis())
    }

    private suspend fun tombstoneNoteHistory(history: NoteHistory, now: Long) {
        val tombstone = history.copy(deleted = true, updatedAt = now)
        notesDao.insertNoteHistory(tombstone)
        syncNoteHistoryToFirestore(tombstone)
    }

    suspend fun clearNoteHistory(userId: String, type: String) {
        val now = System.currentTimeMillis()
        notesDao.tombstoneNoteHistoryByType(userId, type, now)
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("note_history")
                .whereEqualTo("historyType", type)
                .get()
                .await()
            val batch = firestore.batch()
            snapshot.documents.forEach {
                batch.update(it.reference, mapOf("deleted" to true, "updatedAt" to now))
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Error clearing note history from Firestore: ${e.message}")
        }
    }

    private suspend fun syncNoteHistoryToFirestore(history: NoteHistory) {
        try {
            firestore.collection("users")
                .document(history.userId)
                .collection("note_history")
                .document(history.historyUuid)
                .set(history)
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error syncing note history to Firestore: ${e.message}")
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
            Log.d(tag, "Successfully synced spend to Firestore: ${spend.uuid}")
        } catch (e: Exception) {
            Log.e(tag, "Error syncing to Firestore: ${e.message}", e)
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
            Log.d(tag, "Successfully removed spend from Firestore: $uuid")
        } catch (e: Exception) {
            Log.e(tag, "Error removing from Firestore: ${e.message}", e)
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
            Log.e(tag, "Error syncing history to Firestore: ${e.message}")
        }
    }

    suspend fun insertChatMessage(message: ChatMessage) {
        // Stamp the local mutation time so last-write-wins sync can resolve conflicts.
        val message = message.copy(updatedAt = System.currentTimeMillis())
        chatDao.insertMessage(message)
        syncChatMessageToFirestore(message)
    }

    private suspend fun syncChatMessageToFirestore(message: ChatMessage) {
        try {
            firestore.collection("users")
                .document(message.userId)
                .collection("chat_messages")
                .document(message.uuid)
                .set(message)
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error syncing chat message to Firestore: ${e.message}")
        }
    }

    suspend fun deleteChatMessage(message: ChatMessage) {
        // Soft delete, same as spends — see delete() for the resurrection rationale.
        val tombstone = message.copy(deleted = true, updatedAt = System.currentTimeMillis())
        chatDao.insertMessage(tombstone)
        syncChatMessageToFirestore(tombstone)
    }
}
