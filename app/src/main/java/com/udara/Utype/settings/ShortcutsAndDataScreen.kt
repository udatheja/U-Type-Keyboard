package com.udara.Utype.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.udara.Utype.data.ClipboardDuration
import com.udara.Utype.data.ClipboardItem
import com.udara.Utype.data.KeyboardSettings
import com.udara.Utype.data.TextShortcutEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutsAndDataScreen(
    settings: KeyboardSettings,
    shortcuts: List<TextShortcutEntity>,
    clipboardItems: List<ClipboardItem>,
    viewModel: SettingsViewModel,
    focusShortcutsOnly: Boolean = false,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var shortCodeInput by remember { mutableStateOf("") }
    var expansionInput by remember { mutableStateOf("") }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(uri)
        }
    }

    val pinnedCount = clipboardItems.count { it.isPinned }
    val unpinnedCount = clipboardItems.count { !it.isPinned }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (focusShortcutsOnly) {
                ShortcutsSection(
                    shortcuts = shortcuts,
                    onAddClick = {
                        shortCodeInput = ""
                        expansionInput = ""
                        showAddDialog = true
                    },
                    onDeleteClick = { viewModel.deleteShortcut(it) },
                )
            } else {
                // Clipboard Settings
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Clipboard History",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        SettingToggleRow(
                            title = "Save Copied Items",
                            subtitle = "Encrypted locally • Paused in Incognito & passwords",
                            checked = settings.clipboardEnabled,
                            tag = "toggle_clipboard_enabled",
                            onCheckedChange = { v -> viewModel.updateSettings { it.copy(clipboardEnabled = v) } },
                        )

                        Text(
                            text = "Auto-Delete Unpinned Items After",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Default is 2 hours. Pinned items never expire, and unpinning starts a fresh timer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ClipboardDuration.entries.forEach { dur ->
                                FilterChip(
                                    selected = settings.clipboardDuration == dur,
                                    onClick = { viewModel.setClipboardDuration(dur) },
                                    label = { Text(dur.label) },
                                    modifier = Modifier.testTag("clip_duration_${dur.minutes}"),
                                )
                            }
                        }

                        Text(
                            text = "Stored locally: $pinnedCount pinned • $unpinnedCount unpinned (max 50)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedButton(
                            onClick = { viewModel.clearUnpinnedClipboard() },
                            enabled = unpinnedCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("clear_unpinned_clipboard_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Clear All Unpinned Items")
                        }
                    }
                }

                // Offline Backup & Restore (JSON)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Local Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Export your settings, shortcuts, learned words, and pinned clipboard items to a JSON file in Downloads.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Button(
                            onClick = { viewModel.exportBackupToDownloads() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("export_downloads_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export JSON to Downloads")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { saveAsLauncher.launch("UType_Backup.json") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_save_as_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Save As...")
                            }
                            OutlinedButton(
                                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("import_backup_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import JSON")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Text Shortcut") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = shortCodeInput,
                        onValueChange = { shortCodeInput = it },
                        label = { Text("Short code (e.g. addr)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shortcut_code_input"),
                    )
                    OutlinedTextField(
                        value = expansionInput,
                        onValueChange = { expansionInput = it },
                        label = { Text("Full expanded phrase") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shortcut_expansion_input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (shortCodeInput.isNotBlank() && expansionInput.isNotBlank()) {
                            viewModel.addShortcut(shortCodeInput, expansionInput)
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_shortcut_confirm_button"),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ShortcutsSection(
    shortcuts: List<TextShortcutEntity>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Type a short code + space to expand it automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onAddClick,
                    modifier = Modifier.testTag("add_shortcut_button"),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (shortcuts.isEmpty()) {
                Text(
                    text = "No shortcuts yet. Tap Add to create one (for example \"addr\" → your full address).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                shortcuts.forEachIndexed { idx, shortcut ->
                    if (idx > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shortcut.shortCode,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = shortcut.expansion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = { onDeleteClick(shortcut.shortCode) },
                            modifier = Modifier.testTag("delete_shortcut_${shortcut.shortCode}"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete shortcut ${shortcut.shortCode}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
