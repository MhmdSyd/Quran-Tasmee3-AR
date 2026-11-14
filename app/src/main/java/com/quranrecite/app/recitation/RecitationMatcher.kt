package com.quranrecite.app.recitation

import com.quranrecite.app.domain.QuranPage
import com.quranrecite.app.speech.SpeechEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class WordState { Hidden, Revealed, Mistake }

data class RecitationProgress(
    val pageNumber: Int = 0,
    /** Keyed by [com.quranrecite.app.domain.QuranWord.id]. Missing key = Hidden. */
    val wordStates: Map<Int, WordState> = emptyMap(),
    /** Next expected word id, null when idle or finished. */
    val currentWordId: Int? = null,
    val revealedCount: Int = 0,
    val mistakeCount: Int = 0,
    val finished: Boolean = false,
)

/**
 * Matches recognized speech against the expected Quran words and drives reveal/mistake state.
 * Implemented by Agent 4. Pure Kotlin logic, no Android dependencies.
 */
interface RecitationMatcher {
    val progress: StateFlow<RecitationProgress>

    /** Start matching on [page]; [startWordId] null = first word of the page. */
    fun begin(page: QuranPage, startWordId: Int? = null)

    fun onSpeech(event: SpeechEvent)

    fun reset()
}

class NoOpRecitationMatcher : RecitationMatcher {
    private val _progress = MutableStateFlow(RecitationProgress())
    override val progress: StateFlow<RecitationProgress> = _progress
    override fun begin(page: QuranPage, startWordId: Int?) {
        _progress.value = RecitationProgress(pageNumber = page.number)
    }
    override fun onSpeech(event: SpeechEvent) {}
    override fun reset() { _progress.value = RecitationProgress() }
}
