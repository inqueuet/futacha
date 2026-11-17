package com.valoser.futacha.shared.media.edit

import coil3.PlatformContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ImageEditCodecTest {
    @Test fun nativePreviewAndJpegPixelsMatch() = runBlocking { ImageEditCodecContract.nativePixelsPreviewAndJpegExport(PlatformContext.INSTANCE) }
    @Test fun allEightExifOrientationsMatch() = runBlocking { ImageEditCodecContract.exifRotationAndMirroringAreAppliedBeforeEditing(PlatformContext.INSTANCE) }
    @Test fun independentSettingsRejectStaleSessions() = runBlocking { ImageEditCodecContract.featureGatesAreIndependentAndOldSessionsCannotExport(PlatformContext.INSTANCE) }
    @Test fun localStaticWebpCanBeEdited() = runBlocking { ImageEditCodecContract.staticWebpFromDeviceIsSupported(PlatformContext.INSTANCE) }
    @Test fun promptOffRetainsMetadataAndEditedPixels() = runBlocking { ImageEditCodecContract.promptOffPreservesTagsAndEditedPixelsWithoutCache(PlatformContext.INSTANCE) }
}
