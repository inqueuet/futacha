package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
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
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * UIDocumentPicker で Files.app 経由の画像を選択
 */
@OptIn(ExperimentalForeignApi::class)
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
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootViewController?.presentViewController(picker, animated = true, completion = null)
}

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

@OptIn(ExperimentalForeignApi::class)
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
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootViewController?.presentViewController(picker, animated = true, completion = null)
}

/**
 * iOS ディレクトリピッカーで SaveLocation を選択
 * セキュアブックマークを作成して永続化可能にする
 */
@OptIn(ExperimentalForeignApi::class)
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
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootViewController?.presentViewController(picker, animated = true, completion = null)
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
        val base64 = bookmarkData.base64EncodedStringWithOptions(0u)
        SaveLocation.Bookmark(base64)
    }
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
                    val data = (location.bookmarkData as platform.Foundation.NSString).dataUsingEncoding(platform.Foundation.NSUTF8StringEncoding)
                        ?: return false
                    val bookmarkNSData = platform.Foundation.NSData.create(base64EncodedString = location.bookmarkData as platform.Foundation.NSString, options = 0u)
                        ?: return false
                    val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
                    val isStale = alloc<ObjCObjectVar<Boolean>>()
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
