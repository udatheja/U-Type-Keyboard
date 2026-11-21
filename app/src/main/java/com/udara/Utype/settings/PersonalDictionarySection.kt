package com.udara.Utype.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.engine.DictionaryCryptoExporter
import com.udara.Utype.engine.PersonalWordItem

/**
 * Encrypted Personal Dictionary management section inside Settings:
 * - Add word / Remove word / Manage saved words
 * - Clear learned words
 * - Export & Import passphrase-encrypted dictionary via Android SAF (zero storage permissions)
 */
@Composable
fun PersonalDictionaryCard(
    learnedWordCount: Int,
    personalWords: List<PersonalWordItem>,
    viewModel: SettingsViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingExportPassphrase by remember { mutableStateOf<String?>(null) }
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pass = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri != null && !pass.isNullOrEmpty()) {
            viewModel.exportEncryptedDictionaryToUri(uri, pass)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Personal Dictionary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "$learnedWordCount words saved locally with Android Keystore AES-256 encryption. Never leaves your device unless you export it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_personal_word_button"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Word")
                }

                OutlinedButton(
                    onClick = {
                        viewModel.refreshPersonalWords()
                        showManageDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("manage_personal_words_button"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manage")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showExportPassphraseDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("export_dictionary_button"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export")
                }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("import_dictionary_button"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }
            }

            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("clear_learned_words_button"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Learned Words ($learnedWordCount saved)")
            }
        }
    }

    if (showAddDialog) {
        var newWord by remember { mutableStateOf("") }
        var selectedLang by remember { mutableStateOf(LanguageMode.ENGLISH) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add to Personal Dictionary") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { if (it.length <= 40) newWord = it },
                        label = { Text("Single word (English or Sinhala)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_word_input"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageMode.entries.forEach { lang ->
                            FilterChip(
                                selected = selectedLang == lang,
                                onClick = { selectedLang = lang },
                                label = { Text(lang.displayName) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addPersonalWord(newWord, selectedLang)
                        showAddDialog = false
                    },
                    enabled = DictionaryCryptoExporter.isValidSingleWord(newWord.trim()),
                    modifier = Modifier.testTag("confirm_add_word_button"),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showManageDialog) {
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text("Saved Personal Words (${personalWords.size})") },
            text = {
                if (personalWords.isEmpty()) {
                    Text("No personal or learned words saved yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(personalWords, key = { "${it.language.code}:${it.word}" }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.word,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${entry.language.displayName} • used ${entry.frequency}x",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removePersonalWord(entry.word, entry.language) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Remove ${entry.word}",
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageDialog = false }) { Text("Close") }
            },
        )
    }

    if (showExportPassphraseDialog) {
        var passphrase by remember { mutableStateOf("") }
        var confirmPassphrase by remember { mutableStateOf("") }
        val valid = passphrase.length >= DictionaryCryptoExporter.MIN_PASSPHRASE_LENGTH &&
            passphrase == confirmPassphrase
        AlertDialog(
            onDismissRequest = { showExportPassphraseDialog = false },
            title = { Text("Encrypt Dictionary Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your dictionary is encrypted with AES-256-GCM + PBKDF2. Remember this passphrase to import later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase (min 6 characters)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_passphrase_input"),
                    )
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_passphrase_confirm_input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExportPassphrase = passphrase
                        showExportPassphraseDialog = false
                        exportLauncher.launch("UType_PersonalDictionary.enc.json")
                    },
                    enabled = valid,
                    modifier = Modifier.testTag("confirm_export_passphrase_button"),
                ) {
                    Text("Choose Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPassphraseDialog = false }) { Text("Cancel") }
            },
        )
    }

    val importUri = pendingImportUri
    if (importUri != null) {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Decrypt Dictionary Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the passphrase used when exporting this dictionary file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_passphrase_input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = importUri
                        pendingImportUri = null
                        viewModel.importEncryptedDictionaryFromUri(target, passphrase)
                    },
                    enabled = passphrase.isNotEmpty(),
                    modifier = Modifier.testTag("confirm_import_passphrase_button"),
                ) {
                    Text("Decrypt & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Learned Words?") },
            text = {
                Text("This removes all $learnedWordCount locally learned and personal words. Bundled Sinhala and English dictionaries stay intact.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLearnedWords()
                        showClearDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_words_button"),
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}
