package io.github.mobdev.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.mobdev.data.ChatItem
import io.github.mobdev.data.Credentials
import io.github.mobdev.data.local.AppDatabase
import io.github.mobdev.data.local.CredentialsStorage
import io.github.mobdev.data.local.MessageEntity
import io.github.mobdev.data.local.OutgoingEntity
import io.github.mobdev.data.network.ApiClient
import io.github.mobdev.data.network.NetworkStatus
import io.github.mobdev.data.repo.ChatRepository
import io.github.mobdev.data.repo.NetResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val KEY_OPEN_CHAT = "open_chat"
private const val KEY_OPEN_IMAGE = "open_image"

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val repository = ChatRepository(
        channelDao = database.channelDao(),
        messageDao = database.messageDao(),
        outgoingDao = database.outgoingDao()
    )
    private val credentialsStorage = CredentialsStorage(application)
    private val networkStatus = NetworkStatus(application, viewModelScope)

    private val _state = MutableStateFlow(
        AppState(
            openChat = savedStateHandle.get<String>(KEY_OPEN_CHAT),
            openImage = savedStateHandle.get<String>(KEY_OPEN_IMAGE)
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val flushMutex = Mutex()
    private var messagesObservationJob: Job? = null

    init {
        observeNetwork()
        observeChannels()
        observeQueueSize()
        observeUnauthorized()
        observeOpenChat()
        bootstrap()
    }

    // ---------- bootstrap ----------

    private fun bootstrap() {
        viewModelScope.launch {
            val saved = credentialsStorage.credentials.firstOrNull()
            if (saved == null) {
                update { it.copy(auth = AuthState.NeedsLogin) }
                return@launch
            }
            when (repository.login(saved)) {
                is NetResult.Ok -> onLoggedIn(saved.username)
                NetResult.Unauthorized -> {
                    credentialsStorage.clear()
                    update { it.copy(auth = AuthState.NeedsLogin) }
                }
                is NetResult.Failure -> {
                    // No network on start — go offline-logged-in so cached data is shown.
                    update { it.copy(auth = AuthState.LoggedIn(saved.username)) }
                }
            }
        }
    }

    // ---------- observers ----------

    private fun observeNetwork() {
        networkStatus.online
            .onEach { online -> update { it.copy(online = online) } }
            .launchIn(viewModelScope)

        // When network appears, refresh server-side stuff
        networkStatus.online
            .drop(1)
            .filter { it }
            .onEach {
                refreshOnConnect()
            }
            .launchIn(viewModelScope)
    }

    private fun observeChannels() {
        repository.channelsFlow()
            .onEach { list ->
                update { it.copy(channels = it.channels.copy(items = list)) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeQueueSize() {
        repository.queueSize()
            .onEach { size -> update { it.copy(queueSize = size) } }
            .launchIn(viewModelScope)
    }

    private fun observeUnauthorized() {
        viewModelScope.launch {
            ApiClient.authHolder.unauthorized.collect { forceLogout() }
        }
    }

    /**
     * Re-observes Room message+outgoing flows for the currently open chat and rebuilds the
     * merged ChatItem list. Cancelled and restarted whenever the open chat changes.
     */
    private fun observeOpenChat() {
        viewModelScope.launch {
            _state.map { it.openChat }.distinctUntilChanged().collect { channel ->
                messagesObservationJob?.cancel()
                if (channel == null) return@collect
                messagesObservationJob = viewModelScope.launch {
                    combine(
                        repository.messagesFlow(channel),
                        repository.outgoingFlow(channel)
                    ) { confirmed, outgoing -> mergeItems(confirmed, outgoing) }
                        .onEach { merged ->
                            mutateMessages(channel) {
                                it.copy(items = merged, error = null)
                            }
                        }
                        .launchIn(this)
                }
            }
        }
    }

    // ---------- auth actions ----------

    fun login(username: String, password: String) {
        if (_state.value.loginInFlight) return
        if (username.isBlank() || password.isBlank()) return
        update { it.copy(loginInFlight = true, loginError = null) }
        viewModelScope.launch {
            val creds = Credentials(username.trim(), password)
            when (repository.login(creds)) {
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
            database.channelDao().deleteAll()
            database.messageDao().deleteAll()
            database.outgoingDao().deleteAll()
            savedStateHandle[KEY_OPEN_CHAT] = null
            savedStateHandle[KEY_OPEN_IMAGE] = null
            _state.value = AppState(auth = AuthState.NeedsLogin, online = _state.value.online)
        }
    }

    private suspend fun onLoggedIn(username: String) {
        update { it.copy(auth = AuthState.LoggedIn(username)) }
        refreshChannels()
        flushQueue()
        val current = _state.value.openChat
        if (current != null) fetchMessages(current, isFirstLoad = true)
    }

    // ---------- chats ----------

    fun refreshChannels() {
        viewModelScope.launch {
            update { it.copy(channels = it.channels.copy(refreshing = true, error = null)) }
            when (val r = repository.refreshChannels()) {
                is NetResult.Ok -> update {
                    it.copy(channels = it.channels.copy(refreshing = false, error = null))
                }
                NetResult.Unauthorized -> forceLogout()
                is NetResult.Failure -> update {
                    it.copy(channels = it.channels.copy(refreshing = false, error = r.cause.message))
                }
            }
        }
    }

    fun selectChat(channel: String) {
        if (channel.isBlank()) return
        savedStateHandle[KEY_OPEN_CHAT] = channel
        update { it.copy(openChat = channel) }
        viewModelScope.launch {
            flushQueue()
            fetchMessages(channel, isFirstLoad = true)
        }
    }

    /** Pull-to-refresh hook: try to send queued messages and pull newer ones. */
    fun refreshChat(channel: String) {
        viewModelScope.launch {
            flushQueue()
            fetchMessages(channel, isFirstLoad = false)
        }
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

    // ---------- messages ----------

    private suspend fun fetchMessages(channel: String, isFirstLoad: Boolean) {
        if (!_state.value.online) return
        mutateMessages(channel) {
            if (isFirstLoad) it.copy(loading = it.items.isEmpty(), error = null)
            else it
        }
        val result = if (isFirstLoad) repository.fetchLatest(channel)
        else repository.fetchNewer(channel)

        when (result) {
            is NetResult.Ok -> mutateMessages(channel) {
                it.copy(
                    loading = false,
                    canLoadMore = if (isFirstLoad) result.value >= ChatRepository.PAGE_SIZE else it.canLoadMore,
                    error = null
                )
            }
            NetResult.Unauthorized -> forceLogout()
            is NetResult.Failure -> mutateMessages(channel) {
                it.copy(loading = false, error = result.cause.message)
            }
        }
    }

    fun loadMore(channel: String) {
        val current = _state.value.messagesByChat[channel] ?: return
        if (current.loadingMore || current.loading || !current.canLoadMore) return
        if (!_state.value.online) return
        mutateMessages(channel) { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.fetchOlder(channel)) {
                is NetResult.Ok -> mutateMessages(channel) {
                    it.copy(
                        loadingMore = false,
                        canLoadMore = r.value >= ChatRepository.PAGE_SIZE,
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

    fun retryMessages(channel: String) {
        viewModelScope.launch { fetchMessages(channel, isFirstLoad = true) }
    }

    fun sendMessage(channel: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val auth = _state.value.auth as? AuthState.LoggedIn ?: return
        viewModelScope.launch {
            repository.enqueueText(channel, auth.username, trimmed)
            flushQueue()
        }
    }

    // ---------- offline sync ----------

    private fun refreshOnConnect() {
        viewModelScope.launch {
            if (_state.value.auth is AuthState.LoggedIn) {
                refreshChannels()
                _state.value.openChat?.let { fetchMessages(it, isFirstLoad = false) }
                flushQueue()
            }
        }
    }

    private suspend fun flushQueue() {
        if (!_state.value.online) return
        if (!flushMutex.tryLock()) return
        try {
            when (val r = repository.flushQueue()) {
                is NetResult.Ok -> {
                    if (r.value > 0) {
                        _state.value.openChat?.let { repository.fetchNewer(it) }
                    }
                }
                NetResult.Unauthorized -> forceLogout()
                is NetResult.Failure -> Unit // silent — messages remain queued
            }
        } finally {
            flushMutex.unlock()
        }
    }

    // ---------- helpers ----------

    private fun mergeItems(
        confirmed: List<MessageEntity>,
        outgoing: List<OutgoingEntity>
    ): List<ChatItem> {
        val confirmedItems = confirmed.map { m ->
            ChatItem(
                key = "id_${m.serverId}",
                serverId = m.serverId,
                from = m.fromUser,
                text = m.text,
                imageLink = m.imageLink,
                timeMillis = m.timeMillis,
                pending = false
            )
        }
        val outgoingItems = outgoing.map { o ->
            ChatItem(
                key = "out_${o.id}",
                serverId = null,
                from = o.fromUser,
                text = o.text,
                imageLink = null,
                timeMillis = o.createdAt,
                pending = true
            )
        }
        // confirmedItems are already DESC by serverId. Put pending after them so reverseLayout
        // shows pending at the very bottom (visually after confirmed).
        return outgoingItems.sortedByDescending { it.timeMillis ?: 0L } + confirmedItems
    }

    private fun mutateMessages(channel: String, transform: (MessagesState) -> MessagesState) {
        update { state ->
            val existing = state.messagesByChat[channel] ?: MessagesState()
            state.copy(messagesByChat = state.messagesByChat + (channel to transform(existing)))
        }
    }

    private fun forceLogout() {
        viewModelScope.launch {
            credentialsStorage.clear()
            ApiClient.authHolder.setToken(null)
            savedStateHandle[KEY_OPEN_CHAT] = null
            savedStateHandle[KEY_OPEN_IMAGE] = null
            _state.value = AppState(auth = AuthState.NeedsLogin, online = _state.value.online)
        }
    }

    private inline fun update(transform: (AppState) -> AppState) {
        _state.value = transform(_state.value)
    }
}
