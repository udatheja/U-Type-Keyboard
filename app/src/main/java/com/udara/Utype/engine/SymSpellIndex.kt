package com.udara.Utype.engine

import kotlin.math.abs
import kotlin.math.min

data class SymSpellCandidate(
    val word: String,
    val frequency: Int,
    val editDistance: Int,
    val isPrefixCompletion: Boolean = false,
)

/**
 * Memory-efficient, offline SymSpell index (Symmetric Delete spelling correction + O(log N) prefix search).
 * - Uses compact sorted arrays and IntArray posting lists to stay well under 50 MB RAM.
 * - Verifies candidates with exact Damerau-Levenshtein (Optimal String Alignment) distance <= 2.
 * - Caps query length at [MAX_WORD_LENGTH] (40) to prevent CPU/memory abuse on hostile inputs.
 */
class SymSpellIndex(
    private val ignoreCase: Boolean = true,
    private val maxEditDistance: Int = 2,
    private val prefixLength: Int = 7,
) {
    companion object {
        const val MAX_WORD_LENGTH = 40
        private const val MAX_PREFIX_SCAN = 250
    }

    private var sortedWords: Array<String> = emptyArray()
    private var sortedFreqs: IntArray = IntArray(0)
    private var wordToId: HashMap<String, Int> = HashMap()
    private var deletes: HashMap<String, IntArray> = HashMap()

    val size: Int
        get() = sortedWords.size

    fun build(entries: List<Pair<String, Int>>) {
        val merged = HashMap<String, Int>(entries.size)
        for ((rawWord, freq) in entries) {
            val cleaned = rawWord.trim()
            if (cleaned.isEmpty() || cleaned.length > MAX_WORD_LENGTH) continue
            val key = if (ignoreCase) cleaned.lowercase() else cleaned
            val existing = merged[key]
            if (existing == null || freq > existing) {
                merged[key] = freq.coerceAtLeast(1)
            }
        }

        val sortedKeys = merged.keys.sorted()
        val count = sortedKeys.size
        val wordsArr = Array(count) { sortedKeys[it] }
        val freqsArr = IntArray(count) { merged.getValue(sortedKeys[it]) }
        val idMap = HashMap<String, Int>(count * 2)
        val tempDeletes = HashMap<String, MutableList<Int>>(count * 4)

        for (id in 0 until count) {
            val word = wordsArr[id]
            idMap[word] = id
            val prefix = if (word.length > prefixLength) word.substring(0, prefixLength) else word
            val delSet = generateDeletes(prefix, maxEditDistance)
            for (del in delSet) {
                val bucket = tempDeletes.getOrPut(del) { ArrayList(2) }
                bucket.add(id)
            }
        }

        val compactDeletes = HashMap<String, IntArray>(tempDeletes.size * 2)
        for ((k, list) in tempDeletes) {
            compactDeletes[k] = list.toIntArray()
        }

        sortedWords = wordsArr
        sortedFreqs = freqsArr
        wordToId = idMap
        deletes = compactDeletes
    }

    fun isKnownWord(word: String): Boolean {
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return false
        val key = if (ignoreCase) word.lowercase() else word
        return wordToId.containsKey(key)
    }

    fun frequencyOf(word: String): Int {
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return 0
        val key = if (ignoreCase) word.lowercase() else word
        val id = wordToId[key] ?: return 0
        return sortedFreqs[id]
    }

    /**
     * Fast O(log N) prefix completion lookup using binary search on [sortedWords].
     */
    fun lookupPrefixCompletions(prefix: String, maxResults: Int = 15): List<SymSpellCandidate> {
        if (prefix.isEmpty() || prefix.length > MAX_WORD_LENGTH || sortedWords.isEmpty()) return emptyList()
        val key = if (ignoreCase) prefix.lowercase() else prefix
        val startIdx = lowerBound(key)
        if (startIdx >= sortedWords.size) return emptyList()

        val matches = ArrayList<SymSpellCandidate>(min(maxResults * 2, 32))
        var scanned = 0
        var idx = startIdx
        while (idx < sortedWords.size && scanned < MAX_PREFIX_SCAN) {
            val candidate = sortedWords[idx]
            if (!candidate.startsWith(key)) break
            if (candidate.length > key.length) {
                matches.add(
                    SymSpellCandidate(
                        word = candidate,
                        frequency = sortedFreqs[idx],
                        editDistance = 0,
                        isPrefixCompletion = true,
                    )
                )
            }
            idx++
            scanned++
        }

        matches.sortWith(compareByDescending<SymSpellCandidate> { it.frequency }.thenBy { it.word.length })
        return if (matches.size > maxResults) matches.subList(0, maxResults) else matches
    }

    /**
     * Symmetric-delete spelling correction lookup up to [effectiveMaxDistance] (capped at [maxEditDistance]).
     */
    fun lookupCorrections(
        query: String,
        effectiveMaxDistance: Int = maxEditDistance,
        maxResults: Int = 20,
    ): List<SymSpellCandidate> {
        if (query.isEmpty() || query.length > MAX_WORD_LENGTH || sortedWords.isEmpty()) return emptyList()
        val key = if (ignoreCase) query.lowercase() else query
        val maxDist = effectiveMaxDistance.coerceIn(0, maxEditDistance)

        val candidateIds = HashSet<Int>(64)
        val queryPrefix = if (key.length > prefixLength) key.substring(0, prefixLength) else key
        val queryDeletes = generateDeletes(queryPrefix, maxDist)

        for (del in queryDeletes) {
            val posting = deletes[del] ?: continue
            for (id in posting) {
                candidateIds.add(id)
            }
        }

        val results = ArrayList<SymSpellCandidate>(min(candidateIds.size, 32))
        for (id in candidateIds) {
            val candidate = sortedWords[id]
            if (abs(candidate.length - key.length) > maxDist) continue
            val dist = damerauLevenshtein(key, candidate, maxDist)
            if (dist in 0..maxDist) {
                results.add(
                    SymSpellCandidate(
                        word = candidate,
                        frequency = sortedFreqs[id],
                        editDistance = dist,
                        isPrefixCompletion = false,
                    )
                )
            }
        }

        results.sortWith(
            compareBy<SymSpellCandidate> { it.editDistance }
                .thenByDescending { it.frequency }
                .thenBy { it.word.length }
        )
        return if (results.size > maxResults) results.subList(0, maxResults) else results
    }

    private fun lowerBound(target: String): Int {
        var lo = 0
        var hi = sortedWords.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedWords[mid] < target) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        return lo
    }

    private fun generateDeletes(word: String, maxDist: Int): Set<String> {
        val result = HashSet<String>(16)
        result.add(word)
        if (maxDist <= 0 || word.length <= 1) return result

        var frontier: Set<String> = setOf(word)
        for (depth in 1..maxDist) {
            val next = HashSet<String>(frontier.size * 4)
            for (item in frontier) {
                if (item.length <= 1) continue
                for (i in item.indices) {
                    val deleted = item.removeRange(i, i + 1)
                    if (result.add(deleted)) {
                        next.add(deleted)
                    }
                }
            }
            if (next.isEmpty()) break
            frontier = next
        }
        return result
    }
}

/**
 * Optimal String Alignment (Damerau-Levenshtein) edit distance supporting:
 * substitution, insertion, deletion, and adjacent character transposition (cost 1).
 * Returns [maxDistance] + 1 immediately if the distance exceeds [maxDistance].
 */
fun damerauLevenshtein(a: String, b: String, maxDistance: Int = 2): Int {
    if (a == b) return 0
    val lenA = a.length
    val lenB = b.length
    if (abs(lenA - lenB) > maxDistance) return maxDistance + 1
    if (lenA == 0) return if (lenB <= maxDistance) lenB else maxDistance + 1
    if (lenB == 0) return if (lenA <= maxDistance) lenA else maxDistance + 1

    var prevPrev = IntArray(lenB + 1)
    var prev = IntArray(lenB + 1) { it }
    var curr = IntArray(lenB + 1)

    for (i in 1..lenA) {
        curr[0] = i
        var rowMin = curr[0]
        val ca = a[i - 1]
        for (j in 1..lenB) {
            val cb = b[j - 1]
            val cost = if (ca == cb) 0 else 1
            var value = min(
                min(prev[j] + 1, curr[j - 1] + 1),
                prev[j - 1] + cost,
            )
            if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) {
                value = min(value, prevPrev[j - 2] + 1)
            }
            curr[j] = value
            if (value < rowMin) rowMin = value
        }
        if (rowMin > maxDistance) return maxDistance + 1
        val tmp = prevPrev
        prevPrev = prev
        prev = curr
        curr = tmp
    }
    return prev[lenB]
}
