package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun RequestStoragePermission(
    onPermissionResult: (Boolean) -> Unit
) {
    LaunchedEffect(Unit) {
        onPermissionResult(true)
    }
}
