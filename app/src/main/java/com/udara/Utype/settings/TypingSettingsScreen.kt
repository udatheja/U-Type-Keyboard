package com.udara.Utype.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.udara.Utype.data.KeyboardSettings
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.engine.SinhalaTransliterator

@Composable
fun TypingSettingsScreen(
    settings: KeyboardSettings,
    learnedWordCount: Int,
    viewModel: SettingsViewModel,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var singlishProbe by rememberSaveable { mutableStateOf("ammaa") }
    val liveSinhala = remember(singlishProbe) { SinhalaTransliterator.transliterate(singlishProbe) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Typing & Languages",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            // Default Language
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Default Language",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "U Type also remembers your last used language per app automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageMode.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.defaultLanguage == lang,
                                onClick = { viewModel.setDefaultLanguage(lang) },
                                label = { Text(lang.displayName) },
                                modifier = Modifier.testTag("default_lang_${lang.code}"),
                            )
                        }
                    }
                }
            }

            // Keyboard Preferences Toggles
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Keys & Feedback",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    SettingToggleRow(
                        title = "Dedicated Number Row",
                        subtitle = "Show 1–0 row permanently above QWERTY",
                        checked = settings.showNumberRow,
                        tag = "toggle_number_row",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showNumberRow = v) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Long-Press Hint Characters",
                        subtitle = "Show digits & symbols in top-right key corners",
                        checked = settings.showHintCharacters,
                        tag = "toggle_hint_chars",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showHintCharacters = v) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Sinhala Letter Hints on Keys",
                        subtitle = "Display small Sinhala phonetic guide in Sinhala mode",
                        checked = settings.showSinhalaKeyHints,
                        tag = "toggle_sinhala_hints",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSinhalaKeyHints = v) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Haptic Feedback",
                        subtitle = "Subtle vibration pulse on keypress",
                        checked = settings.hapticFeedback,
                        tag = "toggle_haptic",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(hapticFeedback = v) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Keypress Sound",
                        subtitle = "Play soft click sound when typing",
                        checked = settings.keySound,
                        tag = "toggle_key_sound",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(keySound = v) } },
                    )
                }
            }

            // Smart Typing & Learned Words
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Smart Typing & Dictionary",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    SettingToggleRow(
                        title = "Word Suggestions",
                        subtitle = "Show 3 offline Sinhala & English suggestions above keys",
                        checked = settings.suggestionsEnabled,
                        tag = "toggle_suggestions",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(suggestionsEnabled = v) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Auto-Capitalization (English)",
                        subtitle = "Capitalize the first letter of each sentence",
                        checked = settings.autoCapitalize,
                        tag = "toggle_autocap",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoCapitalize = v) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Double-Space Period",
                        subtitle = "Tap space twice in English to insert a period",
                        checked = settings.doubleSpacePeriod,
                        tag = "toggle_double_space",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(doubleSpacePeriod = v) } },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("clear_learned_words_button"),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Learned Words ($learnedWordCount saved)")
                    }
                }
            }

            // Live Singlish Engine Tester
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Singlish Engine Live Preview",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = singlishProbe,
                        onValueChange = { singlishProbe = it },
                        label = { Text("Enter Singlish (e.g. ammaa, sixhala, shrii)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("singlish_tester_input"),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Text(
                        text = liveSinhala.ifEmpty { "—" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Learned Words?") },
            text = {
                Text("This permanently deletes all $learnedWordCount locally learned words. Bundled Sinhala and English dictionaries remain intact.")
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
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}
