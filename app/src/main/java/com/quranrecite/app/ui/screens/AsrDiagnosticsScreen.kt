package com.quranrecite.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranrecite.app.di.AppContainer
import com.quranrecite.app.speech.EngineState
import com.quranrecite.app.speech.SpeechEvent
import com.quranrecite.app.speech.asr.BenchmarkReport
import com.quranrecite.app.speech.asr.MicPermission
import com.quranrecite.app.speech.asr.rememberMicPermissionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Developer screen for the offline ASR engine: live raw recognizer output (model letters, NO diacritics, never
 * Quran text), engine state, latency/CPU/memory numbers and a synthetic-audio benchmark. Use it for the on-device
 * measurements listed in AGENT_4_HANDOFF.md, ideally with airplane mode on.
 */
@Composable
fun AsrDiagnosticsScreen(container: AppContainer, onBack: () -> Unit) {
    val engine = container.asrEngine
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by engine.state.collectAsState()
    val metrics by engine.metrics.collectAsState()

    var micGranted by remember { mutableStateOf(MicPermission.isGranted(ctx)) }
    val requestMic = rememberMicPermissionRequest { micGranted = it }
    var partial by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var bench by remember { mutableStateOf<BenchmarkReport?>(null) }
    var benchRunning by remember { mutableStateOf(false) }
    val finals = remember { mutableStateListOf<String>() }

    LaunchedEffect(engine) {
        engine.events.collect { e ->
            when (e) {
                is SpeechEvent.Listening -> error = null
                is SpeechEvent.Partial -> partial = "${e.text}   [stable ${e.stableWordCount}, ${e.decodeMs} ms]"
                is SpeechEvent.Final -> {
                    partial = ""
                    finals.add(0, "#${e.segmentId} ${e.end} ${e.audioStartMs}-${e.audioEndMs} ms  dec ${e.decodeMs} ms  lat ${e.latencyMs} ms\n${e.text}")
                }
                is SpeechEvent.Error -> error = "${e.code}: ${e.message}"
            }
        }
    }
    DisposableEffect(engine) {
        onDispose { CoroutineScope(Dispatchers.Default).launch { engine.stop() } }
    }

    val m = metrics
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ASR diagnostics (offline, quran-asr-small INT8)", fontSize = 16.sp)
        Text("State: $state   mic: ${if (micGranted) "granted" else "NOT granted"}   model load: ${m.modelLoadMs} ms")
        error?.let { Text("Error: $it") }

        if (!micGranted) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { requestMic() }) { Text("Allow microphone") }
                OutlinedButton(onClick = { ctx.startActivity(MicPermission.appSettingsIntent(ctx)) }) { Text("App settings") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val idle = state == EngineState.Unloaded || state == EngineState.Ready || state == EngineState.Error
            OutlinedButton(enabled = idle, onClick = { scope.launch { engine.prepare() } }) { Text("Load") }
            Button(enabled = idle && micGranted, onClick = { finals.clear(); partial = ""; scope.launch { engine.start() } }) { Text("Start") }
            OutlinedButton(enabled = state == EngineState.Listening, onClick = { scope.launch { engine.pause() } }) { Text("Pause") }
            OutlinedButton(enabled = state == EngineState.Paused, onClick = { scope.launch { engine.resume() } }) { Text("Resume") }
            Button(
                enabled = state == EngineState.Listening || state == EngineState.Paused,
                onClick = { scope.launch { engine.stop() } },
            ) { Text("Stop") }
        }
        OutlinedButton(
            enabled = !benchRunning && (state == EngineState.Ready || state == EngineState.Unloaded || state == EngineState.Error),
            onClick = { benchRunning = true; scope.launch { bench = engine.benchmark(); benchRunning = false } },
        ) { Text(if (benchRunning) "Benchmarking…" else "Benchmark (synthetic audio)") }

        Text("Live (unstable): $partial", fontSize = 18.sp)

        val p = m.pipeline
        val r = m.resources
        Text(
            "decodes ${p.decodes}  RTF avg %.3f last %.3f  last ${p.lastDecodeMs} ms\n".format(p.avgRtf, p.lastRtf) +
                "partial latency p50/p95 ${p.partialLatencyP50Ms}/${p.partialLatencyP95Ms} ms   final p50/p95 ${p.finalLatencyP50Ms}/${p.finalLatencyP95Ms} ms\n" +
                "partials ${p.partials} finals ${p.finals} empty ${p.emptyFinals} stale ${p.stalePartialsDropped} cuts ${p.forcedCuts} blips ${p.discardedBlips} errors ${p.decodeErrors}\n" +
                "CPU %.0f%% now / %.0f%% avg (of one core)   native heap %.0f MB (peak %.0f)   PSS %.0f MB (peak %.0f)".format(
                    r.cpuPercent, r.avgCpuPercent, r.nativeHeapMb, r.peakNativeHeapMb, r.pssMb, r.peakPssMb,
                ),
            fontSize = 12.sp,
        )
        bench?.let { b ->
            Text(
                b.rows.joinToString("\n") { "${it.audioSec}s audio: median ${it.medianMs} ms (min ${it.minMs}, max ${it.maxMs}), RTF %.3f".format(it.rtf) } +
                    "\nCPU avg %.0f%%  heap %.0f -> %.0f MB  peak PSS %.0f MB\n${b.note}".format(b.avgCpuPercent, b.heapBeforeMb, b.heapAfterMb, b.peakPssMb),
                fontSize = 12.sp,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(finals) { Text(it, fontSize = 14.sp) }
        }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
