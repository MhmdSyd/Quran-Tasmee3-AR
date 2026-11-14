package com.quranrecite.app.data

import com.quranrecite.app.domain.QuranPage

/**
 * Quran data access. Implemented by Agent 2 on top of assets/qpc-hafs.db and assets/qpc-v1-15-lines.db.
 * All calls are main-safe (implementations switch dispatchers internally).
 */
interface QuranRepository {
    /** @param number 1..604 */
    suspend fun getPage(number: Int): QuranPage

    /** Page (1..604) on which the given ayah starts. */
    suspend fun getPageForAyah(surah: Int, ayah: Int): Int

    /** Page (1..604) on which the given surah starts. */
    suspend fun getSurahStartPage(surah: Int): Int

    /** Arabic surah name, e.g. "الفاتحة". */
    suspend fun getSurahName(surah: Int): String
}

/** Placeholder so the app runs before Agent 2 lands. Replace in AppContainer. */
class StubQuranRepository : QuranRepository {
    override suspend fun getPage(number: Int) = QuranPage(number, emptyList())
    override suspend fun getPageForAyah(surah: Int, ayah: Int) = 1
    override suspend fun getSurahStartPage(surah: Int) = 1
    override suspend fun getSurahName(surah: Int) = "سورة $surah"
}
