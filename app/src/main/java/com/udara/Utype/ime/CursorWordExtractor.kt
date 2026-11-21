package com.udara.Utype.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.udara.Utype.engine.SymSpellIndex

data class CursorWordContext(
    val word: String,
    val charsBeforeCursor: Int,
    val charsAfterCursor: Int,
    val previousWord: String?,
)

/**
 * Pure, bounded text-context and sensitive-field utilities for the IME service.
 * Strictly operates on small bounded windows (~50 chars) and never logs or persists input text.
 */
object CursorWordExtractor {

    const val BOUNDED_CURSOR_WINDOW = 50

    /**
     * Checks whether a character belongs to a Latin or Sinhala word (including Sinhala vowel signs,
     * hal, ZWJ inside Sinhala conjuncts, and internal English apostrophes).
     */
    fun isWordChar(ch: Char): Boolean {
        if (ch.isLetter()) return true
        if (ch in '\u0D80'..'\u0DFF') return true
        if (ch == '\'' || ch == '\u200D' || ch == '\u200C') return true
        return false
    }

    /**
     * Extracts the word immediately surrounding or ending at the cursor from bounded [before] and [after]
     * strings (e.g., from `getTextBeforeCursor(50, 0)` and `getTextAfterCursor(50, 0)`).
     * Returns null if cursor is not touching a valid word or if the word exceeds 40 chars.
     */
    fun extractWordAtCursor(before: String, after: String): CursorWordContext? {
        val safeBefore = if (before.length > BOUNDED_CURSOR_WINDOW) {
            before.substring(before.length - BOUNDED_CURSOR_WINDOW)
        } else before
        val safeAfter = if (after.length > BOUNDED_CURSOR_WINDOW) {
            after.substring(0, BOUNDED_CURSOR_WINDOW)
        } else after

        var start = safeBefore.length
        while (start > 0 && isWordChar(safeBefore[start - 1])) {
            start--
        }

        var end = 0
        while (end < safeAfter.length && isWordChar(safeAfter[end])) {
            end++
        }

        // Trim stray leading/trailing apostrophes or ZWJ
        var beforePart = safeBefore.substring(start)
        var afterPart = safeAfter.substring(0, end)
        var charsBefore = beforePart.length
        var charsAfter = afterPart.length

        while (beforePart.isNotEmpty() && (beforePart.first() == '\'' || beforePart.first() == '\u200D')) {
            beforePart = beforePart.substring(1)
            charsBefore--
        }
        while (afterPart.isNotEmpty() && (afterPart.last() == '\'' || afterPart.last() == '\u200D')) {
            afterPart = afterPart.dropLast(1)
            charsAfter--
        }
        if (afterPart.isEmpty()) {
            while (beforePart.isNotEmpty() && (beforePart.last() == '\'' || beforePart.last() == '\u200D')) {
                beforePart = beforePart.dropLast(1)
                charsBefore--
            }
        }

        val fullWord = beforePart + afterPart
        if (fullWord.length < 2 || fullWord.length > SymSpellIndex.MAX_WORD_LENGTH) return null
        if (charsBefore == 0 && charsAfter == 0) return null
        if (!fullWord.any { it.isLetter() || it in '\u0D80'..'\u0DFF' }) return null

        val prefixBeforeWord = safeBefore.substring(0, safeBefore.length - charsBefore)
        val prevWord = extractPreviousWord(prefixBeforeWord)

        return CursorWordContext(
            word = fullWord,
            charsBeforeCursor = charsBefore,
            charsAfterCursor = charsAfter,
            previousWord = prevWord,
        )
    }

    /**
     * Extracts the last completed word before trailing whitespace in [textBeforeWord].
     * Used for offline bigram context ranking (e.g., "in " -> previousWord = "in").
     */
    fun extractPreviousWord(textBeforeWord: String): String? {
        if (textBeforeWord.isEmpty()) return null
        var end = textBeforeWord.length
        while (end > 0 && textBeforeWord[end - 1].isWhitespace()) {
            end--
        }
        if (end == 0) return null
        var start = end
        while (start > 0 && isWordChar(textBeforeWord[start - 1])) {
            start--
        }
        val candidate = textBeforeWord.substring(start, end).trim('\'', '\u200D', '\u200C')
        return candidate.takeIf { it.isNotEmpty() && it.length <= SymSpellIndex.MAX_WORD_LENGTH }
    }

    /**
     * Returns true for sensitive input types where suggestions, spell-checking, auto-correct,
     * word learning, and clipboard saving must be strictly disabled:
     * passwords, emails, URLs/URIs, numbers, phone numbers, and date/time fields.
     */
    fun isSensitiveInputType(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> true
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI
            else -> false
        }
    }

    fun isPasswordInputType(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    fun hasNoPersonalizedLearningFlag(info: EditorInfo?): Boolean {
        val imeOptions = info?.imeOptions ?: return false
        return (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
    }
}
