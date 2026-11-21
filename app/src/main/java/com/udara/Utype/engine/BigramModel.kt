package com.udara.Utype.engine

/**
 * Lightweight offline bigram context model mapping `previousWord -> (nextWord -> weight)`.
 * Improves ranking of context-dependent words (e.g., their/there/they're, to/too/two,
 * your/you're, its/it's, then/than, and common Sinhala phrases).
 */
class BigramModel(
    private val ignoreCase: Boolean = true,
) {
    private var table: HashMap<String, HashMap<String, Int>> = HashMap()

    fun load(triples: List<Triple<String, String, Int>>) {
        val nextTable = HashMap<String, HashMap<String, Int>>(triples.size)
        for ((rawPrev, rawNext, score) in triples) {
            val p = if (ignoreCase) rawPrev.trim().lowercase() else rawPrev.trim()
            val n = if (ignoreCase) rawNext.trim().lowercase() else rawNext.trim()
            if (p.isEmpty() || n.isEmpty()) continue
            val inner = nextTable.getOrPut(p) { HashMap(4) }
            val existing = inner[n]
            if (existing == null || score > existing) {
                inner[n] = score.coerceAtLeast(1)
            }
        }
        table = nextTable
    }

    fun bonus(previousWord: String?, candidate: String): Int {
        if (previousWord.isNullOrBlank() || candidate.isBlank()) return 0
        val p = if (ignoreCase) previousWord.trim().lowercase() else previousWord.trim()
        val c = if (ignoreCase) candidate.trim().lowercase() else candidate.trim()
        return table[p]?.get(c) ?: 0
    }

    fun nextCandidates(previousWord: String?, limit: Int = 5): List<Pair<String, Int>> {
        if (previousWord.isNullOrBlank()) return emptyList()
        val p = if (ignoreCase) previousWord.trim().lowercase() else previousWord.trim()
        val inner = table[p] ?: return emptyList()
        return inner.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
}
