package com.valoser.futacha.shared.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.util.PermissionHelper

/**
 * ストレージパーミッション要求のComposable
 */
@Composable
actual fun RequestStoragePermission(
    onPermissionResult: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // パーミッションランチャー
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // すべてのパーミッションが付与されたかチェック
        val allGranted = permissions.values.all { it }
        onPermissionResult(allGranted)
    }

    LaunchedEffect(Unit) {
        if (!PermissionHelper.isStoragePermissionRequired()) {
            // Android 13以降はパーミッション不要
            onPermissionResult(true)
        } else if (PermissionHelper.hasStoragePermission(context)) {
            // すでに付与されている
            onPermissionResult(true)
        } else {
            // パーミッション要求
            val permissions = PermissionHelper.getRequiredPermissions()
            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions)
            } else {
                onPermissionResult(true)
            }
        }
    }
}
