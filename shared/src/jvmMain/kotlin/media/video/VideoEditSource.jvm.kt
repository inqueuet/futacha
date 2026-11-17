package com.valoser.futacha.shared.media.video

internal actual suspend fun inspectDeviceVideo(path: String): VideoEditInfo = inspectDesktopVideo(path)
