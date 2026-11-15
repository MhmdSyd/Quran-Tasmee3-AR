package com.quranrecite.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranrecite.app.domain.JuzStarts
import com.quranrecite.app.domain.QuranConstants
import com.quranrecite.app.domain.SurahInfo
import com.quranrecite.app.ui.mushaf.toArabicDigits
import com.quranrecite.app.ui.theme.HafsFont
import com.quranrecite.app.ui.theme.Muted

/** Parses western or Arabic-Indic digits; returns null if not a number inside 1..[max]. */
internal fun parseNumberInRange(raw: String, max: Int): Int? {
    val ascii = raw.trim().map { ch ->
        when (ch) { in '\u0660'..'\u0669' -> '0' + (ch - '\u0660'); in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0'); else -> ch }
    }.joinToString("")
    if (ascii.isEmpty() || !ascii.all { it in '0'..'9' } || ascii.length > 4) return null
    return ascii.toInt().takeIf { it in 1..max }
}

/**
 * Quran navigation menu: Surah list (114), direct page (1..604) and Juz (1..30).
 * [onGoToPage] navigates the reader; [pageOfJuz] resolves a juz start page from the Quran DB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranNavigationSheet(
    surahs: List<SurahInfo>,
    currentPage: Int,
    pageOfJuz: suspend (Int) -> Int,
    onGoToPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    val titles = listOf("السور", "الصفحة", "الجزء")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> LazyColumn(Modifier.heightIn(max = 560.dp)) {
                items(surahs, key = { it.number }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onGoToPage(s.startPage); onDismiss() }
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
            1 -> PageTab(currentPage, onGoToPage, onDismiss)
            else -> JuzTab(pageOfJuz, onGoToPage, onDismiss)
        }
    }
}

@Composable
private fun PageTab(currentPage: Int, onGoToPage: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = parseNumberInRange(text, QuranConstants.PAGE_COUNT)
    val invalid = text.isNotBlank() && parsed == null
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("اكتب رقم الصفحة (١ - ${QuranConstants.PAGE_COUNT.toArabicDigits()})", color = Muted)
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(4) }, singleLine = true, isError = invalid,
            label = { Text("رقم الصفحة") },
            placeholder = { Text(currentPage.toString()) },
            supportingText = { if (invalid) Text("رقم غير صالح: أدخل رقماً من ١ إلى ${QuranConstants.PAGE_COUNT.toArabicDigits()}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { parsed?.let { onGoToPage(it); onDismiss() } }, enabled = parsed != null, modifier = Modifier.fillMaxWidth()) {
            Text("اذهب")
        }
    }
}

@Composable
private fun JuzTab(pageOfJuz: suspend (Int) -> Int, onGoToPage: (Int) -> Unit, onDismiss: () -> Unit) {
    var pending by remember { mutableStateOf<Int?>(null) }
    pending?.let { juz ->
        androidx.compose.runtime.LaunchedEffect(juz) {
            val page = runCatching { pageOfJuz(juz) }.getOrNull()
            pending = null
            if (page != null) { onGoToPage(page); onDismiss() }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.heightIn(max = 460.dp).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items((1..JuzStarts.COUNT).toList()) { j ->
            OutlinedButton(onClick = { pending = j }, enabled = pending == null) { Text(j.toArabicDigits(), fontSize = 16.sp) }
        }
    }
}
