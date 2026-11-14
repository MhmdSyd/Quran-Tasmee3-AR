package com.quranrecite.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.quranrecite.app.ui.mushaf.firstSurah
import com.quranrecite.app.ui.mushaf.toArabicDigits
import com.quranrecite.app.ui.theme.HafsFont
import com.quranrecite.app.ui.theme.Muted
import kotlinx.coroutines.launch

/**
 * Madani mushaf reader: 604 swipeable pages (RTL: swipe left = next page), prev/next buttons and a surah picker.
 * [onStartRecitation] receives the page currently shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val surahs by produceState(emptyList<SurahInfo>(), data) { value = data.surahs() }
    val unverified by produceState(emptySet<Int>(), data) { value = data.unverifiedPages() }
    val currentPage by produceState<QuranPage?>(null, current, data) { value = data.page(current) }
    var showSurahs by remember { mutableStateOf(false) }

    val surahTitle = currentPage?.firstSurah()?.let { surahs.getOrNull(it - 1)?.nameAr }
    fun goTo(p: Int) = scope.launch { pager.scrollToPage((p - 1).coerceIn(0, QuranConstants.PAGE_COUNT - 1)) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showSurahs = true }) { Text(surahTitle?.let { "سورة $it" } ?: "السور") }
            Spacer(Modifier.weight(1f))
            Text("صفحة ${current.toArabicDigits()}", fontSize = 14.sp, color = Muted)
            TextButton(onClick = onOpenSettings) { Text("الإعدادات") }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { index ->
            MushafPageFrame(pageNumber = index + 1, data = data, modifier = Modifier.fillMaxSize())
        }
        if (current in unverified) {
            Text(
                "ملاحظة: قد يختلف توزيع كلمة واحدة بين سطرين في هذه الصفحة.",
                fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // RTL: the first child is on the right = previous page, as in a printed mushaf.
            OutlinedButton(onClick = { goTo(current - 1) }, enabled = current > 1) { Text("السابق") }
            Button(onClick = { onStartRecitation(current) }, modifier = Modifier.weight(1f)) { Text("ابدأ التسميع") }
            OutlinedButton(onClick = { goTo(current + 1) }, enabled = current < QuranConstants.PAGE_COUNT) { Text("التالي") }
        }
    }

    if (showSurahs) {
        ModalBottomSheet(
            onDismissRequest = { showSurahs = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn {
                items(surahs, key = { it.number }) { s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { goTo(s.startPage); showSurahs = false }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(s.number.toArabicDigits(), color = Muted, fontSize = 14.sp)
                        Text(s.nameAr, fontFamily = HafsFont, fontSize = 22.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${s.ayahCount.toArabicDigits()} آية · ص ${s.startPage.toArabicDigits()}", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
