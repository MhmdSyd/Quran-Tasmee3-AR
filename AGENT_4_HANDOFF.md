# Agent 4 Handoff — Offline Quran speech recognition

## Read this first (honest status)
| Item | Status |
|---|---|
| Model inspected, correct files/config identified | **Done** (from the HF repo itself; details below) |
| Chunking logic, segmenter, pipeline, metrics, alphabet, capture loop | **Compiled with Kotlin 2.0.20 and 34 JVM tests run, all pass** (5 consecutive runs, no flakiness) |
| Android layer (engine, installer, sherpa wrapper, AudioRecord) | **Compiled against API stubs only** (types/signatures checked). Never run on Android. |
| Compose screens, `MicPermission`, Gradle/CI edits | **Not compiled** here (no Google Maven/Gradle access). First real compile = GitHub Actions. Expect small fixes at most. |
| Model actually run | **NO.** `huggingface.co` is blocked from my sandbox, so the 36 MB model could not be downloaded or executed. No transcript, WER or latency was produced by me. CI step `asr_smoke_test.py` is the first real execution. |
| Latency / memory / CPU on hardware | **NOT measured** (no device). A diagnostics screen + benchmark are included to measure in two minutes. **No performance number in this document is a measurement.** |
| Airplane mode on a device | **Not tested.** Offline-ness is enforced structurally: no `INTERNET` permission (CI fails the build if it appears), no download code, model/libs packaged in the APK. |
| True streaming | **No.** The model is offline-only. Incremental output = chunked decoding (below). Do not call it streaming. |

**The 36 MB model is not in the zip.** `tools/fetch_asr_assets.sh` downloads it (pinned commit, SHA-256 verified) plus the sherpa-onnx native libs; CI runs it. Run it once locally before building if you want a working ASR.

## 1. The model (verified from the upstream repo)
`Abdou/quran-asr-small` @ commit `0c2744b9d70aaf45ba574c30c5d318c575258e66`, MIT (training data keeps its own terms; tlog is CC-BY-4.0 → keep an attribution line in the app's About screen).
- **Architecture:** Conformer-CTC, 33M params, ×4 subsampling, 31 outputs, greedy CTC, no LM/lexicon. **Offline** recognizer (the author's own script uses `OfflineRecognizer.from_zipformer_ctc`).
- **Files used:** `model.int8.onnx` (36.1 MB, SHA-256 `88f9df29…cff00f0`), `tokens.txt` (180 bytes, committed). `model.onnx` (fp32, 133 MB), `model.pt`, `spelling.json`, `retrieve.py` are not used.
- **sherpa-onnx loader:** "zipformer CTC" (`OfflineZipformerCtcModelConfig`). I read the runtime source (v2.0.1): it hard-codes subsampling 4, takes vocab size from the output shape and needs no metadata, so this export is loadable by design. Default fbank config (16 kHz, 80-dim, dither 0) = the author's script = my Kotlin config.
- **Runtime:** sherpa-onnx **v2.0.1**, `…-android-static-link-onnxruntime` build (one `libsherpa-onnx-jni.so` per ABI: arm64-v8a, armeabi-v7a, x86_64; 16 KB page-aligned; needs no libc++_shared). Kotlin API files are vendored **verbatim** from the same tag in `com/k2fsa/sherpa/onnx/` (Apache-2.0, license file included); JNI binds to their field names, so never edit them or mix versions.
- **Reported accuracy (author's numbers, not mine):** WER 4.49 / CER 2.17 overall on Quran-Lab; studio reciters 0.69 WER, phone recordings 10.45 WER. Plan for real mistakes in the text.

### Output alphabet (important for Agent 5)
`tokens.txt`: `<blk>`, 29 bare letters `ء ا ب ت ث ج ح خ د ذ ر ز س ش ص ض ط ظ ع غ ف ق ك ل م ن ه و ي`, and `▁` (word separator, U+2581). **No diacritics, no أ إ آ ٱ ى ئ ؤ ة.** Uthmani-style spelling (السموت, ءامنوا).
`speech/asr/ModelAlphabet.kt` ports the author's `retrieve.py` fold table: `foldWord/foldWords/foldText` map canonical Hafs words into the model's letters. Verified against Agent 2's real text (`python3 tools/check_model_alphabet.py`): all 29 letters occur; the **only** words folding to empty are the 6,236 ayah-number markers; **no** word folds to a lone ا/ي. So per-word matching is a clean `foldWord(QuranWord.text)` comparison. This is alphabet normalisation only; matching is Agent 5's.

## 2. Public API (`com.quranrecite.app.speech`)
`SpeechEngine` was extended **source-compatibly** (all new event fields have defaults; `Partial(text)`/`Final(text)` still compile). `AppContainer.speechEngine` is now `SherpaSpeechEngine` (also `container.asrEngine` for metrics/benchmark).
```kotlin
interface SpeechEngine {
  val events: SharedFlow<SpeechEvent>      // no replay: collect BEFORE start()
  val state: StateFlow<EngineState>        // Unloaded, Loading, Ready, Listening, Paused, Stopping, Error
  suspend fun prepare()                    // optional preload (install + load + warm-up), idempotent
  suspend fun start(); suspend fun pause(); suspend fun resume()
  suspend fun stop()                       // closes mic, decodes everything pending, emits remaining events, then returns
  fun release()
}
SpeechEvent: Listening | Partial(text, words, stableWordCount, segmentId, audioStartMs, audioEndMs, decodeMs, raw)
           | Final(text, words, segmentId, audioStartMs, audioEndMs, end, decodeMs, latencyMs, raw) | Error(message, code)
RecognizedWord(text, startMs, endMs)       // session clock, CTC 40 ms grid, approx +-80 ms
SpeechErrorCode: PermissionDenied, ModelMissing, ModelLoadFailed, AudioInitFailed, AudioReadFailed, DecodeFailed, Unknown
```
Semantics Agent 5 can rely on (all covered by `ChunkedRecognitionPipelineTest`):
- **Final segments partition the audio**: no overlap, no repeated text. Concatenating Final texts in arrival order = everything heard. Genuine repeated words (e.g. "فبأي آلاء ربكما تكذبان" ×31) are therefore never dropped by de-duplication (none is performed).
- **Partial = hypothesis for audio since the last Final**; each one *replaces* the previous (full re-decode of the open segment). It is always a prefix-consistent growth in tests but may revise earlier words.
- `stableWordCount` = words two consecutive hypotheses agreed on (LocalAgreement-2), excluding the last word. Reveal only stable words if you want no flicker; use the rest for "listening" feedback.
- **Order invariant:** a Partial never arrives for a segment whose Final was already emitted; a Final always precedes Partials of later audio.
- After a forced cut (`end == MaxLength`) the next Partial has `segmentId + 1`.
- Segments whose decode is empty emit nothing. `raw` keeps the exact recognizer string (with `▁`).
- Error events never crash; the engine goes to `EngineState.Error`; calling `start()` again retries (model load, permission, mic).
- Pause = mic released + open speech flushed as a Final (`end = Flushed`); session clock does not advance while paused. `stop()` waits for the decode queue (limit `drainTimeoutMs`, 30 s).

Wiring sketch for `RecitationScreen` (Agent 5 owns it):
```kotlin
val engine = container.speechEngine
LaunchedEffect(Unit) { engine.events.collect { container.recitationMatcher.onSpeech(it) } }
val askMic = rememberMicPermissionRequest { if (it) scope.launch { engine.start() } }   // MicPermission.isGranted(ctx) first
// pause()/resume() on lifecycle ON_STOP/ON_START; stop() when finishing; matcher must get the last events before summary.
```
Matcher's `SpeechEvent` import is unchanged. Mic permission UI: `MicPermission.isGranted`, `rememberMicPermissionRequest`, `appSettingsIntent` (permanently-denied case).

## 3. How incremental recognition works (chunked, not streaming)
`speech/asr/` (pure JVM unless noted):
- `UtteranceSegmenter`: 20 ms frame energy, adaptive noise floor (drops instantly, rises ≤3 dB/s, ceiling −40 dBFS), speech threshold `max(−50 dBFS, noise+10 dB)`, 300 ms pre-roll, onset after 3 frames. A segment closes after **600 ms** of silence (+200 ms tail pad) or, at **12 s**, by a *forced cut at the quietest 60 ms inside the last 4 s*; audio after the cut stays in the open segment. Blips < 250 ms of speech are dropped.
- `ChunkedRecognitionPipeline`: one worker thread owns the recognizer. Closed segments → Final jobs (FIFO). While speaking, every 600 ms of audio the open segment is re-decoded → Partial (latest wins; stale partials are skipped, never queued). Overlapping windows were deliberately **not** used: text de-duplication at overlaps would corrupt legitimate repetition; audio partitioning needs none.
- `WordParser`: tokens + timestamps → words/times; falls back to even spacing (text never lost) if timestamps are inconsistent.
- Capture: `AudioRecordSource` (16 kHz mono PCM16, VOICE_RECOGNITION → MIC fallback, URGENT_AUDIO thread) → `CaptureLoop` (100 ms chunks). 16 kHz is the model's native rate, so no resampler exists (sherpa resamples file input itself).
- Threads: control (caller) · `asr-capture` · `asr-decode` · ONNX Runtime pool (`numThreads = 2`, like the author's script). UI is never blocked; model install/load run on `Dispatchers.IO`.

**Limitations (known, unmitigated):** (1) energy VAD, no second model: loud steady noise (> ~−40 dBFS) creates false segments during the ~7 s the noise floor needs to adapt (test: `steadyBackgroundNoise…`); noisy rooms degrade endpointing. (2) A forced cut inside continuous speech without any dip could split a word (test passes because the synthetic words have 60 ms gaps; real recitation not yet evaluated). Matcher must tolerate a word split across two Finals. (3) Silence/endpoint latency floor = 600 ms. (4) Thresholds are reasoned, **untuned on real recordings**. (5) Cost of a Partial = RTF × open-segment length; the engine keeps up only if that is below `partialIntervalMs` — unknown until measured; knobs: `partialIntervalMs`, `maxSegmentMs`, `numThreads`. (6) Interruptions (calls, another app taking the mic) are not specially handled beyond read errors → `AudioReadFailed`.

## 4. Assets & installation
- `tools/fetch_asr_assets.sh`: downloads + SHA-256-verifies `model.int8.onnx` (HF commit-pinned URL) and the sherpa archive; checks upstream `tokens.txt` equals the committed copy; idempotent. The sherpa half was run here (works); the HF half could not run (blocked) — it is plain `curl` + `sha256sum` against the hash I read from the HF page.
- Model → `app/src/main/assets/asr/quran-asr-small/` (gitignored; `tokens.txt` + `model-manifest.json` committed). Libs → `app/src/main/jniLibs/<abi>/` (gitignored).
- Runtime install (`ModelInstaller`): first run copies the asset to `filesDir/asr/quran-asr-small-<sha8>/`, verifies SHA-256 against the manifest, writes a marker; later runs check marker+size only; old versions are deleted. No download. Missing asset → `ModelMissing` error with the fix in the message. Cost: ~36 MB in APK + 36 MB in app storage.
- APK size impact (uncompressed .so + model, 3 ABIs): ~+100 MB raw (36 MB model + 24+16+27 MB libs). If that matters, drop `armeabi-v7a`/`x86_64` from `abiFilters` + the script's `ABIS`, or ship ABI splits (Agent 6).

## 5. Measuring on a device (do this; ~2 minutes)
Settings → "تشخيص التعرف على الصوت (ASR)". Turn on airplane mode first. **Load** (shows model-load ms), **Benchmark** (2/5/10 s synthetic audio × 5 runs: median/min/max ms, RTF, CPU %, native heap, PSS), **Start** and recite (live Partial, Finals with decode ms and latency, p50/p95 latencies, CPU % of one core, heap/PSS peaks, forced cuts, stale partials). Benchmark timing is content-independent for greedy CTC, so synthetic audio is valid for latency/RTF/memory, but its text is meaningless. Record: device, SoC, Android version, numbers → append to this file. Pass criteria I suggest: RTF(5 s) < 0.1, partial p95 < 1.2 s, no growth in heap over 10 min.

## 6. Tests
JVM (`./gradlew testDebugUnitTest`; run by me with the real compiler through a JUnit shim): `UtteranceSegmenterTest` (9: endpointing, pre-roll, blips, forced cuts land in gaps and partition audio, input-chunking invariance, noise adaptation), `ChunkedRecognitionPipelineTest` (9: exact script reproduction over 30 s continuous + pauses, no loss/duplication, Partial prefixes + ordering invariant, drain on stop, pause/resume clock, slow-device behaviour, decode-error recovery, empty results), `CaptureLoopTest` (3), `WordParserAndAlphabetTest` (8, incl. tokens.txt ↔ alphabet), `MetricsRecorderTest`, `ResourcesTest`. The tests use a *tone-burst fake transcriber* that returns CTC-style tokens/timestamps: they prove the chunking logic, **not** recognition quality.
Python: `tools/check_model_alphabet.py` (ran: PASS), `tools/asr_smoke_test.py` (CI; real model + sherpa-onnx 2.0.1: loads, decodes noise of 0.3–10 s, checks token timestamps are on the 40 ms grid, and when `everyayah.com` is reachable computes WER on 6 Alafasy clips and prints how truncated clips relate to full transcripts). If that timestamp check fails, `WordParser` already falls back safely.
Not covered: instrumented/device tests, Compose UI, real microphone audio, accuracy.

## 7. Changes to others' files
`SpeechEngine.kt` (extended, NoOp updated) · `AppContainer` (`asrEngine`, `speechEngine`) · `AppNav` (+`asr-diagnostics` route) · `SettingsScreen` (+link, default param) · `app/build.gradle.kts` (abiFilters, noCompress onnx) · `AndroidManifest` (comment only; RECORD_AUDIO already declared; still no INTERNET) · CI workflow · README · `.gitignore`. `RecitationScreen`, matcher, mushaf code untouched.

## 8. Suggested next steps
Agent 5: wire as above; fold canonical words with `ModelAlphabet.foldWord`; tolerate substitutions and split/merged words; use `stableWordCount` and word times. Whoever has a phone: section 5, then tune `endSilenceMs`/`maxSegmentMs`/`partialIntervalMs` and decide ABI set. Later: a Silero-VAD (sherpa has one) if noisy rooms matter; foreground-service/lifecycle policy for backgrounding.
