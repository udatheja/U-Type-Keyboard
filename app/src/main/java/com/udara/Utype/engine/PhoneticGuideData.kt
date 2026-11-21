package com.udara.Utype.engine

/**
 * A single Sinhala glyph and the exact U Type Singlish keystroke(s) that produce it.
 */
data class PhoneticLetterEntry(
    val sinhala: String,
    val keys: String,
    val alternateKeys: List<String> = emptyList(),
    val requiresShift: Boolean = keys.any { it.isUpperCase() },
)

/**
 * A collapsible category row in the Phonetic Letter Index, mirroring the classic
 * 5-group Sinhala phonetic layout (Vowels, Basic Consonants, Mahaprana, Sanyaka/Special, Pillam).
 */
data class PhoneticLetterGroup(
    val id: String,
    val title: String,
    val sinhalaTitle: String,
    val previewLetters: String,
    val hint: String? = null,
    val entries: List<PhoneticLetterEntry>,
)

/**
 * Quick practice word showing Singlish input alongside its Sinhala output.
 */
data class PhoneticSampleWord(
    val singlish: String,
    val sinhala: String,
    val meaning: String,
)

/**
 * Complete, offline Sinhala phonetic ("Singlish") reference guide for U Type.
 * Every primary and alternate key sequence here is verified directly against
 * [SinhalaTransliterator.transliterate].
 */
object PhoneticGuideData {

    val groups: List<PhoneticLetterGroup> = listOf(
        PhoneticLetterGroup(
            id = "vowels",
            title = "Vowels",
            sinhalaTitle = "ස්වර",
            previewLetters = "අ, ආ, ඇ, ඈ, ඉ, ඊ, උ, ඌ, ඍ, ඎ, එ, ඒ, ඓ, ඔ, ඕ, ඖ",
            hint = "Double a vowel for its long sound (a → aa). Use Shift for ඇ (A), ඈ (Aa), ඍ (R), and ඎ (Ru).",
            entries = listOf(
                PhoneticLetterEntry("අ", "a"),
                PhoneticLetterEntry("ආ", "aa"),
                PhoneticLetterEntry("ඇ", "A"),
                PhoneticLetterEntry("ඈ", "Aa", listOf("AA")),
                PhoneticLetterEntry("ඉ", "i"),
                PhoneticLetterEntry("ඊ", "ii"),
                PhoneticLetterEntry("උ", "u"),
                PhoneticLetterEntry("ඌ", "uu"),
                PhoneticLetterEntry("ඍ", "R"),
                PhoneticLetterEntry("ඎ", "Ru", listOf("Ruu")),
                PhoneticLetterEntry("එ", "e"),
                PhoneticLetterEntry("ඒ", "ee"),
                PhoneticLetterEntry("ඓ", "ai"),
                PhoneticLetterEntry("ඔ", "o"),
                PhoneticLetterEntry("ඕ", "oo"),
                PhoneticLetterEntry("ඖ", "au", listOf("ou")),
            ),
        ),
        PhoneticLetterGroup(
            id = "consonants",
            title = "Common Consonants",
            sinhalaTitle = "ව්‍යංජන",
            previewLetters = "ක, ග, ච, ජ, ට, ඩ, ත, ද, න, ණ, ප, බ, ම, ය, ර, ල, ළ, ව, ස, ශ, ෂ, හ, ෆ",
            hint = "Add 'a' after a consonant for the base letter (ka = ක), or omit the vowel for hal kirima (k = ක්). Use Shift for ණ (Na), ළ (La), and ෂ (Sa / Sha).",
            entries = listOf(
                PhoneticLetterEntry("ක", "ka"),
                PhoneticLetterEntry("ග", "ga"),
                PhoneticLetterEntry("ච", "cha", listOf("ca")),
                PhoneticLetterEntry("ජ", "ja"),
                PhoneticLetterEntry("ට", "ta"),
                PhoneticLetterEntry("ඩ", "da"),
                PhoneticLetterEntry("ත", "tha"),
                PhoneticLetterEntry("ද", "dha", listOf("qa")),
                PhoneticLetterEntry("න", "na"),
                PhoneticLetterEntry("ණ", "Na"),
                PhoneticLetterEntry("ප", "pa"),
                PhoneticLetterEntry("බ", "ba"),
                PhoneticLetterEntry("ම", "ma"),
                PhoneticLetterEntry("ය", "ya"),
                PhoneticLetterEntry("ර", "ra"),
                PhoneticLetterEntry("ල", "la"),
                PhoneticLetterEntry("ළ", "La"),
                PhoneticLetterEntry("ව", "wa", listOf("va")),
                PhoneticLetterEntry("ස", "sa"),
                PhoneticLetterEntry("ශ", "sha"),
                PhoneticLetterEntry("ෂ", "Sha", listOf("Sa")),
                PhoneticLetterEntry("හ", "ha"),
                PhoneticLetterEntry("ෆ", "fa"),
            ),
        ),
        PhoneticLetterGroup(
            id = "mahaprana",
            title = "Aspirated Letters",
            sinhalaTitle = "මහාප්‍රාණ",
            previewLetters = "ඛ, ඝ, ඡ, ඣ, ඨ, ඪ, ථ, ධ, ඵ, භ",
            hint = "Add 'h' after a consonant for aspirated (mahaprana) sounds, or use Shift for ඨ (Ta) and ඪ (Da).",
            entries = listOf(
                PhoneticLetterEntry("ඛ", "kha"),
                PhoneticLetterEntry("ඝ", "gha"),
                PhoneticLetterEntry("ඡ", "chha"),
                PhoneticLetterEntry("ඣ", "jha"),
                PhoneticLetterEntry("ඨ", "Ta"),
                PhoneticLetterEntry("ඪ", "Da"),
                PhoneticLetterEntry("ථ", "thha"),
                PhoneticLetterEntry("ධ", "dhha"),
                PhoneticLetterEntry("ඵ", "pha"),
                PhoneticLetterEntry("භ", "bha"),
            ),
        ),
        PhoneticLetterGroup(
            id = "sanyaka",
            title = "Sanyaka & Special",
            sinhalaTitle = "සඤ්ඤක සහ විශේෂ",
            previewLetters = "ඟ, ඦ, ඬ, ඳ, ඥ, ඤ, ඹ, ළු, ඞ",
            hint = "Prefix with 'z' for half-nasal (sanyaka) letters like ඟ (zga) and ඳ (zdha). Use Shift for ඹ (Ba), ළු (Lu), and ඞ (X).",
            entries = listOf(
                PhoneticLetterEntry("ඟ", "zga"),
                PhoneticLetterEntry("ඦ", "zja"),
                PhoneticLetterEntry("ඬ", "zda"),
                PhoneticLetterEntry("ඳ", "zdha", listOf("zqa")),
                PhoneticLetterEntry("ඥ", "zha"),
                PhoneticLetterEntry("ඤ", "zka"),
                PhoneticLetterEntry("ඹ", "Ba"),
                PhoneticLetterEntry("ළු", "Lu"),
                PhoneticLetterEntry("ඞ", "X"),
            ),
        ),
        PhoneticLetterGroup(
            id = "pillam",
            title = "Vowel Signs & Modifiers",
            sinhalaTitle = "පිල්ලම්",
            previewLetters = "ක, කා, කැ, කෑ, කි, කී, කු, කූ, කෘ, කෲ, කෙ, කේ, කෛ, කො, කෝ, කෞ, කඃ, කං, ක්ෂ, ක්‍ය, ක්‍ර ආකාරයේ...",
            hint = "Shown with ක (k) as an example — swap 'k' for any consonant (e.g. m + aa = මා, p + ra = ප්‍ර). Type 'k' alone for ක්.",
            entries = listOf(
                PhoneticLetterEntry("ක්", "k"),
                PhoneticLetterEntry("ක", "ka"),
                PhoneticLetterEntry("කා", "kaa"),
                PhoneticLetterEntry("කැ", "kA"),
                PhoneticLetterEntry("කෑ", "kAa", listOf("kAA")),
                PhoneticLetterEntry("කි", "ki"),
                PhoneticLetterEntry("කී", "kii"),
                PhoneticLetterEntry("කු", "ku"),
                PhoneticLetterEntry("කූ", "kuu"),
                PhoneticLetterEntry("කෘ", "kru"),
                PhoneticLetterEntry("කෲ", "kruu"),
                PhoneticLetterEntry("කෙ", "ke"),
                PhoneticLetterEntry("කේ", "kee"),
                PhoneticLetterEntry("කෛ", "kai"),
                PhoneticLetterEntry("කො", "ko"),
                PhoneticLetterEntry("කෝ", "koo"),
                PhoneticLetterEntry("කෞ", "kau", listOf("kou")),
                PhoneticLetterEntry("කඃ", "kaH"),
                PhoneticLetterEntry("කං", "kax", listOf("kazn")),
                PhoneticLetterEntry("ක්ෂ", "kSha", listOf("kSa")),
                PhoneticLetterEntry("ක්\u200Dය", "kya"),
                PhoneticLetterEntry("ක්\u200Dර", "kra"),
            ),
        ),
    )

    val sampleWords: List<PhoneticSampleWord> = listOf(
        PhoneticSampleWord("sixhala", "සිංහල", "Sinhala"),
        PhoneticSampleWord("aayuboowan", "ආයුබෝවන්", "Ayubowan"),
        PhoneticSampleWord("ammaa", "අම්මා", "Mother"),
        PhoneticSampleWord("shrii", "ශ්\u200Dරී", "Sri"),
        PhoneticSampleWord("koLaBa", "කොළඹ", "Colombo"),
        PhoneticSampleWord("hozdhayi", "හොඳයි", "Good"),
        PhoneticSampleWord("sthuuthiyi", "ස්තූතියි", "Thank you"),
        PhoneticSampleWord("prashna", "ප්\u200Dරශ්න", "Questions"),
    )

    /**
     * Filters entries across all groups matching a Sinhala glyph or Singlish keystroke query.
     */
    fun search(query: String): List<PhoneticLetterEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val lower = trimmed.lowercase()
        return groups.flatMap { it.entries }.filter { entry ->
            entry.sinhala.contains(trimmed) ||
                entry.keys.lowercase().contains(lower) ||
                entry.alternateKeys.any { alt -> alt.lowercase().contains(lower) }
        }
    }
}
