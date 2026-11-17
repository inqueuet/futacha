package com.valoser.futacha.shared.media.edit

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun saveEditedImage(
    fileSystem: FileSystem, location: SaveLocation, image: ImageData, validate: () -> Unit
): String {
    require(image.fileName.matches(Regex("edited-[0-9]+-[a-z0-9]+\\.jpg")))
    val path = "edited_images/${image.fileName}"
    var complete = false
    try {
        validate()
        fileSystem.createDirectory(location, "edited_images").getOrThrow()
        fileSystem.writeBytes(location, path, image.bytes).getOrThrow()
        currentCoroutineContext().ensureActive(); validate()
        complete = true
        return path
    } finally {
        if (!complete) withContext(NonCancellable) { fileSystem.delete(location, path) }
    }
}
