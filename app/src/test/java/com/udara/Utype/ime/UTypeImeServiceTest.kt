package com.udara.Utype.ime

import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.udara.Utype.UTypeApplication
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.layouts.KeyAction
import com.udara.Utype.layouts.LayoutMode
import com.udara.Utype.layouts.ShiftState
import com.udara.Utype.views.TopStripView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * International-standard Robolectric IME lifecycle & Critical User Journey test suite.
 * Covers language switching stability ("සි" / "En"), per-app language memory, input restarts,
 * Sinhala Singlish composing/backspace, cursor jumps, shortcuts, double-space period,
 * multi-line Enter flags, incognito/password privacy, and complex Unicode emoji deletion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UTypeImeServiceTest {

    private lateinit var controller: ServiceController<UTypeInputMethodService>
    private lateinit var service: UTypeInputMethodService
    private lateinit var hostEditText: EditText
    private lateinit var recordingIc: RecordingInputConnection

    private class RecordingInputConnection(private val target: EditText) : BaseInputConnection(target, true) {
        val performedEditorActions = mutableListOf<Int>()
        val sentKeyEvents = mutableListOf<KeyEvent>()

        override fun getEditable() = target.editableText

        override fun performEditorAction(actionCode: Int): Boolean {
            performedEditorActions.add(actionCode)
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            sentKeyEvents.add(event)
            return super.sendKeyEvent(event)
        }
    }

    @Before
    fun setup() {
        controller = Robolectric.buildService(UTypeInputMethodService::class.java).create()
        service = controller.get()
        val app = service.application as UTypeApplication
        app.settingsRepository.clearLanguageCacheForTest()
        hostEditText = EditText(service)
        recordingIc = RecordingInputConnection(hostEditText)
        service.testInputConnectionOverride = recordingIc
        service.onCreateInputView()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun startInput(
        packageName: String? = "com.test.chatapp",
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        imeOptions: Int = EditorInfo.IME_ACTION_DONE,
        restarting: Boolean = false,
    ): EditorInfo {
        val info = EditorInfo().apply {
            this.packageName = packageName
            this.inputType = inputType
            this.imeOptions = imeOptions
        }
        service.testEditorInfoOverride = info
        service.onStartInputView(info, restarting)
        return info
    }

    @Test
    fun pressingSinhalaKeyStaysOnSinhalaAndNeverRevertsToEnglish() {
        // Simulate opening keyboard in an app for the first time
        startInput(packageName = "com.example.freshapp", restarting = false)

        // Immediately press "සි" BEFORE background coroutines finish (reproduces the exact bug!)
        service.dispatchKeyActionForTest(KeyAction.LanguageSwitch)
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)

        // Pump main looper & allow all async DataStore/Room coroutines from onStartInputView to complete
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        // MUST still be Sinhala! Never flip back to English!
        assertEquals(
            "Keyboard must remain on SINHALA after pressing 'සි' once async tasks finish",
            LanguageMode.SINHALA,
            service.currentLanguageModeForTest,
        )
    }

    @Test
    fun restartingInputConnectionOnSameAppKeepsSinhalaSelected() {
        startInput(packageName = "com.example.composeapp", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()

        // User taps "සි"
        service.dispatchKeyActionForTest(KeyAction.LanguageSwitch)
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)

        // Host app (like Compose BasicTextField / WhatsApp / Chrome) immediately restarts input
        startInput(packageName = "com.example.composeapp", restarting = true)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(60)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "InputConnection restart on same app must NOT reset language back to English",
            LanguageMode.SINHALA,
            service.currentLanguageModeForTest,
        )
    }

    @Test
    fun nullHostPackageStillStaysOnSinhalaAfterToggleAndRestart() {
        startInput(packageName = null, restarting = false)
        service.dispatchKeyActionForTest(KeyAction.LanguageSwitch)
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)

        startInput(packageName = null, restarting = true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)

        // Even fresh non-restarting reopen in null host remembers cached language
        startInput(packageName = null, restarting = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)
    }

    @Test
    fun perAppLanguageMemoryIsolatesDifferentApps() {
        // Switch WhatsApp to Sinhala
        startInput(packageName = "com.whatsapp", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()
        service.dispatchKeyActionForTest(KeyAction.LanguageSwitch)
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)

        // Open Gmail -> defaults to English
        startInput(packageName = "com.google.android.gm", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LanguageMode.ENGLISH, service.currentLanguageModeForTest)

        // Switch back to WhatsApp -> automatically restores Sinhala
        startInput(packageName = "com.whatsapp", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)
    }

    @Test
    fun passwordFieldForcesEnglishAndBlocksSinhalaToggle() {
        startInput(
            packageName = "com.bank.app",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            restarting = false,
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LanguageMode.ENGLISH, service.currentLanguageModeForTest)

        service.dispatchKeyActionForTest(KeyAction.LanguageSwitch)
        assertEquals(LanguageMode.ENGLISH, service.currentLanguageModeForTest)
    }

    @Test
    fun sinhalaTypingBackspaceAndSpaceCommitEndToEnd() {
        startInput(packageName = "com.notes.app", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()

        service.dispatchKeyActionForTest(KeyAction.LanguageSwitch)
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)

        "ammaa".forEach { ch ->
            service.dispatchKeyActionForTest(KeyAction.Text(ch.toString()))
        }
        assertEquals("අම්මා", hostEditText.text.toString())

        // Backspace removes 1 Latin letter ('a') -> "amma" -> "අම්ම"
        service.dispatchKeyActionForTest(KeyAction.Backspace)
        assertEquals("අම්ම", hostEditText.text.toString())

        // Type 'a' back -> "අම්මා", then Space commits with trailing space
        service.dispatchKeyActionForTest(KeyAction.Text("a"))
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("අම්මා ", hostEditText.text.toString())
    }

    @Test
    fun cursorJumpClearsStaleComposingBuffer() {
        startInput(packageName = "com.notes.cursor", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()
        service.dispatchKeyActionForTest(KeyAction.LanguageSwitch)

        "am".forEach { service.dispatchKeyActionForTest(KeyAction.Text(it.toString())) }
        assertEquals("අම්", hostEditText.text.toString())

        // User taps to move cursor away (candidates cleared -> -1, -1)
        service.onUpdateSelection(
            oldSelStart = 3,
            oldSelEnd = 3,
            newSelStart = 0,
            newSelEnd = 0,
            candidatesStart = -1,
            candidatesEnd = -1,
        )
        hostEditText.setSelection(0)

        // Next keystroke 'k' starts fresh instead of appending to stale "am"
        service.dispatchKeyActionForTest(KeyAction.Text("k"))
        assertTrue(
            "Fresh keystroke after cursor jump should not reuse stale 'am' buffer",
            hostEditText.text.toString().startsWith("ක්"),
        )
    }

    @Test
    fun shortcutExpansionTriggersOnSpace() = runBlocking {
        val app = service.application as UTypeApplication
        app.suggestionEngine.saveShortcut("addr", "Colombo 07, Sri Lanka")

        startInput(packageName = "com.shortcut.test", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()

        "addr".forEach { service.dispatchKeyActionForTest(KeyAction.Text(it.toString())) }
        service.dispatchKeyActionForTest(KeyAction.Space)

        assertEquals("Colombo 07, Sri Lanka ", hostEditText.text.toString())
    }

    @Test
    fun doubleSpacePeriodWorksInEnglishButNotInSinhala() {
        startInput(packageName = "com.en.test", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()

        "hello".forEach { service.dispatchKeyActionForTest(KeyAction.Text(it.toString())) }
        service.dispatchKeyActionForTest(KeyAction.Space)
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("hello. ", hostEditText.text.toString())

        // Now test Sinhala mode: double space should NOT turn into ". "
        hostEditText.setText("")
        service.dispatchKeyActionForTest(KeyAction.LanguageSwitch)
        "mama".forEach { service.dispatchKeyActionForTest(KeyAction.Text(it.toString())) }
        service.dispatchKeyActionForTest(KeyAction.Space)
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("මම  ", hostEditText.text.toString())
    }

    @Test
    fun shiftSingleTapAndDoubleTapCapsLock() {
        startInput(packageName = "com.shift.test", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()

        // Ensure starting from ShiftState.OFF by typing a character first if auto-cap turned on
        if (service.currentShiftStateForTest == ShiftState.ONCE) {
            service.dispatchKeyActionForTest(KeyAction.Shift) // toggle off
        }
        assertEquals(ShiftState.OFF, service.currentShiftStateForTest)

        // Single tap -> ONCE
        service.dispatchKeyActionForTest(KeyAction.Shift)
        assertEquals(ShiftState.ONCE, service.currentShiftStateForTest)

        // Quick second tap -> CAPS_LOCK
        service.dispatchKeyActionForTest(KeyAction.Shift)
        assertEquals(ShiftState.CAPS_LOCK, service.currentShiftStateForTest)

        // Third tap -> OFF
        service.dispatchKeyActionForTest(KeyAction.Shift)
        assertEquals(ShiftState.OFF, service.currentShiftStateForTest)
    }

    @Test
    fun multiLineAndNoEnterActionInsertsNewlineWhileSendPerformsEditorAction() {
        // Multi-line chat box with IME_FLAG_NO_ENTER_ACTION must insert newline
        startInput(
            packageName = "com.chat.multiline",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION,
            restarting = false,
        )
        shadowOf(Looper.getMainLooper()).idle()
        service.dispatchKeyActionForTest(KeyAction.Enter)
        assertEquals("\n", hostEditText.text.toString())
        assertTrue(recordingIc.performedEditorActions.isEmpty())

        // Single-line Search box performs IME_ACTION_SEARCH
        hostEditText.setText("")
        startInput(
            packageName = "com.browser.search",
            inputType = InputType.TYPE_CLASS_TEXT,
            imeOptions = EditorInfo.IME_ACTION_SEARCH,
            restarting = false,
        )
        shadowOf(Looper.getMainLooper()).idle()
        service.dispatchKeyActionForTest(KeyAction.Enter)
        assertEquals(listOf(EditorInfo.IME_ACTION_SEARCH), recordingIc.performedEditorActions)
    }

    @Test
    fun complexEmojiGraphemesDeleteInSingleBackspaceTap() {
        val flag = "🇱🇰"
        val skinTone = "👍🏽"
        val family = "👨\u200D👩\u200D👧\u200D👦"
        val heartOnFire = "❤\uFE0F\u200D🔥"
        val keycap = "1\uFE0F\u20E3"

        assertEquals(flag.length, UTypeInputMethodService.computeGraphemeDeleteLength("Hi $flag"))
        assertEquals(skinTone.length, UTypeInputMethodService.computeGraphemeDeleteLength("Ok $skinTone"))
        assertEquals(family.length, UTypeInputMethodService.computeGraphemeDeleteLength("Love $family"))
        assertEquals(heartOnFire.length, UTypeInputMethodService.computeGraphemeDeleteLength("Hot $heartOnFire"))
        assertEquals(keycap.length, UTypeInputMethodService.computeGraphemeDeleteLength("Num $keycap"))
        assertEquals(1, UTypeInputMethodService.computeGraphemeDeleteLength("Hello"))
    }

    @Test
    fun incognitoLockAndPanelSwitchingWorkCleanly() {
        startInput(packageName = "com.panel.test", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(service.isIncognitoForTest)
        service.dispatchToolActionForTest(TopStripView.ToolAction.INCOGNITO_LOCK)
        assertTrue(service.isIncognitoForTest)

        service.dispatchToolActionForTest(TopStripView.ToolAction.TEXT_EDITOR)
        assertEquals("TEXT_EDITOR", service.activePanelNameForTest)

        service.dispatchToolActionForTest(TopStripView.ToolAction.STYLISH_FONTS)
        assertEquals("STYLISH_FONTS", service.activePanelNameForTest)

        service.dispatchKeyActionForTest(KeyAction.SymbolsToggle)
        assertEquals(LayoutMode.SYMBOLS_1, service.currentLayoutModeForTest)
        service.dispatchKeyActionForTest(KeyAction.SymbolsMore)
        assertEquals(LayoutMode.SYMBOLS_2, service.currentLayoutModeForTest)
        service.dispatchKeyActionForTest(KeyAction.SymbolsToggle)
        assertEquals(LayoutMode.ALPHA, service.currentLayoutModeForTest)
    }

    @Test
    fun topStripLanguageButtonAndAllToolsWorkEndToEnd() {
        startInput(packageName = "com.topstrip.test", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()

        // Initially English
        assertEquals(LanguageMode.ENGLISH, service.currentLanguageModeForTest)
        assertEquals("EN", service.topStripViewForTest.languageButtonLabelForTest())

        // Click top-bar language switch directly via View click
        service.topStripViewForTest.performToolClickForTest(TopStripView.ToolAction.LANGUAGE_SWITCH)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(60)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LanguageMode.SINHALA, service.currentLanguageModeForTest)
        assertEquals("සිං", service.topStripViewForTest.languageButtonLabelForTest())

        // Click again -> switches back to English cleanly
        service.topStripViewForTest.performToolClickForTest(TopStripView.ToolAction.LANGUAGE_SWITCH)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LanguageMode.ENGLISH, service.currentLanguageModeForTest)
        assertEquals("EN", service.topStripViewForTest.languageButtonLabelForTest())

        // Verify Incognito, Text Editor, Stylish Fonts, Clipboard, and Voice top-bar clicks work
        service.topStripViewForTest.performToolClickForTest(TopStripView.ToolAction.INCOGNITO_LOCK)
        assertTrue(service.isIncognitoForTest)

        service.topStripViewForTest.performToolClickForTest(TopStripView.ToolAction.TEXT_EDITOR)
        assertEquals("TEXT_EDITOR", service.activePanelNameForTest)

        service.topStripViewForTest.performToolClickForTest(TopStripView.ToolAction.STYLISH_FONTS)
        assertEquals("STYLISH_FONTS", service.activePanelNameForTest)

        service.topStripViewForTest.performToolClickForTest(TopStripView.ToolAction.CLIPBOARD)
        assertEquals("CLIPBOARD", service.activePanelNameForTest)

        service.topStripViewForTest.performToolClickForTest(TopStripView.ToolAction.VOICE_MIC)
        assertEquals("VOICE", service.activePanelNameForTest)
    }

    @Test
    fun navigationBarWindowInsetsPadRootContainerAboveSystemButtons() {
        startInput(packageName = "com.insets.test", restarting = false)
        shadowOf(Looper.getMainLooper()).idle()

        val root = service.rootContainerForTest
        val simulatedNavBarInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 144))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, simulatedNavBarInsets)

        assertEquals(
            "rootContainer must reserve exact bottom navigation bar height so system buttons never overlap keys or Text Editor",
            144,
            root.paddingBottom,
        )
    }
}
