@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.ui.compat

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.*
import platform.ImageIO.*
import platform.UIKit.UIImage

/** Ask the decoder for bounded pixels before UIKit draws/encodes the image. */
internal fun downsampleIosPostImage(bytes: ByteArray, maximumSide: Int): UIImage? = memScoped {
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
                CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
                CFDictionarySetValue(options, kCGImageSourceShouldCache, kCFBooleanFalse)
                val thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0u, options) ?: return@memScoped null
                try {
                    UIImage.imageWithCGImage(thumbnail)
                } finally {
                    CFRelease(thumbnail)
                }
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
