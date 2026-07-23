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
    val sessionId: String = "",
    // Same soft-delete tombstone scheme as Spend (see comment there). Tombstones keep the
    // original timestamp, so the 12-hour TTL cleanup purges them on the normal schedule.
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)
