package com.quranrecite.app.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranrecite.app.di.AppContainer
import com.quranrecite.app.settings.Strictness
import com.quranrecite.app.ui.theme.Muted

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onOpenAsrDiagnostics: () -> Unit = {}) {
    val repo = container.settingsRepository
    val s by repo.settings.collectAsState()
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("إعدادات التسميع", fontSize = 20.sp)

        Text("الصرامة")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Strictness.Lenient to "متساهل", Strictness.Normal to "عادي", Strictness.Strict to "صارم").forEach { (value, label) ->
                FilterChip(
                    selected = s.strictness == value,
                    onClick = { repo.update { it.copy(strictness = value) } },
                    label = { Text(label) },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("إظهار علامات المراجعة فوراً")
            Switch(
                checked = s.showMistakeImmediately,
                onCheckedChange = { v -> repo.update { it.copy(showMistakeImmediately = v) } },
            )
        }

        OutlinedButton(onClick = onOpenAsrDiagnostics) { Text("تشخيص التعرف على الصوت (ASR)") }
        Button(onClick = onBack) { Text("رجوع") }

        // About / attribution (see THIRD_PARTY_NOTICES.md)
        Text(
            "تطبيق يعمل دون اتصال بالإنترنت: لا يُرسل أي صوت أو بيانات خارج الجهاز.\n" +
                "التعرف على الصوت تقريبي ولا يحكم على صحة التجويد؛ «للمراجعة» تعني أن الكلمة لم يتأكد التعرف عليها.\n\n" +
                "Offline speech model: Abdou/quran-asr-small (MIT), trained on data incl. tarteel-ai/tlog (CC-BY-4.0). " +
                "Runtime: sherpa-onnx (Apache-2.0). Quran font: KFGQPC Uthmanic Hafs. Full notices: THIRD_PARTY_NOTICES.md.",
            fontSize = 11.sp,
            color = Muted,
        )
    }
}
