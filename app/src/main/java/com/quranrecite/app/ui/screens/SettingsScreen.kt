package com.quranrecite.app.ui.screens

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
import androidx.compose.material3.Slider
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

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onOpenAsrDiagnostics: () -> Unit = {}) {
    val repo = container.settingsRepository
    val s by repo.settings.collectAsState()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

        Text("حجم الخط")
        Slider(
            value = s.fontScale,
            onValueChange = { v -> repo.update { it.copy(fontScale = v) } },
            valueRange = 0.8f..1.6f,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("إظهار الخطأ فوراً")
            Switch(
                checked = s.showMistakeImmediately,
                onCheckedChange = { v -> repo.update { it.copy(showMistakeImmediately = v) } },
            )
        }

        OutlinedButton(onClick = onOpenAsrDiagnostics) { Text("تشخيص التعرف على الصوت (ASR)") }
        Button(onClick = onBack) { Text("رجوع") }
    }
}
