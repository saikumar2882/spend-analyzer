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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpend(spend: Spend)

    @Delete
    suspend fun deleteSpend(spend: Spend)

    @Query("DELETE FROM spends WHERE uuid = :uuid")
    suspend fun deleteSpendByUuid(uuid: String)
}
