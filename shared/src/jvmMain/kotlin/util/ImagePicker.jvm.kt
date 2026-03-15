package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation

actual suspend fun pickImage(): ImageData? = null

actual suspend fun pickDirectoryPath(): String? = null

actual suspend fun pickDirectorySaveLocation(): SaveLocation? = null
