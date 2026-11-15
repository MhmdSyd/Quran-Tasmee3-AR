package com.quranrecite.app.speech.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourcesTest {
    private class FakeProbe : ResourceProbe {
        var cpu = 0L; var heap = 10L * 1048576; var pss = 100L * 1024
        override fun cpuTimeMs() = cpu
        override fun nativeHeapBytes() = heap
        override fun pssKb() = pss
    }

    @Test fun cpuPercentIsOfOneCoreAndPeaksAreTracked() {
        val p = FakeProbe(); var wall = 1000L
        val s = ResourceSampler(p) { wall }
        wall += 1000; p.cpu += 500; p.heap = 50L * 1048576; p.pss = 300L * 1024
        val a = s.sample()
        assertEquals(50.0, a.cpuPercent, 1e-9)
        assertEquals(50.0, a.nativeHeapMb, 1e-9)
        wall += 1000; p.cpu += 2000; p.heap = 20L * 1048576; p.pss = 200L * 1024
        val b = s.sample()
        assertEquals(200.0, b.cpuPercent, 1e-9)                  // two cores busy
        assertEquals(125.0, b.avgCpuPercent, 1e-9)
        assertEquals(50.0, b.peakNativeHeapMb, 1e-9)
        assertEquals(300.0, b.peakPssMb, 1e-9)
    }

    @Test fun syntheticSpeechIsDeterministicAndNotSilent() {
        val a = AsrBenchmark.syntheticSpeech(2)
        assertEquals(32_000, a.size)
        assertTrue(AudioBuilder.rms(a) > 0.02)
        assertTrue(a.contentEquals(AsrBenchmark.syntheticSpeech(2)))
        assertTrue(a.all { it in -1f..1f })
    }

    @Test fun benchmarkExcludesWarmupAndComputesRtf() {
        val t = ToneTranscriber(delayMs = 5)
        val r = AsrBenchmark.run(t, FakeProbe(), durationsSec = listOf(1, 2), runsPerDuration = 3)
        assertEquals(2, r.rows.size)
        assertEquals(2 * (1 + 3), t.calls)                      // 1 warm-up + 3 timed per duration
        assertTrue(r.rows.all { it.medianMs >= 5 && it.minMs <= it.medianMs && it.medianMs <= it.maxMs })
        assertTrue(r.rows[0].rtf > 0.004 && r.rows[0].rtf < 1.0)
    }
}
