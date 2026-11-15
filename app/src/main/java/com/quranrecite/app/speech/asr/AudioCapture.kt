package com.quranrecite.app.speech.asr

import com.quranrecite.app.speech.SpeechErrorCode

/** Source of 16 kHz mono PCM. Abstracted so capture logic is testable without a microphone. */
interface AudioSource {
    /** Opens and starts the device. Throws on failure ([AudioInitException]). */
    fun open()
    /** Blocking read of up to [out].size samples as float [-1, 1]. Returns the sample count, 0 on timeout, or a negative error. */
    fun read(out: FloatArray): Int
    /** Unblocks a pending [read] from another thread WITHOUT releasing resources (e.g. AudioRecord.stop()). */
    fun abort() {}
    /** Releases the device. Only called after the reader thread has finished. Idempotent. */
    fun close()

    companion object {
        const val READ_ERROR = -1
        /** Source ended (only used by file/test sources). */
        const val END_OF_STREAM = -2
    }
}

class AudioInitException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Pumps an [AudioSource] on a dedicated thread into [sink] in [chunkMs] chunks until [stop].
 * [stop] closes the source and joins the thread, so no audio is delivered after it returns.
 */
class CaptureLoop(
    private val source: AudioSource,
    private val sampleRate: Int,
    private val chunkMs: Int = 100,
    private val threadSetup: () -> Unit = {},
    private val onError: (SpeechErrorCode, String) -> Unit,
    private val sink: (FloatArray, Int) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    /** Opens the source (on the caller's thread) and starts reading. Throws [AudioInitException]. */
    fun start() {
        check(!running) { "already running" }
        source.open()
        running = true
        thread = Thread({ loop() }, "asr-capture").apply { start() }
    }

    fun stop() {
        running = false
        val t = thread
        if (t != null) {
            try { source.abort() } catch (_: Throwable) {}
            t.join(2_000)
        }
        try { source.close() } catch (_: Throwable) {}
        thread = null
    }

    val isRunning: Boolean get() = running

    private fun loop() {
        threadSetup()
        val buf = FloatArray(sampleRate * chunkMs / 1000)
        var zeroReads = 0
        while (running) {
            val n = try { source.read(buf) } catch (e: Throwable) {
                if (running) { running = false; onError(SpeechErrorCode.AudioReadFailed, "Audio read failed: ${e.message}") }
                return
            }
            when {
                n > 0 -> { zeroReads = 0; sink(buf, n) }
                n == 0 -> if (++zeroReads > 200) {   // ~20 s of nothing
                    running = false
                    onError(SpeechErrorCode.AudioReadFailed, "Microphone returned no data")
                    return
                }
                n == AudioSource.END_OF_STREAM -> { running = false; return }
                else -> {
                    if (running) { running = false; onError(SpeechErrorCode.AudioReadFailed, "Audio read error $n") }
                    return
                }
            }
        }
    }
}
