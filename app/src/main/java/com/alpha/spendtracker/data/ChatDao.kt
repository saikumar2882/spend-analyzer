package com.alpha.spendtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestamp ASC")
    fun getChatMessages(userId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE timestamp < :threshold")
    suspend fun deleteOldMessages(threshold: Long)

    @Query("SELECT COUNT(DISTINCT sessionId) FROM chat_messages WHERE userId = :userId AND timestamp > :since")
    suspend fun getSessionCountSince(userId: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE userId = :userId AND sessionId = :sessionId AND isFromUser = 1")
    suspend fun getMessageCountInSession(userId: String, sessionId: String): Int

    @Query("SELECT sessionId FROM chat_messages WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSessionId(userId: String): String?
}
