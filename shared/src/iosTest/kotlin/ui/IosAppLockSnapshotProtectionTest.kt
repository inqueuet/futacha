@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.state.buildAppLockPasswordHash
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.*
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Runs the production observer/collector, without Compose recomposition, WebKit or an audio session. */
class IosAppLockSnapshotProtectionTest {
    private val configuredHash = buildAppLockPasswordHash("m65-local-password", "m65-native-fixture")
    private val configurationNotification = "com.valoser.futacha.app-lock-snapshot-configuration"
    @Test fun unresolvedConfigurationProtectsAndRealStoreChangesPublishWithoutAComposeFrame() = nativeTest {
        val reads = MutableSharedFlow<String?>(replay = 1)
        val storage = object : BaseInMemoryPlatformStateStorage() { override val appLockPasswordHash = reads }
        val store = AppStateStore(storage)
        val seen = mutableListOf<Boolean>()
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(configurationNotification, null, null) { note ->
            boolean(note?.userInfo?.get("enabled"))?.let { seen += it }
        }
        val protection = IosAppLockSnapshotProtection(store, FutachaAppLockHolder())
        try {
            protection.start()
            assertEquals(listOf(true), seen, "An unread setting must protect the window synchronously")
            reads.emit(null)
            await { seen.lastOrNull() == false }
            reads.emit(configuredHash)
            await { seen.lastOrNull() == true }
            protection.close()
            val closedCount = seen.size
            reads.emit(null)
            delay(30)
            assertEquals(closedCount, seen.size, "Disposal must stop publishing configuration")
        } finally { protection.close(); NSNotificationCenter.defaultCenter.removeObserver(observer); store.close() }
    }

    @Test fun backgroundLocksSynchronouslyButPermissionOrShareInactivityDoesNot() = nativeTest {
        val storage = BaseInMemoryPlatformStateStorage().apply { appLockPasswordHashState.value = configuredHash }
        val store = AppStateStore(storage)
        val lock = FutachaAppLockHolder()
        val protection = IosAppLockSnapshotProtection(store, lock)
        try {
            protection.start()
            delay(30)
            lock.unlockSession(); lock.setContentVisible(true)
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationWillResignActiveNotification, null)
            assertTrue(lock.isUnlocked, "Inactive permission/share transitions must preserve the session")
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidEnterBackgroundNotification, null)
            assertFalse(lock.isUnlocked, "The native notification must close the holder before returning")
            protection.close()
            lock.unlockSession()
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidEnterBackgroundNotification, null)
            assertTrue(lock.isUnlocked, "A disposed Root must not keep its lifecycle observer")
        } finally { protection.close(); store.close() }
    }

    @Test fun lockOffLeavesTheBackgroundSessionOpen() = nativeTest {
        val store = AppStateStore(BaseInMemoryPlatformStateStorage())
        val lock = FutachaAppLockHolder()
        var configured: Boolean? = null
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(configurationNotification, null, null) { note ->
            configured = boolean(note?.userInfo?.get("enabled"))
        }
        val protection = IosAppLockSnapshotProtection(store, lock)
        try {
            protection.start()
            await { configured == false }
            lock.openSessionWithoutLock(); lock.setContentVisible(true)
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidEnterBackgroundNotification, null)
            assertTrue(lock.isUnlocked)
        } finally { protection.close(); NSNotificationCenter.defaultCenter.removeObserver(observer); store.close() }
    }

    private fun boolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is NSNumber -> value.boolValue
        else -> null
    }
    private suspend fun await(condition: () -> Boolean) { withTimeout(5_000) { while (!condition()) delay(5) } }
    private fun nativeTest(block: suspend () -> Unit) {
        val result = MutableStateFlow<Result<Unit>?>(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val job = scope.launch { result.value = runCatching { withTimeout(6_000) { block() } } }
        val started = TimeSource.Monotonic.markNow()
        try {
            while (!job.isCompleted && started.elapsedNow() < 8.seconds) {
                NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.005))
            }
            assertTrue(job.isCompleted, "Native snapshot observer timed out")
            requireNotNull(result.value).getOrThrow()
        } finally { scope.cancel() }
    }
}
