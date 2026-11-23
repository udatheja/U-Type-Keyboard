package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.UTypeTheme
import com.udara.Utype.settings.AppDestination
import com.udara.Utype.settings.AppearanceScreen
import com.udara.Utype.settings.BackupScreen
import com.udara.Utype.settings.ClipboardSettingsScreen
import com.udara.Utype.settings.HomeDashboardScreen
import com.udara.Utype.settings.OnboardingScreen
import com.udara.Utype.settings.PhoneticIndexScreen
import com.udara.Utype.settings.PrivacyScreen
import com.udara.Utype.settings.SettingsViewModel
import com.udara.Utype.settings.ShortcutsScreen
import com.udara.Utype.settings.TypingSettingsScreen

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_DESTINATION = "utype_open_destination"
        const val DEST_THEMES = "THEMES"
    }

    private val externalDestinationState = mutableStateOf<AppDestination?>(null)

    private fun parseDestination(intent: Intent?): AppDestination? {
        return when (intent?.getStringExtra(EXTRA_OPEN_DESTINATION)) {
            DEST_THEMES -> AppDestination.THEMES
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDestination(intent)?.let { target ->
            externalDestinationState.value = target
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tapjacking protection across settings & destructive actions
        window.decorView.filterTouchesWhenObscured = true
        parseDestination(intent)?.let { target ->
            externalDestinationState.value = target
        }
        setContent {
            val vm: SettingsViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
            val learnedCount by vm.learnedWordCount.collectAsStateWithLifecycle()
            val personalWords by vm.personalWords.collectAsStateWithLifecycle()
            val clipboardItems by vm.clipboardItems.collectAsStateWithLifecycle()
            val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()

            var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
            val externalTarget by externalDestinationState
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(destination, settings.blockScreenshots) {
                val isSensitiveScreen = destination == AppDestination.CLIPBOARD ||
                    destination == AppDestination.BACKUP ||
                    destination == AppDestination.SETTINGS
                if (settings.blockScreenshots && isSensitiveScreen) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(externalTarget) {
                externalTarget?.let { target ->
                    destination = target
                    externalDestinationState.value = null
                }
            }

            LaunchedEffect(statusMessage) {
                statusMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    vm.clearStatusMessage()
                }
            }

            if (destination != AppDestination.HOME) {
                BackHandler {
                    destination = AppDestination.HOME
                }
            }

            UTypeTheme(themeId = settings.themeId) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (destination != AppDestination.HOME) {
                            val title = when (destination) {
                                AppDestination.THEMES -> "Themes & Layout"
                                AppDestination.SETTINGS -> "Settings"
                                AppDestination.BACKUP -> "Backup & Restore"
                                AppDestination.CLIPBOARD -> "Clipboard"
                                AppDestination.SETUP -> "Keyboard Setup"
                                AppDestination.SHORTCUTS -> "Text Shortcuts"
                                AppDestination.PHONETIC_INDEX -> "Index"
                                AppDestination.PRIVACY -> "About & Privacy"
                                AppDestination.HOME -> ""
                            }
                            TopAppBar(
                                title = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                },
                                navigationIcon = {
                                    IconButton(
                                        onClick = { destination = AppDestination.HOME },
                                        modifier = Modifier.testTag("top_bar_back_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                ),
                            )
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    ) {
                        when (destination) {
                            AppDestination.HOME -> HomeDashboardScreen(
                                onNavigate = { destination = it },
                                onOnboardingCompleted = { vm.markOnboardingDone() },
                            )
                            AppDestination.THEMES -> AppearanceScreen(
                                settings = settings,
                                viewModel = vm,
                            )
                            AppDestination.SETTINGS -> TypingSettingsScreen(
                                settings = settings,
                                learnedWordCount = learnedCount,
                                personalWords = personalWords,
                                viewModel = vm,
                                onOpenPhoneticIndex = { destination = AppDestination.PHONETIC_INDEX },
                            )
                            AppDestination.BACKUP -> BackupScreen(
                                shortcuts = shortcuts,
                                learnedWordCount = learnedCount,
                                clipboardItems = clipboardItems,
                                viewModel = vm,
                                onOpenSettingsDictionary = { destination = AppDestination.SETTINGS },
                            )
                            AppDestination.CLIPBOARD -> ClipboardSettingsScreen(
                                settings = settings,
                                clipboardItems = clipboardItems,
                                viewModel = vm,
                            )
                            AppDestination.SHORTCUTS -> ShortcutsScreen(
                                shortcuts = shortcuts,
                                viewModel = vm,
                            )
                            AppDestination.SETUP -> OnboardingScreen(
                                onOpenSettingsTab = { destination = AppDestination.THEMES },
                                onOnboardingCompleted = { vm.markOnboardingDone() },
                            )
                            AppDestination.PHONETIC_INDEX -> PhoneticIndexScreen()
                            AppDestination.PRIVACY -> PrivacyScreen()
                        }
                    }
                }
            }
        }
    }
}
