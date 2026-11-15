package com.quranrecite.app.speech.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig

/**
 * quran-asr-small through sherpa-onnx's OfflineRecognizer (zipformer-CTC loader, greedy search, CPU).
 * Mirrors the model author's reference script: from_zipformer_ctc(model, tokens, num_threads=2) with default
 * 80-dim fbank features at 16 kHz. Not thread-safe: use from one worker thread.
 */
class SherpaTranscriber private constructor(private val recognizer: OfflineRecognizer) : Transcriber {

    override fun transcribe(samples: FloatArray, sampleRate: Int): TranscriptionResult {
        // very short buffers can be shorter than the subsampling front-end needs; zero-pad to 300 ms
        val minSamples = sampleRate * 3 / 10
        val input = if (samples.size >= minSamples) samples else samples.copyOf(minSamples)
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(input, sampleRate)
            recognizer.decode(stream)
            val r = recognizer.getResult(stream)
            return TranscriptionResult(r.text, r.tokens.toList(), r.timestamps)
        } finally {
            stream.release()
        }
    }

    /** One short decode so ONNX Runtime allocates its buffers before the first real utterance. */
    fun warmUp() { transcribe(FloatArray(AsrModel.SAMPLE_RATE), AsrModel.SAMPLE_RATE) }

    override fun release() = recognizer.release()

    companion object {
        fun load(modelPath: String, tokensPath: String, numThreads: Int = 2): SherpaTranscriber {
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = AsrModel.SAMPLE_RATE, featureDim = 80, dither = 0f),
                modelConfig = OfflineModelConfig(
                    zipformerCtc = OfflineZipformerCtcModelConfig(model = modelPath),
                    tokens = tokensPath,
                    numThreads = numThreads,
                    debug = false,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            )
            return SherpaTranscriber(OfflineRecognizer(assetManager = null, config = config))
        }
    }
}
