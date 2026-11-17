package com.valoser.futacha.shared.ui.futaber

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val FUTABER_VIEWER_PLATFORM_TAG = "FutaberViewerPlatform"

/** Android's document creator (ACTION_CREATE_DOCUMENT): the picker the storage providers answer to; the saved file is copied to the chosen document. */
@Composable
internal actual fun rememberFutaberFileExporter(): (path: String, fileName: String, onDone: (Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<Pair<String, (Boolean) -> Unit>>(1) }
    val launcher = rememberExperienceProfileActivityResultLauncher(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri?, _ ->
        val request = pending[0]
        pending[0] = null
        if (uri == null || request == null) return@rememberExperienceProfileActivityResultLauncher
        val (path, done) = request
        scope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "w") ?: error("no output")
                    out.use { sink -> File(path).inputStream().use { it.copyTo(sink) } }
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_VIEWER_PLATFORM_TAG, "Failed to write the exported picture", error)
                false
            }
            done(ok)
        }
    }
    return { path, fileName, onDone ->
        pending[0] = path to onDone
        try {
            launcher.launch(fileName)
        } catch (error: Exception) {
            Logger.e(FUTABER_VIEWER_PLATFORM_TAG, "Failed to open the document creator", error)
            pending[0] = null
            onDone(false)
        }
    }
}

@Composable
internal actual fun ApplyFutaberViewerOrientation(portrait: Boolean) {
    val activity = LocalContext.current.findViewerActivity()
    DisposableEffect(activity, portrait) {
        if (activity == null || !portrait) return@DisposableEffect onDispose { }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = previous }
    }
}

private tailrec fun Context.findViewerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findViewerActivity()
    else -> null
}
