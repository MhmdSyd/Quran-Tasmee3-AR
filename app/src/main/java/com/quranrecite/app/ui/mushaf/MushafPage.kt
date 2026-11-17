package com.quranrecite.app.ui.mushaf

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.unit.dp
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
    onWordLongClick: ((QuranWord) -> Unit)? = null,
    /** (surah, ayah) to mark with the "current" chip, e.g. the ayah just reached from a bookmark. */
    highlightAyah: Pair<Int, Int>? = null,
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
                            line.surah, surahName(line.surah), headerPainter, slotPx, widthPx, style,
                        )
                        is PageLine.Basmallah -> JustifiedLine(true, naturalGapPx, Modifier.fillMaxSize()) {
                            for (word in basmallah) {
                                MushafWord(word, false, WordVisual.Normal, fontSp, metrics.fontPx, style, null)
                            }
                        }
                        is PageLine.Ayahs -> {
                            val defaultVisual = if (mode == MushafMode.Reading) WordVisual.Normal else WordVisual.Hidden
                            val visuals = line.words.map {
                                wordStates[it.id]
                                    ?: if (highlightAyah != null && !it.isAyahMarker && it.surah == highlightAyah.first && it.ayah == highlightAyah.second) WordVisual.Current
                                    else defaultVisual
                            }
                            val kinds = line.words.mapIndexed { i, w -> if (w.isAyahMarker) WordLine.None else visuals[i].lineKind() }
                            JustifiedLine(
                                line.isCentered, naturalGapPx, Modifier.fillMaxSize(),
                                kinds = kinds, fontPx = metrics.fontPx, style = style,
                            ) {
                            line.words.forEachIndexed { i, w -> key(w.id) {
                                val visual = visuals[i]
                                MushafWord(
                                    w.text, w.isAyahMarker, visual, fontSp, metrics.fontPx, style,
                                    onWordClick?.let { cb -> { cb(w) } },
                                    onWordLongClick?.let { cb -> { cb(w) } },
                                    hizbMark = w.hizbMark,
                                )
                            } }
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
            is PageLine.Ayahs -> widest = max(widest, MushafFit.lineEm(line.words.map { em(if (it.hizbMark) HIZB_SIGN + "\u00A0" + it.text else it.text) }, line.isCentered))
            is PageLine.Basmallah -> widest = max(widest, MushafFit.lineEm(basmallah.map { em(it) }, true))
            is PageLine.SurahHeader -> Unit
        }
    }
    return PageMetrics(MushafFit.fontPx(widthPx, MushafFit.pageScale(widest), slotPx))
}

/** Geometry written by the layout pass and read by the draw pass of the same line (layout always precedes draw). */
private class LineGeometry {
    var xs = IntArray(0)
    var widths = IntArray(0)
    var lineY = Float.NaN
}

/**
 * Places its children (words) in reading order from the right edge; see [MushafFit.placeRtl].
 * With [kinds] it also draws the connected underline of the unrevealed words (see [MushafUnderline]) behind them,
 * at the real positions and widths of the words, so revealing a word never moves anything.
 */
@Composable
internal fun JustifiedLine(
    centered: Boolean,
    naturalGapPx: Int,
    modifier: Modifier,
    kinds: List<WordLine>? = null,
    fontPx: Float = 0f,
    style: MushafStyle = MushafStyle.Default,
    content: @Composable () -> Unit,
) {
    val geo = remember { LineGeometry() }
    val drawn = if (kinds == null || kinds.all { it == WordLine.None }) modifier else modifier.drawBehind {
        if (geo.xs.size != kinds.size) return@drawBehind
        val y = if (geo.lineY.isNaN()) size.height * 0.62f else geo.lineY
        for (seg in MushafUnderline.segments(geo.xs, geo.widths, kinds)) {
            val a = Offset(seg.left.toFloat(), y)
            val b = Offset(seg.right.toFloat(), y)
            when (seg.kind) {
                SegmentKind.Base -> drawLine(style.hiddenLine, a, b, strokeWidth = 0.75f.dp.toPx())
                SegmentKind.Expected -> drawLine(style.expectedLine, a, b, strokeWidth = 3f.dp.toPx())
                SegmentKind.Mistake -> drawLine(style.mistakeLine, a, b, strokeWidth = 2.5f.dp.toPx())
                SegmentKind.Skipped -> drawLine(
                    style.skippedLine, a, b, strokeWidth = 2f.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f.dp.toPx(), 4f.dp.toPx())),
                )
            }
        }
    }
    Layout(content = content, modifier = drawn) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight
        else placeables.maxOfOrNull { it.height } ?: 0
        val widths = IntArray(placeables.size) { placeables[it].width }
        val xs = MushafFit.placeRtl(widths, width, centered, naturalGapPx)
        geo.xs = xs; geo.widths = widths
        val base = placeables.firstOrNull { it[FirstBaseline] != androidx.compose.ui.layout.AlignmentLine.Unspecified }
        geo.lineY = if (base == null) Float.NaN else (height - base.height) / 2f + base[FirstBaseline] + fontPx * 0.16f
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.place(xs[i], (height - p.height) / 2) }
        }
    }
}

/**
 * Surah heading: the supplied decoration PNG (aspect preserved) with the surah name from header-surah-name.ttf centered
 * in its inner opening. The size comes from the decoration's geometry ([MushafFit.headerTitlePx]), not from the page text.
 */
@Composable
private fun SurahHeaderLine(
    surah: Int, nameAr: String, painter: Painter, slotPx: Float, widthPx: Float, style: MushafStyle,
) {
    val density = LocalDensity.current
    val headerHeightPx = min(slotPx, widthPx / MushafFit.HEADER_ASPECT)
    val titleSp: TextUnit = with(density) { MushafFit.headerTitlePx(headerHeightPx).toSp() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = style.headerTint?.let { ColorFilter.tint(it) },
        )
        SurahNameText(surah, nameAr, titleSp, style.ink)
    }
}
