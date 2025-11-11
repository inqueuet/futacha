package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable

/**
 * ストレージパーミッション要求（プラットフォーム別）
 */
@Composable
expect fun RequestStoragePermission(
    onPermissionResult: (Boolean) -> Unit
)
