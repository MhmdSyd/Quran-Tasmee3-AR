package com.quranrecite.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranrecite.app.di.AppContainer

@Composable
fun SummaryScreen(container: AppContainer, onDone: () -> Unit) {
    val sessions by container.sessionStore.sessions.collectAsState()
    val last = sessions.firstOrNull()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ملخص التسميع", fontSize = 20.sp)
        if (last == null) {
            Text("لا توجد جلسات بعد")
        } else {
            Text("الصفحة: ${last.pageNumber}")
            Text("الكلمات: ${last.revealedWords}")
            Text("الأخطاء: ${last.mistakes}")
            Text("المدة: ${last.durationMillis / 1000} ثانية")
        }
        Button(onClick = onDone) { Text("تم") }
    }
}
