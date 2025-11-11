package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * iOSではストレージパーミッション不要
 */
@Composable
actual fun RequestStoragePermission(
    onPermissionResult: (Boolean) -> Unit
) {
    LaunchedEffect(Unit) {
        // iOSでは常にtrue（パーミッション不要）
        onPermissionResult(true)
    }
}
