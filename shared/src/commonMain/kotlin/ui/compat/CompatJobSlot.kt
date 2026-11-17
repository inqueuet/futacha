package com.valoser.futacha.shared.ui.compat

import kotlinx.coroutines.Job

/**
 * Main-thread holder for one long-running job that must survive a restart of the
 * effect which started it (e.g. an AI history refresh), so a repeated request can
 * be coalesced instead of running twice or cancelling the first one.
 */
internal class CompatJobSlot {
    var job: Job? = null
    val isActive: Boolean get() = job?.isActive == true
}
