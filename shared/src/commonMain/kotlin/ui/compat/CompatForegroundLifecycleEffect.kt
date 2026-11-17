package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable

/** Reports whether the compatibility host is actually foreground-visible. */
@Composable
internal expect fun CompatForegroundLifecycleEffect(onForegroundChanged: (Boolean) -> Unit)
