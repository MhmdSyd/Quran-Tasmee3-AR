package com.quranrecite.app.speech.asr

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ModelMissingException(message: String) : Exception(message)

/**
 * First-run local installation of the bundled model: assets/asr/quran-asr-small -> filesDir/asr/<name>-<sha8>/.
 * Nothing is downloaded. The copy is verified against the SHA-256 in model-manifest.json; later launches only check a
 * marker file and the file size. Older installed versions are deleted.
 */
class ModelInstaller(private val context: Context) {
    class Installed(val modelPath: String, val tokensPath: String, val installedNow: Boolean, val installMs: Long)

    fun ensureInstalled(): Installed {
        val t0 = System.nanoTime()
        val am = context.assets
        val dir = AsrModel.ASSET_DIR
        val names = am.list(dir)?.toSet() ?: emptySet()
        if (AsrModel.MODEL_FILE !in names) {
            throw ModelMissingException(
                "ASR model asset missing (assets/$dir/${AsrModel.MODEL_FILE}). Run tools/fetch_asr_assets.sh and rebuild.",
            )
        }
        val manifest = JSONObject(am.open("$dir/${AsrModel.MANIFEST_FILE}").bufferedReader().use { it.readText() })
        val modelInfo = manifest.getJSONObject("files").getJSONObject(AsrModel.MODEL_FILE)
        val sha = modelInfo.getString("sha256")
        val minBytes = modelInfo.getLong("minBytes")
        val maxBytes = modelInfo.getLong("maxBytes")

        val root = File(context.filesDir, "asr")
        val target = File(root, "${AsrModel.NAME}-${sha.take(8)}")
        val model = File(target, AsrModel.MODEL_FILE)
        val tokens = File(target, AsrModel.TOKENS_FILE)
        val marker = File(target, ".verified")

        if (marker.exists() && marker.readText() == sha && model.length() in minBytes..maxBytes && tokens.exists()) {
            return Installed(model.path, tokens.path, false, (System.nanoTime() - t0) / 1_000_000)
        }

        target.deleteRecursively()
        target.mkdirs()
        val part = File(target, AsrModel.MODEL_FILE + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        am.open("$dir/${AsrModel.MODEL_FILE}").use { input ->
            FileOutputStream(part).use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n); out.write(buf, 0, n)
                }
                out.fd.sync()
            }
        }
        val got = digest.digest().joinToString("") { "%02x".format(it) }
        if (got != sha || part.length() !in minBytes..maxBytes) {
            part.delete()
            throw ModelMissingException("ASR model asset is corrupt (sha256 $got, expected $sha). Re-run tools/fetch_asr_assets.sh.")
        }
        check(part.renameTo(model)) { "cannot move model into place" }
        am.open("$dir/${AsrModel.TOKENS_FILE}").use { i -> FileOutputStream(tokens).use { o -> i.copyTo(o) } }
        marker.writeText(sha)

        root.listFiles()?.filter { it.isDirectory && it != target }?.forEach { it.deleteRecursively() }
        return Installed(model.path, tokens.path, true, (System.nanoTime() - t0) / 1_000_000)
    }
}
