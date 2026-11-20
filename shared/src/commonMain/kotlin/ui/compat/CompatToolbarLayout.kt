package com.valoser.futacha.shared.ui.compat

/** Buttons keep a 40dp touch slot; bars with more buttons than fit scroll sideways (2026-11-19, aimg seq16). */
internal const val COMPAT_THREAD_TOOLBAR_MIN_ITEM_WIDTH_DP = 40

internal fun compactThreadToolbarItemWidth(width: Float, itemCount: Int): Float =
    maxOf(COMPAT_THREAD_TOOLBAR_MIN_ITEM_WIDTH_DP.toFloat(), width / itemCount.coerceAtLeast(1))
