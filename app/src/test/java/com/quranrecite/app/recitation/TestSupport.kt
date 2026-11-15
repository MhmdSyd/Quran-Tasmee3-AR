package com.quranrecite.app.recitation

import com.quranrecite.app.domain.PageLine
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.settings.Strictness
import com.quranrecite.app.speech.RecognizedWord
import com.quranrecite.app.speech.SegmentEnd
import com.quranrecite.app.speech.SpeechEvent

/** Real pages (exported by tools/export_test_pages.py from the databases) for the plain-JVM matcher tests. */
internal object TestPages {
    private val cache = HashMap<Int, QuranPage>()

    fun page(number: Int): QuranPage = cache.getOrPut(number) { load()[number] ?: error("page $number not in fixture") }

    /** Canonical words of 1:1 without the ayah number, as MushafDataSource.basmallah() returns them. */
    val basmallah: List<String> by lazy { page(1).recitableWords.take(4).map { it.text } }

    private var all: Map<Int, QuranPage>? = null

    private fun load(): Map<Int, QuranPage> {
        all?.let { return it }
        val text = TestPages::class.java.getResourceAsStream("/test_pages.tsv")!!.readBytes().toString(Charsets.UTF_8)
        val byPage = LinkedHashMap<Int, MutableMap<Int, MutableList<List<String>>>>()
        for (row in text.lines()) {
            if (row.isBlank() || row.startsWith("#")) continue
            val c = row.split('\t')
            byPage.getOrPut(c[0].toInt()) { LinkedHashMap() }.getOrPut(c[1].toInt()) { ArrayList() }.add(c)
        }
        val out = LinkedHashMap<Int, QuranPage>()
        for ((p, lines) in byPage) {
            val ls = lines.map { (ln, rows) ->
                val first = rows.first()
                when (first[2]) {
                    "H" -> PageLine.SurahHeader(ln, first[3].toInt())
                    "B" -> PageLine.Basmallah(ln)
                    else -> PageLine.Ayahs(
                        ln,
                        rows.map { QuranWord(it[4].toInt(), it[5].toInt(), it[6].toInt(), it[7].toInt(), it[9], it[8] == "1") },
                        first[3] == "1",
                    )
                }
            }
            out[p] = QuranPage(p, ls)
        }
        all = out
        return out
    }
}

/** Builds speech events the way SherpaSpeechEngine emits them (400 ms per word on the session clock). */
internal object Ev {
    const val STEP = 400L

    fun words(texts: List<String>, t0: Long): List<RecognizedWord> =
        texts.mapIndexed { i, t -> RecognizedWord(t, t0 + i * STEP, t0 + i * STEP + 350) }

    fun end(texts: List<String>, t0: Long) = t0 + texts.size * STEP + 200

    fun fin(seg: Int, texts: List<String>, t0: Long, how: SegmentEnd = SegmentEnd.EndOfSpeech) = SpeechEvent.Final(
        text = texts.joinToString(" "), words = words(texts, t0), segmentId = seg,
        audioStartMs = t0, audioEndMs = end(texts, t0), end = how,
    )

    fun part(seg: Int, texts: List<String>, t0: Long, stable: Int = texts.size - 1) = SpeechEvent.Partial(
        text = texts.joinToString(" "), words = words(texts, t0), stableWordCount = stable.coerceAtLeast(0),
        segmentId = seg, audioStartMs = t0, audioEndMs = t0 + texts.size * STEP,
    )
}

/** A tracker on a real page that speaks canonical words (folded like the recogniser would write them). */
internal class Harness(
    pageNo: Int,
    strict: Strictness = Strictness.Normal,
    startWord: Int? = null,
    withBasmallah: Boolean = false,
) {
    val page = TestPages.page(pageNo)
    val words = page.recitableWords
    val folded = words.map { ArabicMatch.fold(it.text) }
    val tracker = RecitationTracker { strict }
    private var seg = 0
    var clock = 0L

    init { tracker.begin(page, startWord, if (withBasmallah) TestPages.basmallah else emptyList()) }

    fun say(from: Int, toExclusive: Int) = folded.subList(from, toExclusive).toList()

    /** One closed segment containing [texts]; advances the clock by a short pause afterwards. */
    fun final(texts: List<String>, gapMs: Long = 1000): SpeechEvent.Final {
        val e = Ev.fin(seg++, texts, clock)
        clock = e.audioEndMs + gapMs
        tracker.onEvent(e)
        return e
    }

    fun final(from: Int, toExclusive: Int) = final(say(from, toExclusive))

    val p get() = tracker.progress()
    fun st(i: Int): WordState = p.wordStates[words[i].id] ?: WordState.Hidden
    fun cursorIndex(): Int = p.currentWordId?.let { id -> words.indexOfFirst { it.id == id } } ?: words.size
    fun states(from: Int, toExclusive: Int) = (from until toExclusive).map { st(it) }
    fun reasons() = tracker.report().reviewItems.associate { it.word.id to it.reason }
}

/** Replaces letters at [positions] by rare letters so that the result is a controlled number of edits away. */
internal fun mangle(word: String, vararg positions: Int): String {
    val sb = StringBuilder(word)
    for (p in positions) sb[p] = if (sb[p] == 'ش') 'ج' else 'ش'
    return sb.toString()
}
