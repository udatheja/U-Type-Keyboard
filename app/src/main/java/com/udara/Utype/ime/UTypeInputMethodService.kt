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
import android.widget.LinearLayout
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
    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.DARK, true)

    private var activePanel: ActivePanel = ActivePanel.KEYBOARD
    private var layoutMode: LayoutMode = LayoutMode.ALPHA
    private var languageMode: LanguageMode = LanguageMode.ENGLISH
    private var shiftState: ShiftState = ShiftState.OFF
    private var activeFontStyle: StylishFontStyle = StylishFontStyle.NORMAL

    private var isIncognito: Boolean = false
    private var isPasswordField: Boolean = false
    private var currentImeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    private var currentHostPackage: String? = null

    private val sinhalaBuffer = SinhalaTransliterator.ComposingBuffer()
    private val englishBuffer = StringBuilder()

    private var lastShiftTapMs: Long = 0L
    private var lastSpaceTapMs: Long = 0L
    private var justCommittedWordForDoubleSpace: Boolean = false
    private var selectionAnchorPos: Int? = null

    private var clipboardManager: ClipboardManager? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureSystemClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        app = application as UTypeApplication

        // Initialize bundled wordlists & learned words asynchronously off the main thread
        serviceScope.launch {
            app.suggestionEngine.initialize()
        }

        // Observe settings changes reactively
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
        }

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
                    currentInputConnection?.commitText(item.text, 1)
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
                    currentInputConnection?.commitText(emoji, 1)
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
                    currentInputConnection?.commitText(text, 1)
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
        return rootContainer
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

        isPasswordField = isPasswordInputType(inputClass, variation)
        currentImeAction = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        currentHostPackage = info?.packageName

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

        // Refresh language remembered per app + run clipboard cleanup whenever keyboard opens
        serviceScope.launch {
            val savedLang = if (isPasswordField || layoutMode == LayoutMode.EMAIL) {
                LanguageMode.ENGLISH
            } else {
                app.settingsRepository.getLanguageForApp(currentHostPackage) ?: settings.defaultLanguage
            }
            languageMode = savedLang
            app.clipboardRepository.cleanupExpired(settings.clipboardDuration.millis)
            app.suggestionEngine.refreshDynamicData()
            updateAutoCapitalization()
            switchPanel(ActivePanel.KEYBOARD)
            refreshAllViews()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
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
        rootContainer.setBackgroundColor(palette.background)
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
                val selected = currentInputConnection?.getSelectedText(0)?.toString()
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
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        commitComposingBuffer()
        languageMode = languageMode.toggled()
        if (layoutMode != LayoutMode.ALPHA) {
            layoutMode = LayoutMode.ALPHA
        }
        serviceScope.launch {
            app.settingsRepository.setLanguageForApp(currentHostPackage, languageMode)
        }
        updateAutoCapitalization()
        refreshAllViews()
    }

    private fun handleKeyAction(action: KeyAction) {
        // Route keystrokes to Emoji Search when search mode is active
        if (activePanel == ActivePanel.EMOJI && emojiPanel.isSearching()) {
            when (action) {
                is KeyAction.Text -> emojiPanel.appendSearchChar(action.text)
                KeyAction.Space -> emojiPanel.appendSearchChar(" ")
                KeyAction.Backspace -> emojiPanel.backspaceSearchChar()
                KeyAction.Enter -> emojiPanel.exitSearchMode()
                else -> {}
            }
            return
        }

        val ic = currentInputConnection ?: return

        when (action) {
            is KeyAction.Text -> handleTextInput(action.text)
            KeyAction.Shift -> handleShiftTap()
            KeyAction.Backspace -> handleBackspace()
            KeyAction.Space -> handleSpace()
            KeyAction.Enter -> handleEnter()
            KeyAction.SymbolsToggle -> {
                commitComposingBuffer()
                layoutMode = if (layoutMode == LayoutMode.ALPHA || layoutMode == LayoutMode.EMAIL) {
                    LayoutMode.SYMBOLS_1
                } else {
                    LayoutMode.ALPHA
                }
                refreshAllViews()
            }
            KeyAction.SymbolsMore -> {
                layoutMode = if (layoutMode == LayoutMode.SYMBOLS_1) {
                    LayoutMode.SYMBOLS_2
                } else {
                    LayoutMode.SYMBOLS_1
                }
                refreshAllViews()
            }
            KeyAction.Emoji -> switchPanel(ActivePanel.EMOJI)
            KeyAction.LanguageSwitch -> toggleLanguage()
        }
    }

    private fun handleLongPressText(text: String) {
        if (activePanel == ActivePanel.EMOJI && emojiPanel.isSearching()) {
            emojiPanel.appendSearchChar(text)
            return
        }
        val ic = currentInputConnection ?: return
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
        val ic = currentInputConnection ?: return
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
        val ic = currentInputConnection ?: return
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
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(8, 0)?.toString()
        if (before.isNullOrEmpty()) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            return
        }
        // Handle surrogate pairs / emoji variation selectors cleanly
        val deleteCount = computeGraphemeDeleteLength(before)
        ic.deleteSurroundingText(deleteCount, 0)
    }

    private fun computeGraphemeDeleteLength(text: String): Int {
        if (text.isEmpty()) return 1
        var i = text.length
        val last = text[i - 1]
        // Variation selector or skin tone modifier
        if (last == '\uFE0F' && i >= 2) {
            i--
        }
        if (i >= 2 && Character.isLowSurrogate(text[i - 1]) && Character.isHighSurrogate(text[i - 2])) {
            i -= 2
            // If preceded by a Fitzpatrick skin-tone or ZWJ sequence, keep stripping safely
            if (i >= 1 && text[i - 1] == '\u200D') {
                i--
                if (i >= 2 && Character.isLowSurrogate(text[i - 1]) && Character.isHighSurrogate(text[i - 2])) {
                    i -= 2
                } else if (i >= 1) {
                    i--
                }
            }
        } else {
            i = (i - 1).coerceAtLeast(0)
        }
        return (text.length - i).coerceAtLeast(1)
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
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
            topStripView.showTools()
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
        val ic = currentInputConnection ?: return
        commitComposingBuffer()
        justCommittedWordForDoubleSpace = false

        val action = currentImeAction
        val multiLine = (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (!multiLine && action in listOf(
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
        val ic = currentInputConnection
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
        val ic = currentInputConnection ?: return
        sinhalaBuffer.clear()
        englishBuffer.setLength(0)
        ic.commitText("${item.text} ", 1)
        if (!item.isShortcut) {
            maybeLearnWord(item.text, languageMode)
        }
        topStripView.showTools()
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
        val ic = currentInputConnection ?: return
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
        val ic = currentInputConnection
        val selected = ic?.getSelectedText(0)?.toString()
        if (ic != null && !selected.isNullOrEmpty()) {
            val converted = StylishFontEngine.transform(selected, style)
            ic.commitText(converted, 1)
        }
        refreshAllViews()
    }

    private fun handleTextEditorAction(action: TextEditorPanelView.EditorAction, selecting: Boolean) {
        val ic = currentInputConnection ?: return
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
        val ic = currentInputConnection ?: return
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
        val ic = currentInputConnection ?: return
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
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val meta = if (withShift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun sendMetaKey(keyCode: Int, metaState: Int) {
        val ic = currentInputConnection ?: return
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
