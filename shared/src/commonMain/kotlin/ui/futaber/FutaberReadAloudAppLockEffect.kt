package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.valoser.futacha.shared.ui.LocalFutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked

/** Shared by the two modes that use [FutaberReadAloud]. */
@Composable
internal fun FutaberReadAloudAppLockEffect(reader: FutaberReadAloud) {
    val appUnlocked = LocalFutachaAppUnlocked.current
    LaunchedEffect(appUnlocked) { if (!appUnlocked) reader.stop() }
    val appLock = LocalFutachaAppLockHolder.current
    LaunchedEffect(appLock, reader) {
        // Lifecycle callbacks publish this StateFlow without waiting for a UI frame. Background
        // Android/iOS compositions may still expose appUnlocked=true after the session was locked.
        // Observe the session rather than stopping on every background event: an app with no lock
        // keeps its existing background reading, for both system speech and Voicevox.
        appLock?.sessionUnlocked?.collect { unlocked -> if (!unlocked) reader.stop() }
    }
}
