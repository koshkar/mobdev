package io.github.mobdev.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mobdev.R
import io.github.mobdev.ui.chats.ChatsScreen
import io.github.mobdev.ui.image.ImageScreen
import io.github.mobdev.ui.login.LoginScreen
import io.github.mobdev.ui.messages.MessagesScreen

@Composable
fun ChatApp(isLandscape: Boolean, viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val auth = state.auth) {
        AuthState.Resolving -> ResolvingScreen()
        AuthState.NeedsLogin -> {
            BackHandler(enabled = false) {}
            LoginScreen(
                inFlight = state.loginInFlight,
                error = state.loginError,
                onSubmit = viewModel::login,
                onDismissError = viewModel::dismissLoginError
            )
        }
        is AuthState.LoggedIn -> LoggedInRoot(
            state = state,
            username = auth.username,
            isLandscape = isLandscape,
            viewModel = viewModel
        )
    }
}

@Composable
private fun LoggedInRoot(
    state: AppState,
    username: String,
    isLandscape: Boolean,
    viewModel: AppViewModel
) {
    val openImage = state.openImage
    if (openImage != null) {
        BackHandler { viewModel.closeImage() }
        ImageScreen(path = openImage, onClose = viewModel::closeImage)
        return
    }

    if (isLandscape) {
        BackHandler(enabled = state.openChat != null) { viewModel.closeChat() }
        LandscapeContent(state = state, username = username, viewModel = viewModel)
    } else {
        val openChat = state.openChat
        if (openChat != null) {
            BackHandler { viewModel.closeChat() }
            val msgState = state.messagesByChat[openChat] ?: MessagesState()
            MessagesScreen(
                channel = openChat,
                currentUser = username,
                state = msgState,
                online = state.online,
                queueSize = state.queueSize,
                showBack = true,
                onBack = viewModel::closeChat,
                onSend = { text -> viewModel.sendMessage(openChat, text) },
                onLoadMore = { viewModel.loadMore(openChat) },
                onRetry = { viewModel.retryMessages(openChat) },
                onRefresh = { viewModel.refreshChat(openChat) },
                onOpenImage = viewModel::openImage
            )
        } else {
            ChatsScreen(
                state = state.channels,
                online = state.online,
                selectedChat = null,
                showSelectionHighlight = false,
                onSelect = viewModel::selectChat,
                onLogout = viewModel::logout
            )
        }
    }
}

@Composable
private fun LandscapeContent(state: AppState, username: String, viewModel: AppViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(320.dp).fillMaxSize()) {
            ChatsScreen(
                state = state.channels,
                online = state.online,
                selectedChat = state.openChat,
                showSelectionHighlight = true,
                onSelect = viewModel::selectChat,
                onLogout = viewModel::logout
            )
        }
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            val openChat = state.openChat
            if (openChat == null) {
                LandscapePlaceholder()
            } else {
                val msgState = state.messagesByChat[openChat] ?: MessagesState()
                MessagesScreen(
                    channel = openChat,
                    currentUser = username,
                    state = msgState,
                    online = state.online,
                    queueSize = state.queueSize,
                    showBack = true,
                    onBack = viewModel::closeChat,
                    onSend = { text -> viewModel.sendMessage(openChat, text) },
                    onLoadMore = { viewModel.loadMore(openChat) },
                    onRetry = { viewModel.retryMessages(openChat) },
                    onRefresh = { viewModel.refreshChat(openChat) },
                    onOpenImage = viewModel::openImage
                )
            }
        }
    }
}

@Composable
private fun LandscapePlaceholder() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.chats_placeholder_landscape),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ResolvingScreen() {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
