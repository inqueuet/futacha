package com.valoser.futacha.shared.media.video

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.valoser.futacha.shared.media.video.model.MosaicDocument
import com.valoser.futacha.shared.media.video.model.MosaicShape
import com.valoser.futacha.shared.media.video.model.MosaicStyle
import com.valoser.futacha.shared.media.video.model.MosaicMask
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/** Transformer export shader using the common document's upright coordinates and real timestamps. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class MosaicEffect(private val document: AtomicReference<MosaicDocument>, private val timeOffsetUs: Long = 0) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = MosaicShaderProgram(document, useHdr, timeOffsetUs)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class MosaicShaderProgram(private val document: AtomicReference<MosaicDocument>, useHdr: Boolean, private val timeOffsetUs: Long) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VERTEX, fragment())
    private var width = 1
    private var height = 1
    private val atlasEdge=MosaicMask.EDGE*4
    private val atlas=ByteBuffer.allocateDirect(atlasEdge*atlasEdge)
    private val previousMasks=arrayOfNulls<MosaicMask>(MosaicDocument.MAX_REGIONS)
    private val maskTexture=IntArray(1)
    init {
        program.setBufferAttribute("aPosition", GlUtil.getNormalizedCoordinateBounds(), 4)
        GLES20.glGenTextures(1,maskTexture,0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,maskTexture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_ALPHA,atlasEdge,atlasEdge,0,GLES20.GL_ALPHA,GLES20.GL_UNSIGNED_BYTE,atlas)
        GlUtil.checkGlError()
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth; height = inputHeight
        return Size(width, height)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexture", inputTexId, 0)
            program.setSamplerTexIdUniform("uMasks",maskTexture[0],1)
            program.setFloatsUniform("uAspect", floatArrayOf(minOf(width,height).toFloat()/width, minOf(width,height).toFloat()/height))
            val regions = document.get().regions
            var maskChanged=false
            repeat(MosaicDocument.MAX_REGIONS) { i ->
                val region = regions.getOrNull(i)?.takeIf { it.activeAt(presentationTimeUs + timeOffsetUs) }
                val bounds = region?.boundsAt(presentationTimeUs + timeOffsetUs)
                val mask=region?.maskAt(presentationTimeUs + timeOffsetUs)?.dilated(region.maskMargin)
                if(mask!==previousMasks[i]) {
                    previousMasks[i]=mask;maskChanged=true
                    val left=(i%4)*MosaicMask.EDGE;val top=(i/4)*MosaicMask.EDGE
                    for(y in 0 until MosaicMask.EDGE)for(x in 0 until MosaicMask.EDGE)atlas.put((top+y)*atlasEdge+left+x,if(mask?.contains(x,y)==true)(-1).toByte() else 0)
                }
                program.setFloatsUniform("uRect$i", if (bounds == null) floatArrayOf(-2f,-2f,0.01f,0.01f) else floatArrayOf(bounds.centerX,bounds.centerY,bounds.width/2,bounds.height/2))
                program.setFloatUniform("uShape$i", if(mask!=null)2f else if (region?.shape == MosaicShape.ELLIPSE) 1f else 0f)
                program.setFloatUniform("uBlock$i", region?.blockFraction ?: 0f)
                program.setFloatUniform("uDarkness$i", if (region?.style == MosaicStyle.BLACK) 1f else region?.darkness ?: 0f)
            }
            if(maskChanged) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,maskTexture[0]);atlas.position(0)
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,atlasEdge,atlasEdge,GLES20.GL_ALPHA,GLES20.GL_UNSIGNED_BYTE,atlas)
            }
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e, presentationTimeUs) }
    }

    override fun release() { try { super.release() } finally { GLES20.glDeleteTextures(1,maskTexture,0);program.delete() } }

    companion object {
        private const val VERTEX = """
            attribute vec4 aPosition;
            varying vec2 vUv;
            void main() { gl_Position = aPosition; vUv = aPosition.xy * 0.5 + 0.5; }
        """
        private fun fragment(): String = buildString {
            append("precision highp float; varying vec2 vUv; uniform sampler2D uTexture; uniform sampler2D uMasks; uniform vec2 uAspect;\n")
            repeat(MosaicDocument.MAX_REGIONS) { append("uniform vec4 uRect$it; uniform float uShape$it; uniform float uBlock$it; uniform float uDarkness$it;\n") }
            append("void main() { vec2 p = vec2(vUv.x, 1.0-vUv.y); float block = 0.0; float darkness = 0.0;\n")
            repeat(MosaicDocument.MAX_REGIONS) { i ->
                append("{ vec2 d = (p-uRect$i.xy)/uRect$i.zw; bool inside = uShape$i > 0.5 && uShape$i < 1.5 ? dot(d,d)<=1.0 : max(abs(d.x),abs(d.y))<=1.0; ")
                append("if(inside && uShape$i > 1.5) { vec2 cell=min(floor((d*0.5+0.5)*128.0),vec2(127.0)); vec2 maskUv=(vec2(${(i%4)*128}.0,${(i/4)*128}.0)+cell+0.5)/512.0; inside=texture2D(uMasks,maskUv).a>0.5; } ")
                append("if (inside) { block=max(block,uBlock$i); darkness=max(darkness,uDarkness$i); } }\n")
            }
            append("vec2 uv=vUv; if(block>0.0) { vec2 cell=block*uAspect; uv=clamp((floor(uv/cell)+0.5)*cell,vec2(0.0),vec2(1.0)); } vec4 color=texture2D(uTexture,uv); gl_FragColor=vec4(color.rgb*(1.0-darkness),color.a); }")
        }
    }
}
