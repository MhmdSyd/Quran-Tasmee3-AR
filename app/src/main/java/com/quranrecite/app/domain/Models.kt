package com.quranrecite.app.domain

object QuranConstants {
    const val PAGE_COUNT = 604
    const val LINES_PER_PAGE = 15
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
