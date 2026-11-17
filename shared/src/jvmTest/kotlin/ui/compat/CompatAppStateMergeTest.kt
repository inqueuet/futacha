package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_WATCH_INTERVAL_MILLIS
import com.valoser.futacha.shared.compat.COMPAT_WATCH_RULES_KEY
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatWatchRule
import com.valoser.futacha.shared.compat.CompatibilityEvent
import com.valoser.futacha.shared.compat.CompatibilityWorkspaceState
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.compatForegroundLastCheckStoredValue
import com.valoser.futacha.shared.compat.mergeCompatReopenedTab
import com.valoser.futacha.shared.compat.reduceCompatibilityWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatAppStateMergeTest {
    private fun tab(key: String, title: String = "No.$key") = CompatTab(
        key = key,
        canonicalUrl = "https://may.2chan.net/b/res/$key.htm",
        originalUrl = "https://may.2chan.net/b/res/$key.htm",
        boardKey = "may-b",
        boardName = "虹裏",
        threadNo = key,
        title = title,
        insertedAtEpochMillis = 1L,
        contentUpdatedAtEpochMillis = 1L
    )

    @Test
    fun reopeningAnOpenTabKeepsItsStoredState() {
        val existing = tab("1", "猫スレ").copy(
            favorite = true,
            checkedReplyCount = 40,
            replyCount = 50,
            isDead = true,
            isOld = true,
            isDeleted = true,
            snapshotRevision = 77L,
            insertedAtEpochMillis = 5L,
            scrollAnchor = ScrollAnchor(postNo = "9", offsetPx = 3, fallbackIndex = 2)
        )
        val candidate = tab("1", "No.1").copy(replyCount = 45, thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg")
        val merged = mergeCompatReopenedTab(existing, candidate)
        assertTrue(merged.favorite)
        assertEquals(40, merged.checkedReplyCount)
        assertEquals(50, merged.replyCount)
        assertTrue(merged.isDead && merged.isOld && merged.isDeleted)
        assertEquals(77L, merged.snapshotRevision)
        assertEquals(5L, merged.insertedAtEpochMillis)
        assertEquals(existing.scrollAnchor, merged.scrollAnchor)
        assertEquals("猫スレ", merged.title)
        assertEquals(candidate.thumbnailUrl, merged.thumbnailUrl)
        assertEquals(candidate, mergeCompatReopenedTab(null, candidate))
        // A placeholder title is replaced by the history entry's real title.
        assertEquals("猫スレ", mergeCompatReopenedTab(tab("1"), tab("1", "猫スレ")).title)
    }

    @Test
    fun pendingOptimisticTabSurvivesAStoreEmissionFromBeforeItsCommit() {
        val old = tab("1")
        val opened = tab("2")
        val state = CompatibilityWorkspaceState(tabs = listOf(opened, old), activeTabKey = opened.key)
        val reduced = reduceCompatibilityWorkspace(
            state,
            CompatibilityEvent.ReplaceTabs(listOf(old), activeTabKey = old.key, pendingTabs = listOf(opened))
        ).state
        assertEquals(listOf(opened.key, old.key), reduced.tabs.map(CompatTab::key))
        assertEquals(opened.key, reduced.activeTabKey)

        // Once committed, the store copy and its active key win again.
        val committed = opened.copy(title = "stored")
        val after = reduceCompatibilityWorkspace(
            reduced,
            CompatibilityEvent.ReplaceTabs(listOf(committed, old), activeTabKey = opened.key, pendingTabs = listOf(opened))
        ).state
        assertEquals("stored", after.tabs.first().title)
        assertEquals(opened.key, after.activeTabKey)

        // Reopening an already stored tab: its store row exists, but the store's
        // active key is still the previous thread until the open commits.
        val reopened = reduceCompatibilityWorkspace(
            CompatibilityWorkspaceState(tabs = listOf(opened, old), activeTabKey = opened.key),
            CompatibilityEvent.ReplaceTabs(listOf(old, opened), activeTabKey = old.key, pendingTabs = listOf(opened))
        ).state
        assertEquals(opened.key, reopened.activeTabKey)

        // Without pending tabs the store remains authoritative.
        val plain = reduceCompatibilityWorkspace(state, CompatibilityEvent.ReplaceTabs(listOf(old), old.key)).state
        assertEquals(listOf(old.key), plain.tabs.map(CompatTab::key))
        assertEquals(old.key, plain.activeTabKey)
    }

    @Test
    fun deadProbeWritesOnlyTheFlagOntoTheCurrentTab() {
        val probed = tab("1").copy(contentUpdatedAtEpochMillis = 10L)
        val current = probed.copy(favorite = true, checkedReplyCount = 8)
        val updated = mergeNotNull(compatDeadTabUpdate(current, probed))
        assertTrue(updated.isDead)
        assertTrue(updated.favorite)
        assertEquals(8, updated.checkedReplyCount)
        assertNull(compatDeadTabUpdate(current.copy(isDead = true), probed))
        // A body fetched while the probe ran proves the thread alive.
        assertNull(compatDeadTabUpdate(current.copy(contentUpdatedAtEpochMillis = 11L), probed))
    }

    @Test
    fun watcherFetchesOnlyBoardsWithApplicableRulesAndUsesThePersistedTime() {
        val boards = listOf(
            CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0),
            CompatBoard("img-b", "虹裏img", "https://img.2chan.net/b/", "https://img.2chan.net/b/", 1)
        )
        val rules = Json.encodeToString(listOf(CompatWatchRule("猫", "may-b"), CompatWatchRule("犬", enabled = false)))
        val preferences = mapOf(COMPAT_WATCH_RULES_KEY to rules)
        assertEquals(listOf("may-b"), compatBoardsWithWatchWords(boards, preferences).map(CompatBoard::key))

        val now = 10_000_000_000_000L
        assertTrue(isCompatForegroundWatchDue(preferences, wifiConnected = true, nowEpochMillis = now))
        val justChecked = preferences + (COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE to compatForegroundLastCheckStoredValue(now))
        assertFalse(isCompatForegroundWatchDue(justChecked, wifiConnected = true, nowEpochMillis = now + 1_000L))
        assertTrue(
            isCompatForegroundWatchDue(justChecked, wifiConnected = true, nowEpochMillis = now + COMPAT_WATCH_INTERVAL_MILLIS)
        )
    }

    @Test
    fun searchJumpIgnoresRecomputedMatchesButFollowsNewQueriesAndClamps() {
        val tracker = CompatSearchJumpTracker()
        assertTrue(tracker.shouldJump("猫", 2, 2)) // restored screen
        assertFalse(tracker.shouldJump("猫", 2, 2)) // refresh recomputed the hits
        assertFalse(tracker.shouldJump("猫", 3, 3)) // next button already scrolled
        assertTrue(tracker.shouldJump("犬", 0, 0)) // new query
        assertTrue(tracker.shouldJump("犬", 5, 1)) // hits disappeared
        tracker.reset()
        assertTrue(tracker.shouldJump("犬", 1, 1))
    }

    @Test
    fun imageSearchTimeoutBecomesAFailureButCallerCancellationPropagates() = runBlocking {
        val result = runCompatImageSearchCall<String> {
            withTimeout(1L) { delay(1_000L) }
            Result.success("unreachable")
        }
        assertIs<CompatImageSearchTimeoutException>(result.exceptionOrNull())
        assertFailsWith<CancellationException> {
            runCompatImageSearchCall<String> { throw CancellationException("caller") }
        }
        Unit
    }

    private fun <T : Any> mergeNotNull(value: T?): T = requireNotNull(value)
}
