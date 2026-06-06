package io.github.mobdev.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT name FROM channels ORDER BY position ASC")
    fun observe(): Flow<List<String>>

    @Query("SELECT name FROM channels ORDER BY position ASC")
    suspend fun snapshot(): List<String>

    @Transaction
    suspend fun replaceAll(channels: List<String>) {
        deleteAll()
        if (channels.isNotEmpty()) {
            insertAll(channels.mapIndexed { i, n -> ChannelEntity(n, i) })
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY serverId DESC")
    fun observeChannel(channel: String): Flow<List<MessageEntity>>

    @Query("SELECT MIN(serverId) FROM messages WHERE channel = :channel")
    suspend fun minId(channel: String): Long?

    @Query("SELECT MAX(serverId) FROM messages WHERE channel = :channel")
    suspend fun maxId(channel: String): Long?

    @Query("SELECT COUNT(*) FROM messages WHERE channel = :channel")
    suspend fun count(channel: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MessageEntity>): List<Long>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface OutgoingDao {
    @Query("SELECT * FROM outgoing ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OutgoingEntity>>

    @Query("SELECT * FROM outgoing WHERE channel = :channel ORDER BY createdAt ASC")
    fun observeChannel(channel: String): Flow<List<OutgoingEntity>>

    @Query("SELECT * FROM outgoing ORDER BY createdAt ASC")
    suspend fun snapshot(): List<OutgoingEntity>

    @Insert
    suspend fun insert(entity: OutgoingEntity): Long

    @Query("DELETE FROM outgoing WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM outgoing")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM outgoing")
    fun count(): Flow<Int>
}
