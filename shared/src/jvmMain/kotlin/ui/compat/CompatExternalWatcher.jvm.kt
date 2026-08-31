package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private object JvmCompatExternalWatcher : CompatExternalWatcher {
    override suspend fun load(): Result<CompatExternalWatcherSnapshot> = Result.success(
        CompatExternalWatcherSnapshot(message = "外部巡回アプリ連携はAndroidのみ対応")
    )

    override suspend fun delete(key: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("外部巡回アプリ連携はAndroidのみ対応")
    )

    override suspend fun deleteAll(): Result<Unit> = Result.failure(
        UnsupportedOperationException("外部巡回アプリ連携はAndroidのみ対応")
    )

    override fun openManager(): Result<Unit> = Result.failure(
        UnsupportedOperationException("外部巡回アプリ連携はAndroidのみ対応")
    )
}

@Composable
internal actual fun rememberCompatExternalWatcher(
    store: com.valoser.futacha.shared.compat.CompatibilityStore
): CompatExternalWatcher = remember {
    JvmCompatExternalWatcher
}
