package com.valoser.futacha.shared.ui.board

/**
 * (L67) The default eight fit from 320dp screens (304dp after the bar's padding) at 38–49dp each;
 * larger saved bars on narrower screens scroll rather than squeezing the 40dp buttons further.
 */
internal const val FUTACHA_THREAD_ACTION_BAR_MIN_ITEM_WIDTH_DP = 36

internal fun futachaThreadActionBarItemWidth(width: Float, itemCount: Int): Float =
    maxOf(FUTACHA_THREAD_ACTION_BAR_MIN_ITEM_WIDTH_DP.toFloat(), width / itemCount.coerceAtLeast(1))
