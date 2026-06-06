package io.github.mobdev.ui.messages

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.mobdev.R
import io.github.mobdev.data.ChatItem
import io.github.mobdev.data.network.ApiClient
import io.github.mobdev.ui.MessagesState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    channel: String,
    currentUser: String?,
    state: MessagesState,
    online: Boolean,
    queueSize: Int,
    showBack: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onOpenImage: (String) -> Unit
) {
    val pullState = rememberPullToRefreshState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelAvatar(
                            name = channel,
                            size = 36.dp,
                            ringColor = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = displayName(channel),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(
                                    if (online) R.string.status_online else R.string.status_offline
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.messages_back),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            ComposerBar(onSend = onSend)
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            StatusBanners(online = online, queueSize = queueSize)
            PullToRefreshBox(
                isRefreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize().weight(1f)
            ) {
                when {
                    state.loading && state.items.isEmpty() ->
                        CenteredLoader(stringResource(R.string.messages_loading))
                    state.items.isEmpty() && state.error != null ->
                        ErrorState(state.error, onRetry)
                    state.items.isEmpty() ->
                        CenteredText(stringResource(R.string.messages_empty))
                    else -> MessagesList(
                        items = state.items,
                        currentUser = currentUser,
                        canLoadMore = state.canLoadMore && online,
                        loadingMore = state.loadingMore,
                        onLoadMore = onLoadMore,
                        onOpenImage = onOpenImage
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanners(online: Boolean, queueSize: Int) {
    if (!online) {
        BannerRow(
            icon = Icons.Filled.CloudOff,
            text = stringResource(R.string.messages_offline_banner),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    if (queueSize > 0) {
        BannerRow(
            icon = Icons.Filled.Outbox,
            text = stringResource(R.string.messages_queued_banner, queueSize),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun BannerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    container: Color,
    content: Color
) {
    Surface(color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = text, color = content, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MessagesList(
    items: List<ChatItem>,
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
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        reverseLayout = true
    ) {
        items(items, key = { it.key }) { msg ->
            MessageBubble(
                item = msg,
                isOwn = currentUser != null && msg.from == currentUser,
                onOpenImage = onOpenImage
            )
        }
        if (loadingMore) {
            item(key = "loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
    item: ChatItem,
    isOwn: Boolean,
    onOpenImage: (String) -> Unit
) {
    val ownAligned = isOwn || item.pending
    val alignment = if (ownAligned) Alignment.End else Alignment.Start
    val bubbleColor = when {
        item.pending -> MaterialTheme.colorScheme.surfaceVariant
        isOwn -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        item.pending -> MaterialTheme.colorScheme.onSurfaceVariant
        isOwn -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = if (ownAligned) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (!ownAligned) {
            Text(
                text = item.from,
                style = MaterialTheme.typography.labelSmall,
                color = senderColor(item.from),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }
        Surface(
            color = bubbleColor,
            shape = shape,
            tonalElevation = if (ownAligned) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                MessagePayload(item = item, textColor = textColor, onOpenImage = onOpenImage)
                Spacer(Modifier.height(2.dp))
                MessageFooter(item = item, textColor = textColor)
            }
        }
    }
}

@Composable
private fun MessagePayload(
    item: ChatItem,
    textColor: Color,
    onOpenImage: (String) -> Unit
) {
    val text = item.text
    val imageLink = item.imageLink
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
private fun MessageFooter(item: ChatItem, textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.timeMillis?.let { ts ->
            Text(
                text = formatTime(ts),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = if (item.pending) Icons.Filled.Schedule else Icons.Filled.Done,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun ComposerBar(onSend: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        val trimmed = draft.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            draft = ""
            keyboard?.hide()
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
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
                placeholder = { Text(stringResource(R.string.messages_send_hint)) },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Spacer(Modifier.width(8.dp))
            val canSend = draft.trim().isNotEmpty()
            Surface(
                color = if (canSend) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = submit, enabled = canSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.messages_send),
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelAvatar(
    name: String,
    size: androidx.compose.ui.unit.Dp,
    ringColor: Color = Color.Transparent
) {
    val palette = listOf(
        Color(0xFF6650A4), Color(0xFF386A20), Color(0xFFB3261E),
        Color(0xFF006A60), Color(0xFF7A4F00), Color(0xFF265AAB),
        Color(0xFF8E4585), Color(0xFF4A6363)
    )
    val color = palette[(name.hashCode().absoluteValue) % palette.size]
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
    Box(
        modifier = Modifier
            .size(size)
            .background(color = color, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun displayName(channel: String): String {
    return if (channel.endsWith("@channel", ignoreCase = true)) {
        channel.substringBefore("@")
    } else {
        channel
    }
}

private val palette = listOf(
    Color(0xFF6650A4), Color(0xFF386A20), Color(0xFFB3261E),
    Color(0xFF006A60), Color(0xFF7A4F00), Color(0xFF265AAB),
    Color(0xFF8E4585), Color(0xFF4A6363)
)

private fun senderColor(name: String): Color = palette[name.hashCode().absoluteValue % palette.size]

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(unixSecondsOrMillis: Long): String {
    val millis = if (unixSecondsOrMillis < 10_000_000_000L) unixSecondsOrMillis * 1000L
    else unixSecondsOrMillis
    return timeFormatter.format(Date(millis))
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
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
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
