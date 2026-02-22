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
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.concurrent.AtomicReference
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val MAX_PICKED_IMAGE_BYTES = 10L * 1024L * 1024L

/**
 * UIDocumentPicker delegate の保持
 */
private val activePickerDelegate = AtomicReference<NSObject?>(null)

private fun retainPickerDelegate(delegate: NSObject) {
    activePickerDelegate.value = delegate
}

private fun releasePickerDelegate(delegate: NSObject?) {
    delegate ?: return
    activePickerDelegate.compareAndSet(delegate, null)
}

/**
 * 互換性のため公開。現在は pickDirectoryPath() 側で都度解放されるため no-op。
 */
fun releaseSecurityScopedResource() {
    // no-op
}

/**
 * UIDocumentPicker で Files.app 経由の画像を選択
 */
suspend fun pickImageFromDocuments(): ImageData? = suspendCoroutine { continuation ->
    val rootViewController = getRootViewController()
    if (rootViewController == null) {
        Logger.w("ImagePicker.ios", "Cannot present document image picker: root view controller is unavailable")
        continuation.resume(null)
        return@suspendCoroutine
    }
    val hasResumed = AtomicReference(false)
    var delegateRef: NSObject? = null
    fun complete(value: ImageData?) {
        if (!hasResumed.compareAndSet(false, true)) return
        releasePickerDelegate(delegateRef)
        continuation.resume(value)
    }

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
                complete(null)
                return
            }
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                val knownFileSize = resolveFileSizeBytes(url)
                val selected = if (knownFileSize != null && knownFileSize > MAX_PICKED_IMAGE_BYTES) {
                    Logger.w(
                        "ImagePicker.ios",
                        "Selected image is too large: ${knownFileSize / 1024}KB (max: ${MAX_PICKED_IMAGE_BYTES / 1024}KB)"
                    )
                    null
                } else {
                    val data = NSData.dataWithContentsOfURL(url)
                    if (data == null) {
                        Logger.w("ImagePicker.ios", "Failed to load image from ${url.path}")
                        null
                    } else {
                        val dataLength = data.length.toLong()
                        if (dataLength > MAX_PICKED_IMAGE_BYTES) {
                            Logger.w(
                                "ImagePicker.ios",
                                "Selected image is too large: ${dataLength / 1024}KB (max: ${MAX_PICKED_IMAGE_BYTES / 1024}KB)"
                            )
                            null
                        } else {
                            val bytes = ByteArray(dataLength.toInt())
                            bytes.usePinned { pinned ->
                                memcpy(pinned.addressOf(0), data.bytes, data.length)
                            }
                            val fileName = url.lastPathComponent ?: "image.jpg"
                            ImageData(bytes, fileName)
                        }
                    }
                }
                dispatch_async(dispatch_get_main_queue()) {
                    complete(selected)
                }
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            controller.dismissViewControllerAnimated(true, null)
            complete(null)
        }
    }

    delegateRef = delegate
    retainPickerDelegate(delegate)
    picker.delegate = delegate
    runCatching {
        rootViewController.presentViewController(picker, animated = true, completion = null)
    }.onFailure { error ->
        Logger.e("ImagePicker.ios", "Failed to present document image picker", error)
        complete(null)
    }
}

actual suspend fun pickImage(): ImageData? = suspendCoroutine { continuation ->
    val rootViewController = getRootViewController()
    if (rootViewController == null) {
        Logger.w("ImagePicker.ios", "Cannot present photo picker: root view controller is unavailable")
        continuation.resume(null)
        return@suspendCoroutine
    }
    val hasResumed = AtomicReference(false)
    var delegateRef: NSObject? = null
    fun complete(value: ImageData?) {
        if (!hasResumed.compareAndSet(false, true)) return
        releasePickerDelegate(delegateRef)
        continuation.resume(value)
    }

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
                complete(null)
                return
            }

            val result = results.first()
            val itemProvider = result.itemProvider

            // UTType.imageを使って画像を読み込む
            itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                if (error != null || data == null) {
                    complete(null)
                    return@loadDataRepresentationForTypeIdentifier
                }
                val dataLength = data.length.toLong()
                if (dataLength > MAX_PICKED_IMAGE_BYTES) {
                    Logger.w(
                        "ImagePicker.ios",
                        "Selected image is too large: ${dataLength / 1024}KB (max: ${MAX_PICKED_IMAGE_BYTES / 1024}KB)"
                    )
                    complete(null)
                    return@loadDataRepresentationForTypeIdentifier
                }

                val bytes = ByteArray(dataLength.toInt())
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), data.bytes, data.length)
                }

                complete(ImageData(bytes, "image.jpg"))
            }
        }
    }

    delegateRef = delegate
    retainPickerDelegate(delegate)
    picker.delegate = delegate

    runCatching {
        rootViewController.presentViewController(picker, animated = true, completion = null)
    }.onFailure { error ->
        Logger.e("ImagePicker.ios", "Failed to present photo picker", error)
        complete(null)
    }
}

/**
 * ディレクトリを選択してパスを返す。
 *
 * 注意: この関数が返すパスは現在のアプリセッション中のみ有効です。
 * アプリを再起動すると、セキュリティスコープリソースへのアクセス権が失われます。
 * 永続的なアクセスが必要な場合は [pickDirectorySaveLocation] を使用してください。
 *
 * セキュリティスコープは書き込み可否チェック後に即座に解放されます。
 * 永続アクセスが必要な場合は [pickDirectorySaveLocation] を利用してください。
 */
actual suspend fun pickDirectoryPath(): String? = suspendCoroutine { continuation ->
    val rootViewController = getRootViewController()
    if (rootViewController == null) {
        Logger.w("ImagePicker.ios", "Cannot present directory picker: root view controller is unavailable")
        continuation.resume(null)
        return@suspendCoroutine
    }
    val hasResumed = AtomicReference(false)
    var delegateRef: NSObject? = null
    fun complete(value: String?) {
        if (!hasResumed.compareAndSet(false, true)) return
        releasePickerDelegate(delegateRef)
        continuation.resume(value)
    }

    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf<UTType>(UTTypeFolder),
        asCopy = false
    )
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            controller.dismissViewControllerAnimated(true, null)
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                complete(null)
                return
            }

            // Security-scoped resourceへのアクセスを開始
            val started = url.startAccessingSecurityScopedResource()
            val path = url.path
            if (path == null) {
                if (started) {
                    url.stopAccessingSecurityScopedResource()
                }
                complete(null)
                return
            }
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                val canWrite = canWriteTestFile(path)
                dispatch_async(dispatch_get_main_queue()) {
                    if (canWrite) {
                        if (started) {
                            url.stopAccessingSecurityScopedResource()
                        }
                        complete(path)
                    } else {
                        // 失敗時は即座に解放
                        if (started) {
                            url.stopAccessingSecurityScopedResource()
                        }
                        complete(null)
                    }
                }
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            controller.dismissViewControllerAnimated(true, null)
            complete(null)
        }
    }
    delegateRef = delegate
    retainPickerDelegate(delegate)
    picker.delegate = delegate
    runCatching {
        rootViewController.presentViewController(picker, animated = true, completion = null)
    }.onFailure { error ->
        Logger.e("ImagePicker.ios", "Failed to present directory picker", error)
        complete(null)
    }
}

/**
 * iOS ディレクトリピッカーで SaveLocation を選択
 * セキュアブックマークを作成して永続化可能にする
 */
actual suspend fun pickDirectorySaveLocation(): SaveLocation? = suspendCoroutine { continuation ->
    val rootViewController = getRootViewController()
    if (rootViewController == null) {
        Logger.w("ImagePicker.ios", "Cannot present save-location picker: root view controller is unavailable")
        continuation.resume(null)
        return@suspendCoroutine
    }
    val hasResumed = AtomicReference(false)
    var delegateRef: NSObject? = null
    fun complete(value: SaveLocation?) {
        if (!hasResumed.compareAndSet(false, true)) return
        releasePickerDelegate(delegateRef)
        continuation.resume(value)
    }

    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf<UTType>(UTTypeFolder),
        asCopy = false
    )
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            controller.dismissViewControllerAnimated(true, null)
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                complete(null)
                return
            }
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                val bookmarkLocation = createSecureBookmark(url)
                val selected = if (bookmarkLocation != null && canWriteToSaveLocation(bookmarkLocation)) {
                    bookmarkLocation
                } else {
                    Logger.w("ImagePicker.ios", "Failed to create secure bookmark or cannot write to ${url.path}")
                    null
                }
                dispatch_async(dispatch_get_main_queue()) {
                    complete(selected)
                }
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            controller.dismissViewControllerAnimated(true, null)
            complete(null)
        }
    }
    delegateRef = delegate
    retainPickerDelegate(delegate)
    picker.delegate = delegate
    runCatching {
        rootViewController.presentViewController(picker, animated = true, completion = null)
    }.onFailure { error ->
        Logger.e("ImagePicker.ios", "Failed to present save-location picker", error)
        complete(null)
    }
}

/**
 * NSURL からセキュアブックマークを作成
 */
private fun createSecureBookmark(url: NSURL): SaveLocation.Bookmark? {
    return memScoped {
        val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        val bookmarkData = url.bookmarkDataWithOptions(
            options = NSURLBookmarkCreationWithSecurityScope,
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
    val sceneWindows = application.connectedScenes.allObjects
        .mapNotNull { it as? UIWindowScene }
        .flatMap { scene ->
            scene.windows.mapNotNull { it as? UIWindow }
        }
    val sceneKeyWindow = sceneWindows.firstOrNull { it.isKeyWindow } ?: sceneWindows.firstOrNull()
    sceneKeyWindow?.rootViewController?.let { return it }

    val keyWindow = application.windows.firstOrNull { it.isKeyWindow } ?: application.windows.firstOrNull()
    return keyWindow?.rootViewController
}

private fun resolveFileSizeBytes(url: NSURL): Long? {
    val path = url.path ?: return null
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return null
    val fileSize = attributes[NSFileSize] as? NSNumber ?: return null
    return fileSize.longLongValue
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
                        options = NSURLBookmarkResolutionWithSecurityScope,
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
