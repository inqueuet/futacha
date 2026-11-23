package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ui.compat.COMPAT_SAIDANE_NONZERO_LABEL
import com.valoser.futacha.shared.ui.compat.COMPAT_SAIDANE_NONZERO_RIGHT_LABEL
import com.valoser.futacha.shared.ui.compat.compatPreferenceOptions
import com.valoser.futacha.shared.ui.compat.compatPreferenceDisplayValue
import com.valoser.futacha.shared.ui.compat.compatPreferenceStoredValue
import kotlin.test.Test
import kotlin.test.assertEquals

/** may 1449174385: ふたちゃ ignored the "(右寄せ)" そうだね variants. */
class FutachaSaidaneDisplayTest {
    @Test
    fun rightVariantsAreRightAligned() {
        assertEquals(FutachaSaidaneDisplay("そうだねx3", true), futachaSaidaneDisplay("show|right", "そうだねx3"))
        assertEquals(FutachaSaidaneDisplay("3", true), futachaSaidaneDisplay("simple|right", "そうだねx3"))
        assertEquals(FutachaSaidaneDisplay("そうだねx3", false), futachaSaidaneDisplay("show", "そうだねx3"))
        assertEquals(FutachaSaidaneDisplay("そうだねx3", false), futachaSaidaneDisplay(null, "そうだねx3"))
    }

    @Test
    fun simpleAndNonZeroHideZero() {
        assertEquals(null, futachaSaidaneDisplay("simple", "+").label)
        assertEquals(null, futachaSaidaneDisplay("simple|right", "+").label)
        assertEquals(false, futachaSaidaneDisplay("simple|right", "+").rightAligned)
        assertEquals(null, futachaSaidaneDisplay("nonzero", "+").label)
        assertEquals("そうだねx2", futachaSaidaneDisplay("nonzero", "そうだねx2").label)
        assertEquals("+", futachaSaidaneDisplay("show", "+").label)
        assertEquals(null, futachaSaidaneDisplay("hide", "そうだねx2").label)
    }

    @Test
    fun theNewOptionRoundTrips() {
        assertEquals("nonzero", compatPreferenceStoredValue("threadHeaderSoudaneDisplay", COMPAT_SAIDANE_NONZERO_LABEL))
        assertEquals(COMPAT_SAIDANE_NONZERO_LABEL, compatPreferenceDisplayValue("threadHeaderSoudaneDisplay", "nonzero"))
    }

    @Test
    fun theRightAlignedNonZeroOptionHidesZeroAndAlignsRight() {
        assertEquals("nonzero|right", compatPreferenceStoredValue("threadHeaderSoudaneDisplay", COMPAT_SAIDANE_NONZERO_RIGHT_LABEL))
        assertEquals(COMPAT_SAIDANE_NONZERO_RIGHT_LABEL, compatPreferenceDisplayValue("threadHeaderSoudaneDisplay", "nonzero|right"))
        assertEquals(FutachaSaidaneDisplay("そうだねx2", true), futachaSaidaneDisplay("nonzero|right", "そうだねx2"))
        assertEquals(FutachaSaidaneDisplay(null, false), futachaSaidaneDisplay("nonzero|right", "+"))
        val options = compatPreferenceOptions("thread",
            com.valoser.futacha.shared.ui.compat.CompatSettingEntry("そうだねの表示方法", "通常", preferenceKey = "threadHeaderSoudaneDisplay"))
        kotlin.test.assertTrue(COMPAT_SAIDANE_NONZERO_RIGHT_LABEL in options, options.toString())
        // としあき(仮) right-aligns only labels ending in "(右寄せ)"; this one must not change it.
        kotlin.test.assertFalse(COMPAT_SAIDANE_NONZERO_RIGHT_LABEL.endsWith("(右寄せ)"))
    }
}
