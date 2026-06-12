package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.UUID

/**
 * Entity representing a chat message in the AI History Assistant.
 */
@IgnoreExtraProperties
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val uuid: String = "",
    val userId: String = "",
    val text: String = "",
    val fromUser: Boolean = false,
    val timestamp: Long = 0L,
    val sessionId: String = ""
)
