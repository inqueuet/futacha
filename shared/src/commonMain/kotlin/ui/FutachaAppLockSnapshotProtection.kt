package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.state.AppStateStore

/** Observe configuration outside recomposition; OS snapshots must not wait for another Compose frame. */
@Composable
internal expect fun PlatformAppLockSnapshotProtection(store: AppStateStore, lock: FutachaAppLockHolder, retryGeneration: Int = 0)

/** A draw acknowledgement for the native foreground cover; layout/measurement alone is insufficient. */
@Composable
internal expect fun Modifier.appLockSnapshotFrameReady(): Modifier

internal expect fun appLockSnapshotProtectionNotice(): String?

internal fun appLockSettingDescription(enabled: Boolean): String =
    (if (enabled) "有効です。アプリ起動時にパスワードを要求します。" else "無効です。パスワードを設定すると起動時にロックします。") +
        appLockSnapshotProtectionNotice()?.let { "\n$it" }.orEmpty()
