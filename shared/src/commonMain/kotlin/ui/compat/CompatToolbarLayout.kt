package com.valoser.futacha.shared.ui.compat

/** Thread/catalog defaults fit at 25–37dp on phone widths; larger saved toolbars scroll without shrinking their icons. */
internal const val COMPAT_THREAD_TOOLBAR_MIN_ITEM_WIDTH_DP = 24

internal fun compactThreadToolbarItemWidth(width: Float, itemCount: Int): Float =
    maxOf(COMPAT_THREAD_TOOLBAR_MIN_ITEM_WIDTH_DP.toFloat(), width / itemCount.coerceAtLeast(1))
