package com.udara.Utype.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.MainActivity
import com.udara.Utype.UTypeApplication
import com.udara.Utype.data.ClipboardItem
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardSettings
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.data.OneHandedMode
import com.udara.Utype.data.RecentEmojiEntity
import com.udara.Utype.engine.SinhalaTransliterator
import com.udara.Utype.engine.StylishFontEngine
import com.udara.Utype.engine.StylishFontStyle
import com.udara.Utype.engine.SuggestionItem
import com.udara.Utype.layouts.KeyAction
import com.udara.Utype.layouts.KeyboardLayouts
import com.udara.Utype.layouts.LayoutMode
import com.udara.Utype.layouts.ShiftState
import com.udara.Utype.panels.ClipboardPanelView
import com.udara.Utype.panels.EmojiPanelView
import com.udara.Utype.panels.StylishFontsPanelView
import com.udara.Utype.panels.TextEditorPanelView
import com.udara.Utype.panels.VoicePanelView
import com.udara.Utype.views.KeyboardCanvasView
import com.udara.Utype.views.TopStripView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UTypeInputMethodService : InputMethodService() {

    private enum class ActivePanel {
        KEYBOARD,
        TEXT_EDITOR,
        STYLISH_FONTS,
        CLIPBOARD,
        EMOJI,
        VOICE
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var app: UTypeApplication
    private lateinit var rootContainer: LinearLayout
    private lateinit var topStripView: TopStripView
    private lateinit var keyboardView: KeyboardCanvasView
    private lateinit var textEditorPanel: TextEditorPanelView
    private lateinit var stylishFontsPanel: StylishFontsPanelView
    private lateinit var clipboardPanel: ClipboardPanelView
    private lateinit var emojiPanel: EmojiPanelView
    private lateinit var voicePanel: VoicePanelView

    private var settings: KeyboardSettings = KeyboardSettings()
    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.CLASSIC_DARK, true)

    private var activePanel: ActivePanel = ActivePanel.KEYBOARD
    private var layoutMode: LayoutMode = LayoutMode.ALPHA
    private var languageMode: LanguageMode = LanguageMode.ENGLISH
    private var shiftState: ShiftState = ShiftState.OFF
    private var activeFontStyle: StylishFontStyle = StylishFontStyle.NORMAL

    private var isIncognito: Boolean = false
    private var isPasswordField: Boolean = false
    private var currentImeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    private var currentNoEnterAction: Boolean = false
    private var currentHostPackage: String? = null
    private var lastHostPackage: String? = null

    /**
     * Monotonic generation incremented on every explicit user language toggle ("සි" / "En" / "文A").
     * Guarantees that any in-flight async DataStore read from `onStartInputView` can NEVER overwrite
     * a language selection made by the user.
     */
    private var languageChangeGeneration: Long = 0L
    private var languageRestoreJob: Job? = null
    private var maintenanceJob: Job? = null

    private val sinhalaBuffer = SinhalaTransliterator.ComposingBuffer()
    private val englishBuffer = StringBuilder()

    private var lastShiftTapMs: Long = 0L
    private var lastSpaceTapMs: Long = 0L
    private var justCommittedWordForDoubleSpace: Boolean = false
    private var selectionAnchorPos: Int? = null

    /** Optional override used strictly in Robolectric unit tests where Android's Window/Binder IC is absent. */
    internal var testInputConnectionOverride: InputConnection? = null
    internal var testEditorInfoOverride: EditorInfo? = null

    private val activeInputConnection: InputConnection?
        get() = testInputConnectionOverride ?: currentInputConnection

    private val activeEditorInfo: EditorInfo?
        get() = testEditorInfoOverride ?: currentInputEditorInfo

    private var clipboardManager: ClipboardManager? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureSystemClipboard()
    }

    // Internal test inspection helpers
    internal val currentLanguageModeForTest: LanguageMode get() = languageMode
    internal val currentLayoutModeForTest: LayoutMode get() = layoutMode
    internal val currentShiftStateForTest: ShiftState get() = shiftState
    internal val isIncognitoForTest: Boolean get() = isIncognito
    internal val activePanelNameForTest: String get() = activePanel.name
    internal val keyboardViewForTest: KeyboardCanvasView get() = keyboardView
    internal val rootContainerForTest: LinearLayout get() = rootContainer
    internal val topStripViewForTest: TopStripView get() = topStripView
    internal fun dispatchKeyActionForTest(action: KeyAction) = handleKeyAction(action)
    internal fun dispatchToolActionForTest(action: TopStripView.ToolAction) = handleToolStripAction(action)

    override fun onCreate() {
        super.onCreate()
        app = application as UTypeApplication

        // Initialize bundled wordlists & learned words asynchronously off the main thread
        serviceScope.launch {
            app.suggestionEngine.initialize()
        }

        // Observe distinct settings changes reactively (never touches user-toggled languageMode)
        serviceScope.launch {
            app.settingsRepository.settingsFlow.collectLatest { updated ->
                settings = updated
                palette = resolveCurrentPalette()
                if (::rootContainer.isInitialized) {
                    refreshAllViews()
                }
            }
        }

        // Listen for clipboard copies while IME service is running
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        languageRestoreJob?.cancel()
        maintenanceJob?.cancel()
        if (::voicePanel.isInitialized) {
            voicePanel.stopAndRelease()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        palette = resolveCurrentPalette()

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
            clipToPadding = false
        }

        // Critical for Android 15/16 edge-to-edge IME windows: pad rootContainer by the real
        // system navigation bar & display cutout insets so Android's bottom-left hide chevron (⌄),
        // gesture pill, and bottom-right IME switcher (⌨) NEVER overlap the bottom row of keys
        // or the Text Editor / Clipboard / Emoji / Fonts / Voice panels.
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            applyInsetsToRoot(view, insets)
            insets
        }
        rootContainer.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                ViewCompat.requestApplyInsets(v)
                syncInsetsFromDecorFallback()
                syncSystemNavigationBarAppearance()
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })

        topStripView = TopStripView(this).apply {
            listener = object : TopStripView.Listener {
                override fun onToolClicked(action: TopStripView.ToolAction) {
                    performFeedback()
                    handleToolStripAction(action)
                }

                override fun onSuggestionClicked(item: SuggestionItem) {
                    performFeedback()
                    applySuggestion(item)
                }

                override fun onDismissSuggestions() {
                    performFeedback()
                }
            }
        }

        keyboardView = KeyboardCanvasView(this).apply {
            listener = object : KeyboardCanvasView.Listener {
                override fun onKeyAction(action: KeyAction) = handleKeyAction(action)
                override fun onLongPressText(text: String) = handleLongPressText(text)
                override fun onKeyFeedback() = performFeedback()
                override fun onOneHandedExpand() {
                    serviceScope.launch {
                        app.settingsRepository.updateSettings { it.copy(oneHandedMode = OneHandedMode.OFF) }
                    }
                }
            }
        }

        textEditorPanel = TextEditorPanelView(this).apply {
            visibility = View.GONE
            listener = object : TextEditorPanelView.Listener {
                override fun onEditorAction(action: TextEditorPanelView.EditorAction, selecting: Boolean) {
                    performFeedback()
                    handleTextEditorAction(action, selecting)
                }

                override fun onClosePanel() {
                    performFeedback()
                    switchPanel(ActivePanel.KEYBOARD)
                }
            }
        }

        stylishFontsPanel = StylishFontsPanelView(this).apply {
            visibility = View.GONE
            listener = object : StylishFontsPanelView.Listener {
                override fun onStyleSelected(style: StylishFontStyle) {
                    performFeedback()
                    handleStylishFontSelection(style)
                }

                override fun onClosePanel() {
                    performFeedback()
                    switchPanel(ActivePanel.KEYBOARD)
                }
            }
        }

        clipboardPanel = ClipboardPanelView(this).apply {
            visibility = View.GONE
            listener = object : ClipboardPanelView.Listener {
                override fun onPasteItem(item: ClipboardItem) {
                    performFeedback()
                    commitComposingBuffer()
                    activeInputConnection?.commitText(item.text, 1)
                    switchPanel(ActivePanel.KEYBOARD)
                }

                override fun onTogglePin(item: ClipboardItem) {
                    performFeedback()
                    serviceScope.launch {
                        app.clipboardRepository.togglePin(item)
                        refreshClipboardPanel()
                    }
                }

                override fun onDeleteItem(item: ClipboardItem) {
                    performFeedback()
                    serviceScope.launch {
                        app.clipboardRepository.deleteItem(item.id)
                        refreshClipboardPanel()
                    }
                }

                override fun onClearUnpinned() {
                    performFeedback()
                    serviceScope.launch {
                        app.clipboardRepository.clearAllUnpinned()
                        refreshClipboardPanel()
                    }
                }

                override fun onClosePanel() {
                    performFeedback()
                    switchPanel(ActivePanel.KEYBOARD)
                }
            }
        }

        emojiPanel = EmojiPanelView(this).apply {
            visibility = View.GONE
            listener = object : EmojiPanelView.Listener {
                override fun onEmojiSelected(emoji: String) {
                    performFeedback()
                    commitComposingBuffer()
                    activeInputConnection?.commitText(emoji, 1)
                    if (!isIncognito && !isPasswordField) {
                        serviceScope.launch(Dispatchers.IO) {
                            app.database.recentEmojiDao().upsert(RecentEmojiEntity(emoji, System.currentTimeMillis()))
                        }
                    }
                }

                override fun onBackspace() {
                    performFeedback()
                    deleteBeforeCursor()
                }

                override fun onClosePanel() {
                    performFeedback()
                    emojiPanel.exitSearchMode()
                    switchPanel(ActivePanel.KEYBOARD)
                }

                override fun onSearchModeChanged(active: Boolean) {
                    if (active) {
                        topStripView.visibility = View.GONE
                        emojiPanel.visibility = View.VISIBLE
                        keyboardView.visibility = View.VISIBLE
                    } else if (activePanel == ActivePanel.EMOJI) {
                        keyboardView.visibility = View.GONE
                    }
                }
            }
        }

        voicePanel = VoicePanelView(this).apply {
            visibility = View.GONE
            listener = object : VoicePanelView.Listener {
                override fun onVoiceResult(text: String) {
                    commitComposingBuffer()
                    activeInputConnection?.commitText(text, 1)
                }

                override fun onClosePanel() {
                    performFeedback()
                    switchPanel(ActivePanel.KEYBOARD)
                }
            }
        }

        rootContainer.addView(topStripView)
        rootContainer.addView(textEditorPanel)
        rootContainer.addView(stylishFontsPanel)
        rootContainer.addView(clipboardPanel)
        rootContainer.addView(emojiPanel)
        rootContainer.addView(voicePanel)
        rootContainer.addView(keyboardView)

        refreshAllViews()
        syncSystemNavigationBarAppearance()
        return rootContainer
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (::rootContainer.isInitialized) {
            ViewCompat.requestApplyInsets(rootContainer)
            syncInsetsFromDecorFallback()
            syncSystemNavigationBarAppearance()
            rootContainer.post {
                if (::rootContainer.isInitialized) {
                    ViewCompat.requestApplyInsets(rootContainer)
                    syncInsetsFromDecorFallback()
                }
            }
        }
    }

    private fun applyInsetsToRoot(view: View, insets: WindowInsetsCompat) {
        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val sysGestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        val cutouts = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val leftInset = maxOf(navBars.left, cutouts.left)
        val rightInset = maxOf(navBars.right, cutouts.right)
        val bottomInset = maxOf(navBars.bottom, sysGestures.bottom)
        if (view.paddingLeft != leftInset ||
            view.paddingRight != rightInset ||
            view.paddingBottom != bottomInset
        ) {
            view.setPadding(leftInset, 0, rightInset, bottomInset)
        }
    }

    private fun syncInsetsFromDecorFallback() {
        if (!::rootContainer.isInitialized) return
        val insets = ViewCompat.getRootWindowInsets(rootContainer)
            ?: window?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
            ?: return
        applyInsetsToRoot(rootContainer, insets)
    }

    private fun syncSystemNavigationBarAppearance() {
        val win = window?.window ?: return
        try {
            @Suppress("DEPRECATION")
            win.navigationBarColor = palette.background
            WindowInsetsControllerCompat(win, win.decorView).isAppearanceLightNavigationBars = !palette.isDark
        } catch (_: Exception) {
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        sinhalaBuffer.clear()
        englishBuffer.setLength(0)
        justCommittedWordForDoubleSpace = false
        selectionAnchorPos = null

        val inputType = info?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val imeOptions = info?.imeOptions ?: 0

        isPasswordField = isPasswordInputType(inputClass, variation)
        currentImeAction = imeOptions and EditorInfo.IME_MASK_ACTION
        val isMultiLine = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        currentNoEnterAction = isMultiLine || (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        val newHostPackage = info?.packageName
        val sameHostRestart = restarting && newHostPackage == lastHostPackage
        currentHostPackage = newHostPackage
        lastHostPackage = newHostPackage

        layoutMode = when {
            inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME -> LayoutMode.NUMBER_PAD
            inputClass == InputType.TYPE_CLASS_TEXT &&
                (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) -> LayoutMode.EMAIL
            else -> LayoutMode.ALPHA
        }

        if (::keyboardView.isInitialized) {
            keyboardView.isPasswordField = isPasswordField
        }

        // Cancel any previous async language restoration so it can never stomp newer state
        languageRestoreJob?.cancel()

        // 1. Resolve language deterministically without race conditions:
        if (isPasswordField || layoutMode == LayoutMode.EMAIL) {
            languageMode = LanguageMode.ENGLISH
        } else if (!sameHostRestart) {
            // Fast synchronous in-memory cache check prevents any flicker or switch-back race
            val cached = app.settingsRepository.getCachedLanguageForApp(currentHostPackage)
            if (cached != null) {
                languageMode = cached
            } else {
                languageMode = settings.defaultLanguage
                val genAtStart = languageChangeGeneration
                val targetPkg = currentHostPackage
                languageRestoreJob = serviceScope.launch {
                    val persisted = app.settingsRepository.getLanguageForApp(targetPkg)
                    // Strict generation guard: if the user pressed "සි"/"En" while disk read was running,
                    // NEVER overwrite their choice!
                    if (persisted != null &&
                        languageChangeGeneration == genAtStart &&
                        targetPkg == currentHostPackage &&
                        languageMode != persisted
                    ) {
                        languageMode = persisted
                        updateAutoCapitalization()
                        if (::rootContainer.isInitialized) {
                            refreshAllViews()
                        }
                    }
                }
            }
        }

        // 2. Immediately bring up the keyboard synchronously on frame 1
        if (::rootContainer.isInitialized) {
            if (!sameHostRestart) {
                switchPanel(ActivePanel.KEYBOARD)
            } else {
                refreshAllViews()
            }
            updateAutoCapitalization()
        }

        // 3. Background maintenance (clipboard expiry + suggestions refresh) — NEVER mutates languageMode/panel
        maintenanceJob?.cancel()
        maintenanceJob = serviceScope.launch {
            app.clipboardRepository.cleanupExpired(settings.clipboardDuration.millis)
            app.suggestionEngine.refreshDynamicData()
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val hasComposing = !sinhalaBuffer.isEmpty || englishBuffer.isNotEmpty()
        if (hasComposing) {
            val cursorLeftComposing = (candidatesStart == -1 && candidatesEnd == -1) ||
                newSelStart != candidatesEnd ||
                newSelEnd != candidatesEnd
            if (cursorLeftComposing && (newSelStart != oldSelStart || newSelEnd != oldSelEnd)) {
                sinhalaBuffer.clear()
                englishBuffer.setLength(0)
                activeInputConnection?.finishComposingText()
                if (::topStripView.isInitialized) {
                    topStripView.showTools()
                }
            }
        }
        if (newSelStart == newSelEnd && sinhalaBuffer.isEmpty && englishBuffer.isEmpty()) {
            updateAutoCapitalization()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        languageRestoreJob?.cancel()
        commitComposingBuffer()
        if (::keyboardView.isInitialized) {
            keyboardView.dismissPreviewPopup()
            keyboardView.dismissAlternatesPopup()
        }
        if (::voicePanel.isInitialized) {
            voicePanel.stopAndRelease()
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        palette = resolveCurrentPalette()
        if (::rootContainer.isInitialized) {
            refreshAllViews()
        }
    }

    private fun resolveCurrentPalette(): KeyboardPalette {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val systemDark = uiMode == Configuration.UI_MODE_NIGHT_YES
        return KeyboardThemeId.resolvePalette(settings.themeId, systemDark)
    }

    private fun refreshAllViews() {
        if (!::rootContainer.isInitialized) return
        rootContainer.setBackgroundColor(palette.background)
        syncSystemNavigationBarAppearance()
        topStripView.applyTheme(palette)
        topStripView.updateStates(
            incognito = isIncognito,
            stylishFontActive = activeFontStyle != StylishFontStyle.NORMAL,
            sinhalaActive = languageMode == LanguageMode.SINHALA,
        )

        val rows = KeyboardLayouts.buildRows(
            layoutMode = layoutMode,
            languageMode = languageMode,
            shiftState = shiftState,
            showNumberRow = settings.showNumberRow,
            showHints = settings.showHintCharacters,
            showSinhalaHints = settings.showSinhalaKeyHints,
            imeAction = currentImeAction,
            noEnterAction = currentNoEnterAction,
        )
        keyboardView.updateState(rows, settings, palette, shiftState)
    }

    private fun switchPanel(target: ActivePanel) {
        if (activePanel == ActivePanel.VOICE && target != ActivePanel.VOICE) {
            voicePanel.stopAndRelease()
        }
        if (activePanel == ActivePanel.EMOJI && target != ActivePanel.EMOJI) {
            emojiPanel.exitSearchMode()
        }
        activePanel = target

        topStripView.visibility = if (target == ActivePanel.KEYBOARD) View.VISIBLE else View.GONE
        keyboardView.visibility = if (target == ActivePanel.KEYBOARD) View.VISIBLE else View.GONE
        textEditorPanel.visibility = if (target == ActivePanel.TEXT_EDITOR) View.VISIBLE else View.GONE
        stylishFontsPanel.visibility = if (target == ActivePanel.STYLISH_FONTS) View.VISIBLE else View.GONE
        clipboardPanel.visibility = if (target == ActivePanel.CLIPBOARD) View.VISIBLE else View.GONE
        emojiPanel.visibility = if (target == ActivePanel.EMOJI) View.VISIBLE else View.GONE
        voicePanel.visibility = if (target == ActivePanel.VOICE) View.VISIBLE else View.GONE

        when (target) {
            ActivePanel.KEYBOARD -> {
                refreshAllViews()
                updateSuggestions()
            }
            ActivePanel.TEXT_EDITOR -> {
                commitComposingBuffer()
                selectionAnchorPos = null
                textEditorPanel.applyTheme(palette)
            }
            ActivePanel.STYLISH_FONTS -> {
                commitComposingBuffer()
                val selected = activeInputConnection?.getSelectedText(0)?.toString()
                stylishFontsPanel.update(palette, activeFontStyle, !selected.isNullOrEmpty())
            }
            ActivePanel.CLIPBOARD -> {
                commitComposingBuffer()
                captureSystemClipboard()
                refreshClipboardPanel()
            }
            ActivePanel.EMOJI -> {
                commitComposingBuffer()
                serviceScope.launch {
                    val recents = withContext(Dispatchers.IO) { app.database.recentEmojiDao().getRecent() }
                    emojiPanel.update(palette, recents)
                }
            }
            ActivePanel.VOICE -> {
                commitComposingBuffer()
                voicePanel.open(palette, defaultSinhala = languageMode == LanguageMode.SINHALA)
            }
        }
    }

    private fun handleToolStripAction(action: TopStripView.ToolAction) {
        when (action) {
            TopStripView.ToolAction.THEME_SETTINGS -> {
                requestHideSelf(0)
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra(MainActivity.EXTRA_OPEN_DESTINATION, MainActivity.DEST_THEMES)
                }
                startActivity(intent)
            }
            TopStripView.ToolAction.INCOGNITO_LOCK -> {
                isIncognito = !isIncognito
                if (isIncognito) {
                    topStripView.showTools()
                }
                refreshAllViews()
            }
            TopStripView.ToolAction.LANGUAGE_SWITCH -> toggleLanguage()
            TopStripView.ToolAction.TEXT_EDITOR -> switchPanel(ActivePanel.TEXT_EDITOR)
            TopStripView.ToolAction.STYLISH_FONTS -> switchPanel(ActivePanel.STYLISH_FONTS)
            TopStripView.ToolAction.CLIPBOARD -> switchPanel(ActivePanel.CLIPBOARD)
            TopStripView.ToolAction.VOICE_MIC -> switchPanel(ActivePanel.VOICE)
        }
    }

    private fun toggleLanguage() {
        // Password fields stay strictly in English so hidden passwords are never transliterated unexpectedly
        if (isPasswordField) {
            languageMode = LanguageMode.ENGLISH
            refreshAllViews()
            return
        }
        // Cancel any pending async language restore from onStartInputView and bump generation
        languageRestoreJob?.cancel()
        languageChangeGeneration++

        commitComposingBuffer()
        val nextMode = languageMode.toggled()
        languageMode = nextMode
        if (layoutMode != LayoutMode.ALPHA) {
            layoutMode = LayoutMode.ALPHA
        }

        // Update synchronous in-memory cache immediately BEFORE async DataStore write
        val targetPkg = currentHostPackage
        app.settingsRepository.cacheLanguageForApp(targetPkg, nextMode)
        serviceScope.launch {
            app.settingsRepository.setLanguageForApp(targetPkg, nextMode)
        }

        updateAutoCapitalization()
        refreshAllViews()
    }

    private fun handleKeyAction(action: KeyAction) {
        // Route keystrokes to Emoji Search when search mode is active
        if (activePanel == ActivePanel.EMOJI && emojiPanel.isSearching()) {
            when (action) {
                is KeyAction.Text -> {
                    emojiPanel.appendSearchChar(action.text)
                    if (shiftState == ShiftState.ONCE) {
                        shiftState = ShiftState.OFF
                        refreshAllViews()
                    }
                }
                KeyAction.Shift -> handleShiftTap()
                KeyAction.Space -> emojiPanel.appendSearchChar(" ")
                KeyAction.Backspace -> emojiPanel.backspaceSearchChar()
                KeyAction.Enter -> emojiPanel.exitSearchMode()
                else -> {}
            }
            return
        }

        // Language switch & layout navigation must work even if InputConnection is temporarily null
        when (action) {
            KeyAction.LanguageSwitch -> {
                toggleLanguage()
                return
            }
            KeyAction.SymbolsToggle -> {
                commitComposingBuffer()
                layoutMode = if (layoutMode == LayoutMode.ALPHA || layoutMode == LayoutMode.EMAIL) {
                    LayoutMode.SYMBOLS_1
                } else {
                    LayoutMode.ALPHA
                }
                refreshAllViews()
                return
            }
            KeyAction.SymbolsMore -> {
                layoutMode = if (layoutMode == LayoutMode.SYMBOLS_1) {
                    LayoutMode.SYMBOLS_2
                } else {
                    LayoutMode.SYMBOLS_1
                }
                refreshAllViews()
                return
            }
            KeyAction.Emoji -> {
                switchPanel(ActivePanel.EMOJI)
                return
            }
            KeyAction.Shift -> {
                handleShiftTap()
                return
            }
            else -> {}
        }

        activeInputConnection ?: return

        when (action) {
            is KeyAction.Text -> handleTextInput(action.text)
            KeyAction.Backspace -> handleBackspace()
            KeyAction.Space -> handleSpace()
            KeyAction.Enter -> handleEnter()
            KeyAction.LanguageSwitch,
            KeyAction.SymbolsToggle,
            KeyAction.SymbolsMore,
            KeyAction.Emoji,
            KeyAction.Shift -> Unit
        }
    }

    private fun handleLongPressText(text: String) {
        if (activePanel == ActivePanel.EMOJI && emojiPanel.isSearching()) {
            emojiPanel.appendSearchChar(text)
            return
        }
        val ic = activeInputConnection ?: return
        commitComposingBuffer()
        justCommittedWordForDoubleSpace = false
        val styled = if (languageMode == LanguageMode.ENGLISH && !isPasswordField) {
            StylishFontEngine.transform(text, activeFontStyle)
        } else text
        ic.commitText(styled, 1)
        updateAutoCapitalization()
        refreshAllViews()
    }

    private fun handleTextInput(text: String) {
        val ic = activeInputConnection ?: return
        justCommittedWordForDoubleSpace = false

        val isSingleLetter = text.length == 1 && text[0].isLetter()
        val isWordChar = text.length == 1 && (text[0].isLetter() || text[0] == '\'')

        if (languageMode == LanguageMode.SINHALA && layoutMode == LayoutMode.ALPHA && !isPasswordField) {
            if (isSingleLetter) {
                val liveSinhala = sinhalaBuffer.append(text[0])
                ic.setComposingText(liveSinhala, 1)
                if (shiftState == ShiftState.ONCE) {
                    shiftState = ShiftState.OFF
                    refreshAllViews()
                }
                updateSuggestions()
                return
            } else {
                // Punctuation or digit commits Sinhala syllable first
                commitComposingBuffer()
                ic.commitText(text, 1)
                return
            }
        }

        // English / Password / Symbols mode
        if (isWordChar && layoutMode == LayoutMode.ALPHA && !isPasswordField && activeFontStyle == StylishFontStyle.NORMAL) {
            englishBuffer.append(text)
            ic.setComposingText(englishBuffer.toString(), 1)
            if (shiftState == ShiftState.ONCE) {
                shiftState = ShiftState.OFF
                refreshAllViews()
            }
            updateSuggestions()
        } else {
            commitComposingBuffer()
            val outText = if (languageMode == LanguageMode.ENGLISH && !isPasswordField) {
                StylishFontEngine.transform(text, activeFontStyle)
            } else {
                text
            }
            ic.commitText(outText, 1)
            if (shiftState == ShiftState.ONCE) {
                shiftState = ShiftState.OFF
                refreshAllViews()
            }
            if (text == "." || text == "!" || text == "?") {
                updateAutoCapitalization()
            }
        }
    }

    private fun handleShiftTap() {
        val now = SystemClock.uptimeMillis()
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> {
                if (now - lastShiftTapMs <= 350L) ShiftState.CAPS_LOCK else ShiftState.OFF
            }
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
        lastShiftTapMs = now
        refreshAllViews()
    }

    private fun handleBackspace() {
        val ic = activeInputConnection ?: return
        justCommittedWordForDoubleSpace = false

        // In Sinhala mode, delete 1 typed Latin letter from composing buffer first!
        if (!sinhalaBuffer.isEmpty) {
            val updated = sinhalaBuffer.backspace()
            if (updated.isNullOrEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            } else {
                ic.setComposingText(updated, 1)
            }
            updateSuggestions()
            return
        }

        if (englishBuffer.isNotEmpty()) {
            englishBuffer.deleteCharAt(englishBuffer.length - 1)
            if (englishBuffer.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            } else {
                ic.setComposingText(englishBuffer.toString(), 1)
            }
            updateSuggestions()
            return
        }

        deleteBeforeCursor()
        updateAutoCapitalization()
    }

    private fun deleteBeforeCursor() {
        val ic = activeInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(24, 0)?.toString()
        if (before.isNullOrEmpty()) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            return
        }
        val deleteCount = computeGraphemeDeleteLength(before)
        ic.deleteSurroundingText(deleteCount, 0)
    }

    internal companion object {
        private const val VARIATION_SELECTOR_16 = 0xFE0F
        private const val VARIATION_SELECTOR_15 = 0xFE0E
        private const val COMBINING_ENCLOSING_KEYCAP = 0x20E3
        private const val ZWJ = 0x200D

        private fun isSkinToneModifier(cp: Int): Boolean = cp in 0x1F3FB..0x1F3FF
        private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF
        private fun isTagChar(cp: Int): Boolean = cp in 0xE0020..0xE007F

        /**
         * Computes the exact UTF-16 char length of the trailing grapheme/emoji cluster so a single
         * Backspace cleanly deletes complex emojis (🇱🇰 flags, 👍🏽 skin-tones, 👨‍👩‍👧‍👦 ZWJ families,
         * ❤️‍🔥 ZWJ hearts, 1️⃣ keycaps) while deleting 1 Sinhala code unit at a time for editing.
         */
        internal fun computeGraphemeDeleteLength(text: String): Int {
            if (text.isEmpty()) return 1
            var idx = text.length

            fun prevCodePoint(end: Int): Pair<Int, Int> {
                val cp = Character.codePointBefore(text, end)
                return cp to Character.charCount(cp)
            }

            val (lastCp, lastLen) = prevCodePoint(idx)

            // 1. Keycap sequence: [0-9#*]\uFE0F?\u20E3
            if (lastCp == COMBINING_ENCLOSING_KEYCAP) {
                idx -= lastLen
                if (idx > 0) {
                    val (cp2, len2) = prevCodePoint(idx)
                    if (cp2 == VARIATION_SELECTOR_16 || cp2 == VARIATION_SELECTOR_15) {
                        idx -= len2
                    }
                }
                if (idx > 0) {
                    val (_, len3) = prevCodePoint(idx)
                    idx -= len3
                }
                return (text.length - idx).coerceAtLeast(1)
            }

            // 2. Flag tag sequences (e.g. England/Scotland/Wales flags ending in U+E007F)
            if (lastCp == 0xE007F) {
                idx -= lastLen
                while (idx > 0) {
                    val (cp, len) = prevCodePoint(idx)
                    idx -= len
                    if (!isTagChar(cp)) break
                }
                return (text.length - idx).coerceAtLeast(1)
            }

            // 3. Regional Indicator flag pairs (e.g. 🇱🇰 = two regional indicator surrogate pairs)
            if (isRegionalIndicator(lastCp)) {
                idx -= lastLen
                if (idx > 0) {
                    val (cp2, len2) = prevCodePoint(idx)
                    if (isRegionalIndicator(cp2)) {
                        idx -= len2
                    }
                }
                return (text.length - idx).coerceAtLeast(1)
            }

            // 4. General emoji / ZWJ / variation selector / skin-tone cluster loop
            fun consumeSingleEmojiUnit(startEnd: Int): Int {
                var pos = startEnd
                if (pos <= 0) return pos
                var (cp, len) = prevCodePoint(pos)
                if (cp == VARIATION_SELECTOR_16 || cp == VARIATION_SELECTOR_15) {
                    pos -= len
                    if (pos <= 0) return pos
                    val next = prevCodePoint(pos)
                    cp = next.first
                    len = next.second
                }
                if (isSkinToneModifier(cp)) {
                    pos -= len
                    if (pos <= 0) return pos
                    val next = prevCodePoint(pos)
                    cp = next.first
                    len = next.second
                    if (cp == VARIATION_SELECTOR_16 || cp == VARIATION_SELECTOR_15) {
                        pos -= len
                        if (pos <= 0) return pos
                        val next2 = prevCodePoint(pos)
                        cp = next2.first
                        len = next2.second
                    }
                }
                pos -= len
                return pos
            }

            idx = consumeSingleEmojiUnit(idx)
            // Keep consuming preceding ZWJ-joined emoji components (e.g. 👨‍👩‍👧‍👦, ❤️‍🔥, 👩‍💻)
            while (idx > 0) {
                val (cp, len) = prevCodePoint(idx)
                if (cp == ZWJ) {
                    idx -= len
                    idx = consumeSingleEmojiUnit(idx)
                } else {
                    break
                }
            }

            return (text.length - idx).coerceAtLeast(1)
        }
    }

    private fun handleSpace() {
        val ic = activeInputConnection ?: return
        val now = SystemClock.uptimeMillis()

        // Check Text Shortcut expansion on space!
        val rawWord = if (!sinhalaBuffer.isEmpty) sinhalaBuffer.rawLatin else englishBuffer.toString()
        val shortcutExpansion = if (!isPasswordField && rawWord.isNotEmpty()) {
            app.suggestionEngine.getShortcutExpansion(rawWord)
        } else null

        if (shortcutExpansion != null) {
            sinhalaBuffer.clear()
            englishBuffer.setLength(0)
            ic.commitText("$shortcutExpansion ", 1)
            if (::topStripView.isInitialized) topStripView.showTools()
            justCommittedWordForDoubleSpace = false
            updateAutoCapitalization()
            return
        }

        // Double-space -> ". " in English mode
        if (languageMode == LanguageMode.ENGLISH &&
            settings.doubleSpacePeriod &&
            !isPasswordField &&
            justCommittedWordForDoubleSpace &&
            sinhalaBuffer.isEmpty &&
            englishBuffer.isEmpty() &&
            now - lastSpaceTapMs <= 450L
        ) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString()
            if (before != null && before.endsWith(" ") && !before.endsWith(". ")) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                justCommittedWordForDoubleSpace = false
                lastSpaceTapMs = 0L
                updateAutoCapitalization()
                return
            }
        }

        val hadWord = !sinhalaBuffer.isEmpty || englishBuffer.isNotEmpty()
        commitComposingBuffer()
        ic.commitText(" ", 1)
        lastSpaceTapMs = now
        justCommittedWordForDoubleSpace = hadWord
        updateAutoCapitalization()
    }

    private fun handleEnter() {
        val ic = activeInputConnection ?: return
        commitComposingBuffer()
        justCommittedWordForDoubleSpace = false

        val action = currentImeAction
        val editorInfo = activeEditorInfo
        val multiLine = ((editorInfo?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val noEnterAction = currentNoEnterAction ||
            (((editorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0)

        if (!multiLine && !noEnterAction && action in listOf(
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_NEXT,
                EditorInfo.IME_ACTION_DONE,
            )
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
            updateAutoCapitalization()
        }
    }

    private fun commitComposingBuffer() {
        val ic = activeInputConnection
        if (!sinhalaBuffer.isEmpty) {
            val committed = sinhalaBuffer.sinhalaText
            sinhalaBuffer.clear()
            ic?.finishComposingText()
            maybeLearnWord(committed, LanguageMode.SINHALA)
        }
        if (englishBuffer.isNotEmpty()) {
            val committed = englishBuffer.toString()
            englishBuffer.setLength(0)
            ic?.finishComposingText()
            maybeLearnWord(committed, LanguageMode.ENGLISH)
        }
        if (::topStripView.isInitialized) {
            topStripView.showTools()
        }
    }

    private fun applySuggestion(item: SuggestionItem) {
        val ic = activeInputConnection ?: return
        sinhalaBuffer.clear()
        englishBuffer.setLength(0)
        ic.commitText("${item.text} ", 1)
        if (!item.isShortcut) {
            maybeLearnWord(item.text, languageMode)
        }
        if (::topStripView.isInitialized) topStripView.showTools()
        justCommittedWordForDoubleSpace = false
        updateAutoCapitalization()
    }

    private fun updateSuggestions() {
        if (!::topStripView.isInitialized) return
        if (!settings.suggestionsEnabled || isPasswordField) {
            topStripView.showTools()
            return
        }

        val composing = if (languageMode == LanguageMode.SINHALA) {
            sinhalaBuffer.sinhalaText
        } else {
            englishBuffer.toString()
        }
        val rawLatin = if (languageMode == LanguageMode.SINHALA) {
            sinhalaBuffer.rawLatin
        } else {
            englishBuffer.toString()
        }

        if (composing.isEmpty() && rawLatin.isEmpty()) {
            topStripView.showTools()
            return
        }

        val items = app.suggestionEngine.suggest(
            composingWord = composing,
            language = languageMode,
            rawLatin = rawLatin,
            limit = 3,
        )
        if (items.isEmpty()) {
            topStripView.showTools()
        } else {
            topStripView.showSuggestions(items)
        }
    }

    private fun maybeLearnWord(word: String, language: LanguageMode) {
        if (isIncognito || isPasswordField || word.length < 2) return
        serviceScope.launch {
            app.suggestionEngine.learnWord(word, language)
        }
    }

    private fun updateAutoCapitalization() {
        if (shiftState == ShiftState.CAPS_LOCK) return
        if (languageMode != LanguageMode.ENGLISH || !settings.autoCapitalize || isPasswordField || layoutMode != LayoutMode.ALPHA) {
            if (shiftState == ShiftState.ONCE) {
                shiftState = ShiftState.OFF
                if (::keyboardView.isInitialized) refreshAllViews()
            }
            return
        }
        val ic = activeInputConnection ?: return
        val before = ic.getTextBeforeCursor(3, 0)?.toString().orEmpty()
        val shouldCap = before.isEmpty() ||
            before.endsWith("\n") ||
            before.endsWith(". ") ||
            before.endsWith("! ") ||
            before.endsWith("? ")
        val nextShift = if (shouldCap) ShiftState.ONCE else ShiftState.OFF
        if (nextShift != shiftState) {
            shiftState = nextShift
            if (::keyboardView.isInitialized) refreshAllViews()
        }
    }

    private fun handleStylishFontSelection(style: StylishFontStyle) {
        activeFontStyle = style
        val ic = activeInputConnection
        val selected = ic?.getSelectedText(0)?.toString()
        if (ic != null && !selected.isNullOrEmpty()) {
            val converted = StylishFontEngine.transform(selected, style)
            ic.commitText(converted, 1)
        }
        refreshAllViews()
    }

    private fun handleTextEditorAction(action: TextEditorPanelView.EditorAction, selecting: Boolean) {
        val ic = activeInputConnection ?: return
        when (action) {
            TextEditorPanelView.EditorAction.TOGGLE_SELECT -> {
                if (selecting) {
                    val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                    selectionAnchorPos = extracted?.selectionStart ?: 0
                } else {
                    selectionAnchorPos = null
                }
            }
            TextEditorPanelView.EditorAction.LEFT -> moveCursorHorizontal(-1, selecting)
            TextEditorPanelView.EditorAction.RIGHT -> moveCursorHorizontal(1, selecting)
            TextEditorPanelView.EditorAction.UP -> sendDpadKey(KeyEvent.KEYCODE_DPAD_UP, selecting)
            TextEditorPanelView.EditorAction.DOWN -> sendDpadKey(KeyEvent.KEYCODE_DPAD_DOWN, selecting)
            TextEditorPanelView.EditorAction.HOME -> moveCursorLineEdge(toStart = true, selecting = selecting)
            TextEditorPanelView.EditorAction.END -> moveCursorLineEdge(toStart = false, selecting = selecting)
            TextEditorPanelView.EditorAction.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            TextEditorPanelView.EditorAction.COPY -> ic.performContextMenuAction(android.R.id.copy)
            TextEditorPanelView.EditorAction.CUT -> ic.performContextMenuAction(android.R.id.cut)
            TextEditorPanelView.EditorAction.PASTE -> ic.performContextMenuAction(android.R.id.paste)
            TextEditorPanelView.EditorAction.UNDO -> {
                if (!ic.performContextMenuAction(android.R.id.undo)) {
                    sendMetaKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
                }
            }
            TextEditorPanelView.EditorAction.REDO -> {
                if (!ic.performContextMenuAction(android.R.id.redo)) {
                    sendMetaKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditorPanelView.EditorAction.BACKSPACE -> deleteBeforeCursor()
            TextEditorPanelView.EditorAction.ENTER -> handleEnter()
        }
    }

    private fun moveCursorHorizontal(delta: Int, selecting: Boolean) {
        val ic = activeInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val textLen = extracted.text.length
            val selStart = extracted.selectionStart.coerceIn(0, textLen)
            val selEnd = extracted.selectionEnd.coerceIn(0, textLen)
            if (selecting) {
                val anchor = (selectionAnchorPos ?: selStart).coerceIn(0, textLen)
                selectionAnchorPos = anchor
                val active = if (selEnd != anchor) selEnd else selStart
                val nextActive = (active + delta).coerceIn(0, textLen)
                ic.setSelection(anchor, nextActive)
            } else {
                val base = if (delta < 0) minOf(selStart, selEnd) else maxOf(selStart, selEnd)
                val target = if (selStart != selEnd) base else (base + delta).coerceIn(0, textLen)
                ic.setSelection(target, target)
            }
        } else {
            val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            sendDpadKey(keyCode, selecting)
        }
    }

    private fun moveCursorLineEdge(toStart: Boolean, selecting: Boolean) {
        val ic = activeInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val text = extracted.text.toString()
            val len = text.length
            val selStart = extracted.selectionStart.coerceIn(0, len)
            val selEnd = extracted.selectionEnd.coerceIn(0, len)
            val cur = if (selecting) {
                val anchor = (selectionAnchorPos ?: selStart).coerceIn(0, len)
                selectionAnchorPos = anchor
                if (selEnd != anchor) selEnd else selStart
            } else {
                selEnd
            }
            val target = if (toStart) {
                val prevNl = text.lastIndexOf('\n', (cur - 1).coerceAtLeast(0))
                if (prevNl >= 0 && cur > 0) prevNl + 1 else 0
            } else {
                val nextNl = text.indexOf('\n', cur)
                if (nextNl >= 0) nextNl else len
            }
            if (selecting) {
                val anchor = selectionAnchorPos ?: selStart
                ic.setSelection(anchor, target)
            } else {
                ic.setSelection(target, target)
            }
        } else {
            val keyCode = if (toStart) KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_MOVE_END
            sendDpadKey(keyCode, selecting)
        }
    }

    private fun sendDpadKey(keyCode: Int, withShift: Boolean) {
        val ic = activeInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val meta = if (withShift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun sendMetaKey(keyCode: Int, metaState: Int) {
        val ic = activeInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    private fun captureSystemClipboard() {
        if (!settings.clipboardEnabled || isIncognito || isPasswordField) return
        val cm = clipboardManager ?: return
        val clip = try {
            cm.primaryClip
        } catch (_: Exception) {
            null
        } ?: return
        if (clip.itemCount <= 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (text.isBlank()) return

        serviceScope.launch {
            app.clipboardRepository.saveClip(
                text = text,
                retentionMillis = settings.clipboardDuration.millis,
            )
            if (activePanel == ActivePanel.CLIPBOARD) {
                refreshClipboardPanel()
            }
        }
    }

    private fun refreshClipboardPanel() {
        serviceScope.launch {
            val retentionMs = settings.clipboardDuration.millis
            val items = app.clipboardRepository.getItems(retentionMs)
            val banner = when {
                isPasswordField -> "Password field active — clipboard saving paused."
                isIncognito -> "Incognito lock is ON — copied texts are not saved."
                !settings.clipboardEnabled -> "Clipboard history is turned off in Settings."
                else -> null
            }
            clipboardPanel.update(
                newPalette = palette,
                newItems = items,
                banner = banner,
                formatter = { item -> app.clipboardRepository.formatTimeLeft(item, retentionMs) },
            )
        }
    }

    private fun performFeedback() {
        if (settings.hapticFeedback) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
            }
        }
        if (settings.keySound) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f)
            } catch (_: Exception) {
            }
        }
    }

    private fun isPasswordInputType(inputClass: Int, variation: Int): Boolean {
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}
