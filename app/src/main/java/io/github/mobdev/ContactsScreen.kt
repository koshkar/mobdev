package io.github.mobdev

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class PermissionState { Unknown, Granted, Denied, PermanentlyDenied }

@Composable
fun ContactsApp(viewModel: ContactsViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity

    var permissionState by rememberSaveable {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) PermissionState.Granted else PermissionState.Unknown
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = when {
            granted -> PermissionState.Granted
            activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) ->
                PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
    }

    LaunchedEffect(permissionState) {
        if (permissionState == PermissionState.Granted) {
            viewModel.ensureLoaded()
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    val loaded = state as? ContactsState.Loaded
    val selectedContact = remember(loaded, selected) {
        loaded?.contacts?.firstOrNull { it.id == selected }
    }

    if (selectedContact != null) {
        BackHandler { selected = null }
        ContactDetailsScreen(
            contact = selectedContact,
            onBack = { selected = null }
        )
    } else {
        ContactsListScreen(
            permissionState = permissionState,
            contactsState = state,
            onRequestPermission = { launcher.launch(Manifest.permission.READ_CONTACTS) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            onContactClick = { selected = it.id }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsListScreen(
    permissionState: PermissionState,
    contactsState: ContactsState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onContactClick: (Contact) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.contacts_title)) })
        }
    ) { innerPadding ->
        when (permissionState) {
            PermissionState.Granted -> ContactsContent(
                state = contactsState,
                onContactClick = onContactClick,
                contentPadding = innerPadding
            )
            PermissionState.PermanentlyDenied -> PermissionPlaceholder(
                title = stringResource(R.string.permission_required_title),
                message = stringResource(R.string.permission_permanently_denied),
                actionText = stringResource(R.string.permission_open_settings),
                onAction = onOpenSettings,
                contentPadding = innerPadding
            )
            PermissionState.Unknown,
            PermissionState.Denied -> PermissionPlaceholder(
                title = stringResource(R.string.permission_required_title),
                message = stringResource(R.string.permission_required_message),
                actionText = stringResource(R.string.permission_grant),
                onAction = onRequestPermission,
                contentPadding = innerPadding
            )
        }
    }
}

@Composable
private fun ContactsContent(
    state: ContactsState,
    onContactClick: (Contact) -> Unit,
    contentPadding: PaddingValues
) {
    when (state) {
        ContactsState.Idle,
        ContactsState.Loading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.contacts_loading))
            }
        }
        is ContactsState.Loaded -> {
            if (state.contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.contacts_empty))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding
                ) {
                    items(state.contacts, key = { it.id }) { contact ->
                        ContactRow(contact = contact, onClick = { onContactClick(contact) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    val displayName = contact.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.contact_no_name)
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = displayName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PermissionPlaceholder(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    contentPadding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailsScreen(contact: Contact, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contact_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = contact.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.contact_no_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            DetailRow(
                label = stringResource(R.string.contact_field_phone),
                value = contact.phoneNumber
            )
            DetailRow(
                label = stringResource(R.string.contact_field_email),
                value = contact.email
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.contact_field_not_set),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
