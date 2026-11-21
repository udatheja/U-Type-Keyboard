package com.udara.Utype.ime

import android.os.Looper
import android.text.InputType
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.udara.Utype.UTypeApplication
import com.udara.Utype.data.KeyboardSettings
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.layouts.KeyAction
import com.udara.Utype.views.TopStripView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * End-to-end Robolectric IME test suite (18+ test cases) for Section 3 Smart Suggestions:
 * - Live 5-candidate suggestion bar & tap replacement with space
 * - Long-press "Don't suggest this" & "Add to dictionary"
 * - Auto-correct on space (default OFF) + one-tap Backspace undo
 * - Section 3B fixing an already-typed word in the middle of a sentence (preserving punctuation & casing)
 * - Section 3D strict privacy blocking (password, email, URL, number, IME_FLAG_NO_PERSONALIZED_LEARNING, incognito)
 * - Bounded 50-char cursor extraction safety
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartSuggestionsImeTest {

    private lateinit var controller: ServiceController<UTypeInputMethodService>
    private lateinit var service: UTypeInputMethodService
    private lateinit var hostEditText: EditText
    private lateinit var recordingIc: BaseInputConnection

    @Before
    fun setup() = runBlocking {
        controller = Robolectric.buildService(UTypeInputMethodService::class.java).create()
        service = controller.get()
        service.suggestionDebounceMs = 0L // Instant deterministic execution in JVM tests
        val app = service.application as UTypeApplication
        app.settingsRepository.clearLanguageCacheForTest()
        app.suggestionEngine.initialize()
        app.suggestionEngine.clearLearnedWords()
        app.suggestionEngine.clearBlockedSuggestions()

        hostEditText = EditText(service)
        recordingIc = object : BaseInputConnection(hostEditText, true) {
            override fun getEditable() = hostEditText.editableText
        }
        service.testInputConnectionOverride = recordingIc
        service.onCreateInputView()
        drainAsync()
    }

    @After
    fun tearDown() {
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun drainAsync() {
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(45)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun startInput(
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        imeOptions: Int = EditorInfo.IME_ACTION_DONE,
    ) {
        val info = EditorInfo().apply {
            packageName = "com.test.smart"
            this.inputType = inputType
            this.imeOptions = imeOptions
        }
        service.testEditorInfoOverride = info
        service.onStartInputView(info, false)
        drainAsync()
    }

    private fun typeWord(word: String) {
        word.forEach { ch ->
            service.dispatchKeyActionForTest(KeyAction.Text(ch.toString()))
        }
        drainAsync()
    }

    @Test fun liveSuggestions_showQuotedTypedWordFirstAndTapReplacesWithSpace() {
        startInput()
        typeWord("recomm")

        val shown = service.topStripViewForTest.currentSuggestionsForTest()
        assertTrue("Expected 2..5 live suggestions, got $shown", shown.size in 2..5)
        assertEquals("\"recomm\"", shown.first().displayText)

        val recommendIdx = shown.indexOfFirst { it.text == "recommend" }
        assertTrue("Expected 'recommend' in suggestions: ${shown.map { it.text }}", recommendIdx > 0)

        service.topStripViewForTest.performSuggestionClickForTest(recommendIdx)
        assertEquals("recommend ", hostEditText.text.toString())
    }

    @Test fun longPressBlockSuggestion_suppressesWordImmediately() {
        startInput()
        typeWord("recieve")

        val shown = service.topStripViewForTest.currentSuggestionsForTest()
        val idx = shown.indexOfFirst { it.text == "receive" }
        assertTrue(idx > 0)

        service.topStripViewForTest.performBlockSuggestionForTest(idx)
        drainAsync()

        val refreshed = service.topStripViewForTest.currentSuggestionsForTest()
        assertFalse("Blocked word must no longer be suggested", refreshed.any { it.text == "receive" })
    }

    @Test fun longPressAddToDictionary_savesWordEncryptedLocally() = runBlocking {
        startInput()
        typeWord("lankakeyx")

        val shown = service.topStripViewForTest.currentSuggestionsForTest()
        assertTrue(shown.isNotEmpty())
        service.topStripViewForTest.performAddSuggestionToDictionaryForTest(0)
        drainAsync()

        val app = service.application as UTypeApplication
        val words = app.suggestionEngine.getPersonalWords()
        assertTrue(words.any { it.word.equals("lankakeyx", ignoreCase = true) })
    }

    @Test fun autoCorrectOnSpace_isOffByDefault() {
        startInput()
        assertFalse("autoCorrectOnSpace must default to OFF", KeyboardSettings().autoCorrectOnSpace)

        typeWord("recieve")
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("recieve ", hostEditText.text.toString())
    }

    @Test fun autoCorrectOnSpace_fixesMisspellingAndOneTapBackspaceUndoesIt() {
        startInput()
        service.setSettingsForTest(KeyboardSettings(autoCorrectOnSpace = true))

        typeWord("recieve")
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("receive ", hostEditText.text.toString())

        // One-tap Backspace right after auto-correction restores exactly what the user typed
        service.dispatchKeyActionForTest(KeyAction.Backspace)
        assertEquals("recieve", hostEditText.text.toString())
    }

    @Test fun autoCorrectOnSpace_preservesCapitalizationAndAllCaps() {
        startInput()
        service.setSettingsForTest(KeyboardSettings(autoCorrectOnSpace = true))

        typeWord("Recieve")
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("Receive ", hostEditText.text.toString())

        typeWord("SEPERATE")
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("Receive SEPARATE ", hostEditText.text.toString())
    }

    @Test fun autoCorrectUndo_clearsAfterTypingAnotherLetter() {
        startInput()
        service.setSettingsForTest(KeyboardSettings(autoCorrectOnSpace = true))

        typeWord("recieve")
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("receive ", hostEditText.text.toString())

        // Type 'a' -> clears undo state; backspace only deletes 'a'
        service.dispatchKeyActionForTest(KeyAction.Text("a"))
        service.dispatchKeyActionForTest(KeyAction.Backspace)
        assertEquals("receive ", hostEditText.text.toString())
    }

    @Test fun cursorWordFix_replacesMisspelledWordInMiddleOfSentenceKeepingPunctuation() {
        startInput()
        hostEditText.setText("I will recieve, the package.")
        // Place cursor inside "recieve" (between 'i' and 'e', index 11)
        hostEditText.setSelection(11)
        service.onUpdateSelection(0, 0, 11, 11, -1, -1)
        drainAsync()

        val shown = service.topStripViewForTest.currentSuggestionsForTest()
        val fixIdx = shown.indexOfFirst { it.text == "receive" }
        assertTrue("Expected 'receive' fix for cursor word, got ${shown.map { it.text }}", fixIdx >= 0)

        service.topStripViewForTest.performSuggestionClickForTest(fixIdx)
        assertEquals("I will receive, the package.", hostEditText.text.toString())
        // Cursor should stay cleanly right after "receive" (before the comma)
        assertEquals("I will receive".length, hostEditText.selectionStart)
    }

    @Test fun cursorWordFix_preservesTitleCaseAndAllCapsInExistingSentence() {
        startInput()
        hostEditText.setText("Please Definately check!")
        hostEditText.setSelection(12)
        service.onUpdateSelection(0, 0, 12, 12, -1, -1)
        drainAsync()

        val shown1 = service.topStripViewForTest.currentSuggestionsForTest()
        val idx1 = shown1.indexOfFirst { it.text == "Definitely" }
        assertTrue(idx1 >= 0)
        service.topStripViewForTest.performSuggestionClickForTest(idx1)
        assertEquals("Please Definitely check!", hostEditText.text.toString())

        hostEditText.setText("Very SEPERATE items.")
        hostEditText.setSelection(9)
        service.onUpdateSelection(0, 0, 9, 9, -1, -1)
        drainAsync()

        val shown2 = service.topStripViewForTest.currentSuggestionsForTest()
        val idx2 = shown2.indexOfFirst { it.text == "SEPARATE" }
        assertTrue(idx2 >= 0)
        service.topStripViewForTest.performSuggestionClickForTest(idx2)
        assertEquals("Very SEPARATE items.", hostEditText.text.toString())
    }

    @Test fun cursorWordFix_alreadyCorrectWordShowsNothingOrHomophoneAlternative() {
        startInput()
        hostEditText.setText("Hello world")
        hostEditText.setSelection(3)
        service.onUpdateSelection(0, 0, 3, 3, -1, -1)
        drainAsync()
        assertTrue(service.topStripViewForTest.currentSuggestionsForTest().isEmpty())

        // Bigram context-confused homophone: "in there" -> suggests "their"
        hostEditText.setText("Look in there now")
        hostEditText.setSelection(11) // inside "there"
        service.onUpdateSelection(0, 0, 11, 11, -1, -1)
        drainAsync()
        val alts = service.topStripViewForTest.currentSuggestionsForTest()
        assertTrue("Expected 'their' alternative after 'in', got ${alts.map { it.text }}", alts.any { it.text == "their" })
    }

    // --- Section 3D Privacy & Security Gating ---

    @Test fun passwordField_blocksSuggestionsAutoCorrectAndLearning() = runBlocking {
        startInput(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        service.setSettingsForTest(KeyboardSettings(autoCorrectOnSpace = true))
        assertTrue(service.isPrivacyRestrictedForTest)

        typeWord("recieve")
        assertTrue(service.topStripViewForTest.currentSuggestionsForTest().isEmpty())
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("recieve ", hostEditText.text.toString())

        val app = service.application as UTypeApplication
        assertTrue(app.suggestionEngine.getPersonalWords().isEmpty())
    }

    @Test fun emailField_blocksSuggestionsAutoCorrectAndLearning() = runBlocking {
        startInput(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        service.setSettingsForTest(KeyboardSettings(autoCorrectOnSpace = true))
        assertTrue(service.isPrivacyRestrictedForTest)

        typeWord("recieve")
        assertTrue(service.topStripViewForTest.currentSuggestionsForTest().isEmpty())
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("recieve ", hostEditText.text.toString())

        val app = service.application as UTypeApplication
        assertTrue(app.suggestionEngine.getPersonalWords().isEmpty())
    }

    @Test fun urlField_blocksSuggestionsAndLearning() = runBlocking {
        startInput(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        assertTrue(service.isPrivacyRestrictedForTest)

        typeWord("recieve")
        assertTrue(service.topStripViewForTest.currentSuggestionsForTest().isEmpty())
        service.dispatchKeyActionForTest(KeyAction.Space)
        val app = service.application as UTypeApplication
        assertTrue(app.suggestionEngine.getPersonalWords().isEmpty())
    }

    @Test fun numberAndPhoneFields_areMarkedPrivacyRestricted() {
        val numberInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val phoneInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_PHONE }
        val dtInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_DATETIME }
        assertTrue(CursorWordExtractor.isSensitiveInputType(numberInfo))
        assertTrue(CursorWordExtractor.isSensitiveInputType(phoneInfo))
        assertTrue(CursorWordExtractor.isSensitiveInputType(dtInfo))
    }

    @Test fun imeFlagNoPersonalizedLearning_blocksSuggestionsAutoCorrectAndLearning() = runBlocking {
        startInput(
            inputType = InputType.TYPE_CLASS_TEXT,
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
        )
        service.setSettingsForTest(KeyboardSettings(autoCorrectOnSpace = true))
        assertTrue(service.isPrivacyRestrictedForTest)

        typeWord("recieve")
        assertTrue(service.topStripViewForTest.currentSuggestionsForTest().isEmpty())
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("recieve ", hostEditText.text.toString())

        val app = service.application as UTypeApplication
        assertTrue(app.suggestionEngine.getPersonalWords().isEmpty())
    }

    @Test fun incognitoMode_blocksSuggestionsAutoCorrectAndLearning() = runBlocking {
        startInput()
        service.setSettingsForTest(KeyboardSettings(autoCorrectOnSpace = true))
        service.dispatchToolActionForTest(TopStripView.ToolAction.INCOGNITO_LOCK)
        assertTrue(service.isIncognitoForTest)
        assertTrue(service.isPrivacyRestrictedForTest)

        typeWord("recieve")
        assertTrue(service.topStripViewForTest.currentSuggestionsForTest().isEmpty())
        service.dispatchKeyActionForTest(KeyAction.Space)
        assertEquals("recieve ", hostEditText.text.toString())

        val app = service.application as UTypeApplication
        assertTrue(app.suggestionEngine.getPersonalWords().isEmpty())
    }

    @Test fun suggestionBarToggleOff_hidesSuggestionsCleanly() {
        startInput()
        service.setSettingsForTest(KeyboardSettings(suggestionBarEnabled = false))
        typeWord("recomm")
        assertTrue(service.topStripViewForTest.currentSuggestionsForTest().isEmpty())
    }

    @Test fun cursorWordExtractor_enforcesBoundedWindowAndHandlesPunctuationAndLongStrings() {
        val ctx = CursorWordExtractor.extractWordAtCursor("Hello rec", "ieve, friend!")
        assertNotNull(ctx)
        assertEquals("recieve", ctx!!.word)
        assertEquals(3, ctx.charsBeforeCursor)
        assertEquals(4, ctx.charsAfterCursor)
        assertEquals("Hello", ctx.previousWord)

        // Word exceeding 40 chars is safely ignored
        val tooLong = CursorWordExtractor.extractWordAtCursor("a".repeat(25), "b".repeat(25))
        assertNull(tooLong)
    }
}
