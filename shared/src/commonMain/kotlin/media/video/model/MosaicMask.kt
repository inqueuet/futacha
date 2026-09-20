package com.valoser.futacha.shared.media.video.model

import kotlin.math.*
import kotlin.concurrent.Volatile

internal enum class MosaicMaskTool { MOVE, ADD, ERASE }

/** Immutable, bit-packed local silhouette. Contains no source image pixels. */
internal class MosaicMask private constructor(private val bits: ByteArray) {
    val byteSize: Long get() = bits.size.toLong()
    val isEmpty: Boolean get() = bits.all { it == 0.toByte() }
    val coverage: Float get() = bits.sumOf { (it.toInt() and 255).countOneBits() }.toFloat() / (EDGE * EDGE)
    @Volatile private var padded: Pair<Int,MosaicMask>? = null

    fun contains(x: Int, y: Int): Boolean {
        if(x !in 0 until EDGE || y !in 0 until EDGE)return false
        val index=y*EDGE+x
        return bits[index/8].toInt() and (1 shl (index%8)) != 0
    }
    fun contains(u: Float, v: Float): Boolean = u in 0f..1f && v in 0f..1f && contains((u*EDGE).toInt().coerceAtMost(EDGE-1),(v*EDGE).toInt().coerceAtMost(EDGE-1))
    /** Any covered subpixel counts, so downsampling cannot erase a thin contour. */
    fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val x0 = floor(left * EDGE).toInt().coerceIn(0, EDGE)
        val x1 = ceil(right * EDGE).toInt().coerceIn(0, EDGE)
        val y0 = floor(top * EDGE).toInt().coerceIn(0, EDGE)
        val y1 = ceil(bottom * EDGE).toInt().coerceIn(0, EDGE)
        for (y in y0 until y1) for (x in x0 until x1) if (contains(x, y)) return true
        return false
    }
    fun paintWithin(bounds: MosaicBounds, imageWidth: Int, imageHeight: Int, fromX: Float, fromY: Float, toX: Float, toY: Float, brush: Float, erase: Boolean): MosaicMask {
        val left=bounds.centerX-bounds.width/2;val top=bounds.centerY-bounds.height/2
        val radius=minOf(bounds.width*imageWidth,bounds.height*imageHeight)*brush
        return stroke((fromX-left)/bounds.width,(fromY-top)/bounds.height,(toX-left)/bounds.width,(toY-top)/bounds.height,radius/(bounds.width*imageWidth),radius/(bounds.height*imageHeight),erase)
    }

    fun stroke(fromU: Float, fromV: Float, toU: Float, toV: Float, radiusU: Float, radiusV: Float, erase: Boolean): MosaicMask {
        require(listOf(fromU,fromV,toU,toV,radiusU,radiusV).all(Float::isFinite) && radiusU>0 && radiusV>0)
        val result=bits.copyOf()
        val left=floor((minOf(fromU,toU)-radiusU)*EDGE).toInt().coerceIn(0,EDGE)
        val right=ceil((maxOf(fromU,toU)+radiusU)*EDGE).toInt().coerceIn(0,EDGE)
        val top=floor((minOf(fromV,toV)-radiusV)*EDGE).toInt().coerceIn(0,EDGE)
        val bottom=ceil((maxOf(fromV,toV)+radiusV)*EDGE).toInt().coerceIn(0,EDGE)
        val dx=(toU-fromU)/radiusU;val dy=(toV-fromV)/radiusV
        val length=dx*dx+dy*dy
        for(y in top until bottom)for(x in left until right) {
            val px=((x+.5f)/EDGE-fromU)/radiusU;val py=((y+.5f)/EDGE-fromV)/radiusV
            val t=if(length==0f)0f else ((px*dx+py*dy)/length).coerceIn(0f,1f)
            if((px-t*dx).pow(2)+(py-t*dy).pow(2)<=1f) {
                val index=y*EDGE+x;val flag=1 shl (index%8)
                result[index/8]=(if(erase)result[index/8].toInt() and flag.inv() else result[index/8].toInt() or flag).toByte()
            }
        }
        return if(result.contentEquals(bits))this else MosaicMask(result)
    }

    /** Conservative chamfer dilation, cached for the selected margin. */
    fun dilated(radius: Int): MosaicMask {
        require(radius in 0..MAX_MARGIN)
        if(radius==0 || isEmpty)return this
        padded?.takeIf { it.first==radius }?.let { return it.second }
        val distance=IntArray(EDGE*EDGE) { if(contains(it%EDGE,it/EDGE))0 else 10000 }
        for(y in 0 until EDGE)for(x in 0 until EDGE) {
            val i=y*EDGE+x
            if(x>0)distance[i]=minOf(distance[i],distance[i-1]+3)
            if(y>0) {
                distance[i]=minOf(distance[i],distance[i-EDGE]+3)
                if(x>0)distance[i]=minOf(distance[i],distance[i-EDGE-1]+4)
                if(x<EDGE-1)distance[i]=minOf(distance[i],distance[i-EDGE+1]+4)
            }
        }
        for(y in EDGE-1 downTo 0)for(x in EDGE-1 downTo 0) {
            val i=y*EDGE+x
            if(x<EDGE-1)distance[i]=minOf(distance[i],distance[i+1]+3)
            if(y<EDGE-1) {
                distance[i]=minOf(distance[i],distance[i+EDGE]+3)
                if(x>0)distance[i]=minOf(distance[i],distance[i+EDGE-1]+4)
                if(x<EDGE-1)distance[i]=minOf(distance[i],distance[i+EDGE+1]+4)
            }
        }
        return from { x,y -> distance[y*EDGE+x]<=radius*3 }.also { padded=radius to it }
    }

    companion object {
        const val EDGE=128
        const val MAX_MARGIN=16
        const val MAX_SAMPLES=10_000
        val EMPTY=MosaicMask(ByteArray(EDGE*EDGE/8))
        val FULL=MosaicMask(ByteArray(EDGE*EDGE/8) { -1 })
        fun from(contains: (Int,Int)->Boolean): MosaicMask {
            val bits=ByteArray(EDGE*EDGE/8)
            for(y in 0 until EDGE)for(x in 0 until EDGE)if(contains(x,y)) {
                val i=y*EDGE+x;bits[i/8]=(bits[i/8].toInt() or (1 shl(i%8))).toByte()
            }
            return MosaicMask(bits)
        }
    }
}

/** Null restores the original rectangle/ellipse for a frame where segmentation failed. */
internal data class MosaicMaskKeyframe(val timeUs: Long, val mask: MosaicMask?)
