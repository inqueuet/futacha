@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.Photos.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal actual fun photoLibrarySaveAvailable(): Boolean = true
internal actual suspend fun requestPhotoLibraryAddPermission(): Unit = withContext(Dispatchers.Main) {
    suspendCoroutine { continuation ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            if (status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited) continuation.resume(Unit)
            else continuation.resumeWithException(IllegalStateException("写真への追加が許可されていません。iOSの設定で写真への追加を許可してください。ファイルへの保存も利用できます"))
        }
    }
}
internal actual suspend fun saveFileToPhotoLibrary(path: String, video: Boolean): Unit = suspendCoroutine { continuation ->
    PHPhotoLibrary.sharedPhotoLibrary().performChanges({
        PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(
            if (video) PHAssetResourceTypeVideo else PHAssetResourceTypePhoto,
            fileURL = NSURL.fileURLWithPath(path), options = null
        )
    }) { success, error ->
        if (success) continuation.resume(Unit)
        else continuation.resumeWithException(IllegalStateException("写真に保存できませんでした。対応していない形式はファイルへの保存をご利用ください。${error?.localizedDescription.orEmpty()}"))
    }
}
