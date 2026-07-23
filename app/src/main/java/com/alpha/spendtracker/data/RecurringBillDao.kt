package com.alpha.spendtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringBillDao {
    @Query("SELECT * FROM recurring_bills WHERE userId = :userId AND deleted = 0")
    fun getAllRecurringBills(userId: String): Flow<List<RecurringBill>>

    // Includes soft-deleted tombstones — used only by SyncWorker.
    @Query("SELECT * FROM recurring_bills WHERE userId = :userId")
    fun getAllRecurringBillsForSync(userId: String): Flow<List<RecurringBill>>

    @Query("SELECT updatedAt FROM recurring_bills WHERE uuid = :uuid LIMIT 1")
    suspend fun getRecurringBillUpdatedAt(uuid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringBill(bill: RecurringBill)

    @Delete
    suspend fun deleteRecurringBill(bill: RecurringBill)

    @Query("DELETE FROM recurring_bills WHERE deleted = 1 AND updatedAt < :threshold")
    suspend fun deleteOldTombstones(threshold: Long)

    // deleted = 0 matters here: a tombstoned bill must never auto-log a spend again.
    @Query("SELECT * FROM recurring_bills WHERE dayOfMonth = :day AND deleted = 0")
    suspend fun getBillsDueOn(day: Int): List<RecurringBill>

    // Intentionally matches soft-deleted tombstones too: if the user deleted this
    // month's auto-logged bill spend, the RecurringBillWorker must not re-create it.
    @Query("SELECT * FROM spends WHERE userId = :userId AND appName = :appName AND purpose = :purpose AND timestamp >= :startTime AND timestamp <= :endTime LIMIT 1")
    suspend fun findMatchingSpend(
        userId: String,
        appName: String,
        purpose: String,
        startTime: Long,
        endTime: Long
    ): Spend?
}
