package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a chat message in the AI History Assistant.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val userId: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String
)
