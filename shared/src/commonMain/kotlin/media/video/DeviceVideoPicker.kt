package com.valoser.futacha.shared.media.video

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.media.MediaFeaturePermit
import com.valoser.futacha.shared.util.FileSystem

internal data class VideoPickRequest(val fileSystem: FileSystem, val gate: MediaFeatureGate, val permit: MediaFeaturePermit)

internal class DeviceVideoPicker(val launch: (VideoPickRequest) -> Unit, val cancel: () -> Unit)

/** The picker owns the source until the suspending editor callback returns, including cancellation. */
@Composable
internal expect fun rememberDeviceVideoPicker(
    onSelected: suspend (VideoEditSource) -> Unit,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit
): DeviceVideoPicker
