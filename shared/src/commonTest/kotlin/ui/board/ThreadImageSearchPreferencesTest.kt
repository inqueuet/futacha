package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadImageSearchPreferencesTest {
    private val png = "https://may.2chan.net/b/src/fixture.png"
    private val gif = "https://may.2chan.net/b/src/fixture.gif"

    @Test fun absentSelectionAndModePreferencePreserveTheExistingThirteenChoices() {
        assertEquals(CompatImageSearchTarget.entries.toList(), threadImageSearchDialogTargets(png, null, null))
    }

    @Test fun modePreferenceCannotReplaceOrAddToAnExplicitSharedSelection() {
        assertEquals(listOf(CompatImageSearchTarget.LENS_FILE, CompatImageSearchTarget.IQDB_URL),
            threadImageSearchDialogTargets(png, "lens.file|iqdb.url", CompatImageSearchTarget.GOOGLE_URL))
    }

    @Test fun supportedPreferredTargetMovesFirstWithoutRemovingOrDuplicatingAnyOtherChoice() {
        val chosen = listOf(CompatImageSearchTarget.GOOGLE_URL, CompatImageSearchTarget.ASCII2D_URL,
            CompatImageSearchTarget.IQDB_URL)
        assertEquals(listOf(chosen[1], chosen[0], chosen[2]), threadImageSearchDialogTargets(
            png, "google.url|ascii2d.url|iqdb.url|ascii2d.url", CompatImageSearchTarget.ASCII2D_URL))
    }

    @Test fun ascii2dPreferenceOnAGifLeavesEveryOtherSupportedChoiceAvailable() {
        val targets = threadImageSearchDialogTargets(gif, null, CompatImageSearchTarget.ASCII2D_URL)
        assertEquals(CompatImageSearchTarget.entries.filter { it != CompatImageSearchTarget.ASCII2D_URL }, targets)
        assertEquals(12, targets.size)
        assertFalse(CompatImageSearchTarget.ASCII2D_URL in targets)
        assertEquals(listOf(CompatImageSearchTarget.LENS_FILE), threadImageSearchDialogTargets(
            gif, "lens.file|ascii2d.url", CompatImageSearchTarget.ASCII2D_URL))
    }

    @Test fun deliberatelyEmptyAndUnsupportedSelectionsStayEmptyAndLegacyChoicesStillMigrate() {
        listOf("", "unknown").forEach { raw ->
            assertTrue(threadImageSearchDialogTargets(png, raw, CompatImageSearchTarget.GOOGLE_URL).isEmpty())
        }
        assertTrue(threadImageSearchDialogTargets(gif, "ascii2d.url", CompatImageSearchTarget.ASCII2D_URL).isEmpty())
        assertEquals(listOf(CompatImageSearchTarget.TINEYE_URL, CompatImageSearchTarget.IQDB_URL),
            threadImageSearchDialogTargets(png, "TinEye Search|IQDB Search", CompatImageSearchTarget.GOOGLE_URL))
        listOf("file:///private/a.png", "content://photos/a.png", "https://may.2chan.net/b/src/a.webm").forEach { url ->
            assertTrue(threadImageSearchDialogTargets(url, null, CompatImageSearchTarget.GOOGLE_URL).isEmpty())
        }
    }
}
