package ui.compat

import com.valoser.futacha.shared.ui.compat.applyCompatMailPreset
import com.valoser.futacha.shared.ui.compat.compatPostMailPresets
import com.valoser.futacha.shared.ui.compat.CompatPostResetFields
import com.valoser.futacha.shared.ui.compat.compatPostResetFields
import com.valoser.futacha.shared.compat.CompatReplyDraft
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatMailPresetTest {
    @Test
    fun buildShowsIdIpAndSageWhileReplyShowsOnlySage() {
        assertEquals(listOf("ID表示", "IP表示", "sage"), compatPostMailPresets(isBuild = true))
        assertEquals(listOf("sage"), compatPostMailPresets(isBuild = false))
    }

    @Test
    fun replyFormKeepsAllThreeMailPresetsFunctional() {
        assertEquals("id表示", applyCompatMailPreset("sage", "ID表示", isBuild = false))
        assertEquals("ip表示", applyCompatMailPreset("id表示", "IP表示", isBuild = false))
        assertEquals("sage", applyCompatMailPreset("foo", "sage", isBuild = false))
        assertEquals("sage", applyCompatMailPreset("foo sage", "sage", isBuild = false))
    }

    @Test
    fun buildFormPreservesLegacyPresetReplacement() {
        assertEquals("id表示", applyCompatMailPreset("foo", "ID表示", isBuild = true))
        assertEquals("ip表示", applyCompatMailPreset("foo", "IP表示", isBuild = true))
        assertEquals("sage", applyCompatMailPreset("foo", "sage", isBuild = true))
    }

    @Test
    fun resetClearsBuildContentButRestoresTheOpeningReplyDraft() {
        val initial = CompatReplyDraft(
            tabKey = "tab",
            name = "name",
            email = "sage",
            subject = "subject",
            comment = "comment",
            deleteKey = "old-key",
            updatedAtEpochMillis = 1L
        )
        assertEquals(
            CompatPostResetFields("", "", "", "", "edited-key"),
            compatPostResetFields(isBuild = true, currentDeleteKey = "edited-key", initialDraft = initial)
        )
        assertEquals(
            CompatPostResetFields("name", "sage", "subject", "comment", "old-key"),
            compatPostResetFields(isBuild = false, currentDeleteKey = "edited-key", initialDraft = initial)
        )
    }
}
