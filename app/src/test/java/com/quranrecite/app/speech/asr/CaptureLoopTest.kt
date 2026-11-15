package com.quranrecite.app.speech.asr

import com.quranrecite.app.speech.SpeechErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CaptureLoopTest {
    private class ScriptedSource(private val chunks: Int, private val failAt: Int = -1) : AudioSource {
        var opened = false; var closed = false
        private var i = 0
        override fun open() { opened = true }
        override fun read(out: FloatArray): Int {
            if (i == failAt) return AudioSource.READ_ERROR
            if (i++ >= chunks) return AudioSource.END_OF_STREAM
            java.util.Arrays.fill(out, 0.5f); return out.size
        }
        override fun close() { closed = true }
    }

    private class BlockingSource : AudioSource {
        val release = CountDownLatch(1)
        override fun open() {}
        override fun read(out: FloatArray): Int { release.await(10, TimeUnit.SECONDS); return 0 }
        override fun abort() { release.countDown() }
        override fun close() { release.countDown() }
    }

    @Test fun deliversEverySampleInOrderThenStops() {
        val src = ScriptedSource(chunks = 10)
        val total = AtomicInteger()
        val loop = CaptureLoop(src, 16_000, 100, onError = { _, _ -> }, sink = { _, n -> total.addAndGet(n) })
        loop.start()
        val end = System.currentTimeMillis() + 3000
        while (loop.isRunning && System.currentTimeMillis() < end) Thread.sleep(5)
        loop.stop()
        assertEquals(10 * 1600, total.get())
        assertTrue(src.opened && src.closed)
    }

    @Test fun readErrorIsReportedOnce() {
        val src = ScriptedSource(chunks = 100, failAt = 3)
        val errs = ArrayList<Pair<SpeechErrorCode, String>>()
        val loop = CaptureLoop(src, 16_000, 100, onError = { c, m -> synchronized(errs) { errs += c to m } }, sink = { _, _ -> })
        loop.start()
        val end = System.currentTimeMillis() + 3000
        while (loop.isRunning && System.currentTimeMillis() < end) Thread.sleep(5)
        loop.stop()
        assertEquals(1, errs.size)
        assertEquals(SpeechErrorCode.AudioReadFailed, errs[0].first)
    }

    @Test fun stopUnblocksAPendingReadQuicklyAndDeliversNothingAfterwards() {
        val src = BlockingSource()
        val delivered = AtomicInteger()
        val loop = CaptureLoop(src, 16_000, 100, onError = { _, _ -> }, sink = { _, n -> delivered.addAndGet(n) })
        loop.start()
        Thread.sleep(50)
        val t0 = System.currentTimeMillis()
        loop.stop()
        assertTrue("stop took ${System.currentTimeMillis() - t0} ms", System.currentTimeMillis() - t0 < 1500)
        assertEquals(0, delivered.get())
        assertTrue(!loop.isRunning)
    }
}
