package com.quranrecite.app.speech.asr

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Synthetic audio for tests: "words" are tones of unique frequencies, silence is faint noise. */
class AudioBuilder(private val sr: Int = 16_000, seed: Long = 7) {
    private var data = FloatArray(sr * 60)
    private var n = 0
    private val rnd = Random(seed)
    /** Expected transcript, in order. */
    val expected = ArrayList<String>()

    private fun ensure(extra: Int) { if (n + extra > data.size) data = data.copyOf(maxOf(data.size * 2, n + extra)) }

    fun silence(ms: Int, noiseAmp: Float = 0.0003f): AudioBuilder {
        val k = sr * ms / 1000
        ensure(k)
        for (i in 0 until k) data[n++] = (rnd.nextFloat() * 2 - 1) * noiseAmp
        return this
    }

    /** A tone burst standing for WORDS[index]. */
    fun word(index: Int, ms: Int = 350, amp: Float = 0.1f): AudioBuilder {
        val k = sr * ms / 1000
        ensure(k)
        val f = freqOf(index)
        val fade = sr * 5 / 1000
        for (i in 0 until k) {
            val env = minOf(1f, i / fade.toFloat(), (k - 1 - i) / fade.toFloat())
            data[n++] = (amp * env * sin(2.0 * PI * f * i / sr)).toFloat() + (rnd.nextFloat() * 2 - 1) * 0.0003f
        }
        expected += WORDS[index % WORDS.size]
        return this
    }

    /** Words back-to-back separated by short gaps, like fast continuous recitation. */
    fun run(count: Int, startIndex: Int = 0, wordMs: Int = 350, gapMs: Int = 60): AudioBuilder {
        for (i in 0 until count) { word(startIndex + i, wordMs); if (i < count - 1) silence(gapMs) }
        return this
    }

    val sampleCount: Int get() = n
    fun build(): FloatArray = data.copyOf(n)

    companion object {
        val WORDS = listOf(
            "الله", "الرحمن", "الرحيم", "ملك", "يوم", "الدين", "اياك", "نعبد",
            "واياك", "نستعين", "اهدنا", "الصراط", "المستقيم", "صراط", "الذين", "انعمت",
        )
        fun freqOf(index: Int) = 300 + 100 * (index % WORDS.size)

        fun rms(a: FloatArray, from: Int = 0, to: Int = a.size): Double {
            var s = 0.0
            for (i in from until to) s += a[i].toDouble() * a[i]
            return sqrt(s / (to - from).coerceAtLeast(1))
        }

        /** Feeds [audio] to [consumer] in pieces of [chunk] samples. */
        fun chunked(audio: FloatArray, chunk: Int, consumer: (FloatArray, Int) -> Unit) {
            var i = 0
            while (i < audio.size) {
                val k = minOf(chunk, audio.size - i)
                consumer(audio.copyOfRange(i, i + k), k)
                i += k
            }
        }
    }
}

/**
 * Stand-in for the neural model: finds tone bursts and maps each burst's frequency back to its word.
 * Produces CTC-style tokens + timestamps so the real [WordParser] path is exercised.
 * [delayMs] simulates a slow device; [failTimes] makes the first N calls throw.
 */
class ToneTranscriber(private val delayMs: Long = 0, private var failTimes: Int = 0) : Transcriber {
    @Volatile var calls = 0
    val decodedDurationsMs = java.util.Collections.synchronizedList(ArrayList<Long>())

    override fun transcribe(samples: FloatArray, sampleRate: Int): TranscriptionResult {
        calls++
        if (delayMs > 0) Thread.sleep(delayMs)
        if (failTimes > 0) { failTimes--; throw IllegalStateException("boom") }
        decodedDurationsMs += samples.size * 1000L / sampleRate
        val frame = sampleRate / 100                       // 10 ms
        val nFrames = samples.size / frame
        val active = BooleanArray(nFrames) { f -> AudioBuilder.rms(samples, f * frame, (f + 1) * frame) > 0.01 }
        val bursts = ArrayList<IntRange>()
        var f = 0
        while (f < nFrames) {
            if (!active[f]) { f++; continue }
            var e = f
            var gap = 0
            var k = f
            while (k < nFrames && gap < 3) { if (active[k]) { e = k; gap = 0 } else gap++; k++ }
            bursts += f..e
            f = e + 1
        }
        val sb = StringBuilder()
        val tokens = ArrayList<String>()
        val times = ArrayList<Float>()
        for (b in bursts) {
            val s = b.first * frame + frame          // skip edge frames
            val e = (b.last + 1) * frame - frame
            if (e - s < sampleRate / 20) continue    // too short to be a word
            var zc = 0
            for (i in s + 1 until e) if ((samples[i - 1] < 0) != (samples[i] < 0)) zc++
            val freq = zc / 2.0 / ((e - s).toDouble() / sampleRate)
            val idx = Math.round((freq - 300) / 100).toInt()
            if (idx !in AudioBuilder.WORDS.indices) continue
            val word = AudioBuilder.WORDS[idx]
            val t0 = b.first * 0.01f
            sb.append('\u2581'); tokens += "\u2581"; times += t0
            for ((j, ch) in word.withIndex()) { sb.append(ch); tokens += ch.toString(); times += t0 + 0.04f * j }
        }
        return TranscriptionResult(sb.toString(), tokens, times.toFloatArray())
    }

    override fun release() {}
}
