package com.quranrecite.app.recitation

import com.quranrecite.app.settings.Strictness
import com.quranrecite.app.speech.asr.ModelAlphabet

/**
 * Word-level similarity in the recogniser's letter space.
 *
 * Both sides are first folded with [ModelAlphabet.foldWord] (diacritics and Quranic marks dropped; alef/hamza/ya/ta-marbuta
 * variants folded to the 29 model letters). Nothing here ever produces Quran text: the caller always displays the
 * canonical word from the database, the folded string is used for comparison only.
 */
object ArabicMatch {
    /** Letters the model often drops/adds (long vowels, hamza, ha). Inserting/deleting one is a cheaper edit. */
    private const val WEAK_LETTERS = "اوي" + "ءه"

    /** Groups of letters that CTC recognisers commonly confuse. Substitution inside a group is a cheaper edit. */
    private val CONFUSABLE = listOf("تط", "سصث", "ذزظضد", "حهخ", "عء", "قكغ")

    private const val WEAK_EDIT = 0.6
    private const val CONFUSABLE_EDIT = 0.5

    fun fold(word: String): String = ModelAlphabet.foldWord(word)

    private fun indelCost(c: Char) = if (WEAK_LETTERS.indexOf(c) >= 0) WEAK_EDIT else 1.0

    private fun subCost(a: Char, b: Char): Double {
        if (a == b) return 0.0
        for (g in CONFUSABLE) if (g.indexOf(a) >= 0 && g.indexOf(b) >= 0) return CONFUSABLE_EDIT
        return 1.0
    }

    /** Weighted edit distance between two folded words. */
    fun distance(a: String, b: String): Double {
        if (a == b) return 0.0
        var prev = DoubleArray(b.length + 1)
        var cur = DoubleArray(b.length + 1)
        for (j in 1..b.length) prev[j] = prev[j - 1] + indelCost(b[j - 1])
        for (i in 1..a.length) {
            cur[0] = prev[0] + indelCost(a[i - 1])
            for (j in 1..b.length) {
                val sub = prev[j - 1] + subCost(a[i - 1], b[j - 1])
                val del = prev[j] + indelCost(a[i - 1])
                val ins = cur[j - 1] + indelCost(b[j - 1])
                cur[j] = minOf(sub, del, ins)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 1.0 = identical folded words, 0.0 = nothing in common. Symmetric. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val len = maxOf(a.length, b.length)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        // cheap reject: lengths too different to ever reach the weakest threshold
        if (minOf(a.length, b.length).toDouble() / len < 0.5) return 0.0
        return (1.0 - distance(a, b) / len).coerceAtLeast(0.0)
    }

    /** Minimum similarity to call two words "the same" (a recognised word). Short words must be exactly equal. */
    fun acceptThreshold(len: Int, s: Strictness): Double = when (s) {
        Strictness.Normal -> when { len <= 3 -> 1.0; len == 4 -> 0.75; len == 5 -> 0.78; else -> 0.7 }
        Strictness.Strict -> if (len <= 5) 1.0 else 0.85
        Strictness.Lenient -> when { len <= 2 -> 1.0; len == 3 -> 0.66; else -> 0.6 }
    }

    /** Below [acceptThreshold] but above this = "sounds like it, not sure" ([Match.Weak]). Never for words of 1-2 letters. */
    fun weakThreshold(len: Int, s: Strictness): Double =
        if (len <= 2) 2.0 else acceptThreshold(len, s) - (if (s == Strictness.Lenient) 0.15 else 0.2)

    enum class Match { Strong, Weak, Miss }

    fun classify(sim: Double, len: Int, s: Strictness): Match = when {
        sim >= acceptThreshold(len, s) -> Match.Strong
        sim >= weakThreshold(len, s) -> Match.Weak
        else -> Match.Miss
    }

    fun classify(heard: String, expected: String, s: Strictness): Match =
        classify(similarity(heard, expected), maxOf(heard.length, expected.length), s)
}
