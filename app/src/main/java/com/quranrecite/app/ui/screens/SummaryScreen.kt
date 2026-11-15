package com.quranrecite.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranrecite.app.di.AppContainer
import com.quranrecite.app.domain.QuranConstants
import com.quranrecite.app.session.SessionSummary
import com.quranrecite.app.session.SummaryText
import com.quranrecite.app.ui.components.CloseIcon
import com.quranrecite.app.ui.mushaf.MushafStyle
import com.quranrecite.app.ui.mushaf.toArabicDigits
import com.quranrecite.app.ui.theme.Gold
import com.quranrecite.app.ui.theme.Green
import com.quranrecite.app.ui.theme.GreenDark
import com.quranrecite.app.ui.theme.Ink
import com.quranrecite.app.ui.theme.Muted
import com.quranrecite.app.ui.theme.Paper

private val ScreenBg = Color.White
private val RingTrack = Color(0xFFE7DFC6)
private val ReviewInk = Color(0xFF8A2D20)

/** Session summary (design board 4). "للمراجعة" lists words not recognised cleanly; it is not a Tajweed verdict. */
@Composable
fun SummaryScreen(
    container: AppContainer,
    onDone: () -> Unit,
    onRetry: (page: Int) -> Unit = {},
    onNextPage: (page: Int) -> Unit = {},
) {
    val sessions by container.sessionStore.sessions.collectAsState()
    val last = sessions.firstOrNull()
    Column(Modifier.fillMaxSize().background(ScreenBg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDone).semantics { contentDescription = "إغلاق" },
                contentAlignment = Alignment.Center,
            ) { CloseIcon(Ink) }
            Text("ملخص التسميع", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
            Box(Modifier.size(48.dp))
        }
        if (last == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("لا توجد جلسات بعد", color = Muted) }
        } else {
            Content(last, Modifier.weight(1f))
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("أعد التسميع", filled = true) { onRetry(last.pageNumber) }
                if (last.pageNumber < QuranConstants.PAGE_COUNT) BigButton("الصفحة التالية", filled = false) { onNextPage(last.pageNumber + 1) }
            }
        }
    }
}

@Composable
private fun Content(s: SessionSummary, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Ring(s.firstAttemptPercent)
                Text(
                    if (s.completed) "أحسنت! أتممتَ الصفحة ${s.pageNumber.toArabicDigits()}"
                    else "تسميع الصفحة ${s.pageNumber.toArabicDigits()}: ${SummaryText.digits(s.passedWords)} من ${SummaryText.digits(s.totalWords)} كلمة",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center,
                )
                Text(
                    "المدة ${SummaryText.duration(s.durationMillis)} · الآيات ${SummaryText.digits(s.ayahsReached)} (مكتملة ${SummaryText.digits(s.ayahsCompleted)})",
                    fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Modifier.weight(1f), SummaryText.digits(s.passedWords), "كلمة", Paper, Gold, Ink)
                Stat(Modifier.weight(1f), SummaryText.digits(s.confidentWords), "مؤكَّدة", Color(0xFFE3EFE7), Color(0xFFB7D6C2), GreenDark)
                Stat(Modifier.weight(1f), SummaryText.digits(s.review.size), "للمراجعة", Color(0xFFFBEAE6), Color(0xFFE5B9B0), ReviewInk)
            }
        }
        if (s.review.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("كلمات للمراجعة", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("هذه ليست أخطاء مؤكدة: هي كلمات لم يتأكد التعرف عليها أو اعتُمدت بمساعدة.", fontSize = 12.sp, color = Muted)
                }
            }
            items(s.review, key = { it.wordId }) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paper)
                        .border(1.dp, Gold, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // canonical word from the database, in the supplied Hafs font
                    Text(r.text, style = MushafStyle.Default.textStyle(24.sp))
                    Column(Modifier.weight(1f)) {
                        Text(SummaryText.location(r.ayah, r.indexInAyah), fontSize = 12.sp, color = Muted)
                        Text(SummaryText.reason(r.reason), fontSize = 14.sp, color = Ink)
                    }
                }
            }
        } else {
            item { Text("لا توجد كلمات للمراجعة", fontSize = 14.sp, color = GreenDark) }
        }
        item { Box(Modifier.height(4.dp)) }
    }
}

@Composable
private fun Ring(percent: Int) {
    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(148.dp)) {
            val w = 12.dp.toPx()
            val d = size.width - w
            val tl = Offset(w / 2, w / 2)
            drawArc(RingTrack, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            drawArc(Green, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${SummaryText.digits(percent)}٪", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("من أول محاولة", fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String, bg: Color, border: Color, fg: Color) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).border(1.5.dp, border, RoundedCornerShape(14.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = fg)
        Text(label, fontSize = 12.sp, color = if (fg == Ink) Muted else fg)
    }
}

@Composable
private fun BigButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().height(if (filled) 56.dp else 52.dp).clip(shape)
            .then(if (filled) Modifier.background(Green) else Modifier.border(1.5.dp, Green, shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (filled) 17.sp else 16.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.White else GreenDark)
    }
}
