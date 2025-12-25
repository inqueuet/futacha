@file:OptIn(ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * UIDocumentPicker で Files.app 経由の画像を選択
 */
suspend fun pickImageFromDocuments(): ImageData? = suspendCoroutine { continuation ->
    val imageTypes = listOf(
        platform.UniformTypeIdentifiers.UTTypeImage,
        platform.UniformTypeIdentifiers.UTTypeJPEG,
        platform.UniformTypeIdentifiers.UTTypePNG,
        platform.UniformTypeIdentifiers.UTTypeGIF,
        platform.UniformTypeIdentifiers.UTTypeWebP
    )
    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = imageTypes,
        asCopy = true
    )

    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            controller.dismissViewControllerAnimated(true, null)
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                continuation.resume(null)
                return
            }

            val data = NSData.dataWithContentsOfURL(url)
            if (data == null) {
                Logger.w("ImagePicker.ios", "Failed to load image from ${url.path}")
                continuation.resume(null)
                return
            }

            val bytes = ByteArray(data.length.toInt())
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }

            val fileName = url.lastPathComponent ?: "image.jpg"
            continuation.resume(ImageData(bytes, fileName))
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            controller.dismissViewControllerAnimated(true, null)
            continuation.resume(null)
        }
    }

    picker.delegate = delegate
    getRootViewController()?.presentViewController(picker, animated = true, completion = null)
}

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
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), data.bytes, data.length)
                }

                continuation.resume(ImageData(bytes, "image.jpg"))
            }
        }
    }

    picker.delegate = delegate

    getRootViewController()?.presentViewController(picker, animated = true, completion = null)
}

actual suspend fun pickDirectoryPath(): String? = suspendCoroutine { continuation ->
    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf<UTType>(UTTypeFolder),
        asCopy = false
    )
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            controller.dismissViewControllerAnimated(true, null)
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            val path = url?.path
            if (path != null && canWriteTestFile(path)) {
                continuation.resume(path)
            } else {
                continuation.resume(null)
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            controller.dismissViewControllerAnimated(true, null)
            continuation.resume(null)
        }
    }
    picker.delegate = delegate
    getRootViewController()?.presentViewController(picker, animated = true, completion = null)
}

/**
 * iOS ディレクトリピッカーで SaveLocation を選択
 * セキュアブックマークを作成して永続化可能にする
 */
actual suspend fun pickDirectorySaveLocation(): SaveLocation? = suspendCoroutine { continuation ->
    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf<UTType>(UTTypeFolder),
        asCopy = false
    )
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            controller.dismissViewControllerAnimated(true, null)
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                continuation.resume(null)
                return
            }

            // セキュアブックマークを作成
            val bookmarkLocation = createSecureBookmark(url)
            if (bookmarkLocation != null && canWriteToSaveLocation(bookmarkLocation)) {
                continuation.resume(bookmarkLocation)
            } else {
                Logger.w("ImagePicker.ios", "Failed to create secure bookmark or cannot write to ${url.path}")
                continuation.resume(null)
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            controller.dismissViewControllerAnimated(true, null)
            continuation.resume(null)
        }
    }
    picker.delegate = delegate
    getRootViewController()?.presentViewController(picker, animated = true, completion = null)
}

/**
 * NSURL からセキュアブックマークを作成
 */
private fun createSecureBookmark(url: NSURL): SaveLocation.Bookmark? {
    return memScoped {
        val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        val bookmarkData = url.bookmarkDataWithOptions(
            options = 0u,
            includingResourceValuesForKeys = null,
            relativeToURL = null,
            error = error.ptr
        )
        if (bookmarkData == null) {
            Logger.e("ImagePicker.ios", "Failed to create bookmark: ${error.value?.localizedDescription}")
            return null
        }
        val bytes = ByteArray(bookmarkData.length.toInt())
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bookmarkData.bytes, bookmarkData.length)
        }
        val base64 = encodeBase64(bytes)
        SaveLocation.Bookmark(base64)
    }
}

private fun getRootViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val keyWindow = application.windows.firstOrNull { it.isKeyWindow } ?: application.windows.firstOrNull()
    return keyWindow?.rootViewController
}

/**
 * SaveLocation に書き込み可能かテスト
 */
private fun canWriteToSaveLocation(location: SaveLocation): Boolean {
    return when (location) {
        is SaveLocation.Path -> canWriteTestFile(location.path)
        is SaveLocation.Bookmark -> {
            memScoped {
                try {
                    val bookmarkNSData = decodeBase64ToNSData(location.bookmarkData) ?: return false
                    val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
                    val isStale = alloc<BooleanVar>()
                    val url = NSURL.URLByResolvingBookmarkData(
                        bookmarkNSData,
                        options = 0u,
                        relativeToURL = null,
                        bookmarkDataIsStale = isStale.ptr,
                        error = error.ptr
                    )
                    if (url == null) return false
                    val started = url.startAccessingSecurityScopedResource()
                    try {
                        val path = url.path ?: return false
                        canWriteTestFile(path)
                    } finally {
                        if (started) {
                            url.stopAccessingSecurityScopedResource()
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("ImagePicker.ios", "Failed to test write to bookmark", e)
                    false
                }
            }
        }
        is SaveLocation.TreeUri -> false
    }
}

private fun canWriteTestFile(directoryPath: String): Boolean {
    return try {
        val fileManager = NSFileManager.defaultManager
        val probeName = ".futacha_write_probe_${NSUUID().UUIDString}"
        val probePath = directoryPath + "/" + probeName
        val created = fileManager.createFileAtPath(probePath, contents = NSData(), attributes = null)
        if (!created) return false
        fileManager.removeItemAtPath(probePath, null)
        true
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String {
    return Base64.encode(bytes)
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64ToNSData(base64: String): NSData? {
    val bytes = runCatching { Base64.decode(base64) }.getOrNull() ?: return null
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}
