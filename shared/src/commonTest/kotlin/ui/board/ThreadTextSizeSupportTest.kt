package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadPostImageSize
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadTextSizeSupportTest {
    @Test
    fun resolveThreadTextSizeTokens_appliesConfiguredDeltaToBaseStyle() {
        assertEquals(
            ThreadTextSizeTokens(fontSize = 13.sp, lineHeight = 18.sp),
            resolveThreadTextSizeTokens(
                size = ThreadBodyTextSize.Small,
                standardFontSize = 14.sp,
                standardLineHeight = 20.sp,
                fallbackFontSize = 14.sp,
                fallbackLineHeight = 20.sp
            )
        )
        assertEquals(
            ThreadTextSizeTokens(fontSize = 17.sp, lineHeight = 24.sp),
            resolveThreadTextSizeTokens(
                size = ThreadBodyTextSize.Large,
                standardFontSize = 14.sp,
                standardLineHeight = 20.sp,
                fallbackFontSize = 14.sp,
                fallbackLineHeight = 20.sp
            )
        )
        assertEquals(
            ThreadTextSizeTokens(fontSize = 21.sp, lineHeight = 31.sp),
            resolveThreadTextSizeTokens(
                size = ThreadBodyTextSize.ExtraLarge,
                standardFontSize = 16.sp,
                standardLineHeight = 24.sp,
                fallbackFontSize = 16.sp,
                fallbackLineHeight = 24.sp
            )
        )
    }

    @Test
    fun resolveThreadPostThumbnailMaxHeight_matchesConfiguredThumbnailSizes() {
        assertEquals(200.dp, resolveThreadPostThumbnailMaxHeight(ThreadPostImageSize.Small))
        assertEquals(320.dp, resolveThreadPostThumbnailMaxHeight(ThreadPostImageSize.Medium))
        assertEquals(480.dp, resolveThreadPostThumbnailMaxHeight(ThreadPostImageSize.Large))
    }

    @Test
    fun resolveThreadPostThumbnailDisplayBounds_scalesThumbnailUpToSelectedHeight() {
        assertEquals(
            ThreadPostThumbnailDisplayBounds(width = 200.dp, height = 200.dp),
            resolveThreadPostThumbnailDisplayBounds(
                intrinsicWidth = 250f,
                intrinsicHeight = 250f,
                maxWidth = 360.dp,
                maxHeight = 200.dp
            )
        )
        assertEquals(
            ThreadPostThumbnailDisplayBounds(width = 320.dp, height = 320.dp),
            resolveThreadPostThumbnailDisplayBounds(
                intrinsicWidth = 250f,
                intrinsicHeight = 250f,
                maxWidth = 360.dp,
                maxHeight = 320.dp
            )
        )
        assertEquals(
            ThreadPostThumbnailDisplayBounds(width = 360.dp, height = 360.dp),
            resolveThreadPostThumbnailDisplayBounds(
                intrinsicWidth = 250f,
                intrinsicHeight = 250f,
                maxWidth = 360.dp,
                maxHeight = 480.dp
            )
        )
    }
}
