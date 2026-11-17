package com.valoser.futacha.shared.ui.compat

/** 参考アプリ 18-20 「レスメニューの大きさ」 (設定 → スレッド画面 → 画面表示). */
internal const val COMPAT_THREAD_CONTEXT_MENU_SCALE_KEY = "threadContextMenuScale"
internal val COMPAT_THREAD_CONTEXT_MENU_SCALE_OPTIONS = listOf("標準", "150%", "175%", "200%")

/** The stored label as a percentage; anything unknown is the standard size. */
internal fun compatPostMenuScalePercent(value: String?): Int =
    value?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it in setOf(150, 175, 200) } ?: 100

internal data class CompatPostMenuSize(val widthDp: Float, val cellHeightDp: Float, val fontScale: Float)

/**
 * 18-20's ThreadContextDialogFragment: the 300dp menu widens by half the extra scale but never past the
 * screen less 16dp each side, rows grow by the full scale, and the labels grow with the cells.
 */
internal fun compatPostMenuSize(scalePercent: Int, screenWidthDp: Float): CompatPostMenuSize {
    val scale = scalePercent / 100f
    if (scalePercent == 100) return CompatPostMenuSize(300f, 50f, 1f)
    val width = (300f * ((scale - 1f) / 2f + 1f)).coerceAtMost(screenWidthDp - 32f).coerceAtLeast(1f)
    val cellHeight = 50f * scale
    // The label may not grow past what the narrower cell or the taller row allows, nor shrink below the standard.
    val fontScale = minOf(width / 300f * 1.4f, scale).coerceAtLeast(1f)
    return CompatPostMenuSize(width, cellHeight, fontScale)
}
