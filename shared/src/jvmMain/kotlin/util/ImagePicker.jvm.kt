package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation

actual suspend fun pickImage(): ImageData? = com.valoser.futacha.shared.desktop.chooseDesktopFile("画像を選択")?.let {
    com.valoser.futacha.shared.desktop.readDesktopAttachment(it, 64L * 1024 * 1024)
}

actual suspend fun pickDirectoryPath(): String? = com.valoser.futacha.shared.desktop.chooseDesktopDirectory()?.absolutePath

actual suspend fun pickDirectorySaveLocation(): SaveLocation? = pickDirectoryPath()?.let { SaveLocation.Path(it) }
