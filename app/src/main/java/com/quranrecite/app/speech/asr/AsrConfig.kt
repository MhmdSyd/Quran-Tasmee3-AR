package com.quranrecite.app.speech.asr

/** Facts about the bundled model (Abdou/quran-asr-small, INT8 ONNX, sherpa-onnx "zipformer CTC" loader). */
object AsrModel {
    const val SAMPLE_RATE = 16_000
    const val NAME = "quran-asr-small"
    const val ASSET_DIR = "asr/quran-asr-small"
    const val MODEL_FILE = "model.int8.onnx"
    const val TOKENS_FILE = "tokens.txt"
    const val MANIFEST_FILE = "model-manifest.json"

    /** sherpa-onnx fbank frame shift (10 ms) x model subsampling (4): one CTC output frame = 40 ms. */
    const val TOKEN_STEP_MS = 40

    /** CTC word-separator token in tokens.txt (U+2581). Letters are single-character tokens. */
    const val WORD_SEPARATOR = '\u2581'
}

/**
 * Tunables of the chunked ("pseudo-streaming") recognition. Defaults are reasoned, not yet tuned on devices:
 * see AGENT_4_HANDOFF.md for what was and was not measured.
 */
data class StreamingConfig(
    val sampleRate: Int = AsrModel.SAMPLE_RATE,
    /** Energy frame length used for voice-activity decisions. */
    val frameMs: Int = 20,
    /** Audio kept from before speech onset so first consonants are not clipped. */
    val preRollMs: Int = 300,
    /** Consecutive speech frames needed to declare speech onset. */
    val startSpeechFrames: Int = 3,
    /** Trailing silence that closes a segment. Recitation pauses are normally longer than this. */
    val endSilenceMs: Int = 600,
    /** Segments with less speech than this are discarded as noise. */
    val minSpeechMs: Int = 250,
    /** Hard cap on an open segment; bounds decode cost per partial update. */
    val maxSegmentMs: Int = 12_000,
    /** A forced cut is placed at the quietest point inside the last [cutSearchMs] of the segment. */
    val cutSearchMs: Int = 4_000,
    /** Silence kept after the last speech frame in a closed segment. */
    val tailPadMs: Int = 200,
    /** How often (in audio time) the open segment is re-decoded for a Partial. */
    val partialIntervalMs: Int = 600,
    /** Do not decode partials for less audio than this. */
    val minPartialAudioMs: Int = 500,
    /** Speech threshold = max(minSpeechDb, noiseFloor + speechMarginDb), levels in dBFS (RMS per frame). */
    val speechMarginDb: Float = 10f,
    val minSpeechDb: Float = -50f,
    /** Noise floor tracker: drops instantly to quieter frames, rises at most this fast, never above [maxNoiseDb]. */
    val noiseRiseDbPerSec: Float = 3f,
    val maxNoiseDb: Float = -40f,
    /** Max time stop() waits for queued decodes. */
    val drainTimeoutMs: Long = 30_000,
) {
    init {
        require(frameMs in 5..100)
        require(maxSegmentMs > cutSearchMs + minSpeechMs)
        require(endSilenceMs >= frameMs * 2)
    }
}
