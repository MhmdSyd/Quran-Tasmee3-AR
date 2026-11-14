package com.quranrecite.app.ui.mushaf

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

private const val HIDDEN_DESCRIPTION = "كلمة مخفية"

/**
 * One word. All states lay out with the word's real text width (hidden text is transparent, not removed),
 * so state changes never move anything. Verse-number markers ignore [visual] and are always visible.
 */
@Composable
internal fun MushafWord(
    text: String,
    isMarker: Boolean,
    visual: WordVisual,
    fontSp: TextUnit,
    fontPx: Float,
    style: MushafStyle,
    onClick: (() -> Unit)?,
) {
    val v = if (isMarker) WordVisual.Normal else visual
    val color = when {
        isMarker -> style.verseNumber
        else -> when (v) {
            WordVisual.Normal, WordVisual.Revealed -> style.ink
            WordVisual.Correct -> style.correct
            WordVisual.Current -> style.currentText
            WordVisual.Uncertain -> style.uncertainText
            WordVisual.Hidden, WordVisual.Mistake -> Color.Transparent
        }
    }
    var baseline by remember { mutableFloatStateOf(Float.NaN) }
    val hidden = v == WordVisual.Hidden || v == WordVisual.Mistake
    var modifier = Modifier.drawBehind { drawDecoration(v, baseline, fontPx, style) }
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    if (hidden) modifier = modifier.clearAndSetSemantics { contentDescription = HIDDEN_DESCRIPTION }
    Text(
        text = text,
        style = style.textStyle(fontSp, color),
        maxLines = 1,
        softWrap = false,
        onTextLayout = { baseline = it.getLineBaseline(0) },
        modifier = modifier,
    )
}

private fun DrawScope.drawDecoration(v: WordVisual, baselinePx: Float, fontPx: Float, s: MushafStyle) {
    if (v == WordVisual.Normal || v == WordVisual.Revealed || v == WordVisual.Correct) return
    val lineY = if (baselinePx.isNaN()) size.height * 0.82f else baselinePx + fontPx * 0.16f
    val pad = 3f.dp.toPx()
    val chipTop = size.height * 0.06f
    val chip = Size(size.width + 2 * pad, size.height * 0.88f)
    when (v) {
        WordVisual.Hidden ->
            drawLine(s.hiddenLine, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1.5f.dp.toPx())
        WordVisual.Current ->
            drawRoundRect(s.currentFill, Offset(-pad, chipTop), chip, CornerRadius(6f.dp.toPx()))
        WordVisual.Uncertain -> {
            drawRoundRect(s.uncertainFill, Offset(-pad, chipTop), chip, CornerRadius(6f.dp.toPx()))
            drawLine(
                s.uncertainLine, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 2f.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f.dp.toPx(), 4f.dp.toPx())),
            )
        }
        WordVisual.Mistake -> {
            drawRoundRect(s.mistakeFill, Offset(-pad, chipTop), chip, CornerRadius(4f.dp.toPx()))
            drawLine(s.mistakeLine, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 2.5f.dp.toPx())
        }
        else -> Unit
    }
}
