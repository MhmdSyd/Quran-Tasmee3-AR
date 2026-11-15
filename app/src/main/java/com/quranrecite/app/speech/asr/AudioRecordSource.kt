package com.quranrecite.app.speech.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Microphone as 16 kHz mono PCM16 (the model's native input: no resampling needed; 16 kHz capture is guaranteed on
 * every Android device). Prefers VOICE_RECOGNITION (no voice-call processing) and falls back to MIC.
 * Requires RECORD_AUDIO; a missing permission surfaces as [AudioInitException].
 */
class AudioRecordSource(private val sampleRate: Int = AsrModel.SAMPLE_RATE) : AudioSource {
    private var record: AudioRecord? = null
    private var shorts = ShortArray(0)

    @SuppressLint("MissingPermission")
    override fun open() {
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) throw AudioInitException("AudioRecord.getMinBufferSize failed ($min)")
        val bytes = maxOf(min * 2, sampleRate)                 // >= 0.5 s of PCM16
        var r: AudioRecord? = null
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            try {
                val c = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bytes)
                if (c.state == AudioRecord.STATE_INITIALIZED) { r = c; break } else c.release()
            } catch (e: SecurityException) {
                throw AudioInitException("RECORD_AUDIO permission not granted", e)
            } catch (e: IllegalArgumentException) {
                // try next source
            }
        }
        if (r == null) throw AudioInitException("Could not initialise the microphone")
        try {
            r.startRecording()
        } catch (e: IllegalStateException) {
            r.release(); throw AudioInitException("Microphone could not start (in use by another app?)", e)
        }
        if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            r.release(); throw AudioInitException("Microphone is busy")
        }
        record = r
    }

    override fun read(out: FloatArray): Int {
        val r = record ?: return AudioSource.READ_ERROR
        if (shorts.size < out.size) shorts = ShortArray(out.size)
        val n = r.read(shorts, 0, out.size)
        if (n < 0) return AudioSource.READ_ERROR
        for (i in 0 until n) out[i] = shorts[i] / 32768f
        return n
    }

    override fun abort() {
        try { record?.stop() } catch (_: IllegalStateException) {}
    }

    @Synchronized override fun close() {
        val r = record ?: return
        record = null
        try { r.stop() } catch (_: IllegalStateException) {}
        r.release()
    }
}
