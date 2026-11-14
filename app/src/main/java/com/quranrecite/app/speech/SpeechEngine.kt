package com.quranrecite.app.speech

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface SpeechEvent {
    data object Listening : SpeechEvent
    /** Unstable hypothesis; may be revised by later events. */
    data class Partial(val text: String) : SpeechEvent
    /** Committed utterance segment. */
    data class Final(val text: String) : SpeechEvent
    data class Error(val message: String) : SpeechEvent
}

/**
 * Offline Arabic speech recognition. Implemented by Agent 3 (no network, on-device model only).
 * Caller must hold RECORD_AUDIO permission before [start].
 */
interface SpeechEngine {
    val events: SharedFlow<SpeechEvent>
    suspend fun start()
    suspend fun stop()
    fun release()
}

class NoOpSpeechEngine : SpeechEngine {
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<SpeechEvent> = _events
    override suspend fun start() { _events.tryEmit(SpeechEvent.Error("Speech engine not implemented")) }
    override suspend fun stop() {}
    override fun release() {}
}
