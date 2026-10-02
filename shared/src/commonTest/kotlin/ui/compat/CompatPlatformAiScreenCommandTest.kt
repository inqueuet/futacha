package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.buildAiCommandHeldByLockMessage
import com.valoser.futacha.shared.ui.buildAiScreenCommandExpiredMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CompatPlatformAiScreenCommandTest {
    private val readAloud = FutachaAiCommand(FutachaAiAction.StartThreadReadAloud, source = "ios-app-intents")

    private fun unlockedHolder() = FutachaAppLockHolder().apply {
        openSessionWithoutLock()
        setContentVisible(true)
    }

    // E4-1/C4-1: the thread screen gets the command only after the workspace
    // checked its age since the last unlock.
    @Test
    fun slotDeliversOnlyAfterTheCheckAndNeverWhileLocked() = runBlocking {
        val holder = unlockedHolder()
        val slot = CompatAiScreenCommandSlot()
        slot.forward(readAloud)
        assertNull(slot.deliverable(isAppUnlocked = true), "not before the check")
        val supervision = async(start = CoroutineStart.UNDISPATCHED) { slot.supervise(holder) }
        yield()
        assertSame(readAloud, slot.deliverable(isAppUnlocked = true))
        assertNull(slot.deliverable(isAppUnlocked = false))
        slot.consume(readAloud)
        assertNull(slot.pending)
        assertNull(supervision.await(), "a consumed command is not reported as dropped")
    }

    @Test
    fun slotDropsACommandForwardedLongAgo() = runBlocking {
        var now = TimeSource.Monotonic.markNow() - 3_600.seconds
        val slot = CompatAiScreenCommandSlot(now = { now })
        slot.forward(readAloud)
        now = TimeSource.Monotonic.markNow()
        assertEquals(buildAiScreenCommandExpiredMessage(readAloud), slot.supervise(unlockedHolder()))
        assertNull(slot.pending)
        assertNull(slot.deliverable(isAppUnlocked = true))
    }

    @Test
    fun slotDropsACommandHeldBehindTheLockTooLong() = runBlocking {
        val holder = unlockedHolder()
        val slot = CompatAiScreenCommandSlot(now = { TimeSource.Monotonic.markNow() - 70.seconds })
        slot.forward(readAloud)
        holder.lockSession()
        val supervision = async(start = CoroutineStart.UNDISPATCHED) { slot.supervise(holder) }
        yield()
        assertFalse(supervision.isCompleted)
        assertNull(slot.deliverable(isAppUnlocked = true), "never released while locked")
        holder.unlockSession()
        assertEquals(buildAiCommandHeldByLockMessage(readAloud), supervision.await())
        assertNull(slot.pending)
    }

    // C4-4: the same setting gate and exceptions as the modern router.
    @Test
    fun disabledSettingRejectsLinksAndAssistantsButKeepsTheExceptions() {
        val linkThread = FutachaAiCommand(FutachaAiAction.OpenThread, mapOf("url" to "https://may.2chan.net/b/res/1.htm"), "platform")
        assertEquals(COMPAT_AI_COMMANDS_DISABLED_MESSAGE, compatPlatformAiDisabledRejection(linkThread, isAiCommandEnabled = false))
        assertEquals(COMPAT_AI_COMMANDS_DISABLED_MESSAGE, compatPlatformAiDisabledRejection(readAloud, isAiCommandEnabled = false))
        assertNull(compatPlatformAiDisabledRejection(linkThread, isAiCommandEnabled = true))
        assertNull(
            compatPlatformAiDisabledRejection(
                FutachaAiCommand(FutachaAiAction.OpenGlobalSettings, source = "platform"),
                isAiCommandEnabled = false
            )
        )
        assertNull(compatPlatformAiDisabledRejection(linkThread.copy(source = "watchos"), isAiCommandEnabled = false))
        assertNull(
            compatPlatformAiDisabledRejection(
                FutachaAiCommand(FutachaAiAction.StopThreadReadAloud, source = "wear-os"),
                isAiCommandEnabled = false
            )
        )
        // A web page cannot claim to be the watch through the link marker.
        val relayLink = linkThread.copy(parameters = linkThread.parameters + ("relay" to "wear-os"))
        assertEquals(COMPAT_AI_COMMANDS_DISABLED_MESSAGE, compatPlatformAiDisabledRejection(relayLink, isAiCommandEnabled = false))
    }

    // S4-2: the board that will be stored is shown before "続行".
    @Test
    fun addBoardConfirmationShowsTheStoredUrlNameAndLinkOrigin() {
        val message = compatPlatformAiConfirmationMessage(
            FutachaAiCommand(
                FutachaAiAction.AddBoard,
                mapOf("url" to "https://may.2chan.net/b/futaba.htm"),
                source = "platform"
            )
        )
        assertTrue(message.contains("URL: 「https://may.2chan.net/b/」"), message)
        assertTrue(message.contains("板名: 「b」"), message)
        assertTrue(message.contains("外部のリンク"), message)
        // The stored name was empty: the canonical URL ends with '/'.
        assertEquals("b", compatPlatformAiDefaultBoardName("https://may.2chan.net/b/"))
    }

    @Test
    fun watchWordConfirmationShowsTheWord() {
        val message = compatPlatformAiConfirmationMessage(
            FutachaAiCommand(FutachaAiAction.AddWatchWord, mapOf("word" to "実況"), source = "ios")
        )
        assertTrue(message.contains("監視ワード: 「実況」"), message)
    }

    // S4-3/C4-5: any command opening a thread of an unlisted board asks before adding it.
    @Test
    fun linkToAnUnlistedBoardAsksBeforeAddingIt() {
        val listed = com.valoser.futacha.shared.compat.CompatBoard(
            key = "may-b",
            name = "二次元裏",
            canonicalUrl = "https://may.2chan.net/b/",
            originalUrl = "https://may.2chan.net/b/",
            sortOrder = 0
        )
        val unlisted = listed.copy(key = "img-b", canonicalUrl = "https://img.2chan.net/b/")
        val link = FutachaAiCommand(FutachaAiAction.OpenThread, mapOf("url" to "https://img.2chan.net/b/res/1.htm"), "ios")
        assertTrue(compatPlatformAiNeedsBoardConsent(unlisted, listOf(listed)))
        assertFalse(compatPlatformAiNeedsBoardConsent(listed, listOf(listed)))
        assertFalse(compatPlatformAiNeedsBoardConsent(listed.copy(canonicalUrl = "HTTPS://MAY.2CHAN.NET/b/"), listOf(listed)))
        // Siri / Shortcuts and the watch ask as well (C4-5): the board was registered silently.
        val route = resolvePlatformAiThreadRoute(
            link.copy(action = FutachaAiAction.SaveThread, source = "ios-app-intents"),
            com.valoser.futacha.shared.compat.CompatibilityWorkspaceState(),
            listOf(listed)
        )
        val open = kotlin.test.assertIs<CompatPlatformAiThreadRoute.Open>(route)
        assertTrue(compatPlatformAiNeedsBoardConsent(open.board, listOf(listed)))
    }

    // S4-5: a confirmation on screen is not replaced by a later command.
    @Test
    fun laterConfirmationDoesNotReplaceTheOneOnScreen() {
        assertTrue(shouldShowCompatPlatformAiConfirmation(null))
        assertFalse(shouldShowCompatPlatformAiConfirmation(readAloud))
        assertNotNull(readAloud)
    }
}
