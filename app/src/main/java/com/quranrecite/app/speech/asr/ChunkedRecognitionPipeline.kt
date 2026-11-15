package com.quranrecite.app.speech.asr

import com.quranrecite.app.speech.SegmentEnd
import com.quranrecite.app.speech.SpeechErrorCode
import com.quranrecite.app.speech.SpeechEvent
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Chunked ("pseudo-streaming") recognition on top of an OFFLINE model. NOT true streaming.
 *
 *  - [push] (capture thread) feeds audio to an [UtteranceSegmenter]; closed segments become FINAL jobs.
 *  - While speech is open, every [StreamingConfig.partialIntervalMs] of audio the whole open segment is re-decoded
 *    on the worker for a PARTIAL (latest wins: an un-started partial is replaced by a fresher one, so the worker never
 *    builds a backlog of stale partials, and a partial whose segment already closed is dropped).
 *  - One worker thread decodes everything, so the (non-thread-safe) recognizer is never used concurrently, and events
 *    leave in a consistent order: a Final is always emitted before any Partial of audio that follows it.
 *
 * No text de-duplication is needed because Final segments partition the audio (see [UtteranceSegmenter]).
 * Pure JVM: no Android, no coroutines. [sink] is called from the worker thread.
 */
class ChunkedRecognitionPipeline(
    private val transcriber: Transcriber,
    private val config: StreamingConfig = StreamingConfig(),
    private val sink: (SpeechEvent) -> Unit,
    val metrics: MetricsRecorder = MetricsRecorder(),
    private val nanoClock: () -> Long = System::nanoTime,
    private val threadFactory: ThreadFactory = ThreadFactory { r -> Thread(r, "asr-decode").apply { isDaemon = true } },
) {
    private sealed interface Job
    private class FinalJob(val seg: UtteranceSegmenter.Closed, val closedAtNs: Long) : Job
    private class PartialJob(val open: UtteranceSegmenter.Open, val createdNs: Long)
    private object WakePartial : Job
    private object Stop : Job

    private val segmenter = UtteranceSegmenter(config)
    private val lock = Any()                        // guards segmenter + partial scheduling
    private val jobs = LinkedBlockingQueue<Job>()
    private val pendingPartial = AtomicReference<PartialJob?>(null)
    private val outstandingFinals = AtomicInteger(0)
    private val idleMonitor = Object()

    @Volatile private var closedUpTo = -1           // highest segment id already closed
    @Volatile private var running = false
    private var worker: Thread? = null
    private var lastPartialSample = 0L

    // worker-thread-only state
    private val tracker = PartialTracker()
    private var lastPartialText = ""
    private var lastPartialStable = -1
    private var lastPartialSegment = -1

    private val sr = config.sampleRate
    private val partialIntervalSamples = sr.toLong() * config.partialIntervalMs / 1000

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        worker = threadFactory.newThread(::workerLoop).also { it.start() }
    }

    /** Called from the capture thread. Cheap unless a segment closes or a partial snapshot is due. */
    fun push(samples: FloatArray, n: Int = samples.size) {
        if (!running) return
        synchronized(lock) {
            val closed = segmenter.push(samples, n)
            for (c in closed) enqueueFinal(c)
            maybeSchedulePartial()
        }
    }

    /** Closes the open segment (if it has speech) and discards pending partials. Used by pause(). */
    fun flush() {
        synchronized(lock) {
            segmenter.flush()?.let { enqueueFinal(it) }
            pendingPartial.set(null)
        }
    }

    /** Flushes, waits until every queued Final has been decoded and emitted, then stops the worker. */
    fun stopAndDrain(timeoutMs: Long = config.drainTimeoutMs): Boolean {
        flush()
        val deadline = System.currentTimeMillis() + timeoutMs
        var drained = true
        synchronized(idleMonitor) {
            while (outstandingFinals.get() > 0) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) { drained = false; break }
                idleMonitor.wait(left)
            }
        }
        shutdown(deadline - System.currentTimeMillis())
        return drained
    }

    /** Stops immediately, dropping queued work. */
    fun release() {
        jobs.clear()
        shutdown(2_000)
    }

    fun snapshotMetrics(): PipelineMetrics = synchronized(lock) {
        metrics.snapshot(segmenter.forcedCuts, segmenter.discardedBlips, outstandingFinals.get())
    }

    /** Session audio clock in ms (samples accepted so far). */
    fun audioClockMs(): Long = synchronized(lock) { segmenter.totalSamples * 1000 / sr }

    // -----------------------------------------------------------------------------------------------------------

    private fun enqueueFinal(c: UtteranceSegmenter.Closed) {
        closedUpTo = c.segmentId
        outstandingFinals.incrementAndGet()
        jobs.add(FinalJob(c, nanoClock()))
    }

    private fun maybeSchedulePartial() {
        if (!segmenter.isInSpeech) return
        if (segmenter.totalSamples - lastPartialSample < partialIntervalSamples) return
        if (segmenter.openDurationMs() < config.minPartialAudioMs) return
        val open = segmenter.snapshotOpen() ?: return
        lastPartialSample = segmenter.totalSamples
        val prev = pendingPartial.getAndSet(PartialJob(open, nanoClock()))
        if (prev == null) jobs.add(WakePartial)
    }

    private fun shutdown(waitMs: Long) {
        val w: Thread?
        synchronized(lock) {
            if (!running) return
            running = false
            w = worker
        }
        jobs.add(Stop)
        try {
            w?.join(waitMs.coerceAtLeast(100))
            if (w?.isAlive == true) w.interrupt()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun workerLoop() {
        while (true) {
            val job = try { jobs.take() } catch (_: InterruptedException) { return }
            when (job) {
                Stop -> return
                WakePartial -> pendingPartial.getAndSet(null)?.let(::handlePartial)
                is FinalJob -> try { handleFinal(job) } finally {
                    outstandingFinals.decrementAndGet()
                    synchronized(idleMonitor) { idleMonitor.notifyAll() }
                }
            }
        }
    }

    private fun decode(samples: FloatArray): Pair<TranscriptionResult, Long>? {
        val t0 = nanoClock()
        return try {
            val r = transcriber.transcribe(samples, sr)
            r to (nanoClock() - t0) / 1_000_000
        } catch (e: Throwable) {
            metrics.recordError()
            sink(SpeechEvent.Error("Decode failed: ${e.message ?: e.javaClass.simpleName}", SpeechErrorCode.DecodeFailed))
            null
        }
    }

    private fun handlePartial(p: PartialJob) {
        val open = p.open
        if (open.segmentId <= closedUpTo) { metrics.recordStalePartial(); return }
        val (result, decodeMs) = decode(open.audio) ?: return
        val audioMs = open.audio.size.toLong() * 1000 / sr
        metrics.recordDecode(audioMs, decodeMs)
        if (open.segmentId <= closedUpTo) { metrics.recordStalePartial(); return }   // closed while decoding

        val startMs = open.startSample * 1000 / sr
        val words = WordParser.parse(result, startMs, audioMs)
        val text = WordParser.textOf(words)
        val stable = tracker.update(open.segmentId, words.map { it.text })
        val sameSegment = open.segmentId == lastPartialSegment
        if (sameSegment && text == lastPartialText && stable == lastPartialStable) return   // nothing new
        val emitNothing = !sameSegment && text.isEmpty()                                      // no speech decoded yet
        lastPartialSegment = open.segmentId
        lastPartialText = text
        lastPartialStable = stable
        if (emitNothing) return
        metrics.recordPartial((nanoClock() - p.createdNs) / 1_000_000)
        sink(
            SpeechEvent.Partial(
                text = text, words = words, stableWordCount = stable, segmentId = open.segmentId,
                audioStartMs = startMs, audioEndMs = startMs + audioMs, decodeMs = decodeMs, raw = result.raw,
            ),
        )
    }

    private fun handleFinal(job: FinalJob) {
        val seg = job.seg
        // a Final resets partial state: whatever comes next belongs to later audio
        tracker.reset()
        lastPartialText = ""
        lastPartialStable = -1
        lastPartialSegment = -1

        val (result, decodeMs) = decode(seg.audio) ?: return
        val audioMs = seg.audio.size.toLong() * 1000 / sr
        metrics.recordDecode(audioMs, decodeMs)
        val startMs = seg.startSample * 1000 / sr
        val words = WordParser.parse(result, startMs, audioMs)
        if (words.isEmpty()) { metrics.recordEmptyFinal(); return }
        val latency = (nanoClock() - job.closedAtNs) / 1_000_000
        metrics.recordFinal(latency)
        sink(
            SpeechEvent.Final(
                text = WordParser.textOf(words), words = words, segmentId = seg.segmentId,
                audioStartMs = startMs, audioEndMs = startMs + audioMs,
                end = when (seg.reason) {
                    UtteranceSegmenter.CloseReason.EndOfSpeech -> SegmentEnd.EndOfSpeech
                    UtteranceSegmenter.CloseReason.MaxLength -> SegmentEnd.MaxLength
                    UtteranceSegmenter.CloseReason.Flush -> SegmentEnd.Flushed
                },
                decodeMs = decodeMs, latencyMs = latency, raw = result.raw,
            ),
        )
    }
}
