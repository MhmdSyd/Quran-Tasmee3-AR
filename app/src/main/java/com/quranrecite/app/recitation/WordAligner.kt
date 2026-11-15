package com.quranrecite.app.recitation

import com.quranrecite.app.recitation.ArabicMatch.Match
import com.quranrecite.app.settings.Strictness

/** One step of the alignment between heard words (H) and expected words (E, a window starting at the cursor). */
internal data class Step(
    val kind: Kind,
    /** Index into H (for [Kind.Skip]: -1). */
    val h: Int,
    /** Index into E (for [Kind.Ins]: -1). */
    val e: Int,
    val sim: Double = 0.0,
    val match: Match = Match.Miss,
) {
    enum class Kind {
        /** One heard word against one expected word; see [match]. */
        Pair,
        /** One heard word that equals two expected words glued together (recogniser merged them). */
        Merge,
        /** Two heard words that together equal one expected word (recogniser split it). */
        Split,
        /** A heard word with no expected counterpart (extra word, repetition, noise). */
        Ins,
        /** An expected word nobody said (skipped by the reciter or dropped by the recogniser). */
        Skip,
    }

    val isStrong get() = kind != Kind.Ins && kind != Kind.Skip && match == Match.Strong
    val isWeak get() = kind == Kind.Pair && match == Match.Weak
    val isMiss get() = kind == Kind.Pair && match == Match.Miss

    /** Number of expected words this step consumes. */
    val expectedSpan get() = when (kind) { Kind.Pair, Kind.Skip, Kind.Split -> 1; Kind.Merge -> 2; Kind.Ins -> 0 }
}

/**
 * Global alignment of the heard words against a window of expected words. Start is anchored at the window start (the
 * cursor); the end is free, so words the reciter has not reached yet cost nothing. The DP only finds the most plausible
 * explanation; whether it is trustworthy enough to move the cursor is decided by the caller (RecitationTracker).
 */
internal object WordAligner {
    private const val INS = -0.5
    private const val SKIP = -0.7

    /**
     * Expected words skipped before the first heard word matched. The first few cost [SKIP] each (a dropped or skipped
     * word is the usual explanation); beyond that the cost is almost flat, so that "the reciter started further down
     * the page" can be explained by a long, strong run of words instead of being outbid by insertions. Whether such a
     * far jump is believed is decided by the tracker (anchor rules), not here.
     */
    private const val LEADING_FULL_PRICE = 4
    private const val LEADING_TAIL = -0.05
    private const val MISS = -1.0

    private fun pairScore(sim: Double, m: Match) = if (m == Match.Miss) MISS else 2.0 * sim - 1.0
    /** Always below an exact one-to-one match (1.0): a merge/split explains the speech only when nothing simpler does. */
    private fun joinScore(sim: Double) = 1.2 * sim - 0.4

    fun align(heard: List<String>, expected: List<String>, strictness: Strictness): List<Step> {
        val m = heard.size
        val n = expected.size
        if (m == 0) return emptyList()

        val sim = Array(m) { i -> DoubleArray(n) { j -> ArabicMatch.similarity(heard[i], expected[j]) } }
        val cls = Array(m) { i ->
            Array(n) { j -> ArabicMatch.classify(sim[i][j], maxOf(heard[i].length, expected[j].length), strictness) }
        }

        val NEG = -1e9
        val score = Array(m + 1) { DoubleArray(n + 1) { NEG } }
        val from = Array(m + 1) { IntArray(n + 1) }          // 1 pair, 2 ins, 3 skip, 4 merge, 5 split
        score[0][0] = 0.0
        val joinSim = HashMap<Long, Double>()

        fun merged(i: Int, j: Int): Double = joinSim.getOrPut(i.toLong() * 100_000 + j) {
            if (cls[i][j] != Match.Miss) return@getOrPut 0.0     // the heard word is already a match for the first one alone
            val s = ArabicMatch.similarity(heard[i], expected[j] + expected[j + 1])
            val len = maxOf(heard[i].length, expected[j].length + expected[j + 1].length)
            if (ArabicMatch.classify(s, len, strictness) == Match.Strong) s else 0.0
        }
        fun split(i: Int, j: Int): Double = joinSim.getOrPut(-(i.toLong() * 100_000 + j) - 1) {
            if (cls[i][j] == Match.Strong || cls[i + 1][j] == Match.Strong) return@getOrPut 0.0
            val s = ArabicMatch.similarity(heard[i] + heard[i + 1], expected[j])
            val len = maxOf(heard[i].length + heard[i + 1].length, expected[j].length)
            if (ArabicMatch.classify(s, len, strictness) == Match.Strong) s else 0.0
        }

        for (i in 0..m) for (j in 0..n) {
            if (i == 0 && j == 0) continue
            var best = NEG; var how = 0
            if (i > 0 && j > 0 && score[i - 1][j - 1] > NEG) {
                val v = score[i - 1][j - 1] + pairScore(sim[i - 1][j - 1], cls[i - 1][j - 1])
                if (v > best) { best = v; how = 1 }
            }
            if (i > 0 && score[i - 1][j] > NEG) {
                val v = score[i - 1][j] + INS
                if (v > best) { best = v; how = 2 }
            }
            if (j > 0 && score[i][j - 1] > NEG) {
                val v = score[i][j - 1] + (if (i == 0 && j > LEADING_FULL_PRICE) LEADING_TAIL else SKIP)
                if (v > best) { best = v; how = 3 }
            }
            if (i > 0 && j > 1 && score[i - 1][j - 2] > NEG) {
                val s = merged(i - 1, j - 2)
                if (s > 0) { val v = score[i - 1][j - 2] + joinScore(s); if (v > best) { best = v; how = 4 } }
            }
            if (i > 1 && j > 0 && score[i - 2][j - 1] > NEG) {
                val s = split(i - 2, j - 1)
                if (s > 0) { val v = score[i - 2][j - 1] + joinScore(s); if (v > best) { best = v; how = 5 } }
            }
            score[i][j] = best; from[i][j] = how
        }

        // free end: best column of the last row; ties -> the shorter (more conservative) explanation
        var endJ = 0
        for (j in 1..n) if (score[m][j] > score[m][endJ] + 1e-9) endJ = j

        val steps = ArrayList<Step>()
        var i = m; var j = endJ
        while (i > 0 || j > 0) {
            when (from[i][j]) {
                1 -> { steps += Step(Step.Kind.Pair, i - 1, j - 1, sim[i - 1][j - 1], cls[i - 1][j - 1]); i--; j-- }
                2 -> { steps += Step(Step.Kind.Ins, i - 1, -1); i-- }
                3 -> { steps += Step(Step.Kind.Skip, -1, j - 1); j-- }
                4 -> { steps += Step(Step.Kind.Merge, i - 1, j - 2, merged(i - 1, j - 2), Match.Strong); i--; j -= 2 }
                5 -> { steps += Step(Step.Kind.Split, i - 2, j - 1, split(i - 2, j - 1), Match.Strong); i -= 2; j-- }
                else -> error("alignment traceback broke at ($i,$j)")
            }
        }
        steps.reverse()
        return steps
    }
}
