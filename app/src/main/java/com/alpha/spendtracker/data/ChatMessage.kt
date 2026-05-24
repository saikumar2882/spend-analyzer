package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a chat message in the AI History Assistant.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String
)
