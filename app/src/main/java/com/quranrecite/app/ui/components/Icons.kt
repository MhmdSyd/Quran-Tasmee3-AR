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
import androidx.compose.ui.graphics.drawscope.scale
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

/** Gear (settings) icon: 8 teeth around a ring with a hub circle. */
@Composable
fun GearIcon(color: Color, size: Dp = 24.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val c = Offset(12 * k, 12 * k)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val dx = Math.cos(a).toFloat(); val dy = Math.sin(a).toFloat()
        drawLine(color, Offset(c.x + dx * 7.5f * k, c.y + dy * 7.5f * k), Offset(c.x + dx * 10.5f * k, c.y + dy * 10.5f * k),
            strokeWidth = 3f * k, cap = StrokeCap.Round)
    }
    drawCircle(color, radius = 6.5f * k, center = c, style = stroke(k))
    drawCircle(color, radius = 2.5f * k, center = c, style = stroke(k))
}

/** Hamburger-style list icon for the navigation menu button. */
@Composable
fun MenuIcon(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    for (y in listOf(6f, 12f, 18f)) drawLine(color, Offset(4 * k, y * k), Offset(20 * k, y * k), strokeWidth = 2 * k, cap = StrokeCap.Round)
}

@Composable
fun PauseIcon(color: Color, size: Dp = 30.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    drawRoundRect(color, Offset(6 * k, 5 * k), Size(4 * k, 14 * k), CornerRadius(1.5f * k))
    drawRoundRect(color, Offset(14 * k, 5 * k), Size(4 * k, 14 * k), CornerRadius(1.5f * k))
}

@Composable
fun PlayIcon(color: Color, size: Dp = 30.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val p = Path().apply { moveTo(8 * k, 5 * k); lineTo(19 * k, 12 * k); lineTo(8 * k, 19 * k); close() }
    drawPath(p, color)
}

/** Eye: "show". */
@Composable
fun EyeIcon(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val p = Path().apply {
        moveTo(2 * k, 12 * k)
        quadraticBezierTo(12 * k, 2 * k, 22 * k, 12 * k)
        quadraticBezierTo(12 * k, 22 * k, 2 * k, 12 * k)
        close()
    }
    drawPath(p, color, style = stroke(k))
    drawCircle(color, radius = 3 * k, center = Offset(12 * k, 12 * k))
}

/** Bookmark ribbon; [filled] when an ayah is bookmarked. */
@Composable
fun BookmarkIcon(color: Color, filled: Boolean, size: Dp = 24.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val p = Path().apply {
        moveTo(6 * k, 3 * k); lineTo(18 * k, 3 * k); lineTo(18 * k, 21 * k)
        lineTo(12 * k, 16.5f * k); lineTo(6 * k, 21 * k); close()
    }
    if (filled) drawPath(p, color) else drawPath(p, color, style = stroke(0.9f * k))
}

/**
 * Open-book icon that tells which side of the Mushaf spread the page is on: odd pages are the RIGHT page
 * (right half filled), even pages the LEFT page (left half filled).
 */
@Composable
fun PageSideIcon(isRightPage: Boolean, color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val corner = CornerRadius(1.5f * k)
    val left = Offset(2 * k, 5 * k); val right = Offset(13 * k, 5 * k); val page = Size(9 * k, 14 * k)
    drawRoundRect(color, left, page, corner, style = stroke(0.8f * k))
    drawRoundRect(color, right, page, corner, style = stroke(0.8f * k))
    drawRoundRect(color, if (isRightPage) right else left, page, corner)
}

/**
 * Page-turn arrow. Both directions come from the SAME right-pointing shape; the left one is that shape mirrored,
 * so the two buttons can never differ in size or weight (unlike text arrows, which fall back to different fonts).
 */
@Composable
fun ArrowIcon(pointsLeft: Boolean, color: Color, size: Dp = 26.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val draw: DrawScope.() -> Unit = {
        val st = Stroke(width = 2.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(4 * k, 12 * k); lineTo(20 * k, 12 * k)          // shaft
            moveTo(14 * k, 6 * k); lineTo(20 * k, 12 * k); lineTo(14 * k, 18 * k)   // head
        }
        drawPath(p, color, style = st)
    }
    if (pointsLeft) scale(scaleX = -1f, scaleY = 1f, pivot = center) { draw() } else draw()
}

/**
 * Hizb progress pie: [part] 0 = ring with a filled centre (start of a hizb), 1 = a quarter filled, 2 = half, 3 = three quarters.
 * Drawn, not font characters, so it looks the same on every phone.
 */
@Composable
fun HizbPieIcon(part: Int, color: Color, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val ring = 10f * k
    drawCircle(color, radius = ring, center = center, style = Stroke(width = 2f * k))
    if (part <= 0) drawCircle(color, radius = 5.5f * k, center = center)
    else drawArc(
        color, startAngle = -90f, sweepAngle = 90f * part.coerceAtMost(3), useCenter = true,
        topLeft = Offset(center.x - ring, center.y - ring), size = Size(ring * 2, ring * 2),
    )
}
