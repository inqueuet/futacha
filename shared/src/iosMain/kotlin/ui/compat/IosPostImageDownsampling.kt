@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.ui.compat

import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.*
import platform.CoreGraphics.CGImageRef
import platform.ImageIO.*
import platform.UIKit.UIImage

/** Ask the decoder for bounded pixels before UIKit draws/encodes the image. */
internal fun downsampleIosPostImage(bytes: ByteArray, maximumSide: Int): UIImage? {
    val thumbnail = createIosImageThumbnail(bytes, maximumSide, applyOrientation = true) ?: return null
    return try {
        UIImage.imageWithCGImage(thumbnail)
    } finally {
        CFRelease(thumbnail)
    }
}

/**
 * Decodes at most [maximumSide] pixels on the longer side with ImageIO, never
 * the full image first. With [applyOrientation] false the raw pixel grid is
 * kept (what `UIImage.CGImage` and Android's BitmapFactory expose). The caller
 * releases the returned image with CFRelease.
 */
internal fun createIosImageThumbnail(bytes: ByteArray, maximumSide: Int, applyOrientation: Boolean): CGImageRef? = memScoped {
    if (bytes.isEmpty() || maximumSide <= 0) return@memScoped null
    val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) }
        ?: return@memScoped null
    try {
        val source = CGImageSourceCreateWithData(data, null) ?: return@memScoped null
        try {
            val options = CFDictionaryCreateMutable(null, 0, null, null) ?: return@memScoped null
            val side = alloc<IntVar> { value = maximumSide }
            val number = CFNumberCreate(null, kCFNumberIntType, side.ptr)
            try {
                if (number == null) return@memScoped null
                CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, number)
                CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                CFDictionarySetValue(
                    options,
                    kCGImageSourceCreateThumbnailWithTransform,
                    if (applyOrientation) kCFBooleanTrue else kCFBooleanFalse
                )
                CFDictionarySetValue(options, kCGImageSourceShouldCache, kCFBooleanFalse)
                CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
            } finally {
                if (number != null) CFRelease(number)
                CFRelease(options)
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(data)
    }
}

/** Pixel dimensions of an encoded image as stored (EXIF orientation not applied). */
internal data class IosEncodedImageSize(val width: Double, val height: Double, val orientation: Int) {
    /** The size UIImage reports, i.e. with a 90-degree EXIF orientation applied. */
    val displayWidth: Double get() = if (orientation in 5..8) height else width
    val displayHeight: Double get() = if (orientation in 5..8) width else height
}

/** Reads the image header only; no pixels are decoded. */
internal fun readIosEncodedImageSize(bytes: ByteArray): IosEncodedImageSize? = memScoped {
    if (bytes.isEmpty()) return@memScoped null
    val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) }
        ?: return@memScoped null
    try {
        val source = CGImageSourceCreateWithData(data, null) ?: return@memScoped null
        try {
            val properties = CGImageSourceCopyPropertiesAtIndex(source, 0u, null) ?: return@memScoped null
            try {
                fun number(key: CFStringRef?): Double? {
                    val value = CFDictionaryGetValue(properties, key) ?: return null
                    val out = alloc<DoubleVar>()
                    return if (CFNumberGetValue(value.reinterpret(), kCFNumberDoubleType, out.ptr)) out.value else null
                }
                val width = number(kCGImagePropertyPixelWidth) ?: return@memScoped null
                val height = number(kCGImagePropertyPixelHeight) ?: return@memScoped null
                if (!(width > 0.0 && height > 0.0)) return@memScoped null
                IosEncodedImageSize(width, height, number(kCGImagePropertyOrientation)?.toInt() ?: 1)
            } finally {
                CFRelease(properties)
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(data)
    }
}
