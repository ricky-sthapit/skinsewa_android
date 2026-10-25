package org.swasthaconnect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.swasthaconnect.data.auth.EducationalResource
import java.util.Locale

@Composable
internal fun ResourcesScreen(
    admin: Boolean,
    resources: List<EducationalResource>,
    loading: Boolean,
    error: String?,
    message: String?,
    canLoadMore: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (EducationalResource) -> Unit,
    onSave: (Long?, String, android.net.Uri?) -> Unit,
    onDelete: (EducationalResource) -> Unit,
) {
    var editing by remember { mutableStateOf<EducationalResource?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EducationalResource?>(null) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to dashboard") }
            Column(Modifier.weight(1f)) {
                Text(if (admin) "Resource library" else "Resources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (admin) "Manage care guides and learning materials" else "Care guides and learning materials", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (admin) IconButton(onClick = { editing = null; showCreate = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add resource", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(10.dp))
        message?.let { ResourceNotice(it, isError = false) }
        error?.let {
            ResourceNotice(it, isError = true)
            TextButton(onClick = onRefresh, modifier = Modifier.align(Alignment.End)) { Text("Try again") }
        }
        when {
            loading && resources.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Loading resources…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            resources.isEmpty() && error == null -> Text(
                if (admin) "No resources yet. Add a PDF to get started." else "There are no resources available yet.",
                modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(resources, key = { it.id }) { resource ->
                    ResourceCard(
                        resource = resource,
                        admin = admin,
                        onOpen = { onOpen(resource) },
                        onEdit = { editing = resource; showCreate = false },
                        onDelete = { deleting = resource },
                    )
                }
                if (loading) item { CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp) }
                else if (canLoadMore) item {
                    TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("Load more resources") }
                }
            }
        }
    }

    if (showCreate || editing != null) {
        ResourceEditorDialog(
            resource = editing,
            busy = loading,
            onDismiss = { if (!loading) { showCreate = false; editing = null } },
            onSave = { name, uri ->
                onSave(editing?.id, name, uri)
                showCreate = false
                editing = null
            },
        )
    }

    deleting?.let { resource ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete resource?") },
            text = { Text("${resource.displayName} will be removed from the library.") },
            confirmButton = { TextButton(onClick = { onDelete(resource); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ResourceCard(
    resource: EducationalResource,
    admin: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(resource.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${resource.fileName} · ${formatSize(resource.fileSizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open ${resource.displayName}") }
            if (admin) {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit ${resource.displayName}") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${resource.displayName}", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ResourceEditorDialog(
    resource: EducationalResource?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, android.net.Uri?) -> Unit,
) {
    var name by remember(resource?.id) { mutableStateOf(resource?.displayName.orEmpty()) }
    var selectedUri by remember(resource?.id) { mutableStateOf<android.net.Uri?>(null) }
    var selectedFileName by remember(resource?.id) { mutableStateOf<String?>(null) }
    var localError by remember(resource?.id) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        selectedFileName = uri?.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        if (uri != null) localError = null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (resource == null) "Add resource" else "Edit resource") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; localError = null }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedFileName ?: resource?.fileName ?: "Choose PDF")
                }
                Text("PDF only · up to 20 MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    localError = when {
                        name.isBlank() -> "Enter a display name."
                        resource == null && selectedUri == null -> "Choose a PDF to upload."
                        else -> null
                    }
                    if (localError == null) onSave(name.trim(), selectedUri)
                },
                enabled = !busy,
            ) { Text(if (busy) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun ResourceNotice(message: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Text(message, color = color, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
}
