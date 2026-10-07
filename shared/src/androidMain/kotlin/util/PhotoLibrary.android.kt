package com.valoser.futacha.shared.util
internal actual fun photoLibrarySaveAvailable(): Boolean = false
internal actual suspend fun requestPhotoLibraryAddPermission() { error("写真への保存はこの環境では利用できません") }
internal actual suspend fun saveFileToPhotoLibrary(path: String, video: Boolean) { error("写真への保存はこの環境では利用できません") }
