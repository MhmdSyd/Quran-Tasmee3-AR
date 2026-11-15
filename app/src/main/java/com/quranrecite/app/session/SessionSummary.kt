package com.quranrecite.app.session

import com.quranrecite.app.recitation.RecitationReport
import com.quranrecite.app.recitation.ReviewReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A word listed under "for review". [text] is the canonical Hafs word, verbatim. */
data class ReviewEntry(
    val wordId: Int,
    val surah: Int,
    val ayah: Int,
    val indexInAyah: Int,
    val text: String,
    val reason: ReviewReason,
)

/**
 * One finished recitation session. All data stays on the device (the app has no INTERNET permission).
 * "For review" is deliberately neutral: it lists words that were not recognised cleanly, which is not a Tajweed verdict.
 */
data class SessionSummary(
    val startedAtMillis: Long,
    /** Active time (paused time excluded). */
    val durationMillis: Long,
    val pageNumber: Int,
    /** Words from the starting position to the end of the page. */
    val totalWords: Int,
    /** Words the reciter got past (shown, uncertain or marked skipped). */
    val passedWords: Int,
    /** Recognised cleanly on the first attempt, no hint, no manual help. */
    val confidentWords: Int,
    val review: List<ReviewEntry> = emptyList(),
    val ayahsReached: Int = 0,
    val ayahsCompleted: Int = 0,
    val repetitions: Int = 0,
    val retries: Int = 0,
    /** The page was recited to its last word. */
    val completed: Boolean = false,
) {
    /** Share of the words passed that were recognised cleanly at the first attempt (0..100). */
    val firstAttemptPercent: Int
        get() = if (passedWords == 0) 0 else (confidentWords * 100.0 / passedWords).toInt()
}

fun RecitationReport.toSummary(startedAtMillis: Long, activeMillis: Long) = SessionSummary(
    startedAtMillis = startedAtMillis,
    durationMillis = activeMillis,
    pageNumber = pageNumber,
    totalWords = totalWords,
    passedWords = passedWords,
    confidentWords = confidentWords,
    review = reviewItems.map { ReviewEntry(it.word.id, it.word.surah, it.word.ayah, it.word.indexInAyah, it.word.text, it.reason) },
    ayahsReached = ayahsReached.size,
    ayahsCompleted = ayahsCompleted.size,
    repetitions = repetitions,
    retries = retries,
    completed = finished,
)

/** Stopwatch that does not count paused time. [clock] is injectable for tests. */
class ActiveTimer(private val clock: () -> Long = System::currentTimeMillis) {
    private var accumulated = 0L
    private var runningSince = -1L

    fun start() { if (runningSince < 0) runningSince = clock() }
    fun pause() { if (runningSince >= 0) { accumulated += clock() - runningSince; runningSince = -1 } }
    fun elapsedMillis(): Long = accumulated + if (runningSince >= 0) clock() - runningSince else 0
}

interface SessionStore {
    /** Newest first. */
    val sessions: StateFlow<List<SessionSummary>>
    suspend fun save(summary: SessionSummary)
}

/** In-memory placeholder; Agent 6 may persist to a local file/SQLite. */
class InMemorySessionStore : SessionStore {
    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    override val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()
    override suspend fun save(summary: SessionSummary) { _sessions.value = listOf(summary) + _sessions.value }
}
