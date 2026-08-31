package com.valoser.futacha.shared.ui.compat

import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberCompatShareLauncher(): (
    text: String,
    mimeType: String,
    absoluteFilePath: String?
) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text, mimeType, absoluteFilePath ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TEXT, text)
                absoluteFilePath?.let { path ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "shared media", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
