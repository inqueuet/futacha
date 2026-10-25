package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.state.AppStateStore

@Composable internal actual fun PlatformAppLockSnapshotProtection(store: AppStateStore, lock: FutachaAppLockHolder, retryGeneration: Int) = Unit
@Composable internal actual fun Modifier.appLockSnapshotFrameReady(): Modifier = this
internal actual fun appLockSnapshotProtectionNotice(): String? = null
