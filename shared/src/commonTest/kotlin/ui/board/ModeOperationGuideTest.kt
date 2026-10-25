package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlin.test.Test
import kotlin.test.assertTrue

/** L26: the operation guide names each mode's own switch and the check its del confirmation shows. */
class ModeOperationGuideTest {
    private fun reportNg(profile: ExperienceProfile) = modeOperationGuide(profile).single { it.id == "report-ng" }.steps

    @Test fun theDelAndNgStepNamesTheSwitchOfEachMode() {
        val futaber = reportNg(ExperienceProfile.FUTABER)
        assertTrue("「delの確認でこのレスをNGにも登録」" in futaber, futaber)
        assertTrue("「このスレッドのこのレスをNGにも登録」" in futaber, futaber)
        val ftutter = reportNg(ExperienceProfile.FTUTTER)
        assertTrue("「delとこのレスのNGをまとめる」" in ftutter, ftutter)
        assertTrue("「このスレッドのこのレスをNGにも登録」" in ftutter, ftutter)
        for (profile in listOf(ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT)) {
            assertTrue("「このレスをNGにも登録」" in reportNg(profile), reportNg(profile))
        }
    }

    /** L34: ふたちゃ's write button is "返信", and ふたちゃ・としあき(仮) have no "書き込み設定" for the delete key. */
    @Test fun theWriteAndDeleteKeyStepsUseTheNamesOnScreen() {
        fun steps(profile: ExperienceProfile, id: String) = modeOperationGuide(profile).single { it.id == id }.steps
        assertTrue("「返信」" in steps(ExperienceProfile.FUTACHA, "write"))
        for (profile in listOf(ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT)) {
            val key = steps(profile, "key")
            assertTrue("書き込み設定" !in key && "「削除キー」欄" in key, key)
        }
        assertTrue("「書き込み設定」" in steps(ExperienceProfile.FUTABER, "key"))
    }
}
