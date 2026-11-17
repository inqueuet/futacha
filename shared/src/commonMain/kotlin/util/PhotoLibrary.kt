package com.valoser.futacha.shared.util

internal expect fun photoLibrarySaveAvailable(): Boolean
internal expect suspend fun requestPhotoLibraryAddPermission()
internal expect suspend fun saveFileToPhotoLibrary(path: String, video: Boolean)
