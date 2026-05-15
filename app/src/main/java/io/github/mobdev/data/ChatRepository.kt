package io.github.mobdev.data

import io.github.mobdev.network.ApiClient
import io.github.mobdev.network.AuthHolder
import io.github.mobdev.network.ChatApi
import okio.IOException

sealed interface NetResult<out T> {
    data class Ok<T>(val value: T) : NetResult<T>
    data object Unauthorized : NetResult<Nothing>
    data class Failure(val cause: Throwable) : NetResult<Nothing>
}

class ChatRepository(
    private val api: ChatApi = ApiClient.api,
    private val authHolder: AuthHolder = ApiClient.authHolder
) {
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

    suspend fun channels(): NetResult<List<String>> = runCatchingNet {
        val response = api.channels()
        when {
            response.isSuccessful -> NetResult.Ok(response.body().orEmpty())
            response.code() == 401 -> NetResult.Unauthorized
            else -> NetResult.Failure(IOException("HTTP ${response.code()}"))
        }
    }

    suspend fun messages(
        channel: String,
        lastKnownId: Long? = null,
        reverse: Boolean? = null,
        limit: Int = PAGE_SIZE
    ): NetResult<List<Message>> = runCatchingNet {
        val response = api.messages(
            channel = channel,
            limit = limit,
            lastKnownId = lastKnownId,
            reverse = reverse
        )
        when {
            response.isSuccessful -> NetResult.Ok(response.body().orEmpty())
            response.code() == 401 -> NetResult.Unauthorized
            else -> NetResult.Failure(IOException("HTTP ${response.code()}"))
        }
    }

    suspend fun sendText(channel: String, from: String, text: String): NetResult<String> = runCatchingNet {
        val response = api.sendMessage(
            Message(
                from = from,
                to = channel,
                data = MessageData(text = TextPayload(text))
            )
        )
        when {
            response.isSuccessful -> NetResult.Ok(response.body().orEmpty().trim())
            response.code() == 401 -> NetResult.Unauthorized
            else -> NetResult.Failure(IOException("HTTP ${response.code()}"))
        }
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
