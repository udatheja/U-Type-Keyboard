package com.udara.Utype.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guarantees 100% accuracy between every Sinhala letter shown in the Phonetic Letter Index
 * and the live U Type [SinhalaTransliterator] engine.
 */
class PhoneticGuideTest {

    @Test
    fun allFivePhoneticGroupsExistAndAreNonEmpty() {
        val ids = PhoneticGuideData.groups.map { it.id }
        assertEquals(
            listOf("vowels", "consonants", "mahaprana", "sanyaka", "pillam"),
            ids,
        )
        PhoneticGuideData.groups.forEach { group ->
            assertTrue("Group '${group.id}' should not be empty", group.entries.isNotEmpty())
        }
    }

    @Test
    fun everyPrimaryAndAlternateKeyInGuideProducesExactSinhalaLetter() {
        var checkedMappings = 0
        PhoneticGuideData.groups.forEach { group ->
            group.entries.forEach { entry ->
                assertEquals(
                    "Primary key '${entry.keys}' in group '${group.id}' must produce '${entry.sinhala}'",
                    entry.sinhala,
                    SinhalaTransliterator.transliterate(entry.keys),
                )
                checkedMappings++

                entry.alternateKeys.forEach { alt ->
                    assertEquals(
                        "Alternate key '$alt' in group '${group.id}' must produce '${entry.sinhala}'",
                        entry.sinhala,
                        SinhalaTransliterator.transliterate(alt),
                    )
                    checkedMappings++
                }
            }
        }
        assertTrue("Expected at least 90 verified phonetic mappings, got $checkedMappings", checkedMappings >= 90)
    }

    @Test
    fun mahapranaJhaProducesExactLetter() {
        assertEquals("ඣ", SinhalaTransliterator.transliterate("jha"))
        assertEquals("ඣ", SinhalaTransliterator.transliterate("Jha"))
    }

    @Test
    fun everySampleWordTransliteratesExactly() {
        PhoneticGuideData.sampleWords.forEach { sample ->
            assertEquals(
                "Sample word '${sample.singlish}' must produce '${sample.sinhala}'",
                sample.sinhala,
                SinhalaTransliterator.transliterate(sample.singlish),
            )
        }
    }

    @Test
    fun searchFindsBySinhalaGlyphPrimaryKeysAndAlternateKeys() {
        val bySinhala = PhoneticGuideData.search("ඳ")
        assertTrue(bySinhala.any { it.sinhala == "ඳ" && it.keys == "zdha" })

        val byPrimary = PhoneticGuideData.search("kru")
        assertTrue(byPrimary.any { it.sinhala == "කෘ" })
        assertTrue(byPrimary.any { it.sinhala == "කෲ" })

        val byAlternate = PhoneticGuideData.search("kazn")
        assertTrue(byAlternate.any { it.sinhala == "කං" })

        val byMahaprana = PhoneticGuideData.search("ඣ")
        assertTrue(byMahaprana.any { it.keys == "jha" })

        assertTrue(PhoneticGuideData.search("   ").isEmpty())
    }
}
