package io.github.mobdev.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val name: String,
    val position: Int
)

/**
 * Message confirmed by the server. Primary key is the server id; we additionally
 * index by channel for fast paging queries.
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["channel", "serverId"], unique = true)]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val channel: String,
    val serverId: Long,
    val fromUser: String,
    val text: String?,
    val imageLink: String?,
    val timeMillis: Long?
)

/**
 * A message the user composed that hasn't yet been confirmed by the server.
 * Stored persistently so it survives process death and is retried when online.
 */
@Entity(tableName = "outgoing")
data class OutgoingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channel: String,
    val fromUser: String,
    val text: String,
    val createdAt: Long
)
