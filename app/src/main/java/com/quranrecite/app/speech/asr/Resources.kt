package com.quranrecite.app.speech.asr

import kotlin.math.PI
import kotlin.math.sin

/** Process-level resource readings (Android implementation: [AndroidResourceProbe]). */
interface ResourceProbe {
    /** CPU time consumed by this process so far (all threads), ms. */
    fun cpuTimeMs(): Long
    fun nativeHeapBytes(): Long
    /** Total proportional set size, KB. */
    fun pssKb(): Long
}

data class ResourceSnapshot(
    /** CPU use since the previous sample, as % of ONE core (200 = two cores fully busy). */
    val cpuPercent: Double = 0.0,
    /** Average since sampling started. */
    val avgCpuPercent: Double = 0.0,
    val nativeHeapMb: Double = 0.0,
    val peakNativeHeapMb: Double = 0.0,
    val pssMb: Double = 0.0,
    val peakPssMb: Double = 0.0,
)

class ResourceSampler(private val probe: ResourceProbe, private val wallMs: () -> Long = System::currentTimeMillis) {
    private val startCpu = probe.cpuTimeMs()
    private val startWall = wallMs()
    private var lastCpu = startCpu
    private var lastWall = startWall
    private var peakHeap = 0.0
    private var peakPss = 0.0

    @Synchronized fun sample(): ResourceSnapshot {
        val cpu = probe.cpuTimeMs()
        val wall = wallMs()
        val inst = if (wall > lastWall) 100.0 * (cpu - lastCpu) / (wall - lastWall) else 0.0
        val avg = if (wall > startWall) 100.0 * (cpu - startCpu) / (wall - startWall) else 0.0
        lastCpu = cpu; lastWall = wall
        val heap = probe.nativeHeapBytes() / 1048576.0
        val pss = probe.pssKb() / 1024.0
        peakHeap = maxOf(peakHeap, heap); peakPss = maxOf(peakPss, pss)
        return ResourceSnapshot(inst, avg, heap, peakHeap, pss, peakPss)
    }
}

/** Everything the diagnostics UI shows. */
data class EngineMetrics(
    val pipeline: PipelineMetrics = PipelineMetrics(),
    val resources: ResourceSnapshot = ResourceSnapshot(),
    val modelLoadMs: Long = 0,
)

data class BenchmarkRow(val audioSec: Int, val runs: Int, val minMs: Long, val medianMs: Long, val maxMs: Long) {
    /** Real-time factor of the median run; < 1 means faster than real time. */
    val rtf: Double get() = medianMs / (audioSec * 1000.0)
}

data class BenchmarkReport(
    val rows: List<BenchmarkRow>,
    val avgCpuPercent: Double,
    val heapBeforeMb: Double,
    val heapAfterMb: Double,
    val peakPssMb: Double,
    val note: String = "Synthetic speech-like audio: timing and memory are meaningful, the recognised text is not.",
)

/**
 * Timing benchmark on synthetic audio. Offline greedy-CTC cost depends on audio length, not content, so this gives
 * device latency/RTF/CPU/memory numbers without needing recitation fixtures. The first run per duration is a warm-up
 * and is excluded.
 */
object AsrBenchmark {
    /** Harmonic "voice" with 4 Hz syllable-like amplitude modulation and a little noise; amplitude ~0.2. */
    fun syntheticSpeech(seconds: Int, sampleRate: Int = AsrModel.SAMPLE_RATE): FloatArray {
        val n = seconds * sampleRate
        val rnd = java.util.Random(42)
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val f0 = 120.0 + 25.0 * sin(2 * PI * 0.5 * t)
            val env = 0.5 * (1 + sin(2 * PI * 4.0 * t))
            var v = 0.0
            for (h in 1..8) v += sin(2 * PI * f0 * h * t) / h
            (0.12 * env * v + 0.002 * (rnd.nextDouble() * 2 - 1)).toFloat()
        }
    }

    fun run(
        transcriber: Transcriber,
        probe: ResourceProbe,
        durationsSec: List<Int> = listOf(2, 5, 10),
        runsPerDuration: Int = 5,
        sampleRate: Int = AsrModel.SAMPLE_RATE,
        nowNs: () -> Long = System::nanoTime,
    ): BenchmarkReport {
        val sampler = ResourceSampler(probe)
        val heapBefore = probe.nativeHeapBytes() / 1048576.0
        val rows = durationsSec.map { sec ->
            val audio = syntheticSpeech(sec, sampleRate)
            transcriber.transcribe(audio, sampleRate)                       // warm-up
            val times = (1..runsPerDuration).map {
                val t0 = nowNs(); transcriber.transcribe(audio, sampleRate); (nowNs() - t0) / 1_000_000
            }.sorted()
            sampler.sample()
            BenchmarkRow(sec, runsPerDuration, times.first(), times[times.size / 2], times.last())
        }
        val s = sampler.sample()
        return BenchmarkReport(rows, s.avgCpuPercent, heapBefore, probe.nativeHeapBytes() / 1048576.0, s.peakPssMb)
    }
}
