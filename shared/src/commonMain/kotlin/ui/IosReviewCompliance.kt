package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Enables the App Review-specific safety wording only for the iOS host.
 *
 * Android intentionally receives the default value so its existing labels and
 * interaction flow remain unchanged.
 */
@Immutable
data class IosReviewCompliance(
    val isEnabled: Boolean = isIosReviewPlatform()
)

val LocalIosReviewCompliance = staticCompositionLocalOf { IosReviewCompliance() }

internal expect fun isIosReviewPlatform(): Boolean
