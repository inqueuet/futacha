@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.presentPicker
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPresentationController
import platform.darwin.NSObject

private const val IOS_FUTABER_ORIENTATION_NOTIFICATION = "com.valoser.futacha.futaber-orientation-lock"

/** iOS's document picker for exporting (a copy of the saved file goes to where the person picks: Files, Dropbox, Google Drive, ...). */
@Composable
internal actual fun rememberFutaberFileExporter(): (path: String, fileName: String, onDone: (Boolean) -> Unit) -> Unit {
    val retained = remember { arrayOfNulls<Any>(1) }
    return { path, _, onDone ->
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(path)), asCopy = true)
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                retained[0] = null
                onDone(didPickDocumentsAtURLs.isNotEmpty())
            }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { retained[0] = null }
        }
        retained[0] = delegate
        picker.delegate = delegate
        presentPicker(picker, "picture export", onPresented = { }, onPresentFailed = {
            retained[0] = null
            Logger.w("FutaberViewerPlatform.ios", "The export picker could not be shown")
            onDone(false)
        })
    }
}

/** The host holds the upright orientation while the picture list or the viewer is up (the host view controller reads the notification). */
@Composable
internal actual fun ApplyFutaberViewerOrientation(portrait: Boolean) {
    DisposableEffect(portrait) {
        if (!portrait) return@DisposableEffect onDispose { }
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = IOS_FUTABER_ORIENTATION_NOTIFICATION, `object` = null, userInfo = mapOf("portrait" to true)
        )
        onDispose {
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = IOS_FUTABER_ORIENTATION_NOTIFICATION, `object` = null, userInfo = mapOf("portrait" to false)
            )
        }
    }
}
