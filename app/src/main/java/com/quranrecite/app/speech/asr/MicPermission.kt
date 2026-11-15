package com.quranrecite.app.speech.asr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

/** RECORD_AUDIO helpers. The engine itself only *checks* the permission (and reports PermissionDenied). */
object MicPermission {
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Opens this app's system settings page (for the "permanently denied" case). */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}

/**
 * Compose: returns a function that shows the system permission dialog; [onResult] gets the outcome.
 * Android stops showing the dialog after two denials: then [onResult] fires with false immediately and the UI should
 * offer [MicPermission.appSettingsIntent].
 */
@Composable
fun rememberMicPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return { launcher.launch(Manifest.permission.RECORD_AUDIO) }
}
