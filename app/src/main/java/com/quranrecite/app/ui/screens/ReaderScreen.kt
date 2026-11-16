package com.quranrecite.app.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.quranrecite.app.domain.JuzStarts
import com.quranrecite.app.ui.components.GearIcon
import com.quranrecite.app.ui.components.MenuIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.quranrecite.app.settings.Bookmark
import com.quranrecite.app.ui.components.BookmarkIcon
import com.quranrecite.app.ui.components.PageSideIcon
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranrecite.app.di.AppContainer
import com.quranrecite.app.domain.QuranConstants
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.domain.SurahInfo
import com.quranrecite.app.ui.mushaf.MushafPageFrame
import com.quranrecite.app.ui.mushaf.SurahNameText
import com.quranrecite.app.ui.mushaf.firstSurah
import com.quranrecite.app.ui.mushaf.toArabicDigits
import com.quranrecite.app.ui.theme.Muted
import kotlinx.coroutines.launch

/**
 * Madani mushaf reader: 604 swipeable pages (RTL: swipe left = next page), prev/next buttons and a surah picker.
 * [onStartRecitation] receives the page currently shown.
 */
@Composable
fun ReaderScreen(
    page: Int,
    container: AppContainer,
    onStartRecitation: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val data = container.mushafData
    val pager = rememberPagerState(initialPage = (page - 1).coerceIn(0, QuranConstants.PAGE_COUNT - 1)) {
        QuranConstants.PAGE_COUNT
    }
    val scope = rememberCoroutineScope()
    val current = pager.currentPage + 1
    // remember where the user is, so the app reopens on this page after it was closed
    LaunchedEffect(current) { container.readingPosition.lastPage = current }
    val surahs by produceState(emptyList<SurahInfo>(), data) { value = data.surahs() }
    val unverified by produceState(emptySet<Int>(), data) { value = data.unverifiedPages() }
    val currentPage by produceState<QuranPage?>(null, current, data) { value = data.page(current) }
    var showSurahs by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val bookmark by container.bookmarkStore.bookmark.collectAsState()
    var menuAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }     // long-pressed ayah (menu open)
    var flashAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }    // ayah reached from the bookmark icon
    LaunchedEffect(flashAyah) { if (flashAyah != null) { delay(3000); flashAyah = null } }
    val highlight = menuAyah ?: flashAyah
    val menuText by produceState("", menuAyah) {
        value = menuAyah?.let { (s, a) ->
            container.quranRepository.getAyahWords(s, a).filterNot { it.isAyahMarker }.joinToString(" ") { it.text }
        } ?: ""
    }

    val titleSurah = currentPage?.firstSurah()
    val titleName = titleSurah?.let { surahs.getOrNull(it - 1)?.nameAr }
    fun goTo(p: Int) = scope.launch { pager.scrollToPage((p - 1).coerceIn(0, QuranConstants.PAGE_COUNT - 1)) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showSurahs = true }) {
                MenuIcon(MaterialTheme.colorScheme.primary, 18.dp)
                Spacer(Modifier.width(6.dp))
                if (titleSurah != null && titleName != null) SurahNameText(titleSurah, titleName, 22.sp, LocalContentColor.current)
                else Text("الفهرس")
            }
            Spacer(Modifier.weight(1f))
            Text("صفحة ${current.toArabicDigits()}", fontSize = 14.sp, color = Muted)
            // odd pages are the right-hand page of the Mushaf, even pages the left-hand page
            val rightPage = current % 2 == 1
            Spacer(Modifier.width(6.dp))
            Box(Modifier.semantics { contentDescription = if (rightPage) "الصفحة اليمنى من المصحف" else "الصفحة اليسرى من المصحف" }) {
                PageSideIcon(rightPage, MaterialTheme.colorScheme.primary, 20.dp)
            }
            IconButton(
                onClick = {
                    val b = bookmark
                    if (b == null) {
                        Toast.makeText(ctx, "لا توجد علامة بعد: اضغط مطولًا على آية لحفظها", Toast.LENGTH_SHORT).show()
                    } else scope.launch {
                        val target = container.quranRepository.getPageForAyah(b.surah, b.ayah)
                        flashAyah = b.surah to b.ayah
                        pager.scrollToPage((target - 1).coerceIn(0, QuranConstants.PAGE_COUNT - 1))
                    }
                },
                modifier = Modifier.semantics { contentDescription = "العلامة المحفوظة" },
            ) { BookmarkIcon(MaterialTheme.colorScheme.primary, filled = bookmark != null, size = 24.dp) }
            IconButton(onClick = onOpenSettings, modifier = Modifier.semantics { contentDescription = "الإعدادات" }) {
                GearIcon(MaterialTheme.colorScheme.primary, 24.dp)
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { index ->
            MushafPageFrame(
                pageNumber = index + 1, data = data, modifier = Modifier.fillMaxSize(),
                onWordLongClick = { w -> menuAyah = w.surah to w.ayah },
                highlightAyah = highlight,
            )
        }
        if (current in unverified) {
            Text(
                "ملاحظة: قد يختلف توزيع كلمة واحدة بين سطرين في هذه الصفحة.",
                fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { onStartRecitation(current) }, modifier = Modifier.weight(1f)) { Text("ابدأ التسميع") }
            // Arrows sit together on the physical LEFT (forced LTR): ← goes to the next page, → to the previous one.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { goTo(current + 1) }, enabled = current < QuranConstants.PAGE_COUNT,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "الصفحة التالية" },
                    ) { Text("←", fontSize = 22.sp) }
                    OutlinedButton(
                        onClick = { goTo(current - 1) }, enabled = current > 1,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "الصفحة السابقة" },
                    ) { Text("→", fontSize = 22.sp) }
                }
            }
        }
    }

    menuAyah?.let { (s, a) ->
        val name = surahs.getOrNull(s - 1)?.nameAr ?: ""
        AyahActionsDialog(
            title = "سورة $name · الآية ${a.toArabicDigits()}",
            ayahText = menuText,
            onCopy = {
                clipboard.setText(AnnotatedString("$menuText ﴿${a.toArabicDigits()}﴾\n[سورة $name: ${a.toArabicDigits()}]"))
                Toast.makeText(ctx, "تم نسخ الآية", Toast.LENGTH_SHORT).show()
                menuAyah = null
            },
            onBookmark = {
                container.bookmarkStore.save(Bookmark(s, a))
                Toast.makeText(ctx, "تم حفظ العلامة", Toast.LENGTH_SHORT).show()
                menuAyah = null
            },
            onDismiss = { menuAyah = null },
        )
    }

    if (showSurahs) {
        QuranNavigationSheet(
            surahs = surahs,
            currentPage = current,
            pageOfJuz = { juz ->
                val (surah, ayah) = JuzStarts.FIRST_AYAH[juz - 1]
                container.quranRepository.getPageForAyah(surah, ayah)
            },
            onGoToPage = { goTo(it) },
            onDismiss = { showSurahs = false },
        )
    }
}
