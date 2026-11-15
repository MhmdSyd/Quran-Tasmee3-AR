package com.quranrecite.app.speech.asr

/** Aggregated recognition timing. All times in ms. RTF = decode time / audio time (lower is faster; <1 = faster than real time). */
data class PipelineMetrics(
    val decodes: Int = 0,
    val totalAudioMs: Long = 0,
    val totalDecodeMs: Long = 0,
    val avgRtf: Double = 0.0,
    val lastRtf: Double = 0.0,
    val lastDecodeMs: Long = 0,
    /** Partial: audio snapshot taken -> Partial event emitted (includes queue wait + decode). */
    val partialLatencyP50Ms: Long = 0,
    val partialLatencyP95Ms: Long = 0,
    /** Final: segment closed -> Final event emitted. Add endSilenceMs for "user stopped talking -> text". */
    val finalLatencyP50Ms: Long = 0,
    val finalLatencyP95Ms: Long = 0,
    val partials: Int = 0,
    val finals: Int = 0,
    val emptyFinals: Int = 0,
    val stalePartialsDropped: Int = 0,
    val decodeErrors: Int = 0,
    val forcedCuts: Int = 0,
    val discardedBlips: Int = 0,
    val queuedFinals: Int = 0,
)

/** Thread-safe collector behind [PipelineMetrics]; percentiles use the last [window] samples. */
class MetricsRecorder(private val window: Int = 256) {
    private val partialLat = ArrayDeque<Long>()
    private val finalLat = ArrayDeque<Long>()
    private var decodes = 0
    private var audioMs = 0L
    private var decodeMs = 0L
    private var lastRtf = 0.0
    private var lastDecode = 0L
    private var partials = 0
    private var finals = 0
    private var emptyFinals = 0
    private var stale = 0
    private var errors = 0

    @Synchronized fun recordDecode(audioMs: Long, decodeMs: Long) {
        decodes++
        this.audioMs += audioMs
        this.decodeMs += decodeMs
        lastDecode = decodeMs
        lastRtf = if (audioMs > 0) decodeMs.toDouble() / audioMs else 0.0
    }
    @Synchronized fun recordPartial(latencyMs: Long) { partials++; push(partialLat, latencyMs) }
    @Synchronized fun recordFinal(latencyMs: Long) { finals++; push(finalLat, latencyMs) }
    @Synchronized fun recordEmptyFinal() { emptyFinals++ }
    @Synchronized fun recordStalePartial() { stale++ }
    @Synchronized fun recordError() { errors++ }

    @Synchronized fun reset() {
        partialLat.clear(); finalLat.clear()
        decodes = 0; audioMs = 0; decodeMs = 0; lastRtf = 0.0; lastDecode = 0
        partials = 0; finals = 0; emptyFinals = 0; stale = 0; errors = 0
    }

    @Synchronized fun snapshot(forcedCuts: Int = 0, discardedBlips: Int = 0, queuedFinals: Int = 0) = PipelineMetrics(
        decodes = decodes,
        totalAudioMs = audioMs,
        totalDecodeMs = decodeMs,
        avgRtf = if (audioMs > 0) decodeMs.toDouble() / audioMs else 0.0,
        lastRtf = lastRtf,
        lastDecodeMs = lastDecode,
        partialLatencyP50Ms = percentile(partialLat, 0.50),
        partialLatencyP95Ms = percentile(partialLat, 0.95),
        finalLatencyP50Ms = percentile(finalLat, 0.50),
        finalLatencyP95Ms = percentile(finalLat, 0.95),
        partials = partials, finals = finals, emptyFinals = emptyFinals,
        stalePartialsDropped = stale, decodeErrors = errors,
        forcedCuts = forcedCuts, discardedBlips = discardedBlips, queuedFinals = queuedFinals,
    )

    private fun push(q: ArrayDeque<Long>, v: Long) { q.addLast(v); while (q.size > window) q.removeFirst() }

    companion object {
        /** Nearest-rank percentile of [values] (p in 0..1); 0 for an empty list. */
        fun percentile(values: Collection<Long>, p: Double): Long {
            if (values.isEmpty()) return 0
            val sorted = values.sorted()
            val rank = kotlin.math.ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}
