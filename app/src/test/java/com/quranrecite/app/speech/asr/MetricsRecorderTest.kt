package com.quranrecite.app.speech.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsRecorderTest {
    @Test fun nearestRankPercentiles() {
        val v = (1L..100L).toList()
        assertEquals(50L, MetricsRecorder.percentile(v, 0.5))
        assertEquals(95L, MetricsRecorder.percentile(v, 0.95))
        assertEquals(1L, MetricsRecorder.percentile(listOf(1L), 0.95))
        assertEquals(0L, MetricsRecorder.percentile(emptyList(), 0.5))
    }

    @Test fun rtfAndWindow() {
        val m = MetricsRecorder(window = 3)
        m.recordDecode(audioMs = 2000, decodeMs = 100)
        m.recordDecode(audioMs = 2000, decodeMs = 300)
        val s = m.snapshot()
        assertEquals(0.1, s.avgRtf, 1e-9)
        assertEquals(0.15, s.lastRtf, 1e-9)
        assertEquals(300L, s.lastDecodeMs)
        repeat(10) { m.recordPartial(it.toLong()) }
        assertEquals(9L, m.snapshot().partialLatencyP95Ms)
        assertTrue(m.snapshot().partialLatencyP50Ms >= 7)           // only the last 3 samples (7,8,9) are kept
    }
}
