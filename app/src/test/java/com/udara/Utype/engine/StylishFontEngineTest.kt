package com.udara.Utype.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StylishFontEngineTest {

    @Test
    fun normalStyleLeavesTextUnchanged() {
        assertEquals("Hello 123", StylishFontEngine.transform("Hello 123", StylishFontStyle.NORMAL))
    }

    @Test
    fun boldStyleTransformsEnglishLettersAndDigits() {
        val styled = StylishFontEngine.transform("Hello 2026", StylishFontStyle.BOLD)
        assertNotEquals("Hello 2026", styled)
        assertEquals("𝐇𝐞𝐥𝐥𝐨 𝟐𝟎𝟐𝟔", styled)
    }

    @Test
    fun sinhalaTextIsNeverAlteredByAnyStylishFont() {
        val sinhala = "ආයුබෝවන් අම්මා ශ්‍රී ලංකා"
        StylishFontStyle.entries.forEach { style ->
            assertEquals(
                "Style ${style.name} must not alter Sinhala text",
                sinhala,
                StylishFontEngine.transform(sinhala, style)
            )
        }
    }

    @Test
    fun mixedSinhalaAndEnglishOnlyStylesEnglish() {
        val mixed = "සිංහල UType"
        val result = StylishFontEngine.transform(mixed, StylishFontStyle.SANS_BOLD)
        assertEquals("සිංහල 𝗨𝗧𝘆𝗽𝗲", result)
    }
}
