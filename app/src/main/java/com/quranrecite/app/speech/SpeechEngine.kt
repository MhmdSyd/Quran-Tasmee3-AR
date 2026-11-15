package com.quranrecite.app.speech

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One word of recognizer output, in the model's own letter space (bare Uthmani letters, NO diacritics).
 * Times are milliseconds on the session audio clock (0 = first sample after [SpeechEngine.start]; time spent
 * paused is not counted). They come from CTC token frames (40 ms steps) and are approximate (+-80 ms).
 */
data class RecognizedWord(val text: String, val startMs: Long, val endMs: Long)

/** Why a [SpeechEvent.Final] segment was closed. */
enum class SegmentEnd {
    /** Silence of `endSilenceMs` followed the speech. Natural boundary. */
    EndOfSpeech,
    /** Segment hit `maxSegmentMs`; it was cut at the quietest point of its last seconds. May split a word. */
    MaxLength,
    /** Closed by pause()/stop(). */
    Flushed,
}

enum class SpeechErrorCode {
    PermissionDenied, ModelMissing, ModelLoadFailed, AudioInitFailed, AudioReadFailed, DecodeFailed, Unknown,
}

sealed interface SpeechEvent {
    /** Emitted once when audio capture is running (after start() and after resume()). */
    data object Listening : SpeechEvent

    /**
     * Unstable hypothesis for the speech the engine has heard SINCE the last [Final]. Each Partial replaces the
     * previous one completely (it is a re-decode of the whole open segment, not an increment).
     *
     * @param text space-separated words, raw model letters (no diacritics). Never display as Quran text.
     * @param words same words with approximate times.
     * @param stableWordCount length of the word prefix that two consecutive hypotheses agreed on, excluding the last
     *   word (which may be unfinished). Words beyond it may still change. 0 when unknown.
     * @param segmentId id of the segment this hypothesis belongs to; the [Final] that closes it carries the same id,
     *   except after a forced cut ([SegmentEnd.MaxLength]) where the remainder continues in id + 1.
     */
    data class Partial(
        val text: String,
        val words: List<RecognizedWord> = emptyList(),
        val stableWordCount: Int = 0,
        val segmentId: Int = 0,
        val audioStartMs: Long = 0,
        val audioEndMs: Long = 0,
        val decodeMs: Long = 0,
        val raw: String = text,
    ) : SpeechEvent

    /**
     * Committed segment. Final segments partition the audio: no overlap and no repetition, so concatenating Final
     * texts in arrival order gives the whole recognised utterance. Segments with empty text are not emitted.
     *
     * @param raw exact recognizer output (word separator "▁" retained), for matching/diagnostics.
     * @param decodeMs time spent in the model for this segment; [latencyMs] = segment closed -> event emitted.
     */
    data class Final(
        val text: String,
        val words: List<RecognizedWord> = emptyList(),
        val segmentId: Int = 0,
        val audioStartMs: Long = 0,
        val audioEndMs: Long = 0,
        val end: SegmentEnd = SegmentEnd.EndOfSpeech,
        val decodeMs: Long = 0,
        val latencyMs: Long = 0,
        val raw: String = text,
    ) : SpeechEvent

    data class Error(val message: String, val code: SpeechErrorCode = SpeechErrorCode.Unknown) : SpeechEvent
}

enum class EngineState {
    /** Nothing loaded yet (or released). */
    Unloaded,
    /** Installing/loading the model. */
    Loading,
    /** Model loaded, microphone closed. */
    Ready,
    Listening,
    Paused,
    /** stop() is draining pending decodes. */
    Stopping,
    /** Last operation failed; see the last [SpeechEvent.Error]. start() may be called again to retry. */
    Error,
}

/**
 * Offline Arabic speech recognition (Agent 4). Fully on-device; no network.
 *
 * NOTE: the model is an *offline* (non-streaming) CTC model. Incremental output is produced by chunked decoding:
 * silence-endpointed segments plus periodic re-decoding of the open segment. It is not true streaming.
 *
 * Collect [events] BEFORE calling [start]: the flow has no replay, so events emitted while nobody collects are lost.
 * Caller must hold RECORD_AUDIO before [start] (otherwise an Error(PermissionDenied) event is emitted).
 */
interface SpeechEngine {
    val events: SharedFlow<SpeechEvent>
    val state: StateFlow<EngineState>

    /** Optional: install + load the model ahead of time so start() is fast. Idempotent. */
    suspend fun prepare() {}

    suspend fun start()
    suspend fun pause()
    suspend fun resume()

    /** Stops the microphone, decodes everything still pending, emits the remaining events, then returns. */
    suspend fun stop()

    fun release()
}

class NoOpSpeechEngine : SpeechEngine {
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<SpeechEvent> = _events
    private val _state = MutableStateFlow(EngineState.Unloaded)
    override val state: StateFlow<EngineState> = _state
    override suspend fun start() { _events.tryEmit(SpeechEvent.Error("Speech engine not implemented")) }
    override suspend fun pause() {}
    override suspend fun resume() {}
    override suspend fun stop() {}
    override fun release() {}
}
