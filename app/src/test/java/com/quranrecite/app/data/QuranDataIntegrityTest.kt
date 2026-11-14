package com.quranrecite.app.data

import com.quranrecite.app.data.WordMapper.Adjustment
import com.quranrecite.app.data.WordMapper.Kind
import com.quranrecite.app.domain.QuranWord
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Integrity tests over the real asset DBs (JVM, via sqlite-jdbc). Mirrors tools/validate_quran_data.py. */
class QuranDataIntegrityTest {
    private class Verse(val id: Int, val surah: Int, val ayah: Int, val text: String)
    private class Line(val page: Int, val line: Int, val type: String, val first: Int, val last: Int, val surah: Int)

    companion object {
        private val assets = File("src/main/assets")
        private lateinit var hafs: Connection
        private lateinit var index: Connection
        private lateinit var verses: List<Verse>
        private lateinit var lines: List<Line>
        private lateinit var words: Map<Pair<Int, Int>, List<QuranWord>> // (surah, ayah) -> words
        private val ayahCounts = intArrayOf(7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,
            34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,
            44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6)

        @BeforeClass @JvmStatic fun setUp() {
            hafs = DriverManager.getConnection("jdbc:sqlite:" + File(assets, "qpc-hafs.db").absolutePath)
            index = DriverManager.getConnection("jdbc:sqlite:" + File(assets, "quran-index.db").absolutePath)
            verses = hafs.createStatement().executeQuery("SELECT id, surah, ayah, text FROM verses ORDER BY id").use { r ->
                buildList { while (r.next()) add(Verse(r.getInt(1), r.getInt(2), r.getInt(3), r.getString(4))) }
            }
            lines = index.createStatement().executeQuery("SELECT page, line, type, first_word_id, last_word_id, surah FROM lines ORDER BY page, line").use { r ->
                buildList { while (r.next()) add(Line(r.getInt(1), r.getInt(2), r.getString(3), r.getInt(4), r.getInt(5), r.getInt(6))) }
            }
            val adj = index.createStatement().executeQuery("SELECT surah, ayah, kind, token_index FROM word_adjustments").use { r ->
                buildMap { while (r.next()) put(r.getInt(1) * 1000 + r.getInt(2), Adjustment(if (r.getString(3) == "split") Kind.SPLIT else Kind.MERGE, r.getInt(4))) }
            }
            // words from index first_word_id (as the app does) — not from a running counter, so index errors surface.
            val first = index.createStatement().executeQuery("SELECT verse_id, first_word_id FROM verse_index").use { r ->
                buildMap { while (r.next()) put(r.getInt(1), r.getInt(2)) }
            }
            words = verses.associate { v -> (v.surah to v.ayah) to WordMapper.buildWords(v.surah, v.ayah, first.getValue(v.id), v.text, adj[v.surah * 1000 + v.ayah]) }
        }
        @AfterClass @JvmStatic fun tearDown() { hafs.close(); index.close() }
    }

    @Test fun hafsHas6236VersesAnd114SurahsWithCanonicalAyahCounts() {
        assertEquals(6236, verses.size)
        for (s in 1..114) assertEquals("surah $s", ayahCounts[s - 1], verses.count { it.surah == s })
    }

    @Test fun everyVerseEndsWithItsOwnAyahNumberMarker() {
        for (v in verses) {
            val last = WordMapper.tokenize(v.text).last()
            assertEquals("${v.surah}:${v.ayah}", v.ayah, last.map { it - '٠' }.joinToString("").toInt())
        }
    }

    @Test fun wordIdsTileOneToMaxWithNoGapsAndMatchLineDb() {
        val all = verses.flatMap { words.getValue(it.surah to it.ayah) }
        var expected = 1
        for (w in all) { assertEquals(expected, w.id); expected = w.idEnd + 1 }
        assertEquals(83668, expected - 1)
        val ayahLines = lines.filter { it.type == "ayah" }
        assertEquals(8820, ayahLines.size)
        assertEquals(1, ayahLines.first().first); assertEquals(83668, ayahLines.last().last)
        ayahLines.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
    }

    @Test fun surahStartsMatchLineDbForAll114() {
        var cur = 0
        val starts = HashMap<Int, Int>()
        for (l in lines) { if (l.type == "surah_name") cur = l.surah else if (l.type == "ayah" && cur != 0) { starts[cur] = l.first; cur = 0 } }
        assertEquals(114, starts.size)
        for (s in 1..114) assertEquals("surah $s", words.getValue(s to 1).first().id, starts.getValue(s))
    }

    @Test fun everyPageEndsOnAnAyahMarkerAndStartsOnAnAyahStart() {
        val markerEnds = words.values.map { it.last().idEnd }.toHashSet()
        val starts = words.values.map { it.first().id }.toHashSet()
        for (p in 1..604) {
            val pl = lines.filter { it.page == p && it.type == "ayah" }
            assertTrue("page $p end", pl.last().last in markerEnds)
            assertTrue("page $p start", pl.first().first in starts)
        }
    }

    @Test fun juzStartsLandOnExpectedPagesAndAyahs() {
        val juz = listOf(2 to 142, 2 to 253, 3 to 92, 4 to 24, 4 to 148, 5 to 83, 6 to 111, 7 to 88, 8 to 41, 9 to 94, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75,
            21 to 1, 23 to 1, 25 to 21, 27 to 56, 29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47) +
            listOf(51 to 31, 58 to 1, 67 to 1, 78 to 1)
        // juz 2..25 start on pages 22,42,..; juz 27..30 on 522,542,562,582 (juz 26 starts mid-page 502, skipped)
        val pages = (22..482 step 20).toList() + listOf(522, 542, 562, 582)
        juz.zip(pages).forEach { (sa, page) ->
            val first = lines.first { it.page == page && it.type == "ayah" }.first
            assertEquals("juz page $page", words.getValue(sa).first().id, first)
        }
    }

    @Test fun displayedTextIsVerbatimCanonicalTokens() {
        for (v in verses) {
            val expected = WordMapper.tokenize(v.text)
            val actual = words.getValue(v.surah to v.ayah).flatMap { it.text.split(' ') }
            assertEquals("${v.surah}:${v.ayah}", expected, actual)
        }
    }

    @Test fun lineCountsPages1And2Have8Others15() {
        for (p in 1..604) assertEquals("page $p", if (p <= 2) 8 else 15, lines.count { it.page == p })
    }
}
