@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.*
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.Foundation.NSData
import platform.Foundation.create

/** One GPU pass, identical max block/darkness overlap semantics to the Android shader. */
internal class VideoMosaicKernel {
    private val kernel = requireNotNull(CIKernel.kernelWithString(buildString {
        append("kernel vec4 videoMosaic(sampler image, sampler masks, vec2 size")
        repeat(16) { append(", vec4 r$it, vec4 s$it") }
        append(") { vec2 xy=destCoord(); vec2 p=vec2(xy.x/size.x,1.0-xy.y/size.y); float block=0.0; float darkness=0.0;\n")
        repeat(16) { i ->
            append("{ vec2 d=(p-r$i.xy)/r$i.zw; bool inside=s$i.x>0.5 && s$i.x<1.5 ? dot(d,d)<=1.0 : max(abs(d.x),abs(d.y))<=1.0; ")
            append("if(inside && s$i.x>1.5) { vec2 cell=min(floor((d*0.5+0.5)*128.0),vec2(127.0)); inside=sample(masks,samplerTransform(masks,vec2(${i%4*128}.0,${i/4*128}.0)+cell+0.5)).r>0.5; } ")
            append("if(inside) { block=max(block,s$i.y); darkness=max(darkness,s$i.z); } }\n")
        }
        append("vec2 q=xy; if(block>0.0) { float cell=block*min(size.x,size.y); q=clamp((floor(xy/cell)+0.5)*cell,vec2(0.5),size-vec2(0.5)); } vec4 c=sample(image,samplerTransform(image,q)); return vec4(c.rgb*(1.0-darkness),c.a); }")
    })) { "モザイク描画を初期化できません" }
    private var lastMasks: List<MosaicMask?> = emptyList()
    private var atlas = CIImage.imageWithColor(CIColor.colorWithRed(0.0, 0.0, 0.0, 1.0)).imageByCroppingToRect(CGRectMake(0.0, 0.0, 512.0, 512.0))

    fun apply(input: CIImage, document: MosaicDocument, timeUs: Long): CIImage {
        val size = input.extent.useContents { size.width to size.height }
        val regions = List(16) { document.regions.getOrNull(it)?.takeIf { it.activeAt(timeUs) } }
        val masks = regions.map { r -> r?.maskAt(timeUs)?.dilated(r.maskMargin) }
        if (masks != lastMasks && masks.any { it != null }) {
            val bytes = ByteArray(512 * 512 * 4)
            masks.forEachIndexed { i, mask -> if (mask != null) {
                for (y in 0 until 128) for (x in 0 until 128) {
                    val offset = (((i / 4) * 128 + y) * 512 + (i % 4) * 128 + x) * 4
                    val value = if (mask.contains(x, y)) (-1).toByte() else 0
                    bytes[offset] = value; bytes[offset + 1] = value; bytes[offset + 2] = value; bytes[offset + 3] = -1
                }
            } }
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            atlas = CIImage.imageWithBitmapData(data, 2048uL, CGSizeMake(512.0, 512.0), kCIFormatRGBA8, null)
        }
        lastMasks = masks
        val arguments = mutableListOf<Any>(input, atlas, CIVector.vectorWithX(size.first, size.second))
        regions.forEachIndexed { i, r ->
            val b = r?.boundsAt(timeUs)
            arguments += if (b == null) CIVector.vectorWithX(-2.0, -2.0, .01, .01) else
                CIVector.vectorWithX(b.centerX.toDouble(), b.centerY.toDouble(), b.width / 2.0, b.height / 2.0)
            arguments += CIVector.vectorWithX(if (masks[i] != null) 2.0 else if (r?.shape == MosaicShape.ELLIPSE) 1.0 else 0.0,
                r?.blockFraction?.toDouble() ?: 0.0, if (r?.style == MosaicStyle.BLACK) 1.0 else r?.darkness?.toDouble() ?: 0.0, 0.0)
        }
        return requireNotNull(kernel.applyWithExtent(input.extent, { index, _ -> if (index == 0) input.extent else atlas.extent }, arguments)) { "モザイクを描画できません" }
    }
}
