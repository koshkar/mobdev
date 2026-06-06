package io.github.mobdev.ui

import io.github.mobdev.data.ChatItem

sealed interface AuthState {
    data object Resolving : AuthState
    data object NeedsLogin : AuthState
    data class LoggedIn(val username: String) : AuthState
}

data class ChannelsState(
    val items: List<String> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null
)

data class MessagesState(
    val items: List<ChatItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null
)

data class AppState(
    val auth: AuthState = AuthState.Resolving,
    val online: Boolean = false,
    val channels: ChannelsState = ChannelsState(),
    val openChat: String? = null,
    val openImage: String? = null,
    val messagesByChat: Map<String, MessagesState> = emptyMap(),
    val queueSize: Int = 0,
    val loginInFlight: Boolean = false,
    val loginError: LoginError? = null
)

enum class LoginError { InvalidCredentials, Network }
