package com.quranrecite.app.ui.mushaf

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import com.quranrecite.app.data.QuranRepository
import com.quranrecite.app.domain.HizbQuarters
import com.quranrecite.app.domain.PageLine
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.domain.SurahInfo

/** Thin cache over [QuranRepository] (Agent 2's data layer; no second database). Main-safe. */
class MushafDataSource(private val repo: QuranRepository, private val cacheSize: Int = 8) {
    private val pages = object : LinkedHashMap<Int, QuranPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, QuranPage>?) = size > cacheSize
    }
    @Volatile private var basmallahCache: List<String>? = null
    @Volatile private var surahsCache: List<SurahInfo>? = null

    suspend fun page(number: Int): QuranPage {
        synchronized(pages) { pages[number] }?.let { return it }
        return repo.getPage(number).also { synchronized(pages) { pages[number] = it } }
    }

    /** Words of the basmallah = verse 1:1 without its number marker, straight from qpc-hafs.db. */
    suspend fun basmallah(): List<String> = basmallahCache
        ?: repo.getAyahWords(1, 1).filter { !it.isAyahMarker }.map { it.text }.also { basmallahCache = it }

    suspend fun surahs(): List<SurahInfo> = surahsCache ?: repo.getSurahs().also { surahsCache = it }

    @Volatile private var hizbCache: HizbQuarters? = null
    @Volatile private var hizbLoaded = false

    /** The validated 240 quarter starts, or null while the data is missing/incomplete (then no hizb label is shown). */
    suspend fun hizbQuarters(): HizbQuarters? {
        if (!hizbLoaded) { hizbCache = HizbQuarters.from(repo.getHizbQuarterStarts()); hizbLoaded = true }
        return hizbCache
    }

    /** Pages whose line breaks may be off by one word (docs/handoffs/AGENT_2_HANDOFF.md). */
    suspend fun unverifiedPages(): Set<Int> = repo.getDataIssues().unverifiedPages.toSet()
}

/** (surah, ayah) of the first word on the page, or null. */
fun QuranPage.firstAyahKey(): Pair<Int, Int>? {
    for (l in lines) if (l is PageLine.Ayahs && l.words.isNotEmpty()) return l.words.first().let { it.surah to it.ayah }
    return null
}

/** (surah, ayah) of the last word on the page, or null. A quarter that starts inside the page is the one this ayah lies in. */
fun QuranPage.lastAyahKey(): Pair<Int, Int>? {
    for (l in lines.asReversed()) if (l is PageLine.Ayahs && l.words.isNotEmpty()) return l.words.last().let { it.surah to it.ayah }
    return null
}

/** Surah that the page starts in (first ayah word, else first header). */
fun QuranPage.firstSurah(): Int? {
    for (l in lines) if (l is PageLine.Ayahs && l.words.isNotEmpty()) return l.words.first().surah
    for (l in lines) if (l is PageLine.SurahHeader) return l.surah
    return null
}

/** Loads [pageNumber] and draws it with [MushafPage]; blank paper while loading. */
@Composable
fun LoadedMushafPage(
    pageNumber: Int,
    data: MushafDataSource,
    modifier: Modifier = Modifier,
    mode: MushafMode = MushafMode.Reading,
    wordStates: Map<Int, WordVisual> = emptyMap(),
    style: MushafStyle = MushafStyle.Default,
    onWordClick: ((QuranWord) -> Unit)? = null,
    onWordLongClick: ((QuranWord) -> Unit)? = null,
    highlightAyah: Pair<Int, Int>? = null,
) {
    val page by produceState<QuranPage?>(null, pageNumber, data) { value = data.page(pageNumber) }
    val basmallah by produceState(emptyList<String>(), data) { value = data.basmallah() }
    val surahs by produceState(emptyList<SurahInfo>(), data) { value = data.surahs() }
    val nameOf = remember(surahs) { { n: Int -> surahs.getOrNull(n - 1)?.nameAr ?: "" } }
    val p = page
    if (p != null && p.number == pageNumber) {
        MushafPage(p, basmallah, nameOf, modifier, mode, wordStates, style, onWordClick, onWordLongClick, highlightAyah)
    } else {
        Box(modifier)
    }
}

/** Width/height of a printed Madani page; the page is centered and never wider than this in landscape/tablets. */
const val MUSHAF_PAGE_ASPECT = 0.66f

/** [LoadedMushafPage] sized to the available space, centered, limited to the printed page aspect. */
@Composable
fun MushafPageFrame(
    pageNumber: Int,
    data: MushafDataSource,
    modifier: Modifier = Modifier,
    mode: MushafMode = MushafMode.Reading,
    wordStates: Map<Int, WordVisual> = emptyMap(),
    onWordClick: ((QuranWord) -> Unit)? = null,
    onWordLongClick: ((QuranWord) -> Unit)? = null,
    highlightAyah: Pair<Int, Int>? = null,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * MUSHAF_PAGE_ASPECT)
        LoadedMushafPage(pageNumber, data, Modifier.width(w).fillMaxHeight(), mode, wordStates, onWordClick = onWordClick, onWordLongClick = onWordLongClick, highlightAyah = highlightAyah)
    }
}
