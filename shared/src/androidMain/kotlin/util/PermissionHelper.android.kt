package com.valoser.futacha.shared.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * ストレージパーミッションヘルパー
 */
object PermissionHelper {

    /**
     * ストレージパーミッションが必要かチェック
     */
    fun isStoragePermissionRequired(): Boolean {
        // Android 13 (API 33) 以降はパーミッション不要
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }

    /**
     * ストレージパーミッションが付与されているかチェック
     */
    fun hasStoragePermission(context: Context): Boolean {
        // Android 13以降は常にtrue
        if (!isStoragePermissionRequired()) {
            return true
        }

        // Android 12以下: WRITE_EXTERNAL_STORAGEをチェック
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 要求すべきパーミッションのリストを取得
     */
    fun getRequiredPermissions(): Array<String> {
        return if (isStoragePermissionRequired()) {
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            emptyArray()
        }
    }
}
