/**
 * Data Access Object (DAO) for performing database operations on the spends table.
 */
package com.alpha.spendtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpendDao {
    @Query("SELECT * FROM spends WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllSpends(userId: String): Flow<List<Spend>>

    @Query("SELECT * FROM spends WHERE uuid = :uuid LIMIT 1")
    suspend fun getSpendByUuid(uuid: String): Spend?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpend(spend: Spend)

    @Delete
    suspend fun deleteSpend(spend: Spend)

    @Query("DELETE FROM spends WHERE uuid = :uuid")
    suspend fun deleteSpendByUuid(uuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SpendHistory)

    @Query("SELECT * FROM spend_history WHERE userId = :userId AND historyType = :type ORDER BY recordedAt DESC")
    fun getHistory(userId: String, type: String): Flow<List<SpendHistory>>

    @Query("DELETE FROM spend_history WHERE historyUuid = :historyUuid")
    suspend fun deleteHistoryByUuid(historyUuid: String)

    @Query("DELETE FROM spend_history WHERE userId = :userId AND historyType = :type")
    suspend fun deleteHistoryByType(userId: String, type: String)

    @Query("DELETE FROM spend_history WHERE recordedAt < :threshold")
    suspend fun deleteOldHistory(threshold: Long)
}
