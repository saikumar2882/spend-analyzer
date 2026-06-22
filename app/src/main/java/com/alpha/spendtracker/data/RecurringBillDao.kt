package com.alpha.spendtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringBillDao {
    @Query("SELECT * FROM recurring_bills WHERE userId = :userId")
    fun getAllRecurringBills(userId: String): Flow<List<RecurringBill>>

    @Query("SELECT updatedAt FROM recurring_bills WHERE uuid = :uuid LIMIT 1")
    suspend fun getRecurringBillUpdatedAt(uuid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringBill(bill: RecurringBill)

    @Delete
    suspend fun deleteRecurringBill(bill: RecurringBill)

    @Query("SELECT * FROM recurring_bills WHERE dayOfMonth = :day")
    suspend fun getBillsDueOn(day: Int): List<RecurringBill>

    @Query("SELECT * FROM spends WHERE userId = :userId AND appName = :appName AND purpose = :purpose AND timestamp >= :startTime AND timestamp <= :endTime LIMIT 1")
    suspend fun findMatchingSpend(
        userId: String,
        appName: String,
        purpose: String,
        startTime: Long,
        endTime: Long
    ): Spend?
}
