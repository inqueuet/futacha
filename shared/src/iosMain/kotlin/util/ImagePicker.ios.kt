@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS
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
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UIKit.presentationController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIWindowScene
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.*
import platform.FileProvider.NSFileProviderDomain
import platform.FileProvider.NSFileProviderManager
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
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * UIDocumentPicker delegate の保持
 */
private val activePickerDelegates = AtomicReference<List<NSObject>>(emptyList())
private const val DEFAULT_PICKED_VIDEO_FILE_NAME = "video.mov"
private const val MAX_CUSTOM_FONT_BYTES = 16L * 1024L * 1024L
private const val PICKED_MEDIA_LOAD_TIMEOUT_MILLIS = 30_000L
private const val PICKED_MEDIA_READ_CHUNK_BYTES = 64 * 1024
private const val MILLIS_PER_SECOND = 1_000.0

private class ResumeGate {
    private val resumedMarker = Any()
    private val state = AtomicReference<Any?>(null)

    fun tryOpen(): Boolean = state.compareAndSet(null, resumedMarker)
}

private fun retainPickerDelegate(delegate: NSObject) {
    while (true) {
        val current = activePickerDelegates.value
        if (activePickerDelegates.compareAndSet(current, current + delegate)) return
    }
}

private fun releasePickerDelegate(delegate: NSObject?) {
    delegate ?: return
    while (true) {
        val current = activePickerDelegates.value
        val next = current.filterNot { it === delegate }
        if (next.size == current.size || activePickerDelegates.compareAndSet(current, next)) return
    }
}

private fun dismissPresentedPicker(
    picker: UIViewController,
    completion: () -> Unit
) {
    dispatch_async(dispatch_get_main_queue()) {
        if (picker.presentingViewController != null || picker.isBeingPresented()) {
            picker.dismissViewControllerAnimated(true, completion = completion)
        } else {
            completion()
        }
    }
}

private fun isVideoMimeType(mimeType: String): Boolean =
    mimeType.trim().startsWith("video/", ignoreCase = true)

private class PickedMediaLoadTimeout(
    private val timer: NSTimer
) {
    fun cancel() {
        timer.invalidate()
    }
}

private fun schedulePickedMediaLoadTimeout(
    logLabel: String,
    complete: (ImageData?) -> Unit
): PickedMediaLoadTimeout {
    val timer = NSTimer.scheduledTimerWithTimeInterval(
        PICKED_MEDIA_LOAD_TIMEOUT_MILLIS / MILLIS_PER_SECOND,
        repeats = false
    ) {
        Logger.w(
            "ImagePicker.ios",
            "Timed out loading selected $logLabel after ${PICKED_MEDIA_LOAD_TIMEOUT_MILLIS}ms"
        )
        complete(null)
    }
    return PickedMediaLoadTimeout(timer)
}

internal fun documentContentTypesForMimeType(mimeType: String): List<UTType> {
    return if (isVideoMimeType(mimeType)) {
        listOfNotNull(
            UTTypeMovie,
            UTTypeMPEG4Movie,
            UTTypeQuickTimeMovie,
            UTType.typeWithFilenameExtension("webm")
        )
    } else if (mimeType.startsWith("image/", ignoreCase = true)) {
        listOf(
            UTTypeImage,
            UTTypeJPEG,
            UTTypePNG,
            UTTypeGIF,
            UTTypeWebP
        )
    } else {
        // ACTION_GET_CONTENT */* also includes videos and arbitrary documents.
        listOf(UTTypeData)
    }
}

/**
 * Deletes the copies an `asCopy = true` document picker placed in the app's
 * `tmp/<bundle>-Inbox/` (or legacy `Documents/Inbox/`) once they were read.
 * Nothing else is ever deleted, so a user's original (non-copied) file is safe.
 */
internal fun deleteIosDocumentPickerInboxCopies(urls: List<*>) {
    urls.forEach { value ->
        val url = value as? NSURL ?: return@forEach
        if (!isIosDocumentPickerInboxCopy(url)) return@forEach
        val path = url.path ?: return@forEach
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (NSFileManager.defaultManager.fileExistsAtPath(path) &&
                !NSFileManager.defaultManager.removeItemAtPath(path, error.ptr)
            ) {
                Logger.w("ImagePicker.ios", "Failed to delete picker inbox copy: ${error.value?.localizedDescription}")
            }
        }
    }
}

internal fun isIosDocumentPickerInboxCopy(url: NSURL): Boolean {
    if (!url.fileURL) return false
    val path = url.path?.let(::resolvedIosPath) ?: return false
    val temporary = resolvedIosPath(NSTemporaryDirectory()).trimEnd('/')
    if (path.startsWith("$temporary/")) {
        val components = path.removePrefix("$temporary/").split('/')
        return components.size >= 2 && components.first().endsWith("-Inbox") &&
            components.none { it == ".." || it.isEmpty() }
    }
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: return false
    val inbox = resolvedIosPath(documents).trimEnd('/') + "/Inbox/"
    return path.startsWith(inbox) && path.length > inbox.length &&
        path.removePrefix(inbox).split('/').none { it == ".." || it.isEmpty() }
}

private fun resolvedIosPath(path: String): String {
    val standardized = NSString.create(string = path).stringByStandardizingPath
    return NSString.create(string = standardized).stringByResolvingSymlinksInPath
}

internal fun loadPickedMediaFromUrl(
    url: NSURL,
    isVideo: Boolean,
    fallbackFileName: String,
    maxBytes: Long = MAX_PICKED_IMAGE_BYTES
): ImageData? {
    require(maxBytes in 1..Int.MAX_VALUE.toLong()) { "Invalid picker byte limit" }
    val mediaLabel = if (isVideo) "video" else "image"
    val knownFileSize = resolveFileSizeBytes(url)
    if (knownFileSize != null && knownFileSize > maxBytes) {
        Logger.w(
            "ImagePicker.ios",
            "Selected $mediaLabel is too large: ${knownFileSize / 1024}KB (max: ${maxBytes / 1024}KB)"
        )
        return null
    }

    if (knownFileSize != null && knownFileSize > 0L) {
        readPickedMediaBytesFromFileUrl(url, knownFileSize, mediaLabel, maxBytes)?.let { bytes ->
            return buildPickedImageData(bytes, url.lastPathComponent, fallbackFileName, maxBytes)
        }
    }

    readPickedMediaBytesFromUnknownSizeFileUrl(url, mediaLabel, maxBytes)?.let { bytes ->
        return buildPickedImageData(bytes, url.lastPathComponent, fallbackFileName, maxBytes)
    }

    Logger.w("ImagePicker.ios", "Failed to load $mediaLabel from ${url.path}")
    return null
}

private fun readPickedMediaBytesFromFileUrl(
    url: NSURL,
    expectedFileSize: Long,
    mediaLabel: String,
    maxBytes: Long
): ByteArray? {
    val path = url.path ?: return null
    if (expectedFileSize <= 0L || expectedFileSize > maxBytes) return null

    val expectedSize = expectedFileSize.toInt()
    val output = ByteArray(expectedSize)
    val fileHandle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return null
    var totalRead = 0

    try {
        memScoped {
            val readError = alloc<ObjCObjectVar<NSError?>>()
            while (true) {
                // readDataOfLength throws ObjC NSException on I/O errors, which
                // Kotlin/Native cannot catch; use the error-returning variant.
                readError.value = null
                val chunk = fileHandle.readDataUpToLength(PICKED_MEDIA_READ_CHUNK_BYTES.toULong(), error = readError.ptr)
                if (chunk == null) {
                    Logger.w(
                        "ImagePicker.ios",
                        "Failed to read $mediaLabel: ${readError.value?.localizedDescription}"
                    )
                    return null
                }
                val chunkLength = chunk.length.toInt()
                if (chunkLength <= 0) break
                if (totalRead + chunkLength > expectedSize) {
                    Logger.w("ImagePicker.ios", "Selected $mediaLabel exceeded declared file size while reading")
                    return null
                }
                output.usePinned { pinned ->
                    memcpy(pinned.addressOf(totalRead), chunk.bytes, chunk.length)
                }
                totalRead += chunkLength
            }
        }
    } finally {
        memScoped {
            val closeError = alloc<ObjCObjectVar<NSError?>>()
            fileHandle.closeAndReturnError(closeError.ptr)
        }
    }

    if (totalRead <= 0 || totalRead > maxBytes) {
        Logger.w("ImagePicker.ios", "Selected $mediaLabel payload is empty or too large")
        return null
    }
    return if (totalRead == expectedSize) output else output.copyOf(totalRead)
}

private fun readPickedMediaBytesFromUnknownSizeFileUrl(
    url: NSURL,
    mediaLabel: String,
    maxBytes: Long
): ByteArray? {
    val path = url.path ?: return null
    val output = ByteArray(maxBytes.toInt())
    val fileHandle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return null
    var totalRead = 0

    try {
        memScoped {
            val readError = alloc<ObjCObjectVar<NSError?>>()
            while (true) {
                readError.value = null
                val chunk = fileHandle.readDataUpToLength(PICKED_MEDIA_READ_CHUNK_BYTES.toULong(), error = readError.ptr)
                if (chunk == null) {
                    Logger.w(
                        "ImagePicker.ios",
                        "Failed to read $mediaLabel: ${readError.value?.localizedDescription}"
                    )
                    return null
                }
                val chunkLength = chunk.length.toInt()
                if (chunkLength <= 0) break
                if (totalRead + chunkLength > maxBytes) {
                    Logger.w(
                        "ImagePicker.ios",
                        "Selected $mediaLabel is too large: ${(totalRead + chunkLength) / 1024}KB " +
                            "(max: ${maxBytes / 1024}KB)"
                    )
                    return null
                }
                output.usePinned { pinned ->
                    memcpy(pinned.addressOf(totalRead), chunk.bytes, chunk.length)
                }
                totalRead += chunkLength
            }
        }
    } finally {
        memScoped {
            val closeError = alloc<ObjCObjectVar<NSError?>>()
            fileHandle.closeAndReturnError(closeError.ptr)
        }
    }

    if (totalRead <= 0) {
        Logger.w("ImagePicker.ios", "Selected $mediaLabel payload is empty")
        return null
    }
    return output.copyOf(totalRead)
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
suspend fun pickImageFromDocuments(preferredProviderIdentifier: String? = null): ImageData? =
    pickMediaFromDocuments(
        mimeType = "image/*",
        preferredProviderIdentifier = preferredProviderIdentifier
    )

suspend fun pickMediaFromDocuments(
    mimeType: String,
    preferredProviderIdentifier: String? = null,
    maxBytes: Long = MAX_PICKED_IMAGE_BYTES
): ImageData? = suspendCancellableCoroutine { continuation ->
    require(maxBytes in 1..Int.MAX_VALUE.toLong()) { "Invalid picker byte limit" }
    if (getRootViewController() == null) {
        Logger.w("ImagePicker.ios", "Cannot present document media picker: root view controller is unavailable")
        continuation.resumeWithException(IllegalStateException("Cannot present document media picker"))
        return@suspendCancellableCoroutine
    }
    val resumeGate = ResumeGate()
    var delegateRef: NSObject? = null
    var pickerRef: UIDocumentPickerViewController? = null
    var loadTimeout: PickedMediaLoadTimeout? = null
    fun complete(value: ImageData?, failed: Boolean = false) {
        if (!resumeGate.tryOpen()) return
        loadTimeout?.cancel()
        loadTimeout = null
        releasePickerDelegate(delegateRef)
        if (failed) continuation.resumeWithException(IllegalStateException("Could not load selected attachment"))
        else continuation.resume(value)
    }

    val isVideo = isVideoMimeType(mimeType)
    val contentTypes = documentContentTypesForMimeType(mimeType)
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
            complete(null)
        }

        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            controller.dismissViewControllerAnimated(true, null)
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                complete(null)
                return
            }
            loadTimeout = schedulePickedMediaLoadTimeout(
                logLabel = if (isVideo) "video" else "image",
                complete = { complete(it, failed = true) }
            )
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                val selected = try {
                    loadPickedMediaFromUrl(
                        url = url,
                        isVideo = isVideo,
                        fallbackFileName = if (isVideo) DEFAULT_PICKED_VIDEO_FILE_NAME else DEFAULT_PICKED_IMAGE_FILE_NAME,
                        maxBytes = maxBytes
                    )
                } finally {
                    // The bytes (or the rejection) are final: drop the picker's copy.
                    deleteIosDocumentPickerInboxCopies(didPickDocumentsAtURLs)
                }
                dispatch_async(dispatch_get_main_queue()) {
                    complete(selected, failed = selected == null)
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
    continuation.invokeOnCancellation {
        if (resumeGate.tryOpen()) {
            dispatch_async(dispatch_get_main_queue()) {
                loadTimeout?.cancel()
                loadTimeout = null
                pickerRef?.let { picker ->
                    dismissPresentedPicker(picker) { releasePickerDelegate(delegateRef) }
                } ?: releasePickerDelegate(delegateRef)
            }
        }
    }
    resolvePreferredProviderUrl(preferredProviderIdentifier) { preferredUrl ->
        if (!continuation.isActive) return@resolvePreferredProviderUrl
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = contentTypes,
            asCopy = true
        ).apply {
            directoryURL = preferredUrl
            this.delegate = delegate
        }
        pickerRef = picker
        presentPicker(picker, logLabel = "document media picker", onPresented = {
            picker.presentationController?.delegate = delegate
        }) {
            complete(null, failed = true)
        }
    }
}

/**
 * Files.app から TTF / OTF を選択する。
 *
 * UIDocumentPicker の提供するフォント用 UTI は OS バージョンや Files provider
 * によって揺れるため、ここでは data を受け入れ、呼び出し側で拡張子と実データを
 * 検証する。asCopy=true により security-scoped URL を保持しない。
 */
suspend fun pickFontFromDocuments(): ImageData? = suspendCancellableCoroutine { continuation ->
    if (getRootViewController() == null) {
        Logger.w("ImagePicker.ios", "Cannot present custom font picker: root view controller is unavailable")
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val resumeGate = ResumeGate()
    var delegateRef: NSObject? = null
    var loadTimeout: PickedMediaLoadTimeout? = null
    fun complete(value: ImageData?) {
        if (!resumeGate.tryOpen()) return
        loadTimeout?.cancel()
        loadTimeout = null
        releasePickerDelegate(delegateRef)
        continuation.resume(value)
    }

    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            controller.dismissViewControllerAnimated(true, null)
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                complete(null)
                return
            }
            loadTimeout = schedulePickedMediaLoadTimeout(logLabel = "custom font", complete = ::complete)
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                val selected = try {
                    loadPickedFileFromUrl(
                        url = url,
                        maxBytes = MAX_CUSTOM_FONT_BYTES,
                        fileLabel = "custom font",
                        fallbackFileName = "font.ttf"
                    )
                } finally {
                    deleteIosDocumentPickerInboxCopies(didPickDocumentsAtURLs)
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
    continuation.invokeOnCancellation {
        if (resumeGate.tryOpen()) {
            dispatch_async(dispatch_get_main_queue()) {
                loadTimeout?.cancel()
                loadTimeout = null
                releasePickerDelegate(delegateRef)
            }
        }
    }
    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf(UTTypeData),
        asCopy = true
    ).apply {
        this.delegate = delegate
        allowsMultipleSelection = false
    }
    presentPicker(picker, logLabel = "custom font picker") {
        complete(null)
    }
}

private fun loadPickedFileFromUrl(
    url: NSURL,
    maxBytes: Long,
    fileLabel: String,
    fallbackFileName: String
): ImageData? {
    val knownFileSize = resolveFileSizeBytes(url)
    if (knownFileSize != null && (knownFileSize <= 0L || knownFileSize > maxBytes)) {
        Logger.w(
            "ImagePicker.ios",
            "Selected $fileLabel is empty or too large: ${knownFileSize / 1024}KB (max: ${maxBytes / 1024}KB)"
        )
        return null
    }
    val bytes = readPickedFileBytesUpTo(url, maxBytes, fileLabel) ?: return null
    return buildPickedImageData(bytes, url.lastPathComponent, fallbackFileName)
}

private fun readPickedFileBytesUpTo(
    url: NSURL,
    maxBytes: Long,
    fileLabel: String
): ByteArray? {
    val path = url.path ?: return null
    val knownFileSize = resolveFileSizeBytes(url)
    if (knownFileSize != null && (knownFileSize <= 0L || knownFileSize > maxBytes)) return null
    val capacity = (knownFileSize ?: maxBytes).toInt()
    val output = ByteArray(capacity)
    val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return null
    var totalRead = 0
    try {
        memScoped {
            val readError = alloc<ObjCObjectVar<NSError?>>()
            while (true) {
                readError.value = null
                val chunk = handle.readDataUpToLength(PICKED_MEDIA_READ_CHUNK_BYTES.toULong(), error = readError.ptr)
                if (chunk == null) {
                    Logger.w("ImagePicker.ios", "Failed to read $fileLabel: ${readError.value?.localizedDescription}")
                    return null
                }
                val chunkLength = chunk.length.toInt()
                if (chunkLength <= 0) break
                if (totalRead + chunkLength > capacity || totalRead + chunkLength > maxBytes) {
                    Logger.w("ImagePicker.ios", "Selected $fileLabel exceeded its ${maxBytes / 1024}KB limit")
                    return null
                }
                output.usePinned { pinned ->
                    memcpy(pinned.addressOf(totalRead), chunk.bytes, chunk.length)
                }
                totalRead += chunkLength
            }
        }
    } finally {
        memScoped {
            val closeError = alloc<ObjCObjectVar<NSError?>>()
            handle.closeAndReturnError(closeError.ptr)
        }
    }
    if (totalRead <= 0) {
        Logger.w("ImagePicker.ios", "Selected $fileLabel payload is empty")
        return null
    }
    return if (totalRead == output.size) output else output.copyOf(totalRead)
}

suspend fun pickVideo(maxBytes: Long = MAX_PICKED_IMAGE_BYTES): ImageData? = pickFromPhotoLibrary(
    filter = PHPickerFilter.videosFilter,
    typeIdentifier = "public.movie",
    fallbackFileName = DEFAULT_PICKED_VIDEO_FILE_NAME,
    logLabel = "video",
    maxBytes = maxBytes
)

actual suspend fun pickImage(): ImageData? = pickIosImage()

internal suspend fun pickIosImage(maxBytes: Long = MAX_PICKED_IMAGE_BYTES): ImageData? = suspendCancellableCoroutine { continuation ->
    pickFromPhotoLibrary(
        filter = PHPickerFilter.imagesFilter,
        typeIdentifier = "public.image",
        fallbackFileName = DEFAULT_PICKED_IMAGE_FILE_NAME,
        logLabel = "image",
        maxBytes = maxBytes,
        continuation = continuation
    )
}

private fun pickFromPhotoLibrary(
    filter: PHPickerFilter,
    typeIdentifier: String,
    fallbackFileName: String,
    logLabel: String,
    maxBytes: Long,
    continuation: CancellableContinuation<ImageData?>
) {
    require(maxBytes in 1..Int.MAX_VALUE.toLong()) { "Invalid picker byte limit" }
    if (getRootViewController() == null) {
        Logger.w("ImagePicker.ios", "Cannot present photo $logLabel picker: root view controller is unavailable")
        continuation.resumeWithException(IllegalStateException("Cannot present photo picker"))
        return
    }
    val resumeGate = ResumeGate()
    var delegateRef: NSObject? = null
    var loadTimeout: PickedMediaLoadTimeout? = null
    fun complete(value: ImageData?, failed: Boolean = false) {
        if (!resumeGate.tryOpen()) return
        loadTimeout?.cancel()
        loadTimeout = null
        releasePickerDelegate(delegateRef)
        if (failed) continuation.resumeWithException(IllegalStateException("Could not load selected attachment"))
        else continuation.resume(value)
    }

    val config = PHPickerConfiguration().apply {
        selectionLimit = 1
        this.filter = filter
    }

    val picker = PHPickerViewController(configuration = config)

    val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
            complete(null)
        }

        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)

            val results = didFinishPicking.filterIsInstance<PHPickerResult>()
            if (results.isEmpty()) {
                complete(null)
                return
            }

            val result = results.first()
            val itemProvider = result.itemProvider
            val mediaType = if (logLabel == "video") UTTypeMovie else UTTypeImage
            val requestedType = itemProvider.registeredTypeIdentifiers.filterIsInstance<String>()
                .firstOrNull { UTType.typeWithIdentifier(it)?.conformsToType(mediaType) == true }
                ?: typeIdentifier
            val dataFileName = UTType.typeWithIdentifier(requestedType)?.preferredFilenameExtension
                ?.let { "$logLabel.$it" } ?: fallbackFileName
            loadTimeout = schedulePickedMediaLoadTimeout(logLabel = logLabel, complete = { complete(it, failed = true) })

            itemProvider.loadFileRepresentationForTypeIdentifier(requestedType) { url, fileError ->
                if (url != null) {
                    val selected = loadPickedMediaFromUrl(
                        url = url,
                        isVideo = logLabel == "video",
                        fallbackFileName = fallbackFileName,
                        maxBytes = maxBytes
                    )
                    dispatch_async(dispatch_get_main_queue()) {
                        complete(selected, failed = selected == null)
                    }
                    return@loadFileRepresentationForTypeIdentifier
                }

                itemProvider.loadDataRepresentationForTypeIdentifier(requestedType) { data, dataError ->
                    if (dataError != null || data == null) {
                        Logger.w(
                            "ImagePicker.ios",
                            "Failed to load selected $logLabel as file or data: ${fileError?.localizedDescription ?: dataError?.localizedDescription.orEmpty()}"
                        )
                        dispatch_async(dispatch_get_main_queue()) {
                            complete(null, failed = true)
                        }
                        return@loadDataRepresentationForTypeIdentifier
                    }
                    val dataLength = data.length.toLong()
                    if (!isPickedImagePayloadSizeValid(dataLength, maxBytes)) {
                        Logger.w(
                            "ImagePicker.ios",
                            if (dataLength > maxBytes) {
                                "Selected $logLabel is too large: ${dataLength / 1024}KB (max: ${maxBytes / 1024}KB)"
                            } else {
                                "Selected $logLabel payload is empty"
                            }
                        )
                        dispatch_async(dispatch_get_main_queue()) {
                            complete(null, failed = true)
                        }
                        return@loadDataRepresentationForTypeIdentifier
                    }

                    val bytes = ByteArray(dataLength.toInt())
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), data.bytes, data.length)
                    }

                    val selected = buildPickedImageData(bytes, null, dataFileName, maxBytes)
                    dispatch_async(dispatch_get_main_queue()) {
                        complete(selected, failed = selected == null)
                    }
                }
            }
        }
    }

    delegateRef = delegate
    retainPickerDelegate(delegate)
    continuation.invokeOnCancellation {
        if (resumeGate.tryOpen()) {
            dispatch_async(dispatch_get_main_queue()) {
                loadTimeout?.cancel()
                loadTimeout = null
                dismissPresentedPicker(picker) {
                    releasePickerDelegate(delegateRef)
                }
            }
        }
    }
    picker.delegate = delegate

    presentPicker(picker, logLabel = "photo $logLabel picker", onPresented = {
        picker.presentationController?.delegate = delegate
    }) {
        complete(null, failed = true)
    }
}

private suspend fun pickFromPhotoLibrary(
    filter: PHPickerFilter,
    typeIdentifier: String,
    fallbackFileName: String,
    logLabel: String,
    maxBytes: Long
): ImageData? = suspendCancellableCoroutine { continuation ->
    pickFromPhotoLibrary(
        filter = filter,
        typeIdentifier = typeIdentifier,
        fallbackFileName = fallbackFileName,
        logLabel = logLabel,
        maxBytes = maxBytes,
        continuation = continuation
    )
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
actual suspend fun pickDirectoryPath(): String? = suspendCancellableCoroutine { continuation ->
    if (getRootViewController() == null) {
        Logger.w("ImagePicker.ios", "Cannot present directory picker: root view controller is unavailable")
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val resumeGate = ResumeGate()
    var delegateRef: NSObject? = null
    fun complete(value: String?) {
        if (!resumeGate.tryOpen()) return
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
    continuation.invokeOnCancellation {
        if (resumeGate.tryOpen()) {
            releasePickerDelegate(delegateRef)
        }
    }
    picker.delegate = delegate
    presentPicker(picker, logLabel = "directory picker") {
        complete(null)
    }
}

/**
 * iOS ディレクトリピッカーで SaveLocation を選択
 * セキュアブックマークを作成して永続化可能にする
 */
actual suspend fun pickDirectorySaveLocation(): SaveLocation? =
    pickDirectorySaveLocation(preferredProviderIdentifier = null)

suspend fun pickDirectorySaveLocation(preferredProviderIdentifier: String?): SaveLocation? = suspendCancellableCoroutine { continuation ->
    if (getRootViewController() == null) {
        Logger.w("ImagePicker.ios", "Cannot present save-location picker: root view controller is unavailable")
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val resumeGate = ResumeGate()
    var delegateRef: NSObject? = null
    fun complete(value: SaveLocation?) {
        if (!resumeGate.tryOpen()) return
        releasePickerDelegate(delegateRef)
        continuation.resume(value)
    }

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
                    if (selected == null) {
                        presentIosAlert(
                            title = "フォルダを選択できません",
                            message = "選択したフォルダに書き込みできません。Files で別のフォルダを選ぶか、手入力に切り替えてください。"
                        )
                    }
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
    continuation.invokeOnCancellation {
        if (resumeGate.tryOpen()) {
            releasePickerDelegate(delegateRef)
        }
    }
    resolvePreferredProviderUrl(preferredProviderIdentifier) { preferredUrl ->
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf<UTType>(UTTypeFolder),
            asCopy = false
        ).apply {
            directoryURL = preferredUrl
            this.delegate = delegate
        }
        presentPicker(picker, logLabel = "save-location picker") {
            presentIosAlert(
                title = "フォルダ選択を開けません",
                message = "Files のフォルダ選択を開始できませんでした。手入力で保存先を指定してください。"
            )
            complete(null)
        }
    }
}

private fun resolvePreferredProviderUrl(
    providerIdentifier: String?,
    completion: (NSURL?) -> Unit
) {
    if (providerIdentifier.isNullOrBlank()) {
        dispatch_async(dispatch_get_main_queue()) {
            completion(null)
        }
        return
    }
    NSFileProviderManager.getDomainsWithCompletionHandler { domains, _ ->
        val domain = domains
            ?.filterIsInstance<NSFileProviderDomain>()
            ?.firstOrNull { it.identifier == providerIdentifier }
        if (domain == null) {
            dispatch_async(dispatch_get_main_queue()) {
                completion(null)
            }
            return@getDomainsWithCompletionHandler
        }
        val manager = NSFileProviderManager.managerForDomain(domain)
        if (manager == null) {
            dispatch_async(dispatch_get_main_queue()) {
                completion(null)
            }
            return@getDomainsWithCompletionHandler
        }
        runCatching {
            manager.getUserVisibleURLForItemIdentifier("NSFileProviderRootContainerItemIdentifier") { url, _ ->
                dispatch_async(dispatch_get_main_queue()) {
                    completion(url)
                }
            }
        }.onFailure {
            Logger.w(
                "ImagePicker.ios",
                "Failed to resolve preferred provider root for $providerIdentifier; falling back to default picker"
            )
            dispatch_async(dispatch_get_main_queue()) {
                completion(null)
            }
        }
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
        if (bookmarkData.length > MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS.toULong() * 3uL / 4uL) {
            Logger.e("ImagePicker.ios", "Secure bookmark payload is unexpectedly large")
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
    val windows = buildList {
        application.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .forEach { scene ->
                if (scene.activationState == 0L || scene.activationState == 1L) {
                    addAll(scene.windows.filterIsInstance<UIWindow>())
                }
            }
        if (isEmpty()) {
            addAll(application.windows.filterIsInstance<UIWindow>())
        }
    }
    val keyWindow = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
    return keyWindow?.rootViewController
}

/**
 * ルートVCがすでに別のモーダルを present 中でも提示できるよう、
 * presentedViewController チェーンを辿って最前面の VC を返す。
 */
private fun getPresenterViewController(): UIViewController? {
    var presenter = getRootViewController() ?: return null
    while (true) {
        val presented = presenter.presentedViewController ?: break
        if (presented.isBeingDismissed()) break
        presenter = presented
    }
    return presenter
}

/** The active scene's top-most controller for sheets owned outside the picker. */
internal fun currentIosPresentationController(): UIViewController? = getPresenterViewController()

/**
 * ピッカーを最前面の VC から present し、提示できなかった場合は必ず onPresentFailed を呼ぶ。
 *
 * UIKit の presentViewController は「already presenting」等の競合時に例外を投げず
 * 警告ログだけで静かに失敗する。その場合デリゲートが永遠に呼ばれず、ピッカー待ちの
 * コルーチンが再開されないため、present 後に実際に提示されたかを検証する。
 */
internal fun presentPicker(
    picker: UIViewController,
    logLabel: String,
    onPresented: () -> Unit = {},
    onPresentFailed: () -> Unit
) {
    val presenter = getPresenterViewController()
    if (presenter == null) {
        Logger.w("ImagePicker.ios", "Cannot present $logLabel: presenter view controller is unavailable")
        onPresentFailed()
        return
    }
    var acknowledged = false
    var presentationTimeout: NSTimer? = null
    runCatching {
        presenter.presentViewController(picker, animated = true) {
            acknowledged = true
            onPresented()
            presentationTimeout?.invalidate()
            presentationTimeout = null
        }
    }.onFailure { error ->
        Logger.e("ImagePicker.ios", "Failed to present $logLabel", error)
        onPresentFailed()
        return
    }
    if (!acknowledged) {
        // UIKit may defer establishing presentingViewController. Testing it on
        // the next main-queue turn released PHPicker's weak delegate while its
        // UI was visible, breaking both selection and Cancel. Keep the delegate
        // until completion, or a bounded check confirms no presentation exists.
        presentationTimeout = NSTimer.scheduledTimerWithTimeInterval(5.0, repeats = false) {
            presentationTimeout = null
            val attached = picker.presentingViewController != null ||
                presenter.presentedViewController == picker ||
                picker.isBeingPresented() ||
                (picker.isViewLoaded() && picker.view.window != null)
            if (!acknowledged && !attached) {
                Logger.e("ImagePicker.ios", "Presenting $logLabel failed")
                onPresentFailed()
            }
        }
    }
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
    if (base64.isEmpty() || base64.length > MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS) return null
    val bytes = runCatching { Base64.decode(base64) }.getOrNull() ?: return null
    if (bytes.isEmpty()) return null
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}
