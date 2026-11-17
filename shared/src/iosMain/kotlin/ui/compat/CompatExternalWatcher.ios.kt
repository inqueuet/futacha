package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.compat.CompatibilityStore

internal class IosCompatExternalWatcher(store: CompatibilityStore) : CompatExternalWatcher by CompatInternalWatcher(store)

@Composable
internal actual fun rememberCompatExternalWatcher(store: CompatibilityStore): CompatExternalWatcher = remember(store) {
    IosCompatExternalWatcher(store)
}
