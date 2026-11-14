package com.quranrecite.app.domain

object QuranConstants {
    const val PAGE_COUNT = 604
    /** Pages 3..604 have 15 lines; pages 1 and 2 have only 8 (see [linesOnPage]). */
    const val LINES_PER_PAGE = 15
    fun linesOnPage(page: Int) = if (page <= 2) 8 else LINES_PER_PAGE
    const val SURAH_COUNT = 114
}

/** One word of the Madani mushaf. [id] is the global word id used by qpc-v1-15-lines.db. */
data class QuranWord(
    val id: Int,
    val surah: Int,
    val ayah: Int,
    /** 1-based position inside the ayah. The trailing ayah-number marker has [isAyahMarker] = true. */
    val indexInAyah: Int,
    val text: String,
    val isAyahMarker: Boolean = false,
    /**
     * Last global word id covered by this word. Equals [id] except for the (currently provisional) case where the
     * 15-line DB numbers one Hafs token as two words; the word then covers id..idEnd and belongs to the line of [id].
     */
    val idEnd: Int = id,
)

/** One of the 15 lines of a mushaf page. */
sealed interface PageLine {
    val lineNumber: Int

    data class SurahHeader(override val lineNumber: Int, val surah: Int) : PageLine
    data class Basmallah(override val lineNumber: Int) : PageLine
    data class Ayahs(
        override val lineNumber: Int,
        val words: List<QuranWord>,
        val isCentered: Boolean,
    ) : PageLine
}

data class QuranPage(val number: Int, val lines: List<PageLine>) {
    /** Recitable words in reading order (ayah markers excluded). */
    val recitableWords: List<QuranWord>
        get() = lines.filterIsInstance<PageLine.Ayahs>().flatMap { it.words }.filter { !it.isAyahMarker }
}

/** Per-surah navigation info. */
data class SurahInfo(val number: Int, val nameAr: String, val ayahCount: Int, val startPage: Int)

/**
 * Known data limitations. [unverifiedPages] are pages whose word SET is exact but whose line breaks may be off by
 * at most one word because one word-id adjustment in the 15-line DB could not be pinned down (see AGENT_2_HANDOFF.md).
 */
data class DataIssues(val unverifiedPages: List<Int>) { val fullyVerified: Boolean get() = unverifiedPages.isEmpty() }
