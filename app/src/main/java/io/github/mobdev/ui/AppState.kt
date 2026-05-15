package io.github.mobdev.ui

import io.github.mobdev.data.Message

sealed interface AuthState {
    data object Resolving : AuthState
    data object NeedsLogin : AuthState
    data class LoggedIn(val username: String) : AuthState
}

sealed interface ChannelsState {
    data object Idle : ChannelsState
    data object Loading : ChannelsState
    data class Loaded(val channels: List<String>) : ChannelsState
    data class Error(val message: String?) : ChannelsState
}

data class MessagesState(
    val items: List<Message> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val sending: Boolean = false,
    val error: String? = null,
    val sendError: String? = null
)

data class AppState(
    val auth: AuthState = AuthState.Resolving,
    val channels: ChannelsState = ChannelsState.Idle,
    val openChat: String? = null,
    val openImage: String? = null,
    val messagesByChat: Map<String, MessagesState> = emptyMap(),
    val loginInFlight: Boolean = false,
    val loginError: LoginError? = null
)

enum class LoginError { InvalidCredentials, Network }
