package com.quranrecite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranrecite.app.di.AppContainer
import com.quranrecite.app.domain.QuranConstants
import com.quranrecite.app.session.SessionSummary
import com.quranrecite.app.ui.theme.GreenTint

/**
 * Recitation shell: text hidden, placeholder bars per line.
 * Agent 5 wires speech -> matcher -> word reveal; this screen only owns lifecycle + summary hand-off.
 */
@Composable
fun RecitationScreen(
    page: Int,
    container: AppContainer,
    onFinish: (SessionSummary) -> Unit,
    onBack: () -> Unit,
) {
    val startedAt = remember { System.currentTimeMillis() }
    val progress by container.recitationMatcher.progress.collectAsState()

    LaunchedEffect(page) {
        container.recitationMatcher.begin(container.quranRepository.getPage(page))
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("التسميع – الصفحة $page", fontSize = 18.sp)
        Text("كلمات ظاهرة: ${progress.revealedCount}   أخطاء: ${progress.mistakeCount}")
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
            repeat(QuranConstants.LINES_PER_PAGE) {
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(GreenTint)) {}
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("إلغاء") }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    onFinish(
                        SessionSummary(
                            startedAtMillis = startedAt,
                            durationMillis = System.currentTimeMillis() - startedAt,
                            pageNumber = page,
                            revealedWords = progress.revealedCount,
                            mistakes = progress.mistakeCount,
                            mistakeWordIds = progress.wordStates.filterValues {
                                it == com.quranrecite.app.recitation.WordState.Mistake
                            }.keys.toList(),
                        ),
                    )
                },
            ) { Text("إنهاء") }
        }
    }
}
