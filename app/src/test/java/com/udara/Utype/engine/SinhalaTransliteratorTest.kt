package com.udara.Utype.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Comprehensive unit test suite for the U Type Sinhala phonetic ("Singlish") engine.
 * Covers 50+ transliteration examples across vowels, consonants, mahaprana, sanyaka,
 * vowel signs (pili), yansaya, rakaransaya, vocalic-r, modifiers, full words, and backspace.
 */
class SinhalaTransliteratorTest {

    @Test
    fun testIndependentVowels() {
        val cases = listOf(
            "a" to "අ",
            "aa" to "ආ",
            "A" to "ඇ",
            "Aa" to "ඈ",
            "AA" to "ඈ",
            "i" to "ඉ",
            "ii" to "ඊ",
            "u" to "උ",
            "uu" to "ඌ",
            "R" to "ඍ",
            "Ru" to "ඎ",
            "e" to "එ",
            "ee" to "ඒ",
            "ai" to "ඓ",
            "o" to "ඔ",
            "oo" to "ඕ",
            "au" to "ඖ",
            "ou" to "ඖ",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Independent vowel '$input'", expected, SinhalaTransliterator.transliterate(input))
        }
    }

    @Test
    fun testBaseConsonantsAndMahapranaAndSanyaka() {
        val cases = listOf(
            "ka" to "ක",
            "ga" to "ග",
            "cha" to "ච",
            "ja" to "ජ",
            "ta" to "ට",
            "da" to "ඩ",
            "tha" to "ත",
            "dha" to "ද",
            "qa" to "ද",
            "na" to "න",
            "Na" to "ණ",
            "pa" to "ප",
            "ba" to "බ",
            "ma" to "ම",
            "ya" to "ය",
            "ra" to "ර",
            "la" to "ල",
            "La" to "ළ",
            "wa" to "ව",
            "va" to "ව",
            "sa" to "ස",
            "sha" to "ශ",
            "Sa" to "ෂ",
            "Sha" to "ෂ",
            "ha" to "හ",
            "fa" to "ෆ",
            // Mahaprana
            "kha" to "ඛ",
            "gha" to "ඝ",
            "chha" to "ඡ",
            "Ta" to "ඨ",
            "Da" to "ඪ",
            "thha" to "ථ",
            "dhha" to "ධ",
            "pha" to "ඵ",
            "bha" to "භ",
            // Sanyaka & special consonants
            "zga" to "ඟ",
            "zja" to "ඦ",
            "zda" to "ඬ",
            "zdha" to "ඳ",
            "zqa" to "ඳ",
            "zka" to "ඤ",
            "zha" to "ඥ",
            "Ba" to "ඹ",
            "Lu" to "ළු",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Consonant '$input'", expected, SinhalaTransliterator.transliterate(input))
        }
    }

    @Test
    fun testVowelSignsPiliAndModifiers() {
        val cases = listOf(
            "k" to "ක්",
            "ka" to "ක",
            "kaa" to "කා",
            "kA" to "කැ",
            "kAa" to "කෑ",
            "kAA" to "කෑ",
            "ki" to "කි",
            "kii" to "කී",
            "ku" to "කු",
            "kuu" to "කූ",
            "kru" to "කෘ",
            "kruu" to "කෲ",
            "ke" to "කෙ",
            "kee" to "කේ",
            "kai" to "කෛ",
            "ko" to "කො",
            "koo" to "කෝ",
            "kau" to "කෞ",
            "kaH" to "කඃ",
            "kax" to "කං",
            "kazn" to "කං",
            "kaX" to "කඞ",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Pili '$input'", expected, SinhalaTransliterator.transliterate(input))
        }
    }

    @Test
    fun testYansayaAndRakaransayaAndRealSinhalaWords() {
        val cases = listOf(
            "ammaa" to "අම්මා",
            "thaaththaa" to "තාත්තා",
            "kya" to "ක්\u200Dය",
            "kra" to "ක්\u200Dර",
            "kyaa" to "ක්\u200Dයා",
            "krama" to "ක්\u200Dරම",
            "shrii" to "ශ්\u200Dරී",
            "prashna" to "ප්\u200Dරශ්න",
            "sathya" to "සත්\u200Dය",
            "vidhyaa" to "විද්\u200Dයා",
            "sixhala" to "සිංහල",
            "laxkaawa" to "ලංකාව",
            "aayuboowan" to "ආයුබෝවන්",
            "mama" to "මම",
            "oyaa" to "ඔයා",
            "gedhara" to "ගෙදර",
            "kohomadha" to "කොහොමද",
            "hozdhayi" to "හොඳයි",
            "sthuuthiyi" to "ස්තූතියි",
            "mrudhu" to "මෘදු",
            "koLaBa" to "කොළඹ",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Word '$input'", expected, SinhalaTransliterator.transliterate(input))
        }
    }

    @Test
    fun testComposingBufferBackspaceRemovesOneLatinLetterAtATime() {
        val buffer = SinhalaTransliterator.ComposingBuffer()
        assertEquals("අ", buffer.append('a'))
        assertEquals("අම්", buffer.append('m'))
        assertEquals("අම්ම්", buffer.append('m'))
        assertEquals("අම්ම", buffer.append('a'))
        assertEquals("අම්මා", buffer.append('a'))

        // Backspace removes Latin letters one by one first
        assertEquals("අම්ම", buffer.backspace())
        assertEquals("අම්ම්", buffer.backspace())
        assertEquals("අම්", buffer.backspace())
        assertEquals("අ", buffer.backspace())
        assertEquals("", buffer.backspace())
        // Once buffer is empty, returns null so IME deletes committed Sinhala characters
        assertNull(buffer.backspace())
    }

    @Test
    fun testUppercaseNonCollidingLettersAndStandaloneC() {
        val cases = listOf(
            "Mama" to "මම",
            "Kandy" to "කන්ඩ්ය්",
            "Galle" to "ගල්ලෙ",
            "Oyaa" to "ඔයා",
            "Eka" to "එක",
            "Ithin" to "ඉතින්",
            "Udara" to "උඩර",
            "Wathura" to "වතුර",
            "ca" to "ච",
            "Ca" to "ච",
            "Cha" to "ච",
            "Chha" to "ඡ",
            // Case-sensitive Sinhala letters MUST remain untouched!
            "A" to "ඇ",
            "Aa" to "ඈ",
            "Ta" to "ඨ",
            "Da" to "ඪ",
            "Na" to "ණ",
            "La" to "ළ",
            "Sa" to "ෂ",
            "Ba" to "ඹ",
            "kaH" to "කඃ",
            "kaX" to "කඞ",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Uppercase/C rule '$input'", expected, SinhalaTransliterator.transliterate(input))
        }
    }
}
