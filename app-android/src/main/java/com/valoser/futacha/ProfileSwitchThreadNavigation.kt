package com.valoser.futacha

import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ModeSwitchRecoveryException

/**
 * The durable thread link a mode switch hands to the target profile. The new
 * Activity opens it and registers its board without asking again, so a link
 * may stay stored only while that switch can still complete (M-6): a failed
 * switch removes the link it stored, and a switch without a link removes one
 * left behind earlier, instead of opening it at some later switch.
 * When the failed switch could not be rolled back, its journal stays and the
 * next launch completes the switch to the target (M4-2), so the link is kept
 * for that launch to open.
 */
internal class ProfileSwitchThreadNavigation(
    private val save: (url: String, target: ExperienceProfile) -> Unit,
    private val clear: (expectedUrl: String?) -> Unit
) {
    fun beforeSwitch(threadUrl: String?, target: ExperienceProfile) {
        if (threadUrl != null) save(threadUrl, target) else clear(null)
    }

    fun afterFailedSwitch(threadUrl: String?, failure: Throwable? = null) {
        if (threadUrl != null && !willCompleteOnNextLaunch(failure)) clear(threadUrl)
    }

    /**
     * The coordinator records a failed rollback as suppressed on the failure.
     * A [ModeSwitchRecoveryException] instead means an earlier journal could
     * not be finished; this switch never started, so its link is removed.
     */
    private fun willCompleteOnNextLaunch(failure: Throwable?): Boolean =
        failure != null && failure !is ModeSwitchRecoveryException && failure.suppressed.isNotEmpty()
}
