package com.quranrecite.app.speech.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class UtteranceSegmenterTest {
    private val cfg = StreamingConfig()
    private val sr = cfg.sampleRate

    private fun segment(audio: FloatArray, chunk: Int = 1600, flush: Boolean = true): Pair<List<UtteranceSegmenter.Closed>, UtteranceSegmenter> {
        val seg = UtteranceSegmenter(cfg)
        val out = ArrayList<UtteranceSegmenter.Closed>()
        AudioBuilder.chunked(audio, chunk) { a, n -> out += seg.push(a, n) }
        if (flush) seg.flush()?.let { out += it }
        return out to seg
    }

    @Test fun singleUtteranceClosesAfterEndSilence() {
        val a = AudioBuilder().silence(1000).word(0, 2000).silence(1000).build()
        val (closed, _) = segment(a, flush = false)
        assertEquals(1, closed.size)
        val c = closed[0]
        assertEquals(UtteranceSegmenter.CloseReason.EndOfSpeech, c.reason)
        assertEquals(0, c.segmentId)
        // pre-roll (300) + speech (2000) + tail pad (200) = 2500 ms, +- two frames
        val ms = c.audio.size * 1000 / sr
        assertTrue("segment ms=$ms", ms in 2440..2560)
        // starts 300 ms before speech onset at 1000 ms
        assertTrue("start=${c.startSample * 1000 / sr}", c.startSample * 1000 / sr in 660..740)
    }

    @Test fun twoUtterancesGiveTwoSegmentsWithIncreasingIds() {
        val a = AudioBuilder().silence(500).word(0, 1000).silence(1500).word(1, 1000).silence(1500).build()
        val (closed, _) = segment(a, flush = false)
        assertEquals(listOf(0, 1), closed.map { it.segmentId })
        assertTrue(closed[1].startSample > closed[0].startSample + closed[0].audio.size - 1)
    }

    @Test fun shortBlipIsDiscardedAsNoise() {
        val a = AudioBuilder().silence(500).word(0, 120).silence(1500).build()
        val (closed, seg) = segment(a, flush = false)
        assertEquals(0, closed.size)
        assertEquals(1, seg.discardedBlips)
        assertEquals(0, seg.currentSegmentId)   // ids are not consumed by discarded blips
    }

    @Test fun pauseWithinAnUtteranceShorterThanEndSilenceDoesNotSplit() {
        val a = AudioBuilder().silence(500).word(0, 800).silence(450).word(1, 800).silence(1500).build()
        val (closed, _) = segment(a, flush = false)
        assertEquals(1, closed.size)
    }

    @Test fun forcedCutsFallInQuietGapsAndPartitionTheAudio() {
        val a = AudioBuilder().silence(500).run(count = 90).silence(1500).build()   // ~37 s continuous
        val (closed, seg) = segment(a, flush = true)
        assertTrue("expected several forced cuts, got ${closed.size}", closed.size >= 3)
        assertTrue(seg.forcedCuts >= 3)
        // contiguous: every piece starts exactly where the previous one ended
        for (i in 1 until closed.size) {
            val prev = closed[i - 1]
            if (prev.reason == UtteranceSegmenter.CloseReason.MaxLength) {
                assertEquals("gap between piece ${i - 1} and $i", prev.startSample + prev.audio.size, closed[i].startSample)
            }
        }
        // bounded length
        for (c in closed) assertTrue(c.audio.size * 1000 / sr <= cfg.maxSegmentMs + cfg.frameMs)
        // each forced cut ends in (nearly) silence = the gap between two words, never inside a word
        for (c in closed.filter { it.reason == UtteranceSegmenter.CloseReason.MaxLength }) {
            val tailRms = AudioBuilder.rms(c.audio, c.audio.size - sr / 50, c.audio.size)
            assertTrue("cut inside speech, tail rms=$tailRms", tailRms < 0.005)
        }
        assertEquals(listOf(UtteranceSegmenter.CloseReason.EndOfSpeech), closed.takeLast(1).map { it.reason })
    }

    @Test fun inputChunkingDoesNotChangeTheSegmentation() {
        val a = AudioBuilder().silence(700).run(40).silence(1200).run(10).silence(1000).build()
        fun sig(chunk: (Random) -> Int): List<Pair<Long, Int>> {
            val seg = UtteranceSegmenter(cfg); val out = ArrayList<Pair<Long, Int>>()
            val rnd = Random(3); var i = 0
            while (i < a.size) {
                val k = minOf(chunk(rnd), a.size - i)
                out += seg.push(a.copyOfRange(i, i + k), k).map { it.startSample to it.audio.size }
                i += k
            }
            return out
        }
        val whole = sig { 1600 }
        assertEquals(whole, sig { 1 + it.nextInt(777) })
        assertEquals(whole, sig { 320 })
        assertTrue(whole.size >= 3)
    }

    @Test fun flushClosesOpenSpeechAndResetsToIdle() {
        val seg = UtteranceSegmenter(cfg)
        val a = AudioBuilder().silence(400).word(0, 1500).build()
        AudioBuilder.chunked(a, 1600) { x, n -> seg.push(x, n) }
        assertTrue(seg.isInSpeech)
        val c = seg.flush()
        assertTrue(c != null && c.reason == UtteranceSegmenter.CloseReason.Flush)
        assertTrue(!seg.isInSpeech)
        assertEquals(null, seg.snapshotOpen())
    }

    @Test fun snapshotOpenOnlyWhileSpeaking() {
        val seg = UtteranceSegmenter(cfg)
        AudioBuilder.chunked(AudioBuilder().silence(800).build(), 1600) { x, n -> seg.push(x, n) }
        assertEquals(null, seg.snapshotOpen())
        AudioBuilder.chunked(AudioBuilder().word(0, 1000).build(), 1600) { x, n -> seg.push(x, n) }
        val s = seg.snapshotOpen()
        assertTrue(s != null && s.audio.size > sr / 2)
    }

    @Test fun steadyBackgroundNoiseStopsCountingAsSpeechAfterAdaptation() {
        // 25 s of constant noise at about -40 dBFS RMS, then a clear -20 dBFS tone
        val rnd = Random(1)
        val noise = FloatArray(sr * 25) { ((rnd.nextFloat() * 2 - 1) * 0.0173f) }     // uniform amp 0.0173 -> rms 0.01 = -40 dBFS
        val speech = AudioBuilder().word(3, 1500, amp = 0.14f).silence(1500).build()
        val seg = UtteranceSegmenter(cfg)
        val closed = ArrayList<UtteranceSegmenter.Closed>()
        AudioBuilder.chunked(noise + speech, 1600) { x, n -> closed += seg.push(x, n) }
        // only the warm-up noise (first seconds, before the floor adapts) may produce a segment; the tone must be found
        assertTrue("segments=${closed.size}", closed.size in 1..2)
        val last = closed.last()
        assertTrue("last segment must contain the tone, start=${last.startSample * 1000 / sr} ms", last.startSample > sr * 20L)
        assertTrue(closed.dropLast(1).all { it.startSample + it.audio.size < sr * 15L })
    }
}
