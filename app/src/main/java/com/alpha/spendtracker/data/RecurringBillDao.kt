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

    // userId scoping matters as much as deleted = 0 here: Room is shared across accounts on a
    // device, so an unscoped query fired reminders for a previously signed-in user's bills.
    // deleted = 0 keeps a tombstoned bill from ever auto-logging a spend again.
    @Query("SELECT * FROM recurring_bills WHERE userId = :userId AND dayOfMonth = :day AND deleted = 0")
    suspend fun getBillsDueOn(userId: String, day: Int): List<RecurringBill>
}
