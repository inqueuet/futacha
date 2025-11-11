package com.valoser.futacha.shared.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
actual suspend fun pickImage(): ImageData? = suspendCoroutine { continuation ->
    val config = PHPickerConfiguration().apply {
        selectionLimit = 1
        filter = PHPickerFilter.imagesFilter
    }

    val picker = PHPickerViewController(configuration = config)

    val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)

            val results = didFinishPicking.filterIsInstance<PHPickerResult>()
            if (results.isEmpty()) {
                continuation.resume(null)
                return
            }

            val result = results.first()
            val itemProvider = result.itemProvider

            // UTType.imageを使って画像を読み込む
            itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                if (error != null || data == null) {
                    continuation.resume(null)
                    return@loadDataRepresentationForTypeIdentifier
                }

                val bytes = ByteArray(data.length.toInt())
                memcpy(bytes.refTo(0), data.bytes, data.length.toULong())

                continuation.resume(ImageData(bytes, "image.jpg"))
            }
        }
    }

    picker.delegate = delegate

    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootViewController?.presentViewController(picker, animated = true, completion = null)
}
