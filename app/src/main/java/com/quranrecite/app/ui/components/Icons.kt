package com.quranrecite.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tiny vector icons drawn from the design's 24x24 SVG paths (no icon library dependency). Decorative: no semantics. */

private fun DrawScope.stroke(k: Float) = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun MicIcon(color: Color, size: Dp = 30.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    drawRoundRect(color, Offset(9 * k, 3 * k), Size(6 * k, 12 * k), CornerRadius(3 * k), style = stroke(k))
    drawArc(color, 0f, 180f, false, Offset(5 * k, 4 * k), Size(14 * k, 14 * k), style = stroke(k))
    drawLine(color, Offset(12 * k, 18 * k), Offset(12 * k, 21 * k), strokeWidth = 2 * k, cap = StrokeCap.Round)
}

@Composable
fun BulbIcon(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    drawCircle(color, radius = 6 * k, center = Offset(12 * k, 9 * k), style = stroke(k))
    drawLine(color, Offset(9 * k, 18 * k), Offset(15 * k, 18 * k), strokeWidth = 2 * k, cap = StrokeCap.Round)
    drawLine(color, Offset(10 * k, 21 * k), Offset(14 * k, 21 * k), strokeWidth = 2 * k, cap = StrokeCap.Round)
}

@Composable
fun StopIcon(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    drawRoundRect(color, Offset(6 * k, 6 * k), Size(12 * k, 12 * k), CornerRadius(2 * k))
}

/** Chevron pointing right: "back" in a right-to-left layout. */
@Composable
fun BackIcon(color: Color, size: Dp = 24.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val p = Path().apply { moveTo(9 * k, 5 * k); lineTo(16 * k, 12 * k); lineTo(9 * k, 19 * k) }
    drawPath(p, color, style = stroke(k))
}

@Composable
fun CloseIcon(color: Color, size: Dp = 24.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    drawLine(color, Offset(6 * k, 6 * k), Offset(18 * k, 18 * k), strokeWidth = 2.2f * k, cap = StrokeCap.Round)
    drawLine(color, Offset(18 * k, 6 * k), Offset(6 * k, 18 * k), strokeWidth = 2.2f * k, cap = StrokeCap.Round)
}

@Composable
fun SlidersIcon(color: Color, size: Dp = 24.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    fun line(x1: Float, y: Float, x2: Float) = drawLine(color, Offset(x1 * k, y * k), Offset(x2 * k, y * k), strokeWidth = 2 * k, cap = StrokeCap.Round)
    line(4f, 7f, 14f); line(18f, 7f, 20f); line(4f, 17f, 6f); line(10f, 17f, 20f)
    drawCircle(color, radius = 2 * k, center = Offset(16 * k, 7 * k), style = stroke(k))
    drawCircle(color, radius = 2 * k, center = Offset(8 * k, 17 * k), style = stroke(k))
}
