@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, ExperimentalForeignApi::class)

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
import platform.UIKit.UIWindowScene
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeGIF
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeJPEG
import platform.UniformTypeIdentifiers.UTTypeMPEG4Movie
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.UniformTypeIdentifiers.UTTypePNG
import platform.UniformTypeIdentifiers.UTTypeQuickTimeMovie
import platform.UniformTypeIdentifiers.UTTypeWebP
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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * UIDocumentPicker delegate の保持
 */
private val activePickerDelegates = AtomicReference<List<NSObject>>(emptyList())
private const val DEFAULT_PICKED_VIDEO_FILE_NAME = "video.mov"
private const val PICKED_MEDIA_LOAD_TIMEOUT_MILLIS = 30_000L
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

private fun documentContentTypesForMimeType(mimeType: String): List<UTType> {
    return if (isVideoMimeType(mimeType)) {
        listOf(
            UTTypeMovie,
            UTTypeMPEG4Movie,
            UTTypeQuickTimeMovie
        )
    } else {
        listOf(
            UTTypeImage,
            UTTypeJPEG,
            UTTypePNG,
            UTTypeGIF,
            UTTypeWebP
        )
    }
}

private fun loadPickedMediaFromUrl(
    url: NSURL,
    isVideo: Boolean,
    fallbackFileName: String
): ImageData? {
    val mediaLabel = if (isVideo) "video" else "image"
    val knownFileSize = resolveFileSizeBytes(url)
    if (knownFileSize != null && knownFileSize > MAX_PICKED_IMAGE_BYTES) {
        Logger.w(
            "ImagePicker.ios",
            "Selected $mediaLabel is too large: ${knownFileSize / 1024}KB (max: ${MAX_PICKED_IMAGE_BYTES / 1024}KB)"
        )
        return null
    }

    if (knownFileSize != null && knownFileSize > 0L) {
        readPickedMediaBytesFromFileUrl(url, knownFileSize, mediaLabel)?.let { bytes ->
            return buildPickedImageData(bytes, url.lastPathComponent, fallbackFileName)
        }
    }

    val data = NSData.dataWithContentsOfURL(url)
    if (data == null) {
        Logger.w("ImagePicker.ios", "Failed to load $mediaLabel from ${url.path}")
        return null
    }

    val dataLength = data.length.toLong()
    if (!isPickedImagePayloadSizeValid(dataLength)) {
        Logger.w(
            "ImagePicker.ios",
            if (dataLength > MAX_PICKED_IMAGE_BYTES) {
                "Selected $mediaLabel is too large: ${dataLength / 1024}KB (max: ${MAX_PICKED_IMAGE_BYTES / 1024}KB)"
            } else {
                "Selected $mediaLabel payload is empty"
            }
        )
        return null
    }

    val bytes = ByteArray(dataLength.toInt())
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return buildPickedImageData(bytes, url.lastPathComponent, fallbackFileName)
}

private fun readPickedMediaBytesFromFileUrl(
    url: NSURL,
    expectedFileSize: Long,
    mediaLabel: String
): ByteArray? {
    val path = url.path ?: return null
    if (expectedFileSize <= 0L || expectedFileSize > MAX_PICKED_IMAGE_BYTES) return null

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
                val chunk = fileHandle.readDataUpToLength(64UL * 1024UL, error = readError.ptr)
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

    if (totalRead <= 0 || totalRead > MAX_PICKED_IMAGE_BYTES) {
        Logger.w("ImagePicker.ios", "Selected $mediaLabel payload is empty or too large")
        return null
    }
    return if (totalRead == expectedSize) output else output.copyOf(totalRead)
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
    preferredProviderIdentifier: String? = null
): ImageData? = suspendCancellableCoroutine { continuation ->
    if (getRootViewController() == null) {
        Logger.w("ImagePicker.ios", "Cannot present document media picker: root view controller is unavailable")
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

    val isVideo = isVideoMimeType(mimeType)
    val contentTypes = documentContentTypesForMimeType(mimeType)
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            controller.dismissViewControllerAnimated(true, null)
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                complete(null)
                return
            }
            loadTimeout = schedulePickedMediaLoadTimeout(
                logLabel = if (isVideo) "video" else "image",
                complete = ::complete
            )
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                val selected = loadPickedMediaFromUrl(
                    url = url,
                    isVideo = isVideo,
                    fallbackFileName = if (isVideo) DEFAULT_PICKED_VIDEO_FILE_NAME else DEFAULT_PICKED_IMAGE_FILE_NAME
                )
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
            loadTimeout?.cancel()
            loadTimeout = null
            releasePickerDelegate(delegateRef)
        }
    }
    resolvePreferredProviderUrl(preferredProviderIdentifier) { preferredUrl ->
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = contentTypes,
            asCopy = true
        ).apply {
            directoryURL = preferredUrl
            this.delegate = delegate
        }
        presentPicker(picker, logLabel = "document media picker") {
            complete(null)
        }
    }
}

suspend fun pickVideo(): ImageData? = pickFromPhotoLibrary(
    filter = PHPickerFilter.videosFilter,
    typeIdentifier = "public.movie",
    fallbackFileName = DEFAULT_PICKED_VIDEO_FILE_NAME,
    logLabel = "video"
)

actual suspend fun pickImage(): ImageData? = suspendCancellableCoroutine { continuation ->
    pickFromPhotoLibrary(
        filter = PHPickerFilter.imagesFilter,
        typeIdentifier = "public.image",
        fallbackFileName = DEFAULT_PICKED_IMAGE_FILE_NAME,
        logLabel = "image",
        continuation = continuation
    )
}

private fun pickFromPhotoLibrary(
    filter: PHPickerFilter,
    typeIdentifier: String,
    fallbackFileName: String,
    logLabel: String,
    continuation: CancellableContinuation<ImageData?>
) {
    if (getRootViewController() == null) {
        Logger.w("ImagePicker.ios", "Cannot present photo $logLabel picker: root view controller is unavailable")
        continuation.resume(null)
        return
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

    val config = PHPickerConfiguration().apply {
        selectionLimit = 1
        this.filter = filter
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
            loadTimeout = schedulePickedMediaLoadTimeout(logLabel = logLabel, complete = ::complete)

            itemProvider.loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, fileError ->
                if (url != null) {
                    val selected = loadPickedMediaFromUrl(
                        url = url,
                        isVideo = logLabel == "video",
                        fallbackFileName = fallbackFileName
                    )
                    dispatch_async(dispatch_get_main_queue()) {
                        complete(selected)
                    }
                    return@loadFileRepresentationForTypeIdentifier
                }

                itemProvider.loadDataRepresentationForTypeIdentifier(typeIdentifier) { data, dataError ->
                    if (dataError != null || data == null) {
                        Logger.w(
                            "ImagePicker.ios",
                            "Failed to load selected $logLabel as file or data: ${fileError?.localizedDescription ?: dataError?.localizedDescription.orEmpty()}"
                        )
                        complete(null)
                        return@loadDataRepresentationForTypeIdentifier
                    }
                    val dataLength = data.length.toLong()
                    if (!isPickedImagePayloadSizeValid(dataLength)) {
                        Logger.w(
                            "ImagePicker.ios",
                            if (dataLength > MAX_PICKED_IMAGE_BYTES) {
                                "Selected $logLabel is too large: ${dataLength / 1024}KB (max: ${MAX_PICKED_IMAGE_BYTES / 1024}KB)"
                            } else {
                                "Selected $logLabel payload is empty"
                            }
                        )
                        complete(null)
                        return@loadDataRepresentationForTypeIdentifier
                    }

                    val bytes = ByteArray(dataLength.toInt())
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), data.bytes, data.length)
                    }

                    complete(buildPickedImageData(bytes, null, fallbackFileName))
                }
            }
        }
    }

    delegateRef = delegate
    retainPickerDelegate(delegate)
    continuation.invokeOnCancellation {
        if (resumeGate.tryOpen()) {
            loadTimeout?.cancel()
            loadTimeout = null
            releasePickerDelegate(delegateRef)
        }
    }
    picker.delegate = delegate

    presentPicker(picker, logLabel = "photo $logLabel picker") {
        complete(null)
    }
}

private suspend fun pickFromPhotoLibrary(
    filter: PHPickerFilter,
    typeIdentifier: String,
    fallbackFileName: String,
    logLabel: String
): ImageData? = suspendCancellableCoroutine { continuation ->
    pickFromPhotoLibrary(
        filter = filter,
        typeIdentifier = typeIdentifier,
        fallbackFileName = fallbackFileName,
        logLabel = logLabel,
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

/**
 * ピッカーを最前面の VC から present し、提示できなかった場合は必ず onPresentFailed を呼ぶ。
 *
 * UIKit の presentViewController は「already presenting」等の競合時に例外を投げず
 * 警告ログだけで静かに失敗する。その場合デリゲートが永遠に呼ばれず、ピッカー待ちの
 * コルーチンが再開されないため、present 後に実際に提示されたかを検証する。
 */
private fun presentPicker(
    picker: UIViewController,
    logLabel: String,
    onPresentFailed: () -> Unit
) {
    val presenter = getPresenterViewController()
    if (presenter == null) {
        Logger.w("ImagePicker.ios", "Cannot present $logLabel: presenter view controller is unavailable")
        onPresentFailed()
        return
    }
    runCatching {
        presenter.presentViewController(picker, animated = true, completion = null)
    }.onFailure { error ->
        Logger.e("ImagePicker.ios", "Failed to present $logLabel", error)
        onPresentFailed()
        return
    }
    dispatch_async(dispatch_get_main_queue()) {
        if (picker.presentingViewController == null && !picker.isBeingPresented()) {
            Logger.e("ImagePicker.ios", "Presenting $logLabel silently failed; resuming with null")
            onPresentFailed()
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
    val bytes = runCatching { Base64.decode(base64) }.getOrNull() ?: return null
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}
