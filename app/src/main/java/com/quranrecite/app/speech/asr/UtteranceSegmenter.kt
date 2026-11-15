package com.quranrecite.app.speech.asr

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Splits a continuous 16 kHz mono audio stream into speech segments using frame energy (adaptive noise floor).
 * Pure Kotlin, single-threaded (callers synchronise), deterministic: time is the sample clock, never wall time.
 *
 * Closed segments PARTITION the audio they cover: a segment is closed either at end of speech (silence >=
 * endSilenceMs) or, when it grows beyond maxSegmentMs, by a forced cut at the quietest point of its last cutSearchMs.
 * After a forced cut the audio after the cut stays in the open segment, so no sample is decoded twice and no text
 * needs de-duplication at boundaries.
 *
 * This is deliberately a plain energy VAD (no second ML model). It is robust enough for a quiet room and a phone held
 * near the reciter; very noisy rooms will degrade endpointing (see handoff).
 */
class UtteranceSegmenter(private val cfg: StreamingConfig = StreamingConfig()) {

    enum class CloseReason { EndOfSpeech, MaxLength, Flush }

    /** A closed segment. [startSample] is on the session sample clock. */
    class Closed(val segmentId: Int, val startSample: Long, val audio: FloatArray, val reason: CloseReason)

    /** Copy of the currently open segment (speech has started, not yet closed). */
    class Open(val segmentId: Int, val startSample: Long, val audio: FloatArray)

    private val frameSize = cfg.sampleRate * cfg.frameMs / 1000
    private val preRollFrames = (cfg.preRollMs + cfg.frameMs - 1) / cfg.frameMs
    private val tailPadFrames = cfg.tailPadMs / cfg.frameMs
    private val endSilenceFrames = (cfg.endSilenceMs + cfg.frameMs - 1) / cfg.frameMs
    private val maxFrames = cfg.maxSegmentMs / cfg.frameMs
    private val cutSearchFrames = cfg.cutSearchMs / cfg.frameMs
    private val minSpeechFrames = (cfg.minSpeechMs + cfg.frameMs - 1) / cfg.frameMs
    private val noiseRisePerFrame = cfg.noiseRiseDbPerSec * cfg.frameMs / 1000f

    private var buf = FloatArray(frameSize * (maxFrames + 8))
    private var dbs = FloatArray(maxFrames + 8)
    private var flags = BooleanArray(maxFrames + 8)
    private var nf = 0                       // complete frames in buf
    private var bufStartSample = 0L          // sample-clock index of buf[0]

    private val pending = FloatArray(frameSize)
    private var pendingLen = 0

    private var inSpeech = false
    private var onsetRun = 0
    private var firstSpeechFrame = -1
    private var lastSpeechFrame = -1
    private var silenceRun = 0
    private var noiseDb = -60f

    private var nextSegmentId = 0

    /** Total samples accepted so far (session clock). */
    var totalSamples = 0L
        private set
    var forcedCuts = 0
        private set
    var discardedBlips = 0
        private set

    val isInSpeech: Boolean get() = inSpeech
    /** Id the segment currently being collected will get when it closes. */
    val currentSegmentId: Int get() = nextSegmentId
    val noiseFloorDb: Float get() = noiseDb

    /** Feeds [n] samples; returns the segments closed by this input (usually none). */
    fun push(samples: FloatArray, n: Int = samples.size): List<Closed> {
        var out: ArrayList<Closed>? = null
        var i = 0
        while (i < n) {
            val take = minOf(frameSize - pendingLen, n - i)
            System.arraycopy(samples, i, pending, pendingLen, take)
            pendingLen += take
            i += take
            if (pendingLen == frameSize) {
                pendingLen = 0
                val c = processFrame()
                if (c != null) { if (out == null) out = ArrayList(2); out.add(c) }
            }
        }
        totalSamples += n
        return out ?: emptyList()
    }

    /** Snapshot of the open segment for a partial decode, or null when no speech is in progress. */
    fun snapshotOpen(): Open? {
        if (!inSpeech || nf == 0) return null
        return Open(nextSegmentId, bufStartSample, buf.copyOf(nf * frameSize))
    }

    /** Duration of audio in the open segment (including pre-roll), ms; 0 when idle. */
    fun openDurationMs(): Int = if (inSpeech) nf * cfg.frameMs else 0

    /**
     * Closes the open segment if it holds enough speech, discards everything else and returns to idle.
     * Used by pause()/stop(). A trailing partial frame (< frameMs) is dropped.
     */
    fun flush(): Closed? {
        var closed: Closed? = null
        if (inSpeech && lastSpeechFrame >= firstSpeechFrame && firstSpeechFrame >= 0 &&
            lastSpeechFrame - firstSpeechFrame + 1 >= minSpeechFrames
        ) {
            val end = minOf(nf - 1, lastSpeechFrame + tailPadFrames)
            closed = emit(end + 1, CloseReason.Flush)
        } else if (inSpeech) {
            discardedBlips++
        }
        nf = 0
        bufStartSample = totalSamples
        pendingLen = 0
        inSpeech = false
        onsetRun = 0
        firstSpeechFrame = -1
        lastSpeechFrame = -1
        silenceRun = 0
        return closed
    }

    // ---------------------------------------------------------------------------------------------------------

    private fun processFrame(): Closed? {
        val db = frameDb(pending)
        // noise floor: instant down, slow up (bounded)
        noiseDb = if (db <= noiseDb) db else minOf(noiseDb + noisePerFrameRise(), db)
        noiseDb = noiseDb.coerceIn(cfg.minSpeechDb - 10f, cfg.maxNoiseDb)
        val threshold = maxOf(cfg.minSpeechDb, noiseDb + cfg.speechMarginDb)
        val isSpeech = db > threshold

        append(db, isSpeech)

        if (!inSpeech) {
            if (isSpeech) onsetRun++ else onsetRun = 0
            if (onsetRun >= cfg.startSpeechFrames) {
                inSpeech = true
                firstSpeechFrame = nf - onsetRun
                lastSpeechFrame = nf - 1
                silenceRun = 0
            } else {
                // idle: keep only the pre-roll (plus the frames of a possible onset in progress)
                val keep = preRollFrames + onsetRun
                if (nf > keep) dropHead(nf - keep)
            }
            return null
        }

        if (isSpeech) { lastSpeechFrame = nf - 1; silenceRun = 0 } else silenceRun++

        if (silenceRun >= endSilenceFrames) return closeAtEndOfSpeech()
        if (nf >= maxFrames) return forcedCut()
        return null
    }

    private fun noisePerFrameRise() = noiseRisePerFrame

    private fun closeAtEndOfSpeech(): Closed? {
        val speechFrames = lastSpeechFrame - firstSpeechFrame + 1
        var closed: Closed? = null
        val end = minOf(nf - 1, lastSpeechFrame + tailPadFrames)
        if (speechFrames >= minSpeechFrames) {
            closed = emit(end + 1, CloseReason.EndOfSpeech)
        } else {
            discardedBlips++
        }
        dropHead(end + 1)
        goIdle()
        return closed
    }

    private fun forcedCut(): Closed? {
        // quietest 3-frame window within the last cutSearchFrames; ties -> latest (keeps more audio in the cut piece)
        val lo = maxOf(firstSpeechFrame + minSpeechFrames, nf - cutSearchFrames)
        var bestStart = -1
        var bestAvg = Float.MAX_VALUE
        var c = lo
        while (c + 2 < nf) {
            val avg = (dbs[c] + dbs[c + 1] + dbs[c + 2]) / 3f
            if (avg <= bestAvg) { bestAvg = avg; bestStart = c }
            c++
        }
        if (bestStart < 0) return null           // cannot happen with sane config; keep collecting
        val cutExclusive = bestStart + 2          // frames [0, bestStart+1] go to the closed segment
        val closed = emit(cutExclusive, CloseReason.MaxLength)
        forcedCuts++
        dropHead(cutExclusive)
        // the remainder continues the utterance
        firstSpeechFrame = 0
        if (lastSpeechFrame < 0) {
            // no speech left after the cut
            goIdle()
        } else {
            silenceRun = minOf(silenceRun, nf)
        }
        return closed
    }

    private fun goIdle() {
        inSpeech = false
        onsetRun = 0
        firstSpeechFrame = -1
        lastSpeechFrame = -1
        silenceRun = 0
        val keep = preRollFrames
        if (nf > keep) dropHead(nf - keep)
    }

    private fun emit(frames: Int, reason: CloseReason): Closed {
        val audio = buf.copyOf(frames * frameSize)
        val c = Closed(nextSegmentId, bufStartSample, audio, reason)
        nextSegmentId++
        return c
    }

    private fun append(db: Float, speech: Boolean) {
        if (nf + 1 > flags.size) growFrames()
        System.arraycopy(pending, 0, buf, nf * frameSize, frameSize)
        dbs[nf] = db
        flags[nf] = speech
        nf++
    }

    private fun growFrames() {
        val newCap = flags.size * 2
        buf = buf.copyOf(newCap * frameSize)
        dbs = dbs.copyOf(newCap)
        flags = flags.copyOf(newCap)
    }

    private fun dropHead(frames: Int) {
        if (frames <= 0) return
        val k = minOf(frames, nf)
        System.arraycopy(buf, k * frameSize, buf, 0, (nf - k) * frameSize)
        System.arraycopy(dbs, k, dbs, 0, nf - k)
        System.arraycopy(flags, k, flags, 0, nf - k)
        nf -= k
        bufStartSample += k.toLong() * frameSize
        if (firstSpeechFrame >= 0) firstSpeechFrame -= k
        if (lastSpeechFrame >= 0) lastSpeechFrame = if (lastSpeechFrame - k >= 0) lastSpeechFrame - k else -1
    }

    private fun frameDb(f: FloatArray): Float {
        var sum = 0.0
        for (x in f) sum += (x * x).toDouble()
        val rms = sqrt(sum / f.size)
        return if (rms < 1e-5) -100f else (20.0 * log10(rms)).toFloat()
    }
}
