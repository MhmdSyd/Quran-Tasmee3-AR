package com.quranrecite.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import com.quranrecite.app.domain.QuranConstants
import com.quranrecite.app.domain.QuranWord
import com.quranrecite.app.recitation.ShowAyahResult
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
import com.quranrecite.app.ui.components.GearIcon
import com.quranrecite.app.ui.components.ArrowIcon
import com.quranrecite.app.ui.components.EyeIcon
import com.quranrecite.app.ui.components.PageSideIcon
import com.quranrecite.app.ui.components.PauseIcon
import com.quranrecite.app.ui.components.PlayIcon
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

private val ScreenBg = Color.White

// Microphone toggle colours: idle = the existing start green, reciting = amber, paused = slate blue.
private val MicActive = Color(0xFFB5651D)
private val MicPaused = Color(0xFF3E5C76)

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
    val engineState by engine.state.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState()

    // Resume the session when this screen is re-entered for the same page (Settings and back, rotation); else start fresh.
    val resumed = remember(page) {
        val s = container.activeSession
        val p = matcher.progress.value
        s != null && s.page == page && p.pageNumber == s.currentPage && !p.finished
    }
    // A fresh screen must not see the previous page's matcher state (finished = true would end the new session at once,
    // with an empty summary), so the matcher is reset synchronously, BEFORE progress is first read.
    val session = remember(page) {
        if (resumed) container.activeSession!! else { matcher.reset(); container.beginSession(page) }
    }
    val progress by matcher.progress.collectAsState()
    val startedAt = session.startedAt
    val timer = session.timer
    var error by remember { mutableStateOf<SpeechEvent.Error?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<QuranWord?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var feedbackVisible by remember { mutableStateOf(false) }
    // The page on screen. Starts at the page of the route; "show ayah" and the arrows can move it without leaving the screen,
    // so the microphone and the session keep running.
    var curPage by remember(page) { mutableStateOf(if (resumed) session.currentPage else page) }
    var switching by remember { mutableStateOf(false) }
    LaunchedEffect(curPage) { container.readingPosition.lastPage = curPage }
    // True while progress.finished was caused by "show ayah" (the page's last ayah was shown): that must not end the session.
    var finishedByShow by remember { mutableStateOf(false) }

    // 1) page + basmala -> matcher
    LaunchedEffect(page) {
        if (resumed) return@LaunchedEffect
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
        onDispose { timer.pause(); container.appScope.launch { engine.stop() } }
    }

    // Leaving with Back abandons the session (Settings / rotation do not go through here).
    BackHandler { container.endSession(); onBack() }

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
            EngineState.Paused -> { timer.start(); scope.launch { engine.resume() } }
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
            container.endSession()
            onFinish(matcher.report().toSummary(startedAt, timer.elapsedMillis()))
        }
    }
    // Moves the screen (and the matcher) to [target], keeping the audio session and its bookkeeping. Progress of the page
    // that is left is not carried over: the matcher follows one page at a time.
    fun moveToPage(target: Int, thenShowAyah: Boolean) {
        scope.launch {
            val p = container.mushafData.page(target)
            matcher.continueOnPage(p, container.mushafData.basmallah())
            session.currentPage = target
            curPage = target
            if (thenShowAyah) { finishedByShow = true; matcher.showAyah() }
            switching = false
        }
    }
    fun onArrow(target: Int) {
        if (finishing || switching || target !in 1..QuranConstants.PAGE_COUNT) return
        switching = true
        moveToPage(target, thenShowAyah = false)
    }
    fun onShowAyah() {
        if (finishing || switching) return
        finishedByShow = true
        if (matcher.showAyah() == ShowAyahResult.Shown) return
        val next = curPage + 1
        if (next > QuranConstants.PAGE_COUNT) { finishedByShow = false; finish(); return }   // end of the Quran
        switching = true
        moveToPage(next, thenShowAyah = true)
    }

    // Page completed by reciting. "تسميع مستمر" on: carry on with the next page (microphone and session keep running, no
    // results screen). Off, or at the end of the Quran: finish and show the results as before.
    LaunchedEffect(progress.finished) {
        if (!progress.finished) {
            finishedByShow = false
        } else if (!finishedByShow && progress.pageNumber == curPage && progress.totalWords > 0) {
            if (container.settingsRepository.settings.value.continuousRecitation &&
                curPage < QuranConstants.PAGE_COUNT && !switching && !finishing
            ) {
                switching = true
                moveToPage(curPage + 1, thenShowAyah = false)
            } else finish()
        }
    }

    Column(Modifier.fillMaxSize().background(ScreenBg).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        // header
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButtonBox("رجوع إلى المصحف", onClick = { container.endSession(); onBack() }) { BackIcon(Ink) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تسميع الصفحة ${curPage.toArabicDigits()}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.width(6.dp))
                    val rightPage = curPage % 2 == 1   // odd = right-hand page of the Mushaf, even = left-hand page
                    Box(Modifier.semantics { contentDescription = if (rightPage) "الصفحة اليمنى من المصحف" else "الصفحة اليسرى من المصحف" }) {
                        PageSideIcon(rightPage, Ink, 18.dp)
                    }
                }
                Text(
                    "${SummaryText.digits(progress.passedWords)} من ${SummaryText.digits(progress.totalWords)} كلمة",
                    fontSize = 11.sp, color = Muted,
                )
            }
            IconButtonBox("إعدادات التسميع", onOpenSettings) { GearIcon(Ink) }
        }

        // Same box as the reader's pager (weight 1, 8dp sides) -> the page has the same size and position in both screens.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            MushafPageFrame(
                pageNumber = curPage,
                data = container.mushafData,
                modifier = Modifier.fillMaxSize(),
                mode = MushafMode.Recitation,
                wordStates = remember(progress, settings.showMistakeImmediately) { progress.toWordVisuals(settings.showMistakeImmediately) },
                onWordClick = { if (!finishing) picked = it },
            )
            // Status / feedback / errors float over the bottom of the page instead of taking space above it.
            StatusLine(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                error = error, micDenied = micDenied, loading = engineState == EngineState.Loading,
                finishing = finishing, feedback = if (feedbackVisible) SummaryText.feedback(progress.feedback) else null,
                onOpenAppSettings = { ctx.startActivity(MicPermission.appSettingsIntent(ctx)) },
            )
        }

        // controls (physical left -> right): previous/next page, hint, show ayah, microphone toggle.
        // The row is forced LTR so the arrows stay on the left and the microphone on the right in this RTL screen.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                // same height/padding as the reader's bottom bar (48dp controls, 8dp vertical) so the page keeps its size
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onArrow(curPage + 1) }, enabled = !finishing && !switching && curPage < QuranConstants.PAGE_COUNT,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "الصفحة التالية" },
                    ) { ArrowIcon(pointsLeft = true, color = LocalContentColor.current) }
                    OutlinedButton(
                        onClick = { onArrow(curPage - 1) }, enabled = !finishing && !switching && curPage > 1,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "الصفحة السابقة" },
                    ) { ArrowIcon(pointsLeft = false, color = LocalContentColor.current) }
                }
                Spacer(Modifier.weight(1f))
                ControlPill("تلميح", enabled = !finishing && !progress.finished, onClick = { matcher.hint() })
                ControlPill("إظهار الآية", enabled = !finishing && !switching, onClick = ::onShowAyah)
                MicButton(state = engineState, onClick = ::onMic)
            }
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
    modifier: Modifier,
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
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
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

/** Compact 48dp-high text control, the same height as the page arrows and the microphone button. */
@Composable
private fun ControlPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(48.dp),
    ) { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) }
}

/**
 * One toggle for start / pause / resume. Idle: green microphone. Reciting: amber with a pause icon (no glow).
 * Paused: slate blue with a play icon. The action itself lives in onMic(): it calls engine.start / pause / resume.
 */
@Composable
private fun MicButton(state: EngineState, onClick: () -> Unit) {
    val busy = state == EngineState.Loading || state == EngineState.Stopping
    val description = when (state) {
        EngineState.Listening -> "إيقاف التسميع مؤقتًا"
        EngineState.Paused -> "متابعة التسميع"
        EngineState.Loading, EngineState.Stopping -> "جارٍ التحميل"
        else -> "ابدأ التسميع"
    }
    val color = when (state) {
        EngineState.Listening -> MicActive
        EngineState.Paused -> MicPaused
        else -> Green
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (busy) color.copy(alpha = 0.6f) else color)
            .clickable(enabled = !busy, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            EngineState.Listening -> PauseIcon(Paper, 24.dp)
            EngineState.Paused -> PlayIcon(Paper, 24.dp)
            else -> MicIcon(Paper, 24.dp)
        }
    }
}
