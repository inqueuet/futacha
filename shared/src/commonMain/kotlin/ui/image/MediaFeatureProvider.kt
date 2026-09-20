package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.staticCompositionLocalOf
import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.media.MediaFeatureSettings

val LocalMediaFeatureSettings = staticCompositionLocalOf { MediaFeatureSettings.Disabled }
val LocalMediaFeatureGate = staticCompositionLocalOf<MediaFeatureGate?> { null }
val LocalPromptContentVisible = staticCompositionLocalOf { true }
val LocalMediaFeatureUpdater = staticCompositionLocalOf<(suspend ((MediaFeatureSettings) -> MediaFeatureSettings) -> Unit)?> { null }
