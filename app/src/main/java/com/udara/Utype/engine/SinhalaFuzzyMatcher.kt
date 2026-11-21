package com.udara.Utype.engine

/**
 * Generates bounded phonetic Singlish fuzzy variants and transliterates them to Sinhala
 * so common phonetic typing slips (missing double letters, short vs. long vowels,
 * dental vs. retroflex letters, or missing inherent 'a') resolve accurately against the
 * Sinhala frequency dictionary.
 *
 * Example:
 * - "amma" -> "අම්ම" -> variant "ammaa" -> "අම්මා"
 * - "oya" -> "ඔය" -> variant "oyaa" -> "ඔයා"
 * - "kohomda" -> "කොහොම්ද" -> variant "kohomada" -> "කොහොමද"
 */
object SinhalaFuzzyMatcher {

    private const val MAX_LATIN_LEN = 32
    private const val MAX_VARIANTS = 36

    private val vowelPairs = listOf(
        "aa" to "a",
        "a" to "aa",
        "ii" to "i",
        "i" to "ii",
        "uu" to "u",
        "u" to "uu",
        "ee" to "e",
        "e" to "ee",
        "oo" to "o",
        "o" to "oo",
    )

    private val consonantSwaps = listOf(
        "t" to "th",
        "th" to "t",
        "d" to "dh",
        "dh" to "d",
        "w" to "v",
        "v" to "w",
        "n" to "N",
        "l" to "L",
        "s" to "sh",
        "k" to "kh",
    )

    private val doubleableConsonants = setOf('m', 'n', 'k', 't', 'd', 'p', 'l', 's', 'y', 'r')
    private val vowels = setOf('a', 'e', 'i', 'o', 'u', 'A', 'E', 'I', 'O', 'U')

    /**
     * Returns unique Sinhala Unicode candidates produced by phonetic perturbations of [rawLatin].
     */
    fun generateSinhalaVariants(rawLatin: String): List<String> {
        val trimmed = rawLatin.trim()
        if (trimmed.length < 2 || trimmed.length > MAX_LATIN_LEN) return emptyList()
        if (!trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }) return emptyList()

        val latinVariants = LinkedHashSet<String>(MAX_VARIANTS)

        fun addVariant(v: String) {
            if (latinVariants.size < MAX_VARIANTS && v != trimmed && v.length <= MAX_LATIN_LEN + 2) {
                latinVariants.add(v)
            }
        }

        // 1. Trailing vowel lengthening/shortening (very common in Singlish: amma -> ammaa, oya -> oyaa)
        if (trimmed.endsWith("a") && !trimmed.endsWith("aa")) addVariant("${trimmed}a")
        if (trimmed.endsWith("aa")) addVariant(trimmed.dropLast(1))
        if (trimmed.endsWith("i") && !trimmed.endsWith("ii")) addVariant("${trimmed}i")
        if (trimmed.endsWith("u") && !trimmed.endsWith("uu")) addVariant("${trimmed}u")
        if (trimmed.endsWith("e") && !trimmed.endsWith("ee")) addVariant("${trimmed}e")
        if (trimmed.endsWith("o") && !trimmed.endsWith("oo")) addVariant("${trimmed}o")
        if (trimmed.last() !in vowels) {
            addVariant("${trimmed}a")
            addVariant("${trimmed}aa")
        }

        // 2. Leading vowel lengthening (ayubowan -> aayubowan)
        if (trimmed.startsWith("a") && !trimmed.startsWith("aa")) addVariant("a$trimmed")
        if (trimmed.startsWith("u") && !trimmed.startsWith("uu")) addVariant("u$trimmed")

        // 3. Single vowel length toggle & missing inherent 'a' between adjacent consonants (kohomda -> kohomada)
        for (i in trimmed.indices) {
            if (latinVariants.size >= MAX_VARIANTS) break
            val ch = trimmed[i]
            if (i < trimmed.length - 1 && ch !in vowels && trimmed[i + 1] !in vowels) {
                // Insert missing inherent 'a' between consonant pair
                addVariant(trimmed.substring(0, i + 1) + "a" + trimmed.substring(i + 1))
            }
            if (ch in doubleableConsonants) {
                if (i + 1 < trimmed.length && trimmed[i + 1] == ch) {
                    // Collapse double consonant
                    addVariant(trimmed.removeRange(i, i + 1))
                } else {
                    // Double single consonant (ama -> amma)
                    addVariant(trimmed.substring(0, i + 1) + ch + trimmed.substring(i + 1))
                }
            }
        }

        // 4. First occurrence swaps of common Singlish vowel/consonant pairs
        for ((from, to) in vowelPairs) {
            if (latinVariants.size >= MAX_VARIANTS) break
            val idx = trimmed.indexOf(from)
            if (idx >= 0) {
                addVariant(trimmed.substring(0, idx) + to + trimmed.substring(idx + from.length))
            }
        }
        for ((from, to) in consonantSwaps) {
            if (latinVariants.size >= MAX_VARIANTS) break
            val idx = trimmed.indexOf(from)
            if (idx >= 0) {
                addVariant(trimmed.substring(0, idx) + to + trimmed.substring(idx + from.length))
            }
        }

        val sinhalaSet = LinkedHashSet<String>(latinVariants.size)
        val originalSinhala = SinhalaTransliterator.transliterate(trimmed)
        for (variant in latinVariants) {
            val transliterated = SinhalaTransliterator.transliterate(variant)
            if (transliterated.isNotEmpty() && transliterated != originalSinhala) {
                sinhalaSet.add(transliterated)
            }
        }
        return sinhalaSet.toList()
    }
}
