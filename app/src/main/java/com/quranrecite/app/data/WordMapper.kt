package com.quranrecite.app.data

import com.quranrecite.app.domain.QuranWord

/**
 * Pure Kotlin (no Android) conversion of canonical verse text into [QuranWord]s with global word ids.
 * Display text is never normalised: every word is a verbatim token (or two verbatim tokens joined by one space) of
 * verses.text from qpc-hafs.db.
 */
object WordMapper {
    private const val ORNAMENT = "۞" // ۞ standalone token, not a word

    enum class Kind { SPLIT, MERGE }

    /** [tokenIndex] is 1-based over the verse's tokens (ornament excluded, ayah marker included). */
    data class Adjustment(val kind: Kind, val tokenIndex: Int)

    /** Tokens are separated by U+0020 or U+00A0 only (verified over all 6236 verses). */
    fun tokenize(text: String): List<String> = tokenizeMarked(text).map { it.first }

    /** Same tokens, each with `true` when a Hizb-quarter sign (۞) stood directly in front of it. */
    fun tokenizeMarked(text: String): List<Pair<String, Boolean>> {
        val raw = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in text) {
            if (ch == ' ' || ch == ' ') {
                if (sb.isNotEmpty()) { raw.add(sb.toString()); sb.setLength(0) }
            } else sb.append(ch)
        }
        if (sb.isNotEmpty()) raw.add(sb.toString())
        val out = ArrayList<Pair<String, Boolean>>()
        var pending = false
        for (t in raw) {
            if (t == ORNAMENT) pending = true else { out.add(t to pending); pending = false }
        }
        return out
    }

    /** SPLIT: token occupies two ids (idEnd = id + 1), text unchanged. MERGE: tokens k and k+1 become one word. */
    fun buildWords(surah: Int, ayah: Int, firstWordId: Int, text: String, adj: Adjustment?): List<QuranWord> {
        val marked = tokenizeMarked(text)
        val toks = marked.map { it.first }
        val raw = ArrayList<Triple<Int, Int, String>>() // id, idEnd, text
        val marks = ArrayList<Boolean>()                // hizb sign in front of this word
        var nid = firstWordId
        var i = 0
        while (i < toks.size) {
            val k = i + 1
            if (adj != null && adj.kind == Kind.SPLIT && k == adj.tokenIndex) {
                raw.add(Triple(nid, nid + 1, toks[i])); marks.add(marked[i].second); nid += 2
            } else if (adj != null && adj.kind == Kind.MERGE && k == adj.tokenIndex && i + 1 < toks.size) {
                raw.add(Triple(nid, nid, toks[i] + " " + toks[i + 1])); marks.add(marked[i].second); nid += 1; i++
            } else {
                raw.add(Triple(nid, nid, toks[i])); marks.add(marked[i].second); nid += 1
            }
            i++
        }
        return raw.mapIndexed { n, (id, idEnd, t) ->
            QuranWord(id = id, surah = surah, ayah = ayah, indexInAyah = n + 1, text = t,
                isAyahMarker = n == raw.size - 1, idEnd = idEnd, hizbMark = marks[n])
        }
    }
}
