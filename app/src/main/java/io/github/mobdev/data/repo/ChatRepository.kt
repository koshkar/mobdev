package io.github.mobdev.data.repo

import io.github.mobdev.data.Credentials
import io.github.mobdev.data.LoginRequest
import io.github.mobdev.data.Message
import io.github.mobdev.data.MessageData
import io.github.mobdev.data.TextPayload
import io.github.mobdev.data.local.ChannelDao
import io.github.mobdev.data.local.MessageDao
import io.github.mobdev.data.local.MessageEntity
import io.github.mobdev.data.local.OutgoingDao
import io.github.mobdev.data.local.OutgoingEntity
import io.github.mobdev.data.network.ApiClient
import io.github.mobdev.data.network.AuthHolder
import io.github.mobdev.data.network.ChatApi
import kotlinx.coroutines.flow.Flow
import okio.IOException

sealed interface NetResult<out T> {
    data class Ok<T>(val value: T) : NetResult<T>
    data object Unauthorized : NetResult<Nothing>
    data class Failure(val cause: Throwable) : NetResult<Nothing>
}

class ChatRepository(
    private val api: ChatApi = ApiClient.api,
    private val authHolder: AuthHolder = ApiClient.authHolder,
    private val channelDao: ChannelDao,
    private val messageDao: MessageDao,
    private val outgoingDao: OutgoingDao
) {

    fun channelsFlow(): Flow<List<String>> = channelDao.observe()
    fun messagesFlow(channel: String): Flow<List<MessageEntity>> = messageDao.observeChannel(channel)
    fun outgoingFlow(channel: String): Flow<List<OutgoingEntity>> = outgoingDao.observeChannel(channel)
    fun queueSize(): Flow<Int> = outgoingDao.count()

    suspend fun login(credentials: Credentials): NetResult<String> = runCatchingNet {
        val response = api.login(LoginRequest(credentials.username, credentials.password))
        when {
            response.isSuccessful -> {
                val token = response.body()?.trim().orEmpty()
                authHolder.setToken(token.ifBlank { null })
                NetResult.Ok(token)
            }
            response.code() == 401 -> NetResult.Unauthorized
            else -> NetResult.Failure(IOException("HTTP ${response.code()}"))
        }
    }

    suspend fun logout(): NetResult<Unit> = runCatchingNet {
        runCatching { api.logout() }
        authHolder.setToken(null)
        NetResult.Ok(Unit)
    }

    suspend fun refreshChannels(): NetResult<Unit> = runCatchingNet {
        val response = api.channels()
        when {
            response.isSuccessful -> {
                channelDao.replaceAll(response.body().orEmpty().distinct())
                NetResult.Ok(Unit)
            }
            response.code() == 401 -> NetResult.Unauthorized
            else -> NetResult.Failure(IOException("HTTP ${response.code()}"))
        }
    }

    /** Loads the latest [PAGE_SIZE] messages for the channel and merges into cache. */
    suspend fun fetchLatest(channel: String): NetResult<Int> = runCatchingNet {
        val response = api.messages(channel, limit = PAGE_SIZE, lastKnownId = Long.MAX_VALUE, reverse = true)
        when {
            response.isSuccessful -> {
                val items = response.body().orEmpty().mapNotNull { it.toEntity(channel) }
                messageDao.insertAll(items)
                NetResult.Ok(items.size)
            }
            response.code() == 401 -> NetResult.Unauthorized
            else -> NetResult.Failure(IOException("HTTP ${response.code()}"))
        }
    }

    /** Loads messages newer than what we have in cache. */
    suspend fun fetchNewer(channel: String): NetResult<Int> = runCatchingNet {
        val lastKnown = messageDao.maxId(channel)
        val response = api.messages(channel, limit = PAGE_SIZE, lastKnownId = lastKnown, reverse = false)
        when {
            response.isSuccessful -> {
                val items = response.body().orEmpty().mapNotNull { it.toEntity(channel) }
                messageDao.insertAll(items)
                NetResult.Ok(items.size)
            }
            response.code() == 401 -> NetResult.Unauthorized
            else -> NetResult.Failure(IOException("HTTP ${response.code()}"))
        }
    }

    /** Loads messages older than the oldest cached one. */
    suspend fun fetchOlder(channel: String): NetResult<Int> = runCatchingNet {
        val oldest = messageDao.minId(channel) ?: return@runCatchingNet NetResult.Ok(0)
        val response = api.messages(channel, limit = PAGE_SIZE, lastKnownId = oldest, reverse = true)
        when {
            response.isSuccessful -> {
                val items = response.body().orEmpty().mapNotNull { it.toEntity(channel) }
                messageDao.insertAll(items)
                NetResult.Ok(items.size)
            }
            response.code() == 401 -> NetResult.Unauthorized
            else -> NetResult.Failure(IOException("HTTP ${response.code()}"))
        }
    }

    /** Adds an outgoing message to the persistent queue. */
    suspend fun enqueueText(channel: String, from: String, text: String) {
        outgoingDao.insert(
            OutgoingEntity(
                channel = channel,
                fromUser = from,
                text = text,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** Tries to send everything in the queue. Stops on first failure / 401. */
    suspend fun flushQueue(): NetResult<Int> = runCatchingNet {
        val pending = outgoingDao.snapshot()
        var sent = 0
        for (item in pending) {
            val message = Message(
                from = item.fromUser,
                to = item.channel,
                data = MessageData(text = TextPayload(item.text))
            )
            val response = api.sendMessage(message)
            when {
                response.isSuccessful -> {
                    outgoingDao.delete(item.id)
                    sent++
                }
                response.code() == 401 -> return@runCatchingNet NetResult.Unauthorized
                else -> return@runCatchingNet NetResult.Failure(
                    IOException("HTTP ${response.code()}")
                )
            }
        }
        NetResult.Ok(sent)
    }

    private fun Message.toEntity(channel: String): MessageEntity? {
        val serverId = id ?: return null
        return MessageEntity(
            channel = channel,
            serverId = serverId,
            fromUser = from,
            text = data.text?.text,
            imageLink = data.image?.link,
            timeMillis = time
        )
    }

    private inline fun <T> runCatchingNet(block: () -> NetResult<T>): NetResult<T> =
        try {
            block()
        } catch (e: IOException) {
            NetResult.Failure(e)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) NetResult.Unauthorized else NetResult.Failure(e)
        }

    companion object {
        const val PAGE_SIZE: Int = 20
    }
}
