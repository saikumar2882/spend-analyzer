/**
 * Data Access Object (DAO) for performing database operations on the spends table.
 */
package com.alpha.spendtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpendDao {
    @Query("SELECT * FROM spends WHERE userId = :userId AND deleted = 0 ORDER BY timestamp DESC")
    fun getAllSpends(userId: String): Flow<List<Spend>>

    // Includes soft-deleted tombstones — used only by SyncWorker so deletions performed
    // while another device was offline still propagate through the periodic upload.
    @Query("SELECT * FROM spends WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllSpendsForSync(userId: String): Flow<List<Spend>>

    @Query("DELETE FROM spends WHERE deleted = 1 AND updatedAt < :threshold")
    suspend fun deleteOldTombstones(threshold: Long)

    @Query("SELECT * FROM spends WHERE uuid = :uuid LIMIT 1")
    suspend fun getSpendByUuid(uuid: String): Spend?

    @Query("SELECT updatedAt FROM spends WHERE uuid = :uuid LIMIT 1")
    suspend fun getSpendUpdatedAt(uuid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpend(spend: Spend)

    @Delete
    suspend fun deleteSpend(spend: Spend)

    @Query("DELETE FROM spends WHERE uuid = :uuid")
    suspend fun deleteSpendByUuid(uuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SpendHistory)

    @Query("SELECT * FROM spend_history WHERE userId = :userId AND historyType = :type AND deleted = 0 ORDER BY recordedAt DESC")
    fun getHistory(userId: String, type: String): Flow<List<SpendHistory>>

    // Includes tombstones and both history types — used only by SyncWorker.
    @Query("SELECT * FROM spend_history WHERE userId = :userId")
    fun getAllHistoryForSync(userId: String): Flow<List<SpendHistory>>

    @Query("SELECT updatedAt FROM spend_history WHERE historyUuid = :historyUuid LIMIT 1")
    suspend fun getHistoryUpdatedAt(historyUuid: String): Long?

    @Query("DELETE FROM spend_history WHERE historyUuid = :historyUuid")
    suspend fun deleteHistoryByUuid(historyUuid: String)

    @Query("UPDATE spend_history SET deleted = 1, updatedAt = :now WHERE userId = :userId AND historyType = :type AND deleted = 0")
    suspend fun tombstoneHistoryByType(userId: String, type: String, now: Long)

    // Scoped clears so the main-history trash and the lend/borrow trash empty independently.
    @Query("UPDATE spend_history SET deleted = 1, updatedAt = :now WHERE userId = :userId AND historyType = :type AND deleted = 0 AND purpose IN ('Lending', 'Borrowing')")
    suspend fun tombstoneLendBorrowHistoryByType(userId: String, type: String, now: Long)

    @Query("UPDATE spend_history SET deleted = 1, updatedAt = :now WHERE userId = :userId AND historyType = :type AND deleted = 0 AND purpose NOT IN ('Lending', 'Borrowing')")
    suspend fun tombstoneRegularHistoryByType(userId: String, type: String, now: Long)

    @Query("DELETE FROM spend_history WHERE recordedAt < :threshold")
    suspend fun deleteOldHistory(threshold: Long)
}
