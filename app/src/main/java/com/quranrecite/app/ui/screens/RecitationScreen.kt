package com.quranrecite.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quranrecite.app.di.AppContainer
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.session.ActiveTimer
import com.quranrecite.app.session.SessionSummary
import com.quranrecite.app.session.SummaryText
import com.quranrecite.app.session.toSummary
import com.quranrecite.app.speech.EngineState
import com.quranrecite.app.speech.SpeechErrorCode
import com.quranrecite.app.speech.SpeechEvent
import com.quranrecite.app.speech.asr.MicPermission
import com.quranrecite.app.speech.asr.rememberMicPermissionRequest
import com.quranrecite.app.ui.components.BackIcon
import com.quranrecite.app.ui.components.BulbIcon
import com.quranrecite.app.ui.components.MicIcon
import com.quranrecite.app.ui.components.SlidersIcon
import com.quranrecite.app.ui.components.StopIcon
import com.quranrecite.app.ui.mushaf.MushafMode
import com.quranrecite.app.ui.mushaf.MushafPageFrame
import com.quranrecite.app.ui.mushaf.MushafStyle
import com.quranrecite.app.ui.mushaf.toArabicDigits
import com.quranrecite.app.ui.mushaf.toWordVisuals
import com.quranrecite.app.ui.theme.Gold
import com.quranrecite.app.ui.theme.Green
import com.quranrecite.app.ui.theme.GreenDark
import com.quranrecite.app.ui.theme.Ink
import com.quranrecite.app.ui.theme.Muted
import com.quranrecite.app.ui.theme.Paper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private val ScreenBg = Color(0xFFFBF6EA)
private val RingGreen = Color(0xFFCFE3D6)

/**
 * Recitation: the real mushaf page with every word hidden; words appear as the matcher recognises them
 * (speech -> RecitationMatcher -> progress -> page). Offline; the microphone is only open while listening.
 */
@Composable
fun RecitationScreen(
    page: Int,
    container: AppContainer,
    onFinish: (SessionSummary) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val matcher = container.recitationMatcher
    val engine = container.speechEngine
    val progress by matcher.progress.collectAsState()
    val engineState by engine.state.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState()

    val startedAt = remember { System.currentTimeMillis() }
    val timer = remember { ActiveTimer() }
    var error by remember { mutableStateOf<SpeechEvent.Error?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<QuranWord?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var feedbackVisible by remember { mutableStateOf(false) }

    // 1) page + basmala -> matcher
    LaunchedEffect(page) {
        val p = container.mushafData.page(page)
        matcher.begin(p, null, container.mushafData.basmallah())
    }

    // 2) speech events -> matcher. Collected before start(): the engine's flow has no replay.
    LaunchedEffect(Unit) {
        engine.events.collect { ev ->
            when (ev) {
                is SpeechEvent.Listening -> { error = null; micDenied = false; timer.start() }
                is SpeechEvent.Error -> error = ev
                else -> Unit
            }
            matcher.onSpeech(ev)
        }
    }

    // 3) unobtrusive feedback line, shown for a few seconds
    LaunchedEffect(progress.feedbackSeq) {
        if (progress.feedbackSeq > 0) { feedbackVisible = true; delay(3500); feedbackVisible = false }
    }

    // keep the screen on while reciting; stop the microphone whenever the screen is left
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) {
        onDispose { container.appScope.launch { engine.stop() } }
    }

    // 4) backgrounding / interruptions: release the mic, continue where we were when the user is back
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentEngineState by rememberUpdatedState(engineState)
    DisposableEffect(lifecycleOwner) {
        var wasListening = false
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (currentEngineState == EngineState.Listening) {
                    wasListening = true
                    timer.pause()
                    scope.launch { engine.pause() }
                }
                Lifecycle.Event.ON_START -> if (wasListening) {
                    wasListening = false
                    scope.launch { engine.resume() }
                }
                else -> Unit
            }
            Unit
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun startListening() { error = null; scope.launch { engine.start() } }
    val askMic = rememberMicPermissionRequest { granted ->
        if (granted) { micDenied = false; startListening() } else micDenied = true
    }
    fun onMic() {
        if (finishing) return
        when (engineState) {
            EngineState.Listening -> { timer.pause(); scope.launch { engine.pause() } }
            EngineState.Paused -> scope.launch { engine.resume() }
            EngineState.Loading, EngineState.Stopping -> Unit
            else -> if (MicPermission.isGranted(ctx)) startListening() else askMic()
        }
    }
    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            engine.stop()                      // drains pending decodes and emits the last events
            repeat(3) { yield() }              // let the collector hand them to the matcher
            delay(150)
            timer.pause()
            onFinish(matcher.report().toSummary(startedAt, timer.elapsedMillis()))
        }
    }
    LaunchedEffect(progress.finished) { if (progress.finished) finish() }

    Column(Modifier.fillMaxSize().background(ScreenBg).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        // header
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButtonBox("رجوع إلى المصحف", onBack) { BackIcon(Ink) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("تسميع الصفحة ${page.toArabicDigits()}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "${SummaryText.digits(progress.passedWords)} من ${SummaryText.digits(progress.totalWords)} كلمة",
                    fontSize = 12.sp, color = Muted,
                )
            }
            IconButtonBox("إعدادات التسميع", onOpenSettings) { SlidersIcon(Ink) }
        }

        StatusLine(
            error = error, micDenied = micDenied, loading = engineState == EngineState.Loading,
            finishing = finishing, feedback = if (feedbackVisible) SummaryText.feedback(progress.feedback) else null,
            onOpenAppSettings = { ctx.startActivity(MicPermission.appSettingsIntent(ctx)) },
        )

        MushafPageFrame(
            pageNumber = page,
            data = container.mushafData,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            mode = MushafMode.Recitation,
            wordStates = remember(progress, settings.showMistakeImmediately) { progress.toWordVisuals(settings.showMistakeImmediately) },
            onWordClick = { if (!finishing) picked = it },
        )

        // controls: hint | microphone | finish
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction("تلميح", enabled = !finishing && !progress.finished, onClick = { matcher.hint() }) { BulbIcon(Ink) }
            MicButton(listening = engineState == EngineState.Listening, busy = engineState == EngineState.Loading, onClick = ::onMic)
            RoundAction("إنهاء", enabled = !finishing, onClick = ::finish) { StopIcon(Ink) }
        }
    }

    picked?.let { w ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(w.text, style = MushafStyle.Default.textStyle(28.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text("الآية ${SummaryText.digits(w.ayah)} · الكلمة ${SummaryText.digits(w.indexInAyah)}", color = Muted) },
            confirmButton = { TextButton(onClick = { matcher.moveTo(w.id); picked = null }) { Text("ابدأ من هنا") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { matcher.markRecited(w.id); picked = null }) { Text("أظهر الكلمة") }
                    TextButton(onClick = { picked = null }) { Text("إغلاق") }
                }
            },
        )
    }
}

@Composable
private fun IconButtonBox(description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun StatusLine(
    error: SpeechEvent.Error?,
    micDenied: Boolean,
    loading: Boolean,
    finishing: Boolean,
    feedback: String?,
    onOpenAppSettings: () -> Unit,
) {
    val text: String? = when {
        error != null -> when (error.code) {
            SpeechErrorCode.PermissionDenied -> "لا يوجد إذن للميكروفون"
            SpeechErrorCode.ModelMissing -> "ملف نموذج التعرف على الصوت غير موجود في التطبيق"
            SpeechErrorCode.ModelLoadFailed -> "تعذّر تحميل نموذج التعرف على الصوت"
            SpeechErrorCode.AudioInitFailed, SpeechErrorCode.AudioReadFailed -> "توقف الميكروفون (ربما يستخدمه تطبيق آخر) — اضغط الميكروفون للمتابعة"
            else -> error.message
        }
        micDenied -> "الميكروفون مطلوب للتسميع — اضغط هنا لفتح الإعدادات"
        finishing -> "جارٍ إنهاء التسميع…"
        loading -> "جارٍ تحميل نموذج التعرف على الصوت…"
        else -> feedback
    }
    val warn = error != null || micDenied
    Box(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        if (text != null) {
            Text(
                text,
                fontSize = 13.sp,
                color = if (warn) Color(0xFF8A2D20) else GreenDark,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (warn) Color(0xFFFBEAE6) else Color(0xFFE3EFE7))
                    .then(if (micDenied) Modifier.clickable(onClick = onOpenAppSettings) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RoundAction(label: String, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.width(64.dp).clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Paper).border(1.5.dp, if (enabled) Gold else Gold.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Text(label, fontSize = 13.sp, color = if (enabled) Ink else Muted)
    }
}

@Composable
private fun MicButton(listening: Boolean, busy: Boolean, onClick: () -> Unit) {
    val description = when { listening -> "إيقاف الاستماع مؤقتًا"; busy -> "جارٍ التحميل"; else -> "ابدأ الاستماع" }
    Box(
        Modifier.size(84.dp).clip(CircleShape).background(if (listening) RingGreen else Color.Transparent)
            .clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(if (busy) Green.copy(alpha = 0.6f) else Green),
            contentAlignment = Alignment.Center,
        ) { MicIcon(Paper) }
    }
}
