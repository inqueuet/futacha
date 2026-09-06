package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberCompatWatcherNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit

@Composable
internal expect fun rememberCompatWatcherNotificationSettings(): () -> Result<Unit>
