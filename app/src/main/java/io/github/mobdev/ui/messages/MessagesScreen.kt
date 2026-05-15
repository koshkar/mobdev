package io.github.mobdev.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.mobdev.R
import io.github.mobdev.data.Message
import io.github.mobdev.network.ApiClient
import io.github.mobdev.ui.MessagesState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    channel: String,
    currentUser: String?,
    state: MessagesState,
    showBack: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onClearSendError: () -> Unit,
    onOpenImage: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val sendFailedText = stringResource(R.string.messages_send_failed)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.sendError) {
        if (state.sendError != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar(sendFailedText) }
            onClearSendError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = channel,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.messages_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ComposerBar(
                enabled = state.sending.not(),
                sending = state.sending,
                onSend = onSend
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading && state.items.isEmpty() -> CenteredLoader(
                    text = stringResource(R.string.messages_loading)
                )
                state.error != null && state.items.isEmpty() -> ErrorState(
                    message = state.error,
                    onRetry = onRetry
                )
                state.items.isEmpty() -> CenteredText(stringResource(R.string.messages_empty))
                else -> MessagesList(
                    items = state.items,
                    currentUser = currentUser,
                    canLoadMore = state.canLoadMore,
                    loadingMore = state.loadingMore,
                    onLoadMore = onLoadMore,
                    onOpenImage = onOpenImage
                )
            }
        }
    }
}

@Composable
private fun MessagesList(
    items: List<Message>,
    currentUser: String?,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenImage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        reverseLayout = true
    ) {
        items(
            items,
            key = { msg -> msg.id?.let { "id_$it" } ?: "fb_${msg.from}_${msg.time ?: 0L}" }
        ) { msg ->
            MessageBubble(
                message = msg,
                isOwn = currentUser != null && msg.from == currentUser,
                onOpenImage = onOpenImage
            )
        }
        if (loadingMore) {
            item(key = "loading-more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.messages_loading_more),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else if (canLoadMore) {
            item(key = "load-more-btn") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = onLoadMore) {
                        Text(stringResource(R.string.messages_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    onOpenImage: (String) -> Unit
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isOwn) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = message.from,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .alpha(0.7f)
        )
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                MessagePayload(message = message, textColor = textColor, onOpenImage = onOpenImage)
            }
        }
    }
}

@Composable
private fun MessagePayload(
    message: Message,
    textColor: androidx.compose.ui.graphics.Color,
    onOpenImage: (String) -> Unit
) {
    val text = message.data.text?.text
    val imageLink = message.data.image?.link
    when {
        !text.isNullOrEmpty() -> Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge
        )
        !imageLink.isNullOrBlank() -> {
            val context = LocalContext.current
            val request = remember(imageLink) {
                ImageRequest.Builder(context)
                    .data(ApiClient.BASE_URL + "thumb/" + imageLink.trimStart('/'))
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.messages_image_alt),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .heightIn(min = 120.dp, max = 220.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenImage(imageLink) }
            )
        }
        else -> Text(
            text = stringResource(R.string.messages_unknown_payload),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ComposerBar(
    enabled: Boolean,
    sending: Boolean,
    onSend: (String) -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    val submit = {
        val trimmed = draft.trim()
        if (enabled && trimmed.isNotEmpty()) {
            onSend(trimmed)
            draft = ""
            keyboard?.hide()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.messages_send_hint)) },
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = submit,
                enabled = enabled && draft.trim().isNotEmpty()
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.messages_send)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredLoader(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, modifier = Modifier.alpha(0.7f))
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.alpha(0.7f))
    }
}

@Composable
private fun ErrorState(message: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.messages_load_error),
                style = MaterialTheme.typography.titleMedium
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.7f)
                )
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.chats_retry))
            }
        }
    }
}
