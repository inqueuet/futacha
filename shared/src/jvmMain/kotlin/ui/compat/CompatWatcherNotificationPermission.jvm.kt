package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberCompatWatcherNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit = { onResult(false) }

@Composable
internal actual fun rememberCompatWatcherNotificationSettings(): () -> Result<Unit> = { Result.failure(UnsupportedOperationException("端末の通知設定を開いてください")) }
