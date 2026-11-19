package com.udara.Utype.engine

/**
 * Longest-match Sinhala phonetic ("Singlish") transliteration engine.
 *
 * Algorithm overview:
 * Scans the input Latin string left-to-right at index [pos]:
 * 1. Consonant match (longest first from [SinhalaMap.sortedConsonantKeys]):
 *    a. Check vocalic-r pili ("ruu" -> ෲ, "ru" -> ෘ) first so "kru" becomes කෘ rather than ක්‍රු.
 *    b. Check rakaransaya: if the next char is 'r' followed by a vowel sign (and base consonant != ර),
 *       emit `consonant + HAL + ZWJ + RA + vowelSign` (e.g., "kra" -> ක්‍ර, "shrii" -> ශ්‍රී).
 *    c. Check yansaya: if the next char is 'y' followed by a vowel sign (and base consonant != ය),
 *       emit `consonant + HAL + ZWJ + YA + vowelSign` (e.g., "kya" -> ක්‍ය, "vidhyaa" -> විද්‍යා).
 *    d. Check regular dependent vowel signs (pili) using longest match (e.g., "aa" -> ා, "a" -> "").
 *    e. Otherwise (bare consonant before another consonant, space, punctuation, or end-of-word),
 *       attach HAL (්) -> e.g., "k" -> ක්, "ammaa" -> අම්මා.
 * 2. Modifier match ("zn" / "x" -> ං, "H" -> ඃ, "X" -> ඞ).
 * 3. Independent vowel match (longest first from [SinhalaMap.sortedIndependentVowelKeys]).
 * 4. Passthrough for any non-mapped character (spaces, digits, punctuation, existing Sinhala).
 */
object SinhalaTransliterator {

    /**
     * Converts a Latin "Singlish" input string to Sinhala Unicode text.
     */
    fun transliterate(input: String): String {
        if (input.isEmpty()) return ""
        val out = StringBuilder(input.length)
        var pos = 0
        val len = input.length

        while (pos < len) {
            // 1. Try matching a consonant stem (longest first)
            val consonantKey = matchLongest(input, pos, SinhalaMap.sortedConsonantKeys)
            if (consonantKey != null) {
                val base = SinhalaMap.consonants.getValue(consonantKey)
                pos += consonantKey.length

                // 1a. Check vocalic-r pili ("ruu", "ru") before rakaransaya ("r" + vowel)
                val vocalicRKey = matchLongest(input, pos, SinhalaMap.sortedVocalicRKeys)
                if (vocalicRKey != null && base != SinhalaMap.RA) {
                    out.append(base)
                    out.append(SinhalaMap.vocalicRPili.getValue(vocalicRKey))
                    pos += vocalicRKey.length
                    continue
                }

                // 1b. Check Rakaransaya: consonant + 'r' + vowel sign (e.g. kra -> ක්‍ර, kri -> ක්‍රි)
                if (pos < len && input[pos] == 'r' && base != SinhalaMap.RA) {
                    val afterR = pos + 1
                    val rVowelKey = matchLongest(input, afterR, SinhalaMap.sortedVowelSignKeys)
                    if (rVowelKey != null) {
                        out.append(base)
                        out.append(SinhalaMap.HAL)
                        out.append(SinhalaMap.ZWJ)
                        out.append(SinhalaMap.RA)
                        out.append(SinhalaMap.vowelSigns.getValue(rVowelKey))
                        pos = afterR + rVowelKey.length
                        continue
                    }
                }

                // 1c. Check Yansaya: consonant + 'y' + vowel sign (e.g. kya -> ක්‍ය, kyaa -> ක්‍යා)
                if (pos < len && input[pos] == 'y' && base != SinhalaMap.YA) {
                    val afterY = pos + 1
                    val yVowelKey = matchLongest(input, afterY, SinhalaMap.sortedVowelSignKeys)
                    if (yVowelKey != null) {
                        out.append(base)
                        out.append(SinhalaMap.HAL)
                        out.append(SinhalaMap.ZWJ)
                        out.append(SinhalaMap.YA)
                        out.append(SinhalaMap.vowelSigns.getValue(yVowelKey))
                        pos = afterY + yVowelKey.length
                        continue
                    }
                }

                // 1d. Check dependent vowel sign (pili)
                val vowelSignKey = matchLongest(input, pos, SinhalaMap.sortedVowelSignKeys)
                if (vowelSignKey != null) {
                    out.append(base)
                    out.append(SinhalaMap.vowelSigns.getValue(vowelSignKey))
                    pos += vowelSignKey.length
                } else {
                    // 1e. No vowel follows -> attach hal (al-lakuna ්)
                    out.append(base)
                    out.append(SinhalaMap.HAL)
                }
                continue
            }

            // 2. Try matching post-vowel / standalone modifiers (zn, x, H, X)
            val modifierKey = matchLongest(input, pos, SinhalaMap.sortedModifierKeys)
            if (modifierKey != null) {
                out.append(SinhalaMap.modifiers.getValue(modifierKey))
                pos += modifierKey.length
                continue
            }

            // 3. Try matching an independent vowel (at word start or after vowel/space/punctuation)
            val indVowelKey = matchLongest(input, pos, SinhalaMap.sortedIndependentVowelKeys)
            if (indVowelKey != null) {
                out.append(SinhalaMap.independentVowels.getValue(indVowelKey))
                pos += indVowelKey.length
                continue
            }

            // 4. Unmapped character (digit, space, punctuation, etc.): pass through unchanged
            out.append(input[pos])
            pos++
        }

        return out.toString()
    }

    /**
     * Stateful composing buffer helper for live IME typing.
     * Tracks the typed Latin characters so Backspace removes one Latin letter at a time
     * and live-updates the underlined Sinhala composing text cleanly.
     */
    class ComposingBuffer {
        private val latin = StringBuilder()

        val rawLatin: String
            get() = latin.toString()

        val isEmpty: Boolean
            get() = latin.isEmpty()

        val sinhalaText: String
            get() = transliterate(latin.toString())

        fun append(ch: Char): String {
            latin.append(ch)
            return sinhalaText
        }

        fun appendText(text: String): String {
            latin.append(text)
            return sinhalaText
        }

        /**
         * Removes the last typed Latin character.
         * Returns the updated Sinhala composing text, or null if the buffer was already empty
         * (signaling the IME to delete a committed character before the cursor).
         */
        fun backspace(): String? {
            if (latin.isEmpty()) return null
            latin.deleteCharAt(latin.length - 1)
            return sinhalaText
        }

        fun clear() {
            latin.setLength(0)
        }
    }

    private fun matchLongest(input: String, offset: Int, sortedKeys: List<String>): String? {
        for (key in sortedKeys) {
            if (input.startsWith(key, offset)) {
                return key
            }
        }
        return null
    }
}
