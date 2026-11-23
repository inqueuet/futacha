package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

/** may 1449285572: a video can be set to play over and over or to stop after one play, in both modes. */
class CompatViewerVideoEndTest {
    private fun loop(stored: String?, reference: Boolean) = compatViewerBehaviour(
        stored?.let { mapOf("compat.viewer.$COMPAT_VIEWER_VIDEO_END_KEY" to it) }.orEmpty(), reference, false
    ).videoOptions.loop

    @Test fun unsavedKeepsEachModesOwn() {
        assertEquals(true, loop(null, reference = true))
        assertEquals(false, loop(null, reference = false))
        assertEquals(false, loop("", reference = false))
    }

    @Test fun theChoiceActsInBothModes() {
        assertEquals(true, loop("loop", reference = false))
        assertEquals(false, loop("stop", reference = true))
    }

    @Test fun labelsAndStoredValuesRoundTrip() {
        COMPAT_VIEWER_VIDEO_END_OPTIONS.forEach { label ->
            assertEquals(label, compatViewerVideoEndLabel(compatViewerVideoEndStored(label)))
        }
        assertEquals(COMPAT_VIEWER_VIDEO_END_OPTIONS, compatPreferenceOptions("viewer",
            CompatSettingEntry("動画を最後まで再生したら", COMPAT_VIEWER_VIDEO_END_DEFAULT_LABEL, preferenceKey = COMPAT_VIEWER_VIDEO_END_KEY)))
    }
}
