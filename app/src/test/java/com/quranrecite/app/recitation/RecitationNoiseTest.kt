package com.quranrecite.app.recitation

import com.quranrecite.app.settings.Strictness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * SIMULATED recogniser errors on real pages (NOT real ASR output; real accuracy is unmeasured, see the handoff).
 * Per spoken word: drop 3 %, one wrong letter 5 %, two wrong letters 2 %, glue to next word 2 %, split in two 2 %.
 * The reciter also skips 1 % of words for real. Words are spoken in segments of 3..12.
 */
class RecitationNoiseTest {
    private class Outcome(var words: Int = 0, var revealed: Int = 0, var falseReveals: Int = 0, var finished: Int = 0, var pages: Int = 0)

    private fun run(pageNo: Int, seed: Long, strict: Strictness, out: Outcome) {
        val h = Harness(pageNo, strict)
        val rnd = Random(seed)
        val n = h.words.size
        val spoken = BooleanArray(n)                  // did the reciter actually say it (even if the recogniser garbled it)?
        val trace = ArrayList<String>()
        var i = 0
        while (i < n) {
            val len = 3 + rnd.nextInt(10)
            val seg = ArrayList<String>()
            var k = i
            while (k < minOf(n, i + len)) {
                val w = h.folded[k]
                val r = rnd.nextDouble()
                if (r < 0.01) { k++; continue }                                   // reciter skipped it
                spoken[k] = true
                when {
                    r < 0.04 -> {}                                                  // recogniser dropped it
                    r < 0.09 && w.length >= 2 -> seg += mangle(w, rnd.nextInt(w.length))
                    r < 0.11 && w.length >= 4 -> seg += mangle(w, 0, w.length - 1)
                    r < 0.13 && k + 1 < minOf(n, i + len) -> { seg += w + h.folded[k + 1]; spoken[k + 1] = true; k++ }
                    r < 0.15 && w.length >= 4 -> { val c = w.length / 2; seg += w.substring(0, c); seg += w.substring(c) }
                    else -> seg += w
                }
                k++
            }
            i += len
            if (seg.isNotEmpty()) { h.final(seg); trace += "seg@$i cur=${h.cursorIndex()} heard=$seg" }
        }
        val p = h.p
        out.pages++; out.words += n; out.revealed += p.revealedCount
        if (p.finished || h.cursorIndex() >= n - 3) out.finished++ else { println("  stuck: page $pageNo seed $seed cursor=${h.cursorIndex()}/$n"); trace.takeLast(3).forEach { println("    $it") }; println("    expected from cursor: ${h.folded.drop(h.cursorIndex()).take(8)}") }
        // a word that nobody said must never be shown
        for (idx in 0 until n) {
            val st = h.st(idx)
            if (!spoken[idx] && (st == WordState.Revealed || st == WordState.Uncertain)) out.falseReveals++
        }
    }

    @Test fun noisyRecitationStaysOnTrackAndNeverShowsUnspokenWords() {
        val failures = ArrayList<String>()
        for (strict in Strictness.values()) {
            val o = Outcome()
            for (page in listOf(1, 2, 3, 50, 187, 262, 531, 532, 586, 604)) for (seed in 1L..8L) run(page, seed * 31 + page, strict, o)
            val rate = o.revealed.toDouble() / o.words
            println("noise[$strict]: revealed %.3f of %d words, unspoken-but-shown=%d, pages reaching the end=%d/%d"
                .format(rate, o.words, o.falseReveals, o.finished, o.pages))
            if (o.finished < o.pages * 0.95) failures += "$strict: tracks to the end ${o.finished}/${o.pages}"
            if (rate <= 0.85) failures += "$strict: reveals most words ($rate)"
            // merged/glued words legitimately show both; only gross errors count
            if (o.falseReveals > o.words / 200) failures += "$strict: unspoken words shown = ${o.falseReveals}"
        }
        assertTrue(failures.toString(), failures.isEmpty())
    }

    @Test fun silentOrGarbageInputNeverRevealsAnything() {
        val h = Harness(3)
        val rnd = Random(5)
        repeat(200) {
            val ws = List(1 + rnd.nextInt(8)) { String(CharArray(3 + rnd.nextInt(5)) { "شزجضظغ"[rnd.nextInt(6)] }) }
            h.final(ws)
        }
        assertEquals(0, h.p.revealedCount)
        assertEquals(0, h.cursorIndex())
    }
}
