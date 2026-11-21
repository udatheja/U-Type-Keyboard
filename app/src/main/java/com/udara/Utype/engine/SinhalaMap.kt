package com.udara.Utype.engine

/**
 * Editable phonetic transliteration mapping table for Sinhala ("Singlish", Helakuru-style).
 *
 * You can customize or extend any rule in this file without touching the engine logic in
 * [SinhalaTransliterator]. All lookup tables are automatically sorted by descending key length
 * so longest-match transliteration always wins (e.g., "chh" before "ch", "aa" before "a").
 */
object SinhalaMap {

    const val HAL = "\u0DCA"        // ් (al-lakuna / virama)
    const val ZWJ = "\u200D"        // Zero Width Joiner for yansaya & rakaransaya
    const val YA = "\u0DBA"         // ය
    const val RA = "\u0DBB"         // ර

    /**
     * Independent vowels used at the start of a word or after a non-consonant (space, vowel, punctuation).
     */
    val independentVowels: Map<String, String> = linkedMapOf(
        "aa" to "ආ",
        "Aa" to "ඈ",
        "AA" to "ඈ",
        "ii" to "ඊ",
        "II" to "ඊ",
        "Ii" to "ඊ",
        "uu" to "ඌ",
        "UU" to "ඌ",
        "Uu" to "ඌ",
        "Ru" to "ඎ",
        "Ruu" to "ඎ",
        "ee" to "ඒ",
        "EE" to "ඒ",
        "Ee" to "ඒ",
        "ai" to "ඓ",
        "Ai" to "ඓ",
        "AI" to "ඓ",
        "oo" to "ඕ",
        "OO" to "ඕ",
        "Oo" to "ඕ",
        "au" to "ඖ",
        "Au" to "ඖ",
        "AU" to "ඖ",
        "ou" to "ඖ",
        "Ou" to "ඖ",
        "OU" to "ඖ",
        "a" to "අ",
        "A" to "ඇ",
        "i" to "ඉ",
        "I" to "ඉ",
        "u" to "උ",
        "U" to "උ",
        "R" to "ඍ",
        "e" to "එ",
        "E" to "එ",
        "o" to "ඔ",
        "O" to "ඔ",
    )

    /**
     * Consonant stems (without the inherent 'a' vowel).
     * Includes standard consonants, mahaprana (aspirated) consonants, sanyaka (prenasalized) consonants,
     * and safe non-colliding uppercase aliases (K, G, C, J, Q, P, M, Y, W, V, F) so accidental Shift
     * never leaks raw Latin characters while preserving all case-sensitive Sinhala keys (A, T, D, N, L, S, B, R, H, X).
     */
    val consonants: Map<String, String> = linkedMapOf(
        // 3-character stems (Mahaprana & Sanyaka)
        "chh" to "ඡ",
        "Chh" to "ඡ",
        "thh" to "ථ",
        "dhh" to "ධ",
        "zdh" to "ඳ",

        // 2-character stems (Standard, Mahaprana & Sanyaka)
        "ch" to "ච",
        "Ch" to "ච",
        "th" to "ත",
        "dh" to "ද",
        "sh" to "ශ",
        "Sh" to "ෂ",
        "kh" to "ඛ",
        "Kh" to "ඛ",
        "gh" to "ඝ",
        "Gh" to "ඝ",
        "jh" to "ඣ",
        "Jh" to "ඣ",
        "ph" to "ඵ",
        "Ph" to "ඵ",
        "bh" to "භ",
        "zg" to "ඟ",
        "zj" to "ඦ",
        "zd" to "ඬ",
        "zq" to "ඳ",
        "zk" to "ඤ",
        "zh" to "ඥ",

        // 1-character stems
        "k" to "ක",
        "K" to "ක",
        "g" to "ග",
        "G" to "ග",
        "c" to "ච",
        "C" to "ච",
        "j" to "ජ",
        "J" to "ජ",
        "t" to "ට",
        "d" to "ඩ",
        "q" to "ද",
        "Q" to "ද",
        "n" to "න",
        "N" to "ණ",
        "p" to "ප",
        "P" to "ප",
        "b" to "බ",
        "m" to "ම",
        "M" to "ම",
        "y" to "ය",
        "Y" to "ය",
        "r" to "ර",
        "l" to "ල",
        "L" to "ළ",
        "w" to "ව",
        "W" to "ව",
        "v" to "ව",
        "V" to "ව",
        "s" to "ස",
        "S" to "ෂ",
        "h" to "හ",
        "f" to "ෆ",
        "F" to "ෆ",
        "T" to "ඨ",
        "D" to "ඪ",
        "B" to "ඹ",
    )

    /**
     * Special vocalic-r pili (gaetta-pilla) that take precedence over rakaransaya when "ru" / "ruu"
     * follows a consonant (e.g., kru -> කෘ, kruu -> කෲ).
     */
    val vocalicRPili: Map<String, String> = linkedMapOf(
        "ruu" to "\u0DF2", // ෲ
        "ru" to "\u0DD8",  // ෘ
    )

    /**
     * Dependent vowel signs (pili) attached directly after a consonant stem.
     * Note: "a" maps to the empty string because Sinhala consonants carry an inherent 'a'.
     */
    val vowelSigns: Map<String, String> = linkedMapOf(
        "aa" to "\u0DCF",  // ා
        "Aa" to "\u0DD1",  // ෑ
        "AA" to "\u0DD1",  // ෑ
        "ii" to "\u0DD3",  // ී
        "uu" to "\u0DD6",  // ූ
        "ee" to "\u0DDA",  // ේ
        "ai" to "\u0DDB",  // ෛ
        "oo" to "\u0DDD",  // ෝ
        "au" to "\u0DDE",  // ෞ
        "ou" to "\u0DDE",  // ෞ
        "a" to "",         // inherent vowel
        "A" to "\u0DD0",   // ැ
        "i" to "\u0DD2",   // ි
        "u" to "\u0DD4",   // ු
        "e" to "\u0DD9",   // ෙ
        "o" to "\u0DDC",   // ො
    )

    /**
     * Post-vowel / standalone phonetic modifiers (anusvara, visarga, kantaja nasikyaya).
     * Example: ka + x = කං, ka + zn = කං, ka + H = කඃ, ka + X = කඞ, si + x + ha + la = සිංහල.
     */
    val modifiers: Map<String, String> = linkedMapOf(
        "zn" to "\u0D82", // ං (anusvara)
        "x" to "\u0D82",  // ං (anusvara)
        "H" to "\u0D83",  // ඃ (visarga)
        "X" to "\u0D9E",  // ඞ (kantaja nasikyaya)
    )

    /**
     * Optional small Sinhala letter hints shown in the top-left/top-right of QWERTY keys
     * when Sinhala key hints are enabled in Settings.
     */
    val keyHints: Map<String, String> = mapOf(
        "q" to "ද",
        "w" to "ව",
        "e" to "එ",
        "r" to "ර",
        "t" to "ට",
        "y" to "ය",
        "u" to "උ",
        "i" to "ඉ",
        "o" to "ඔ",
        "p" to "ප",
        "a" to "අ",
        "s" to "ස",
        "d" to "ඩ",
        "f" to "ෆ",
        "g" to "ග",
        "h" to "හ",
        "j" to "ජ",
        "k" to "ක",
        "l" to "ල",
        "z" to "ඤ",
        "x" to "ං",
        "c" to "ච",
        "v" to "ව",
        "b" to "බ",
        "n" to "න",
        "m" to "ම",
        "A" to "ඇ",
        "S" to "ෂ",
        "D" to "ඪ",
        "T" to "ඨ",
        "N" to "ණ",
        "L" to "ළ",
        "B" to "ඹ",
        "R" to "ඍ",
        "H" to "ඃ",
        "X" to "ඞ",
    )

    // Pre-sorted key lists (longest first) for O(1)-overhead greedy matching during live typing.
    val sortedConsonantKeys: List<String> = consonants.keys.sortedByDescending { it.length }
    val sortedIndependentVowelKeys: List<String> = independentVowels.keys.sortedByDescending { it.length }
    val sortedVowelSignKeys: List<String> = vowelSigns.keys.sortedByDescending { it.length }
    val sortedVocalicRKeys: List<String> = vocalicRPili.keys.sortedByDescending { it.length }
    val sortedModifierKeys: List<String> = modifiers.keys.sortedByDescending { it.length }
}
