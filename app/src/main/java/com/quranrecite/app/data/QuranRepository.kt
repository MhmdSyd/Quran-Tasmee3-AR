package com.quranrecite.app.data

import com.quranrecite.app.domain.DataIssues
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.domain.SurahInfo

/**
 * Quran data access over assets/qpc-hafs.db (canonical text) + assets/quran-index.db (derived mapping/layout).
 * All calls are main-safe (implementations switch dispatchers internally). Offline; no network.
 * All Arabic text returned comes verbatim from qpc-hafs.db.
 */
interface QuranRepository {
    /** Page → lines → words. @param number 1..604. Pages 1-2 have 8 lines, the rest 15. */
    suspend fun getPage(number: Int): QuranPage

    /** Page (1..604) on which the given ayah starts. */
    suspend fun getPageForAyah(surah: Int, ayah: Int): Int

    /** Page (1..604) on which the given surah starts (its header line). */
    suspend fun getSurahStartPage(surah: Int): Int

    /** Arabic surah name, e.g. "الفاتحة". */
    suspend fun getSurahName(surah: Int): String

    /** All 114 surahs in order. */
    suspend fun getSurahs(): List<SurahInfo>

    /** Fully vocalized ayah text exactly as stored in qpc-hafs.db (ends with NBSP + Arabic-Indic ayah number). */
    suspend fun getAyahText(surah: Int, ayah: Int): String

    /** Words of one ayah, last one is the ayah-number marker (isAyahMarker = true). */
    suspend fun getAyahWords(surah: Int, ayah: Int): List<QuranWord>

    /** Word by global id (1..83668), or null if out of range. */
    suspend fun getWord(wordId: Int): QuranWord?

    /** Page (1..604) containing the global word id, or null if out of range. */
    suspend fun getPageForWord(wordId: Int): Int?

    /** Known data limitations (empty list == fully verified mapping). */
    suspend fun getDataIssues(): DataIssues

    /** The 240 hizb-quarter starts (surah, ayah) from the hizb_quarters table of qpc-hafs.db; empty if the table is missing. */
    suspend fun getHizbQuarterStarts(): List<Pair<Int, Int>> = emptyList()
}

/** Placeholder for previews/tests. The app uses [SqliteQuranRepository]. */
class StubQuranRepository : QuranRepository {
    override suspend fun getPage(number: Int) = QuranPage(number, emptyList())
    override suspend fun getPageForAyah(surah: Int, ayah: Int) = 1
    override suspend fun getSurahStartPage(surah: Int) = 1
    override suspend fun getSurahName(surah: Int) = "سورة $surah"
    override suspend fun getSurahs() = emptyList<SurahInfo>()
    override suspend fun getAyahText(surah: Int, ayah: Int) = ""
    override suspend fun getAyahWords(surah: Int, ayah: Int) = emptyList<QuranWord>()
    override suspend fun getWord(wordId: Int): QuranWord? = null
    override suspend fun getPageForWord(wordId: Int): Int? = null
    override suspend fun getDataIssues() = DataIssues(emptyList())
}
