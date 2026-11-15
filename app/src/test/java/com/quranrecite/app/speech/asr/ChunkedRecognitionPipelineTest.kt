package com.quranrecite.app.speech.asr

import com.quranrecite.app.speech.SegmentEnd
import com.quranrecite.app.speech.SpeechErrorCode
import com.quranrecite.app.speech.SpeechEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class ChunkedRecognitionPipelineTest {
    private val cfg = StreamingConfig()

    private class Harness(t: Transcriber, cfg: StreamingConfig) {
        val events: MutableList<SpeechEvent> = Collections.synchronizedList(ArrayList())
        val pipeline = ChunkedRecognitionPipeline(t, cfg, sink = { events += it })
        fun finals() = events.filterIsInstance<SpeechEvent.Final>()
        fun partials() = events.filterIsInstance<SpeechEvent.Partial>()
        fun errors() = events.filterIsInstance<SpeechEvent.Error>()
    }

    private fun feed(h: Harness, audio: FloatArray, chunkSamples: Int = 1600, sleepMs: Long = 0) {
        AudioBuilder.chunked(audio, chunkSamples) { a, n -> h.pipeline.push(a, n); if (sleepMs > 0) Thread.sleep(sleepMs) }
    }

    private fun waitFor(timeoutMs: Long = 3000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(5) }
        return cond()
    }

    @Test fun finalsReproduceTheScriptExactlyWithoutLossOrDuplication() {
        val b = AudioBuilder().silence(600)
        b.run(6, 0).silence(1200)
        b.run(4, 6).silence(900)
        b.run(80, 2).silence(1500)               // ~30 s continuous: forced cuts
        b.run(3, 9).silence(1000)
        val h = Harness(ToneTranscriber(), cfg)
        h.pipeline.start()
        feed(h, b.build())
        assertTrue(h.pipeline.stopAndDrain())

        val got = h.finals().flatMap { it.text.split(' ') }
        assertEquals(b.expected, got)
        val ids = h.finals().map { it.segmentId }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.distinct(), ids)
        assertTrue("expected forced cuts", h.finals().count { it.end == SegmentEnd.MaxLength } >= 2)
        // Final time ranges never overlap
        h.finals().zipWithNext().forEach { (x, y) -> assertTrue(x.audioEndMs <= y.audioStartMs + 1) }
        assertTrue(h.errors().isEmpty())
    }

    @Test fun wordTimesFollowTheAudioClock() {
        val b = AudioBuilder().silence(1000).word(0, 400).silence(60).word(1, 400).silence(1000)
        val h = Harness(ToneTranscriber(), cfg)
        h.pipeline.start(); feed(h, b.build()); h.pipeline.stopAndDrain()
        val w = h.finals().single().words
        assertEquals(2, w.size)
        // words start at 1000 ms and 1460 ms on the session clock; token times are 10 ms-quantised bursts (+- 60 ms)
        assertTrue("start0=${w[0].startMs}", w[0].startMs in 950..1060)
        assertTrue("start1=${w[1].startMs}", w[1].startMs in 1400..1520)
        assertTrue(w[0].endMs > w[0].startMs && w[1].startMs >= w[0].startMs)
    }

    @Test fun partialsGrowAsPrefixesOfTheFinalAndNeverFollowItsFinal() {
        val b = AudioBuilder().silence(500).run(14, 0).silence(1500)
        val h = Harness(ToneTranscriber(), cfg)
        h.pipeline.start()
        feed(h, b.build(), sleepMs = 6)           // ~16x faster than real time, still lets the worker interleave
        h.pipeline.stopAndDrain()

        val fin = h.finals().single()
        val finalWords = fin.text.split(' ')
        assertTrue("partials=${h.partials().size}", h.partials().size >= 3)
        var maxFinal = -1
        var lastCount = 0
        for (e in h.events) {
            when (e) {
                is SpeechEvent.Partial -> {
                    assertTrue("partial of closed segment arrived after its final", e.segmentId > maxFinal)
                    val pw = e.text.split(' ').filter { it.isNotEmpty() }
                    assertEquals(finalWords.take(pw.size), pw)               // always a prefix of the truth
                    assertTrue("stable=${e.stableWordCount} size=${pw.size}", e.stableWordCount <= maxOf(0, pw.size - 1))
                    assertEquals(finalWords.take(e.stableWordCount), pw.take(e.stableWordCount))
                    assertTrue(pw.size >= lastCount)                         // text only grows here
                    lastCount = pw.size
                }
                is SpeechEvent.Final -> maxFinal = maxOf(maxFinal, e.segmentId)
                else -> {}
            }
        }
        assertTrue(h.partials().any { it.stableWordCount > 0 })
    }

    @Test fun stopDrainsTheLastWordsEvenWithoutTrailingSilence() {
        val b = AudioBuilder().silence(300).run(5, 0)       // user stops mid-air, no silence after
        val h = Harness(ToneTranscriber(), cfg)
        h.pipeline.start(); feed(h, b.build())
        assertTrue(h.finals().isEmpty() || h.finals().flatMap { it.text.split(' ') }.size < 5)
        h.pipeline.stopAndDrain()
        assertEquals(b.expected, h.finals().flatMap { it.text.split(' ') })
        assertEquals(SegmentEnd.Flushed, h.finals().last().end)
    }

    @Test fun pauseFlushesAndTheClockKeepsRunningOnResume() {
        val h = Harness(ToneTranscriber(), cfg)
        h.pipeline.start()
        feed(h, AudioBuilder().silence(400).run(4, 0).build())
        h.pipeline.flush()                                   // = pause()
        assertTrue(waitFor { h.finals().size == 1 })
        val first = h.finals()[0]
        assertEquals(SegmentEnd.Flushed, first.end)
        feed(h, AudioBuilder().silence(400).run(3, 4).silence(1000).build())   // = after resume()
        h.pipeline.stopAndDrain()
        assertEquals(2, h.finals().size)
        assertTrue(h.finals()[1].audioStartMs >= first.audioEndMs)
        assertEquals(AudioBuilder().run(4, 0).expected + AudioBuilder().run(3, 4).expected, h.finals().flatMap { it.text.split(' ') })
    }

    @Test fun slowDeviceNeverLosesFinalsAndSkipsStalePartials() {
        val b = AudioBuilder().silence(300)
        repeat(6) { b.run(5, it * 2).silence(900) }
        val slow = ToneTranscriber(delayMs = 120)
        val h = Harness(slow, cfg)
        h.pipeline.start()
        feed(h, b.build(), sleepMs = 15)                     // ~6x real time; decoder (120 ms/decode) cannot keep up with partials
        assertTrue(h.pipeline.stopAndDrain(60_000))
        assertEquals(b.expected, h.finals().flatMap { it.text.split(' ') })
        val m = h.pipeline.snapshotMetrics()
        assertEquals(0, m.queuedFinals)
        assertTrue(m.avgRtf >= 0.0)
        // partial decodes were skipped/replaced rather than queued: fewer decodes than 600 ms intervals in 30 s of audio
        assertTrue("decodes=${slow.calls}", slow.calls < 6 + 50)
    }

    @Test fun decodeFailureIsReportedAndLaterSegmentsStillWork() {
        val b = AudioBuilder().silence(300).run(3, 0).silence(1200).run(3, 3).silence(1200)
        val h = Harness(ToneTranscriber(failTimes = 1), cfg)
        h.pipeline.start(); feed(h, b.build(), sleepMs = 3); h.pipeline.stopAndDrain()
        assertTrue(h.errors().any { it.code == SpeechErrorCode.DecodeFailed })
        assertTrue(h.finals().isNotEmpty())
        assertEquals(AudioBuilder().run(3, 3).expected, h.finals().last().text.split(' '))
    }

    @Test fun segmentsWithNoRecognisedWordsProduceNoFinal() {
        val silent = object : Transcriber {
            override fun transcribe(samples: FloatArray, sampleRate: Int) = TranscriptionResult("")
            override fun release() {}
        }
        val b = AudioBuilder().silence(300).word(0, 1000).silence(1200).word(1, 1000).silence(1200)
        val h = Harness(silent, cfg)
        h.pipeline.start(); feed(h, b.build()); h.pipeline.stopAndDrain()
        assertTrue(h.finals().isEmpty())
        assertEquals(2, h.pipeline.snapshotMetrics().emptyFinals)
    }

    @Test fun pushAfterReleaseIsIgnoredAndReleaseIsIdempotent() {
        val h = Harness(ToneTranscriber(), cfg)
        h.pipeline.start()
        h.pipeline.release()
        h.pipeline.push(FloatArray(1600), 1600)
        h.pipeline.release()
        assertTrue(h.events.isEmpty())
    }
}
