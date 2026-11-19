package com.udara.Utype.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udara.Utype.data.KeyboardSettings
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.data.OneHandedMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    settings: KeyboardSettings,
    viewModel: SettingsViewModel,
) {
    val systemDark = isSystemInDarkTheme()
    val previewPalette = KeyboardThemeId.resolvePalette(settings.themeId, systemDark)

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
                text = "Appearance & Layout",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            // Live Interactive Mini-Keyboard Preview
            MiniKeyboardPreview(settings = settings, palette = previewPalette)

            // Theme Picker
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Keyboard Theme",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KeyboardThemeId.entries.forEach { theme ->
                            val selected = settings.themeId == theme
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .clickable { viewModel.setTheme(theme) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("theme_option_${theme.id}"),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(theme.previewSwatchHex))
                                )
                                Text(
                                    text = theme.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // Key Dimensions & Geometry Sliders
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Key Dimensions & Spacing",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    SettingSliderRow(
                        label = "Key Height",
                        value = settings.keyHeightDp,
                        range = 42f..64f,
                        unit = "dp",
                        tag = "key_height_slider",
                        onChange = { viewModel.setKeyHeight(it) },
                    )
                    SettingSliderRow(
                        label = "Horizontal Key Gap",
                        value = settings.keyHorizontalGapDp,
                        range = 6f..12f,
                        unit = "dp",
                        tag = "key_hgap_slider",
                        onChange = { viewModel.setHorizontalGap(it) },
                    )
                    SettingSliderRow(
                        label = "Vertical Row Gap",
                        value = settings.keyVerticalGapDp,
                        range = 8f..14f,
                        unit = "dp",
                        tag = "key_vgap_slider",
                        onChange = { viewModel.setVerticalGap(it) },
                    )
                    SettingSliderRow(
                        label = "Key Corner Roundness",
                        value = settings.keyCornerRadiusDp,
                        range = 4f..20f,
                        unit = "dp",
                        tag = "key_radius_slider",
                        onChange = { viewModel.setCornerRadius(it) },
                    )
                    SettingSliderRow(
                        label = "Bottom Padding (Gesture Bar)",
                        value = settings.bottomPaddingDp,
                        range = 0f..36f,
                        unit = "dp",
                        tag = "bottom_padding_slider",
                        onChange = { viewModel.setBottomPadding(it) },
                    )
                }
            }

            // One-Handed Mode
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
                        text = "One-Handed Mode",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Compact the keys to the left or right edge for effortless thumb typing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OneHandedMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.oneHandedMode == mode,
                                onClick = { viewModel.setOneHandedMode(mode) },
                                label = { Text(mode.displayName) },
                                modifier = Modifier.testTag("one_handed_${mode.id}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniKeyboardPreview(
    settings: KeyboardSettings,
    palette: com.udara.Utype.data.KeyboardPalette,
) {
    val bgColor = Color(palette.background)
    val stripColor = Color(palette.toolStrip)
    val keyColor = Color(palette.keyBg)
    val keyTextColor = Color(palette.keyText)
    val hintColor = Color(palette.hintText)
    val accentColor = Color(palette.accent)
    val corner = (settings.keyCornerRadiusDp * 0.7f).dp
    val hGap = (settings.keyHorizontalGapDp * 0.65f).dp
    val vGap = (settings.keyVerticalGapDp * 0.65f).dp
    val keyH = (settings.keyHeightDp * 0.68f).dp

    val fraction = if (settings.oneHandedMode == OneHandedMode.OFF) 1f else 0.84f
    val align = when (settings.oneHandedMode) {
        OneHandedMode.LEFT -> Alignment.Start
        OneHandedMode.RIGHT -> Alignment.End
        OneHandedMode.OFF -> Alignment.CenterHorizontally
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
    ) {
        // Mini 7-icon tool strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(stripColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("U", "🔒", "文A", "✎", "A", "📋", "🎙").forEachIndexed { idx, glyph ->
                Text(
                    text = glyph,
                    color = if (idx == 0) accentColor else keyTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .align(align)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(vGap),
        ) {
            val previewRow1 = listOf("q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5", "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(hGap),
            ) {
                previewRow1.forEach { (ch, hint) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(keyH)
                            .clip(RoundedCornerShape(corner))
                            .background(keyColor),
                    ) {
                        if (settings.showHintCharacters) {
                            Text(
                                text = hint,
                                color = hintColor,
                                fontSize = 8.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 1.dp, end = 3.dp),
                            )
                        }
                        Text(
                            text = ch,
                            color = keyTextColor,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            // Bottom row preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(hGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(keyH)
                        .clip(RoundedCornerShape(corner))
                        .background(keyColor),
                ) {
                    Text("සි", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(4.5f)
                        .height(keyH)
                        .clip(RoundedCornerShape(corner))
                        .background(keyColor),
                ) {
                    Text(
                        text = settings.defaultLanguage.spaceLabel,
                        color = keyTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(keyH)
                        .clip(RoundedCornerShape(corner))
                        .background(accentColor),
                ) {
                    Text("↵", color = Color(palette.onAccent), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height((settings.bottomPaddingDp * 0.35f).dp))
        }
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${value.roundToInt()} $unit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.testTag(tag),
        )
    }
}
