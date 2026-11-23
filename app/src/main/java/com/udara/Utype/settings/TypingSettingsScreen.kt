package com.udara.Utype.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.udara.Utype.engine.PersonalWordItem
import com.udara.Utype.engine.SinhalaTransliterator

@Composable
fun TypingSettingsScreen(
    settings: KeyboardSettings,
    learnedWordCount: Int,
    personalWords: List<PersonalWordItem>,
    viewModel: SettingsViewModel,
    onOpenPhoneticIndex: () -> Unit = {},
) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Default Language
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Default Language",
                        style = MaterialTheme.typography.titleMedium,
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

            // Smart Suggestions & Spell Check (Section 3E)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Smart Suggestions & Spelling",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    SettingToggleRow(
                        title = "Suggestion Bar",
                        subtitle = "Show the suggestion bar above the keys while typing",
                        checked = settings.suggestionBarEnabled,
                        tag = "toggle_suggestion_bar",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(suggestionBarEnabled = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Show Suggestions",
                        subtitle = "Display up to 5 offline completions & candidates",
                        checked = settings.suggestionsEnabled,
                        tag = "toggle_suggestions",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(suggestionsEnabled = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Spelling Correction",
                        subtitle = "Suggest fixes while typing or when tapping an existing word",
                        checked = settings.spellingCorrectionEnabled,
                        tag = "toggle_spelling_correction",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(spellingCorrectionEnabled = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Auto-Correct on Space",
                        subtitle = "Replace misspelled words on Space (press Backspace to undo)",
                        checked = settings.autoCorrectOnSpace,
                        tag = "toggle_autocorrect_space",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoCorrectOnSpace = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Learn from My Typing",
                        subtitle = "Rank your frequent words higher (disabled in incognito & sensitive fields)",
                        checked = settings.learnFromTyping,
                        tag = "toggle_learn_typing",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(learnFromTyping = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Block Offensive Suggestions",
                        subtitle = "Never auto-suggest or auto-correct to offensive words",
                        checked = settings.blockOffensiveSuggestions,
                        tag = "toggle_block_offensive",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(blockOffensiveSuggestions = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "English Dictionary",
                        subtitle = "Offline English completions, bigrams, and spell check",
                        checked = settings.englishDictionaryEnabled,
                        tag = "toggle_english_dict",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(englishDictionaryEnabled = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Sinhala Dictionary",
                        subtitle = "Offline Sinhala & Singlish phonetic fuzzy suggestions",
                        checked = settings.sinhalaDictionaryEnabled,
                        tag = "toggle_sinhala_dict",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(sinhalaDictionaryEnabled = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Auto-Capitalization",
                        subtitle = "Capitalize the start of each English sentence",
                        checked = settings.autoCapitalize,
                        tag = "toggle_autocap",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoCapitalize = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Double-Space Period",
                        subtitle = "Double tap space in English for a period",
                        checked = settings.doubleSpacePeriod,
                        tag = "toggle_double_space",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(doubleSpacePeriod = v) } },
                    )
                }
            }

            // Encrypted Personal Dictionary Management
            PersonalDictionaryCard(
                learnedWordCount = learnedWordCount,
                personalWords = personalWords,
                viewModel = viewModel,
            )

            // Keys & Feedback
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Keys & Feedback",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    SettingToggleRow(
                        title = "Dedicated Number Row",
                        subtitle = "Show 1–0 row above QWERTY",
                        checked = settings.showNumberRow,
                        tag = "toggle_number_row",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showNumberRow = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Hint Characters",
                        subtitle = "Show numbers and symbols on key corners",
                        checked = settings.showHintCharacters,
                        tag = "toggle_hint_chars",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showHintCharacters = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Sinhala Letter Hints",
                        subtitle = "Show Sinhala phonetic letters on keys in Sinhala mode",
                        checked = settings.showSinhalaKeyHints,
                        tag = "toggle_sinhala_hints",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(showSinhalaKeyHints = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Popup on Keypress",
                        subtitle = "Show enlarged key bubble while typing",
                        checked = settings.keyPreviewPopup,
                        tag = "toggle_key_preview_popup",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(keyPreviewPopup = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Haptic Feedback",
                        subtitle = "Vibrate lightly on keypress",
                        checked = settings.hapticFeedback,
                        tag = "toggle_haptic",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(hapticFeedback = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Keypress Sound",
                        subtitle = "Play soft click sound on keypress",
                        checked = settings.keySound,
                        tag = "toggle_key_sound",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(keySound = v) } },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    SettingToggleRow(
                        title = "Block Screenshots on Sensitive Screens",
                        subtitle = "Enable Android FLAG_SECURE on Clipboard, Backup & Settings (blacks out screen casting)",
                        checked = settings.blockScreenshots,
                        tag = "toggle_block_screenshots",
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(blockScreenshots = v) } },
                    )
                }
            }

            // Singlish Engine Quick Check
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
                        text = "Singlish Preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = singlishProbe,
                        onValueChange = { singlishProbe = it },
                        label = { Text("Type Singlish (e.g. ammaa, sixhala, shrii)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("singlish_tester_input"),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Text(
                        text = liveSinhala.ifEmpty { "—" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(
                        onClick = onOpenPhoneticIndex,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_phonetic_index_button"),
                    ) {
                        Text("Open Full Sinhala Phonetic Letter Index")
                    }
                }
            }
        }
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
                style = MaterialTheme.typography.bodyLarge,
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
