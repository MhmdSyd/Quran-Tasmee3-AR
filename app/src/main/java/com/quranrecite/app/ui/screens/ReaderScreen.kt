package com.quranrecite.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranrecite.app.R
import com.quranrecite.app.di.AppContainer
import com.quranrecite.app.domain.PageLine
import com.quranrecite.app.domain.QuranConstants
import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.ui.theme.HafsFont

const val BASMALLAH = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

/** Reader shell. Agent 5 replaces the body with the real 15-line mushaf rendering. */
@Composable
fun ReaderScreen(
    page: Int,
    container: AppContainer,
    onPageChange: (Int) -> Unit,
    onStartRecitation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val quranPage by produceState<QuranPage?>(initialValue = null, key1 = page) {
        value = container.quranRepository.getPage(page)
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("الصفحة $page", fontSize = 18.sp)
            OutlinedButton(onClick = onOpenSettings) { Text("الإعدادات") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            quranPage?.lines?.forEach { line -> PageLineRow(line) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onPageChange(page + 1) }, enabled = page < QuranConstants.PAGE_COUNT) { Text("التالي") }
            Button(onClick = onStartRecitation, modifier = Modifier.weight(1f)) { Text("ابدأ التسميع") }
            OutlinedButton(onClick = { onPageChange(page - 1) }, enabled = page > 1) { Text("السابق") }
        }
    }
}

@Composable
fun PageLineRow(line: PageLine) {
    when (line) {
        is PageLine.SurahHeader -> Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.surah_header_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Text("سورة ${line.surah}", fontFamily = HafsFont, fontSize = 20.sp)
        }
        is PageLine.Basmallah -> Text(
            BASMALLAH, fontFamily = HafsFont, fontSize = 22.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        is PageLine.Ayahs -> Text(
            line.words.joinToString(" ") { it.text }, fontFamily = HafsFont, fontSize = 22.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}
