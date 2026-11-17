package com.valoser.futacha.shared.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutachaAiCommandLinkPolicyTest {
    @BeforeTest
    fun setUp() {
        FutachaAiCommandBridge.drainBufferedCommandsForTest()
    }

    @AfterTest
    fun tearDown() {
        FutachaAiCommandBridge.drainBufferedCommandsForTest()
    }

    @Test
    fun linkSourcedSettingChangesRequireConfirmation() {
        for (source in listOf("platform", "deep-link", "bridge", "ios", "ios-retry", "IOS")) {
            for (action in listOf(
                FutachaAiAction.DisablePrivacyFilter,
                FutachaAiAction.DisableAiPostFilter,
                FutachaAiAction.AddNgWord,
                FutachaAiAction.AddNgHeader,
                FutachaAiAction.AddWatchWord,
                FutachaAiAction.EnableBackgroundRefresh,
                FutachaAiAction.SetCatalogMode
            )) {
                assertTrue(FutachaAiCommand(action, source = source).requiresConfirmation(), "$source $action")
            }
        }
    }

    @Test
    fun assistantAndWatchKeepTheActionRisk() {
        for (source in listOf("android-app-functions", "ios-app-intents", "wear-os", "watchos", "unknown")) {
            assertFalse(FutachaAiCommand(FutachaAiAction.DisablePrivacyFilter, source = source).requiresConfirmation())
            assertTrue(FutachaAiCommand(FutachaAiAction.ClearHistory, source = source).requiresConfirmation())
        }
        // Navigation from a link keeps working without a dialog.
        assertFalse(FutachaAiCommand(FutachaAiAction.OpenBoardList, source = "platform").requiresConfirmation())
        assertFalse(FutachaAiCommand(FutachaAiAction.OpenThreadFromUrl, source = "platform").requiresConfirmation())
    }

    @Test
    fun expiredQueuedCommandIsDroppedOnReceive() = runBlocking<Unit> {
        val stale = FutachaAiCommand(FutachaAiAction.StartThreadReadAloud, source = "wear-os")
        val fresh = FutachaAiCommand(FutachaAiAction.OpenBoardList, source = "wear-os")
        assertTrue(FutachaAiCommandBridge.enqueue(stale, maxAgeMillis = 1L))
        delay(20L)
        assertTrue(FutachaAiCommandBridge.enqueue(fresh, maxAgeMillis = 60_000L))

        val received = withTimeout(1_000L) { FutachaAiCommandBridge.receiveQueued() }

        assertEquals(fresh, received.command)
        assertEquals(60_000L, received.maxAgeMillis)
    }
}
