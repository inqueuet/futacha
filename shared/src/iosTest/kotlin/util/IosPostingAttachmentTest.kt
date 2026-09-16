@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import platform.UIKit.UIImage
import platform.UniformTypeIdentifiers.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class IosPostingAttachmentTest {
    @Test fun pickerHonorsBothModesLimitsForImagesAndVideos() {
        for (extension in listOf("jpg", "mp4", "webm")) {
            val path = NSTemporaryDirectory() + NSUUID().UUIDString + "." + extension
            val bytes = ByteArray(MAX_PICKED_IMAGE_BYTES.toInt() + 1) { 7 }
            try {
                assertTrue(bytes.asData().writeToFile(path, atomically = true))
                val url = NSURL.fileURLWithPath(path)
                assertNull(loadPickedMediaFromUrl(url, extension != "jpg", "attachment.$extension"))
                val result = assertNotNull(loadPickedMediaFromUrl(url, extension != "jpg", "attachment.$extension", 32_000_000))
                assertEquals(bytes.size, result.bytes.size)
                assertEquals(7.toByte(), result.bytes.last())
                assertTrue(result.fileName.endsWith(".$extension"))
                assertNull(loadPickedMediaFromUrl(url, true, "video.mp4", bytes.size.toLong() - 1))
            } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
        }
    }

    @Test fun wildcardDocumentsAllowVideoAndBackupDataAlongsideImages() {
        assertEquals(listOf(UTTypeData), documentContentTypesForMimeType("*/*"))
        assertEquals(listOf(UTTypeData), documentContentTypesForMimeType("application/octet-stream"))
        assertTrue(UTTypeMovie in documentContentTypesForMimeType("video/*"))
        assertTrue(UTTypeImage in documentContentTypesForMimeType("image/*"))
    }

    @Test fun heicCameraPhotoBecomesDecodableJpegForBothForms() = runBlocking {
        val result = normalizeIosPostingAttachment(ImageData(Base64.decode(HEIC), "camera.HEIC"), 8_192_000)
        assertEquals("camera.jpg", result.fileName)
        assertEquals(0xff.toByte(), result.bytes[0])
        assertEquals(0xd8.toByte(), result.bytes[1])
        assertNotNull(UIImage.imageWithData(result.bytes.asData()))
        Unit
    }

    @Test fun quickTimeCameraVideoBecomesRealMp4ForBothForms() = runBlocking {
        val result = normalizeIosPostingAttachment(ImageData(Base64.decode(MOV), "camera.MOV"), 8_192_000)
        assertEquals("camera.mp4", result.fileName)
        assertTrue(result.bytes.size > 100)
        assertEquals("ftyp", result.bytes.copyOfRange(4, 8).decodeToString())
        assertTrue(result.bytes.copyOfRange(8, 32).decodeToString().contains("mp4"))
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4"
        try {
            assertTrue(result.bytes.asData().writeToFile(path, atomically = true))
            val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path), options = null)
            assertTrue(CMTimeGetSeconds(asset.duration) >= 0.9, "Export must preserve the whole video")
        } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    }

    @Test fun supportedFormatsKeepTheirOriginalBytesAndNames() = runBlocking {
        for (name in listOf("photo.jpg", "animation.gif", "photo.png", "photo.webp", "video.mp4", "video.webm", "backup.json")) {
            val original = ImageData(byteArrayOf(1, 2, 3), name)
            assertSame(original, normalizeIosPostingAttachment(original, 32_000_000))
        }
    }

    @Test fun unreadableCameraFormatsFailInsteadOfReturningAnAttachment() = runBlocking {
        for (name in listOf("broken.heic", "broken.mov")) {
            assertFails { normalizeIosPostingAttachment(ImageData(byteArrayOf(1), name), 1024) }
        }
    }

    private fun ByteArray.asData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

    // Locally generated solid-colour fixtures: 32px HEIC and one-second 64px H.264 MOV.
    private companion object {
        const val HEIC = "AAAAJGZ0eXBoZWljAAAAAG1pZjFNaVBybWlhZk1pSEJoZWljAAABhm1ldGEAAAAAAAAAIWhkbHIAAAAAAAAAAHBpY3QAAAAAAAAAAAAAAAAAAAAAJGRpbmYAAAAcZHJlZgAAAAAAAAABAAAADHVybCAAAAABAAAADnBpdG0AAAAAAAEAAAAjaWluZgAAAAAAAQAAABVpbmZlAgAAAAABAABodmMxAAAAAOZpcHJwAAAAxWlwY28AAAATY29scm5jbHgAAgACAAaAAAAADGNsbGkAywBAAAAAFGlzcGUAAAAAAAAAIAAAACAAAAAJaXJvdAAAAAAQcGl4aQAAAAADCAgIAAAAcWh2Y0MBA3AAAACwAAAAAAAe8AD8/fj4AAALA6AAAQAXQAEMAf//A3AAAAMAsAAAAwAAAwAecCShAAEAI0IBAQNwAAADALAAAAMAAAMAHqAUIEHAgwjiHuRZVNwICBgCogABAAlEAcBhcshAUyQAAAAZaXBtYQAAAAAAAAABAAEGgQIDBYaEAAAAHmlsb2MAAAAARAAAAQABAAAAAQAAAboAAABIAAAAAW1kYXQAAAAAAAAAWAAAAEQoAa+i9kaBfP/1Lnr/hr//9mqPVf+gH//rHyf7hQ90yyZ/oD6DYFjg15fyqcyZA72hHwBIF66Mq5DNDMObj1m3qwSPwA=="
        const val MOV = "AAAAFGZ0eXBxdCAgAAAAAHF0ICAAAAAId2lkZQAAAT5tZGF0AAAAOgYFMkdWStxcTEM/lO/FETzRQ6gBAAADAAEDAAADAAECAAHmAAsAAAMAAAMAAAa4DAOJKAEN/////4AAAAAxJbggH4AuSqwRNmYXSACJwyG5akafRwrPDoFqVCtjHBP+QvRWhyAAGk1PzfAEsEedgAAAABEh4RBfAoAvQrFXFN4ACQ7CtgAAABEhqIKEv0lLBV6Lr6YAAnQS8gAAABEBqMGP/1MMYpw6NiqACkmiKAAAABEBqMOL/0zRYL/Qy0CACQ2rvAAAABQh4yGiIn8AkcxCiD2aBhiAC5sNPgAAABEhqQaETzv3gItG4vwgAuXJYgAAABEBqUWP/1MMYpw6NiqACkmiKAAAABEBqUeP/1MMYpw6NiqACkmiKAAAABQh5SWiIn8AkcxCiD2aBhiAC5sNPgAAA9htb292AAAAbG12aGQAAAAA5s/qfebP6n0AAAJYAAACWAABAAABAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAADZHRyYWsAAABcdGtoZAAAAA/mz+p95s/qfQAAAAEAAAAAAAACWAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAQAAAAEAAAAAAAER0YXB0AAAAFGNsZWYAAAAAAEAAAABAAAAAAAAUcHJvZgAAAAAAQAAAAEAAAAAAABRlbm9mAAAAAABAAAAAQAAAAAAAJGVkdHMAAAAcZWxzdAAAAAAAAAABAAACWAAAAAAAAQAAAAACmG1kaWEAAAAgbWRoZAAAAADmz+p95s/qfQAAAlgAAAJYVcQAAAAAADFoZGxyAAAAAG1obHJ2aWRlYXBwbAAAAAAAAAAAEENvcmUgTWVkaWEgVmlkZW8AAAI/bWluZgAAABR2bWhkAAAAAQBAgACAAIAAAAAAOGhkbHIAAAAAZGhscmFsaXNhcHBsAAAAAAAAAAAXQ29yZSBNZWRpYSBEYXRhIEhhbmRsZXIAAAAkZGluZgAAABxkcmVmAAAAAAAAAAEAAAAMYWxpcwAAAAEAAAHHc3RibAAAAJFzdHNkAAAAAAAAAAEAAACBYXZjMQAAAAAAAAABAAAAAAAAAAAAAAIAAAACAABAAEAASAAAAEgAAAAAAAAAAQVILjI2NAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABj//wAAACdhdmNDAWQAC//hAAwnZAALrFZQw3gQYRQBAAQo7jyw/fj4AAAAAAAAAAAYc3R0cwAAAAAAAAABAAAACgAAADwAAABgY3R0cwAAAAAAAAAKAAAAAQAAAAAAAAABAAAAtAAAAAEAAAAAAAAAAf///4gAAAAB////xAAAAAEAAAC0AAAAAQAAAAAAAAAB////iAAAAAH////EAAAAAQAAAAAAAAAgY3NsZwAAAAAAAAB4////iAAAALQAAAAAAAACWAAAABRzdHNzAAAAAAAAAAEAAAABAAAAFnNkdHAAAAAAIBAQGBgQEBgYEAAAABxzdHNjAAAAAAAAAAEAAAABAAAACgAAAAEAAAA8c3RzegAAAAAAAAAAAAAACgAAAHMAAAAVAAAAFQAAABUAAAAVAAAAGAAAABUAAAAVAAAAFQAAABgAAAAUc3RjbwAAAAAAAAABAAAAJA=="
    }
}
