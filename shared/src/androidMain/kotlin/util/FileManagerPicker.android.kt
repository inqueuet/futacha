package com.valoser.futacha.shared.util

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * ファイラーアプリの情報
 * @property packageName パッケージ名
 * @property label 表示名
 * @property icon アイコン（nullable）
 */
data class FileManagerApp(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?
)

/**
 * デバイスにインストールされている利用可能なファイラーアプリの一覧を取得
 * @param packageManager PackageManager
 * @return ファイラーアプリのリスト
 */
fun getAvailableFileManagers(packageManager: PackageManager): List<FileManagerApp> {
    // 端末やファイラーによって対応するインテントが異なるため、複数パターンで探索する
    val intents = listOf(
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addCategory(Intent.CATEGORY_DEFAULT) },
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        },
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        },
        Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_FILES) } // 一般的なファイラー向けカテゴリ
    )

    val resolveInfos: List<ResolveInfo> = intents
        .flatMap { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
        }
        .distinctBy { it.activityInfo?.packageName to it.activityInfo?.name }

    return resolveInfos.mapNotNull { resolveInfo ->
        val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
        val packageName = activityInfo.packageName
        val label = resolveInfo.loadLabel(packageManager).toString()
        val icon = resolveInfo.loadIcon(packageManager)

        FileManagerApp(packageName, label, icon)
    }.distinctBy { it.packageName }
}
