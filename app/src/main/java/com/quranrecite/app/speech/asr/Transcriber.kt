package com.quranrecite.app.speech.asr

import com.quranrecite.app.speech.RecognizedWord

/** Raw output of one offline decode. [tokens]/[tokenTimesSec] may be empty when the runtime provides none. */
class TranscriptionResult(
    /** Exactly what the recognizer returned (word separator "▁" retained). */
    val raw: String,
    val tokens: List<String> = emptyList(),
    /** Seconds from the start of the decoded audio, one per token. */
    val tokenTimesSec: FloatArray = FloatArray(0),
)

/** One offline decode of a complete audio buffer. Implementations need not be thread-safe (one worker thread). */
interface Transcriber {
    /** @param samples mono float PCM in [-1, 1] at [sampleRate] */
    fun transcribe(samples: FloatArray, sampleRate: Int): TranscriptionResult
    fun release()
}

/** Turns a [TranscriptionResult] into words with session-clock times. Pure; no Android. */
object WordParser {
    /**
     * @param offsetMs session-clock time of the first decoded sample
     * Word start = time of its first letter token, end = time of its last letter token + one token step.
     * Falls back to evenly spread times if tokens and timestamps are unusable; the words themselves always come
     * from [TranscriptionResult.raw], so text is never lost when timestamps are bad.
     */
    fun parse(result: TranscriptionResult, offsetMs: Long, audioDurationMs: Long): List<RecognizedWord> {
        val textWords = result.raw.split(AsrModel.WORD_SEPARATOR, ' ').filter { it.isNotEmpty() }
        if (textWords.isEmpty()) return emptyList()

        val tokens = result.tokens
        val times = result.tokenTimesSec
        if (tokens.isNotEmpty() && tokens.size == times.size) {
            val words = ArrayList<RecognizedWord>(textWords.size)
            val sb = StringBuilder()
            var start = -1L
            var end = -1L
            fun flush() {
                if (sb.isNotEmpty()) {
                    words += RecognizedWord(sb.toString(), start, end)
                    sb.clear()
                    start = -1L
                }
            }
            for (i in tokens.indices) {
                val t = tokens[i]
                val ms = offsetMs + Math.round(times[i] * 1000f).toLong()
                if (t.all { it == AsrModel.WORD_SEPARATOR || it == ' ' }) { flush(); continue }
                // a token may contain a leading/trailing separator in unusual vocabularies; treat as boundary
                val parts = t.split(AsrModel.WORD_SEPARATOR, ' ')
                for ((k, part) in parts.withIndex()) {
                    if (k > 0) flush()
                    if (part.isEmpty()) continue
                    if (sb.isEmpty()) start = ms
                    sb.append(part)
                    end = ms + AsrModel.TOKEN_STEP_MS
                }
            }
            flush()
            // consistency guard: must reproduce the same words as the raw text
            if (words.map { it.text } == textWords && words.all { it.startMs >= offsetMs - 1 && it.startMs <= it.endMs }) {
                return words
            }
        }
        // fallback: evenly spaced
        val n = textWords.size
        val span = audioDurationMs.coerceAtLeast(n.toLong())
        return textWords.mapIndexed { i, w ->
            RecognizedWord(w, offsetMs + span * i / n, offsetMs + span * (i + 1) / n)
        }
    }

    fun textOf(words: List<RecognizedWord>): String = words.joinToString(" ") { it.text }
}

/**
 * LocalAgreement-2 over consecutive hypotheses of the same open segment: words that two successive decodes agree on
 * (as a prefix) are considered stable. The final word of the newest hypothesis is never reported stable.
 */
class PartialTracker {
    private var prev: List<String> = emptyList()
    private var prevSegment: Int = -1

    fun update(segmentId: Int, words: List<String>): Int {
        val p = if (segmentId == prevSegment) prev else emptyList()
        var n = 0
        while (n < p.size && n < words.size && p[n] == words[n]) n++
        prev = words
        prevSegment = segmentId
        return minOf(n, (words.size - 1).coerceAtLeast(0))
    }

    fun reset() { prev = emptyList(); prevSegment = -1 }
}
