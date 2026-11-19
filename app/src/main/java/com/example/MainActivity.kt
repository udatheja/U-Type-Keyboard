package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.UTypeTheme
import com.udara.Utype.settings.AppearanceScreen
import com.udara.Utype.settings.OnboardingScreen
import com.udara.Utype.settings.PrivacyScreen
import com.udara.Utype.settings.SettingsViewModel
import com.udara.Utype.settings.ShortcutsAndDataScreen
import com.udara.Utype.settings.TypingSettingsScreen

private enum class SettingsTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    SETUP("setup", "Setup", Icons.Default.RocketLaunch),
    THEME("theme", "Theme", Icons.Default.Palette),
    TYPING("typing", "Typing", Icons.Default.Keyboard),
    TOOLS("tools", "Tools", Icons.Default.Build),
    PRIVACY("privacy", "Privacy", Icons.Default.Shield),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SettingsViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
            val learnedCount by vm.learnedWordCount.collectAsStateWithLifecycle()
            val clipboardItems by vm.clipboardItems.collectAsStateWithLifecycle()
            val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()

            var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.SETUP) }
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(statusMessage) {
                statusMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    vm.clearStatusMessage()
                }
            }

            if (selectedTab != SettingsTab.SETUP) {
                BackHandler {
                    selectedTab = SettingsTab.SETUP
                }
            }

            UTypeTheme(themeId = settings.themeId) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ) {
                                    SettingsTab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = selectedTab == tab,
                                            onClick = { selectedTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.label,
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            modifier = Modifier.testTag("nav_tab_${tab.route}"),
                                        )
                                    }
                                }
                            }
                        },
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            if (useRail) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxHeight(),
                                ) {
                                    SettingsTab.entries.forEach { tab ->
                                        NavigationRailItem(
                                            selected = selectedTab == tab,
                                            onClick = { selectedTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.label,
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            modifier = Modifier.testTag("nav_rail_${tab.route}"),
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                when (selectedTab) {
                                    SettingsTab.SETUP -> OnboardingScreen(
                                        onOpenSettingsTab = { selectedTab = SettingsTab.THEME },
                                        onOnboardingCompleted = { vm.markOnboardingDone() },
                                    )
                                    SettingsTab.THEME -> AppearanceScreen(
                                        settings = settings,
                                        viewModel = vm,
                                    )
                                    SettingsTab.TYPING -> TypingSettingsScreen(
                                        settings = settings,
                                        learnedWordCount = learnedCount,
                                        viewModel = vm,
                                    )
                                    SettingsTab.TOOLS -> ShortcutsAndDataScreen(
                                        settings = settings,
                                        shortcuts = shortcuts,
                                        clipboardItems = clipboardItems,
                                        viewModel = vm,
                                    )
                                    SettingsTab.PRIVACY -> PrivacyScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
