package io.github.mobdev.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.mobdev.data.ChatRepository
import io.github.mobdev.data.Credentials
import io.github.mobdev.data.CredentialsStorage
import io.github.mobdev.data.Message
import io.github.mobdev.data.NetResult
import io.github.mobdev.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val KEY_OPEN_CHAT = "open_chat"
private const val KEY_OPEN_IMAGE = "open_image"

class AppViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository: ChatRepository = ChatRepository()
    private val credentialsStorage: CredentialsStorage = CredentialsStorage(application)

    private val _state = MutableStateFlow(
        AppState(
            openChat = savedStateHandle.get<String>(KEY_OPEN_CHAT),
            openImage = savedStateHandle.get<String>(KEY_OPEN_IMAGE)
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        observeUnauthorized()
        bootstrap()
    }

    private fun observeUnauthorized() {
        viewModelScope.launch {
            ApiClient.authHolder.unauthorized.collect {
                forceLogout()
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val saved = credentialsStorage.credentials.firstOrNull()
            if (saved == null) {
                update { it.copy(auth = AuthState.NeedsLogin) }
                return@launch
            }
            when (val result = repository.login(saved)) {
                is NetResult.Ok -> onLoggedIn(saved.username)
                NetResult.Unauthorized -> {
                    credentialsStorage.clear()
                    update { it.copy(auth = AuthState.NeedsLogin) }
                }
                is NetResult.Failure -> {
                    // network problem during auto-login: still ask for login
                    update { it.copy(auth = AuthState.NeedsLogin) }
                }
            }
        }
    }

    fun login(username: String, password: String) {
        if (_state.value.loginInFlight) return
        if (username.isBlank() || password.isBlank()) return
        update { it.copy(loginInFlight = true, loginError = null) }
        viewModelScope.launch {
            val creds = Credentials(username.trim(), password)
            when (val result = repository.login(creds)) {
                is NetResult.Ok -> {
                    credentialsStorage.save(creds)
                    onLoggedIn(creds.username)
                    update { it.copy(loginInFlight = false, loginError = null) }
                }
                NetResult.Unauthorized -> update {
                    it.copy(loginInFlight = false, loginError = LoginError.InvalidCredentials)
                }
                is NetResult.Failure -> update {
                    it.copy(loginInFlight = false, loginError = LoginError.Network)
                }
            }
        }
    }

    fun dismissLoginError() {
        update { it.copy(loginError = null) }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            credentialsStorage.clear()
            update {
                AppState(auth = AuthState.NeedsLogin)
            }
            savedStateHandle[KEY_OPEN_CHAT] = null
            savedStateHandle[KEY_OPEN_IMAGE] = null
        }
    }

    private suspend fun onLoggedIn(username: String) {
        update { it.copy(auth = AuthState.LoggedIn(username)) }
        loadChannels()
        val current = _state.value.openChat
        if (current != null) ensureMessages(current)
    }

    fun refreshChannels() {
        viewModelScope.launch { loadChannels() }
    }

    private suspend fun loadChannels() {
        update { it.copy(channels = ChannelsState.Loading) }
        when (val r = repository.channels()) {
            is NetResult.Ok -> update {
                it.copy(channels = ChannelsState.Loaded(r.value.distinct()))
            }
            NetResult.Unauthorized -> forceLogout()
            is NetResult.Failure -> update {
                it.copy(channels = ChannelsState.Error(r.cause.message))
            }
        }
    }

    fun selectChat(channel: String) {
        if (channel.isBlank()) return
        savedStateHandle[KEY_OPEN_CHAT] = channel
        update { it.copy(openChat = channel) }
        viewModelScope.launch { ensureMessages(channel) }
    }

    fun closeChat() {
        savedStateHandle[KEY_OPEN_CHAT] = null
        savedStateHandle[KEY_OPEN_IMAGE] = null
        update { it.copy(openChat = null, openImage = null) }
    }

    fun openImage(path: String) {
        savedStateHandle[KEY_OPEN_IMAGE] = path
        update { it.copy(openImage = path) }
    }

    fun closeImage() {
        savedStateHandle[KEY_OPEN_IMAGE] = null
        update { it.copy(openImage = null) }
    }

    private suspend fun ensureMessages(channel: String) {
        val current = _state.value.messagesByChat[channel]
        if (current != null && (current.items.isNotEmpty() || current.loading || current.error != null)) return
        loadFirstPage(channel)
    }

    private fun mutateMessages(channel: String, transform: (MessagesState) -> MessagesState) {
        update { state ->
            val existing = state.messagesByChat[channel] ?: MessagesState()
            state.copy(messagesByChat = state.messagesByChat + (channel to transform(existing)))
        }
    }

    private suspend fun loadFirstPage(channel: String) {
        mutateMessages(channel) { it.copy(loading = true, error = null) }
        when (val r = repository.messages(channel, lastKnownId = LATEST_SENTINEL, reverse = true)) {
            is NetResult.Ok -> mutateMessages(channel) {
                MessagesState(
                    items = r.value.sortedByDescending { msg -> msg.id ?: Long.MIN_VALUE },
                    loading = false,
                    loadingMore = false,
                    canLoadMore = r.value.size >= ChatRepository.PAGE_SIZE,
                    error = null
                )
            }
            NetResult.Unauthorized -> forceLogout()
            is NetResult.Failure -> mutateMessages(channel) {
                it.copy(loading = false, error = r.cause.message)
            }
        }
    }

    fun loadMore(channel: String) {
        val current = _state.value.messagesByChat[channel] ?: return
        if (current.loadingMore || current.loading || !current.canLoadMore) return
        val minId = current.items.mapNotNull { it.id }.minOrNull() ?: return
        mutateMessages(channel) { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.messages(channel, lastKnownId = minId, reverse = true)) {
                is NetResult.Ok -> mutateMessages(channel) { existing ->
                    val combined = (existing.items + r.value)
                        .distinctBy { it.id ?: (it.from + (it.time ?: 0L)) }
                        .sortedByDescending { it.id ?: Long.MIN_VALUE }
                    existing.copy(
                        items = combined,
                        loadingMore = false,
                        canLoadMore = r.value.size >= ChatRepository.PAGE_SIZE,
                        error = null
                    )
                }
                NetResult.Unauthorized -> forceLogout()
                is NetResult.Failure -> mutateMessages(channel) {
                    it.copy(loadingMore = false, error = r.cause.message)
                }
            }
        }
    }

    private companion object {
        const val LATEST_SENTINEL: Long = Long.MAX_VALUE
    }

    fun retryMessages(channel: String) {
        viewModelScope.launch {
            mutateMessages(channel) { MessagesState() }
            ensureMessages(channel)
        }
    }

    fun sendMessage(channel: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val auth = _state.value.auth as? AuthState.LoggedIn ?: return
        mutateMessages(channel) { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            when (val r = repository.sendText(channel = channel, from = auth.username, text = trimmed)) {
                is NetResult.Ok -> mutateMessages(channel) {
                    it.copy(sending = false, sendError = null)
                }.also { refreshLatest(channel) }
                NetResult.Unauthorized -> forceLogout()
                is NetResult.Failure -> mutateMessages(channel) {
                    it.copy(sending = false, sendError = r.cause.message)
                }
            }
        }
    }

    fun clearSendError(channel: String) {
        mutateMessages(channel) { it.copy(sendError = null) }
    }

    private fun refreshLatest(channel: String) {
        val current = _state.value.messagesByChat[channel] ?: return
        val lastId = current.items.mapNotNull { it.id }.maxOrNull()
        viewModelScope.launch {
            when (val r = repository.messages(channel, lastKnownId = lastId, reverse = false)) {
                is NetResult.Ok -> mutateMessages(channel) { existing ->
                    val combined = (existing.items + r.value)
                        .distinctBy { it.id ?: (it.from + (it.time ?: 0L)) }
                        .sortedByDescending { it.id ?: Long.MIN_VALUE }
                    existing.copy(items = combined)
                }
                NetResult.Unauthorized -> forceLogout()
                is NetResult.Failure -> Unit // silent, send already succeeded
            }
        }
    }

    private fun forceLogout() {
        viewModelScope.launch {
            credentialsStorage.clear()
            ApiClient.authHolder.setToken(null)
            update {
                AppState(auth = AuthState.NeedsLogin)
            }
            savedStateHandle[KEY_OPEN_CHAT] = null
            savedStateHandle[KEY_OPEN_IMAGE] = null
        }
    }

    private inline fun update(transform: (AppState) -> AppState) {
        _state.value = transform(_state.value)
    }
}
