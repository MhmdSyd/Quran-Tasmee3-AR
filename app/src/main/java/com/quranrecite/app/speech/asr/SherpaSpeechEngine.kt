package com.quranrecite.app.speech.asr

import android.content.Context
import android.os.Process
import android.util.Log
import com.quranrecite.app.speech.EngineState
import com.quranrecite.app.speech.SpeechEngine
import com.quranrecite.app.speech.SpeechErrorCode
import com.quranrecite.app.speech.SpeechEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Offline Quran recitation recognizer: quran-asr-small (INT8 ONNX) on sherpa-onnx, fed by AudioRecord at 16 kHz mono.
 * Chunked pseudo-streaming (see [ChunkedRecognitionPipeline]); NOT true streaming.
 *
 * Construction is cheap (no native code is touched). The model is installed/loaded by [prepare] or lazily by [start].
 * Public suspend functions are serialised by one mutex. Threads: caller's (control), "asr-capture" (AudioRecord loop),
 * "asr-decode" (all model inference), plus ONNX Runtime's own pool of [numThreads].
 */
class SherpaSpeechEngine(
    context: Context,
    private val config: StreamingConfig = StreamingConfig(),
    /** ONNX Runtime intra-op threads. 2 matches the model author's reference; leaves cores for UI/audio. */
    private val numThreads: Int = 2,
    private val probe: ResourceProbe = AndroidResourceProbe(),
) : SpeechEngine {
    private val appContext = context.applicationContext

    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 512)
    override val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()
    private val _state = MutableStateFlow(EngineState.Unloaded)
    override val state: StateFlow<EngineState> = _state.asStateFlow()
    private val _metrics = MutableStateFlow(EngineMetrics())
    val metrics: StateFlow<EngineMetrics> = _metrics.asStateFlow()

    /** Last failure, for UIs that want more than the transient [SpeechEvent.Error]. */
    @Volatile var lastError: SpeechEvent.Error? = null
        private set

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var transcriber: SherpaTranscriber? = null
    private var pipeline: ChunkedRecognitionPipeline? = null
    private var capture: CaptureLoop? = null
    private var captureGeneration = 0
    private var sampler: ResourceSampler? = null
    private var samplerJob: Job? = null
    private var modelLoadMs = 0L

    // ---------------------------------------------------------------------------------------------- SpeechEngine

    override suspend fun prepare() { mutex.withLock { prepareLocked() } }

    override suspend fun start() {
        mutex.withLock {
            when (_state.value) {
                EngineState.Listening, EngineState.Paused, EngineState.Stopping, EngineState.Loading -> return
                else -> {}
            }
            if (!MicPermission.isGranted(appContext)) {
                fail(SpeechErrorCode.PermissionDenied, "RECORD_AUDIO permission not granted"); return
            }
            if (!prepareLocked()) return
            val t = transcriber ?: return
            val p = ChunkedRecognitionPipeline(t, config, sink = ::emit)
            p.start()
            if (!startCaptureLocked(p)) { p.release(); return }
            pipeline = p
            startSamplerLocked()
            _state.value = EngineState.Listening
            emit(SpeechEvent.Listening)
        }
    }

    override suspend fun pause() {
        mutex.withLock {
            if (_state.value != EngineState.Listening) return
            capture?.stop(); capture = null
            pipeline?.flush()                       // open speech becomes a Final (end = Flushed)
            stopSamplerLocked()
            _state.value = EngineState.Paused
        }
    }

    override suspend fun resume() {
        mutex.withLock {
            if (_state.value != EngineState.Paused) return
            if (!MicPermission.isGranted(appContext)) {
                fail(SpeechErrorCode.PermissionDenied, "RECORD_AUDIO permission not granted"); return
            }
            val p = pipeline ?: return
            if (!startCaptureLocked(p)) return
            startSamplerLocked()
            _state.value = EngineState.Listening
            emit(SpeechEvent.Listening)
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            val s = _state.value
            if (s == EngineState.Unloaded || s == EngineState.Ready || s == EngineState.Loading || s == EngineState.Stopping) return
            _state.value = EngineState.Stopping
            capture?.stop(); capture = null
            val p = pipeline; pipeline = null
            if (p != null) {
                val drained = withContext(Dispatchers.IO) { p.stopAndDrain(config.drainTimeoutMs) }
                if (!drained) Log.w(TAG, "stop(): decode queue did not drain within ${config.drainTimeoutMs} ms")
                publishMetrics(p)
            }
            stopSamplerLocked()
            _state.value = if (transcriber != null) EngineState.Ready else EngineState.Unloaded
        }
    }

    /** Call when no other engine call is in flight (e.g. from onCleared / Application teardown). The engine can be reused. */
    override fun release() {
        samplerJob?.cancel(); samplerJob = null
        capture?.stop(); capture = null
        pipeline?.release(); pipeline = null
        transcriber?.release(); transcriber = null
        _state.value = EngineState.Unloaded
    }

    // ------------------------------------------------------------------------------------------- diagnostics

    /** Times the model on synthetic audio (not while listening). Null if the engine is busy or the model cannot load. */
    suspend fun benchmark(): BenchmarkReport? = mutex.withLock {
        val s = _state.value
        if (s == EngineState.Listening || s == EngineState.Paused || s == EngineState.Stopping) return null
        if (!prepareLocked()) return null
        val t = transcriber ?: return null
        withContext(Dispatchers.Default) { AsrBenchmark.run(t, probe) }
    }

    // ------------------------------------------------------------------------------------------------ internals

    private suspend fun prepareLocked(): Boolean {
        if (transcriber != null) return true
        _state.value = EngineState.Loading
        return try {
            val t0 = System.nanoTime()
            val t = withContext(Dispatchers.IO) {
                val installed = ModelInstaller(appContext).ensureInstalled()
                SherpaTranscriber.load(installed.modelPath, installed.tokensPath, numThreads).also { it.warmUp() }
            }
            transcriber = t
            modelLoadMs = (System.nanoTime() - t0) / 1_000_000
            _metrics.value = _metrics.value.copy(modelLoadMs = modelLoadMs)
            _state.value = EngineState.Ready
            true
        } catch (e: CancellationException) {
            _state.value = EngineState.Unloaded; throw e
        } catch (e: ModelMissingException) {
            fail(SpeechErrorCode.ModelMissing, e.message ?: "ASR model missing"); false
        } catch (e: UnsatisfiedLinkError) {
            fail(SpeechErrorCode.ModelLoadFailed, "sherpa-onnx native library unavailable for this device ABI: ${e.message}"); false
        } catch (e: Throwable) {
            fail(SpeechErrorCode.ModelLoadFailed, "Could not load the ASR model: ${e.message ?: e.javaClass.simpleName}"); false
        }
    }

    private fun startCaptureLocked(p: ChunkedRecognitionPipeline): Boolean {
        val generation = ++captureGeneration
        val loop = CaptureLoop(
            source = AudioRecordSource(config.sampleRate),
            sampleRate = config.sampleRate,
            chunkMs = 100,
            threadSetup = { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) },
            onError = { code, msg -> onCaptureFailed(generation, code, msg) },
            sink = { buf, n -> p.push(buf, n) },
        )
        return try {
            loop.start(); capture = loop; true
        } catch (e: AudioInitException) {
            fail(SpeechErrorCode.AudioInitFailed, e.message ?: "Microphone unavailable"); false
        }
    }

    /** Runs on the capture thread: must not take [mutex] (stop() holds it while joining this thread). */
    private fun onCaptureFailed(generation: Int, code: SpeechErrorCode, msg: String) {
        fail(code, msg)
        scope.launch {
            mutex.withLock {
                if (generation != captureGeneration) return@withLock       // already stopped/restarted
                capture?.stop(); capture = null
                val p = pipeline; pipeline = null
                if (p != null) withContext(Dispatchers.IO) { p.stopAndDrain(config.drainTimeoutMs) }
                stopSamplerLocked()
                // keep EngineState.Error so the UI sees the failure; start() may be called again
            }
        }
    }

    private fun startSamplerLocked() {
        samplerJob?.cancel()
        val s = ResourceSampler(probe)
        sampler = s
        samplerJob = scope.launch {
            while (isActive) {
                delay(1000)
                publishMetrics(pipeline, s)
            }
        }
    }

    private fun stopSamplerLocked() {
        samplerJob?.cancel(); samplerJob = null
    }

    private fun publishMetrics(p: ChunkedRecognitionPipeline?, s: ResourceSampler? = sampler) {
        _metrics.value = EngineMetrics(
            pipeline = p?.snapshotMetrics() ?: _metrics.value.pipeline,
            resources = s?.sample() ?: _metrics.value.resources,
            modelLoadMs = modelLoadMs,
        )
    }

    private fun fail(code: SpeechErrorCode, message: String) {
        val e = SpeechEvent.Error(message, code)
        lastError = e
        _state.value = EngineState.Error
        emit(e)
    }

    private fun emit(e: SpeechEvent) {
        if (!_events.tryEmit(e)) Log.w(TAG, "event buffer full, dropped $e")
    }

    private companion object { const val TAG = "SherpaSpeechEngine" }
}
