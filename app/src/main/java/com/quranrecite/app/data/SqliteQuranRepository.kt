package com.quranrecite.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.LruCache
import com.quranrecite.app.data.WordMapper.Adjustment
import com.quranrecite.app.data.WordMapper.Kind
import com.quranrecite.app.domain.DataIssues
import com.quranrecite.app.domain.PageLine
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.domain.SurahInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Verse row of quran-index.db `verse_index`. */
private class VerseRef(val verseId: Int, val surah: Int, val ayah: Int, val hafsRowId: Int, val firstWordId: Int, val wordCount: Int)

class SqliteQuranRepository(context: Context) : QuranRepository {
    private val dbs by lazy { QuranDatabases(context) }
    private val index: SQLiteDatabase get() = dbs.index
    private val hafs: SQLiteDatabase get() = dbs.hafs

    private val adjustments: Map<Int, Adjustment> by lazy {
        // key = surah * 1000 + ayah
        val m = HashMap<Int, Adjustment>()
        index.rawQuery("SELECT surah, ayah, kind, token_index FROM word_adjustments", null).use { c ->
            while (c.moveToNext()) {
                m[c.getInt(0) * 1000 + c.getInt(1)] =
                    Adjustment(if (c.getString(2) == "split") Kind.SPLIT else Kind.MERGE, c.getInt(3))
            }
        }
        m
    }
    private val verseWordsCache = LruCache<Int, List<QuranWord>>(128)

    private fun verseRef(c: android.database.Cursor) = VerseRef(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5))
    private val verseCols = "verse_id, surah, ayah, hafs_rowid, first_word_id, word_count"

    private fun verseText(rowId: Int): String =
        hafs.rawQuery("SELECT text FROM verses WHERE rowid = ?", arrayOf(rowId.toString())).use {
            if (it.moveToFirst()) it.getString(0) else ""
        }

    private fun wordsOf(v: VerseRef): List<QuranWord> {
        verseWordsCache.get(v.verseId)?.let { return it }
        val w = WordMapper.buildWords(v.surah, v.ayah, v.firstWordId, verseText(v.hafsRowId), adjustments[v.surah * 1000 + v.ayah])
        verseWordsCache.put(v.verseId, w)
        return w
    }

    private fun verseByKey(surah: Int, ayah: Int): VerseRef? =
        index.rawQuery("SELECT $verseCols FROM verse_index WHERE surah = ? AND ayah = ?", arrayOf(surah.toString(), ayah.toString())).use {
            if (it.moveToFirst()) verseRef(it) else null
        }

    /** Verse containing global word id (uses idx_verse_firstword). */
    private fun verseOfWord(wordId: Int): VerseRef? =
        index.rawQuery("SELECT $verseCols FROM verse_index WHERE first_word_id <= ? ORDER BY first_word_id DESC LIMIT 1", arrayOf(wordId.toString())).use {
            if (!it.moveToFirst()) return null
            val v = verseRef(it)
            if (wordId in 1..(v.firstWordId + v.wordCount - 1)) v else null
        }

    /** Verses overlapping [from, to] in reading order. */
    private fun versesInRange(from: Int, to: Int): List<VerseRef> =
        index.rawQuery(
            "SELECT $verseCols FROM verse_index WHERE first_word_id >= " +
                "(SELECT MAX(first_word_id) FROM verse_index WHERE first_word_id <= ?) AND first_word_id <= ? ORDER BY first_word_id",
            arrayOf(from.toString(), to.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(verseRef(c)) } }

    override suspend fun getPage(number: Int): QuranPage = withContext(Dispatchers.IO) {
        require(number in 1..604) { "page $number" }
        class Row(val line: Int, val type: String, val centered: Boolean, val first: Int, val last: Int, val surah: Int)
        val rows = index.rawQuery(
            "SELECT line, type, centered, first_word_id, last_word_id, surah FROM lines WHERE page = ? ORDER BY line",
            arrayOf(number.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Row(c.getInt(0), c.getString(1), c.getInt(2) == 1,
                    if (c.isNull(3)) 0 else c.getInt(3), if (c.isNull(4)) 0 else c.getInt(4), if (c.isNull(5)) 0 else c.getInt(5)))
            }
        }
        val ayahRows = rows.filter { it.type == "ayah" }
        val byId = HashMap<Int, QuranWord>()
        if (ayahRows.isNotEmpty()) {
            for (v in versesInRange(ayahRows.first().first, ayahRows.last().last)) for (w in wordsOf(v)) byId[w.id] = w
        }
        val lines = rows.map { r ->
            when (r.type) {
                "surah_name" -> PageLine.SurahHeader(r.line, r.surah)
                "basmallah" -> PageLine.Basmallah(r.line)
                else -> PageLine.Ayahs(r.line, (r.first..r.last).mapNotNull { byId[it] }, r.centered)
            }
        }
        QuranPage(number, lines)
    }

    override suspend fun getPageForWord(wordId: Int): Int? = withContext(Dispatchers.IO) {
        index.rawQuery(
            "SELECT page, last_word_id FROM lines WHERE first_word_id <= ? ORDER BY first_word_id DESC LIMIT 1", arrayOf(wordId.toString()),
        ).use { if (it.moveToFirst() && wordId in 1..it.getInt(1)) it.getInt(0) else null }
    }

    override suspend fun getPageForAyah(surah: Int, ayah: Int): Int = withContext(Dispatchers.IO) {
        val v = verseByKey(surah, ayah) ?: throw IllegalArgumentException("no ayah $surah:$ayah")
        getPageForWord(v.firstWordId) ?: throw IllegalStateException("no page for $surah:$ayah")
    }

    override suspend fun getSurahStartPage(surah: Int): Int = withContext(Dispatchers.IO) {
        index.rawQuery("SELECT start_page FROM surahs WHERE surah = ?", arrayOf(surah.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else throw IllegalArgumentException("surah $surah")
        }
    }

    override suspend fun getSurahName(surah: Int): String = withContext(Dispatchers.IO) {
        index.rawQuery("SELECT name_ar FROM surahs WHERE surah = ?", arrayOf(surah.toString())).use {
            if (it.moveToFirst()) it.getString(0) else throw IllegalArgumentException("surah $surah")
        }
    }

    override suspend fun getSurahs(): List<SurahInfo> = withContext(Dispatchers.IO) {
        index.rawQuery("SELECT surah, name_ar, ayah_count, start_page FROM surahs ORDER BY surah", null).use { c ->
            buildList { while (c.moveToNext()) add(SurahInfo(c.getInt(0), c.getString(1), c.getInt(2), c.getInt(3))) }
        }
    }

    override suspend fun getAyahText(surah: Int, ayah: Int): String = withContext(Dispatchers.IO) {
        val v = verseByKey(surah, ayah) ?: throw IllegalArgumentException("no ayah $surah:$ayah")
        verseText(v.hafsRowId)
    }

    override suspend fun getAyahWords(surah: Int, ayah: Int): List<QuranWord> = withContext(Dispatchers.IO) {
        val v = verseByKey(surah, ayah) ?: throw IllegalArgumentException("no ayah $surah:$ayah")
        wordsOf(v)
    }

    override suspend fun getWord(wordId: Int): QuranWord? = withContext(Dispatchers.IO) {
        verseOfWord(wordId)?.let { v -> wordsOf(v).firstOrNull { wordId in it.id..it.idEnd } }
    }

    override suspend fun getHizbQuarterStarts(): List<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            hafs.rawQuery("SELECT start_verse_key FROM hizb_quarters ORDER BY rub_el_hizb", null).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val (s, a) = c.getString(0).split(':')
                        add(s.toInt() to a.toInt())
                    }
                }
            }
        } catch (e: android.database.SQLException) { emptyList() }   // older database without the table
    }

    override suspend fun getDataIssues(): DataIssues = withContext(Dispatchers.IO) {
        index.rawQuery("SELECT DISTINCT page FROM word_adjustments WHERE verified = 0 ORDER BY page", null).use { c ->
            DataIssues(buildList { while (c.moveToNext()) add(c.getInt(0)) })
        }
    }
}
