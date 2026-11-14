package com.quranrecite.app.ui.mushaf

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.quranrecite.app.R
import com.quranrecite.app.domain.PageLine
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.QuranWord
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One mushaf page: exactly the lines of [page] in database order (15, or 8 on pages 1-2), each line filling an
 * equal share of the height. Words are never reflowed between lines. Surah-name lines show surah_header_bg.png
 * (aspect preserved), basmallah lines are centered, ayah lines are justified unless the DB marks them centered.
 *
 * @param basmallah the words of the basmallah (see [MushafDataSource.basmallah]); drawn on every Basmallah line.
 * @param wordStates explicit state per [QuranWord.id]; missing = Normal in [MushafMode.Reading], Hidden in Recitation.
 */
@Composable
fun MushafPage(
    page: QuranPage,
    basmallah: List<String>,
    surahName: (Int) -> String,
    modifier: Modifier = Modifier,
    mode: MushafMode = MushafMode.Reading,
    wordStates: Map<Int, WordVisual> = emptyMap(),
    style: MushafStyle = MushafStyle.Default,
    onWordClick: ((QuranWord) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val headerPainter = painterResource(R.drawable.surah_header_bg)
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val bounded = constraints.hasBoundedHeight
        val lineCount = max(page.lines.size, 1)
        val slotPx = if (bounded) constraints.maxHeight.toFloat() / lineCount else widthPx / 8f
        val metrics = remember(page, basmallah, widthPx, slotPx, density.density, density.fontScale, style.fontFamily) {
            computeMetrics(measurer, density, style, page, basmallah, widthPx, slotPx)
        }
        val fontSp = with(density) { metrics.fontPx.toSp() }
        val naturalGapPx = (MushafFit.NATURAL_GAP_EM * metrics.fontPx).roundToInt()
        val slotDp = with(density) { slotPx.toDp() }

        Column(Modifier.fillMaxSize()) {
            for (line in page.lines) {
                val slot = if (bounded) Modifier.weight(1f).fillMaxWidth() else Modifier.height(slotDp).fillMaxWidth()
                Box(slot) {
                    when (line) {
                        is PageLine.SurahHeader -> SurahHeaderLine(
                            "سورة " + surahName(line.surah), headerPainter, metrics.fontPx, slotPx, widthPx, style,
                        )
                        is PageLine.Basmallah -> JustifiedLine(true, naturalGapPx, Modifier.fillMaxSize()) {
                            for (word in basmallah) {
                                MushafWord(word, false, WordVisual.Normal, fontSp, metrics.fontPx, style, null)
                            }
                        }
                        is PageLine.Ayahs -> JustifiedLine(line.isCentered, naturalGapPx, Modifier.fillMaxSize()) {
                            for (w in line.words) key(w.id) {
                                val visual = wordStates[w.id]
                                    ?: if (mode == MushafMode.Reading) WordVisual.Normal else WordVisual.Hidden
                                MushafWord(
                                    w.text, w.isAyahMarker, visual, fontSp, metrics.fontPx, style,
                                    onWordClick?.let { cb -> { cb(w) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class PageMetrics(val fontPx: Float)

/** Measures every word once at 100 sp, derives the page font size with [MushafFit]. */
private fun computeMetrics(
    measurer: TextMeasurer, density: Density, style: MushafStyle,
    page: QuranPage, basmallah: List<String>, widthPx: Float, slotPx: Float,
): PageMetrics {
    val refSp = 100.sp
    val refPx = with(density) { refSp.toPx() }
    val refStyle = style.textStyle(refSp)
    val cache = HashMap<String, Float>()
    fun em(text: String): Float = cache.getOrPut(text) {
        measurer.measure(text = text, style = refStyle, softWrap = false, maxLines = 1).size.width / refPx
    }
    var widest = 0f
    for (line in page.lines) {
        when (line) {
            is PageLine.Ayahs -> widest = max(widest, MushafFit.lineEm(line.words.map { em(it.text) }, line.isCentered))
            is PageLine.Basmallah -> widest = max(widest, MushafFit.lineEm(basmallah.map { em(it) }, true))
            is PageLine.SurahHeader -> Unit
        }
    }
    return PageMetrics(MushafFit.fontPx(widthPx, MushafFit.pageScale(widest), slotPx))
}

/** Places its children (words) in reading order from the right edge; see [MushafFit.placeRtl]. */
@Composable
internal fun JustifiedLine(
    centered: Boolean,
    naturalGapPx: Int,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight
        else placeables.maxOfOrNull { it.height } ?: 0
        val xs = MushafFit.placeRtl(IntArray(placeables.size) { placeables[it].width }, width, centered, naturalGapPx)
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.place(xs[i], (height - p.height) / 2) }
        }
    }
}

@Composable
private fun SurahHeaderLine(
    title: String, painter: Painter, fontPx: Float, slotPx: Float, widthPx: Float, style: MushafStyle,
) {
    val density = LocalDensity.current
    val headerPx = min(slotPx, widthPx / MushafFit.HEADER_ASPECT)
    val titleSp: TextUnit = with(density) { min(fontPx * 1.25f, headerPx * 0.5f).toSp() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = style.headerTint?.let { ColorFilter.tint(it) },
        )
        Text(text = title, style = style.textStyle(titleSp), maxLines = 1, softWrap = false)
    }
}
