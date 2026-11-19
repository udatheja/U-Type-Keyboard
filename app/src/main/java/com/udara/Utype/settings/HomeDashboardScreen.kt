package com.udara.Utype.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BrandWordmarkTint

enum class AppDestination {
    HOME,
    THEMES,
    SETTINGS,
    BACKUP_CLIPBOARD,
    SETUP,
    SHORTCUTS,
    PRIVACY,
}

/**
 * Clean, calm, user-friendly Home screen inspired directly by the user's daily-driver keyboard app:
 * - Minimalist "U Type / Keyboard" wordmark + top-right menu (Privacy & Info)
 * - Clean 2x3 icon grid (Themes, Settings, Backup, Clipboard, Setup, Shortcuts)
 * - Subtle one-tap setup helper banner only when U Type is not yet enabled/selected
 * - Bottom persistent "Type here & try out keyboard" test input box that sits cleanly above the IME
 */
@Composable
fun HomeDashboardScreen(
    onNavigate: (AppDestination) -> Unit,
    onOnboardingCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isEnabled by remember { mutableStateOf(isUTypeEnabled(context)) }
    var isSelected by remember { mutableStateOf(isUTypeSelected(context)) }
    var tryOutText by rememberSaveable { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = isUTypeEnabled(context)
                isSelected = isUTypeSelected(context)
                if (isEnabled && isSelected) {
                    onOnboardingCompleted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
        ) {
            // Top bar: Clean stacked wordmark + menu icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = "U Type",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrandWordmarkTint,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 28.sp,
                    )
                    Text(
                        text = "Keyboard",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrandWordmarkTint,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 28.sp,
                    )
                }

                IconButton(
                    onClick = { onNavigate(AppDestination.PRIVACY) },
                    modifier = Modifier.testTag("open_menu_privacy_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Privacy and About",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // Compact setup prompt only when keyboard isn't active yet
            if (!isEnabled || !isSelected) {
                val promptLabel = if (!isEnabled) {
                    "Step 1 of 2: Enable U Type in Android Settings"
                } else {
                    "Step 2 of 2: Select U Type as active keyboard"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (!isEnabled) {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            } else {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showInputMethodPicker()
                            }
                        }
                        .testTag("quick_setup_banner"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = promptLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (!isEnabled) "Enable" else "Select",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2 x 3 Clean Minimalist Action Grid (matching user screenshot)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    HomeGridTile(
                        icon = Icons.Outlined.Palette,
                        label = "Themes",
                        tag = "home_tile_themes",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppDestination.THEMES) },
                    )
                    HomeGridTile(
                        icon = Icons.Outlined.Settings,
                        label = "Settings",
                        tag = "home_tile_settings",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppDestination.SETTINGS) },
                    )
                    HomeGridTile(
                        icon = Icons.Outlined.Backup,
                        label = "Backup",
                        tag = "home_tile_backup",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppDestination.BACKUP_CLIPBOARD) },
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    HomeGridTile(
                        icon = Icons.Outlined.ContentPaste,
                        label = "Clipboard",
                        tag = "home_tile_clipboard",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppDestination.BACKUP_CLIPBOARD) },
                    )
                    HomeGridTile(
                        icon = Icons.Outlined.Keyboard,
                        label = "Setup",
                        tag = "home_tile_setup",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppDestination.SETUP) },
                    )
                    HomeGridTile(
                        icon = Icons.Outlined.AutoFixHigh,
                        label = "Shortcuts",
                        tag = "home_tile_shortcuts",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AppDestination.SHORTCUTS) },
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom persistent "Type here & try out keyboard" bar (matching screenshot)
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                if (tryOutText.isEmpty()) {
                    Text(
                        text = "Type here & try out keyboard",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = tryOutText,
                    onValueChange = { tryOutText = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp,
                    ),
                    cursorBrush = SolidColor(BrandWordmarkTint),
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_try_keyboard_input"),
                )
            }
        }
    }
}

@Composable
private fun HomeGridTile(
    icon: ImageVector,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = true),
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
