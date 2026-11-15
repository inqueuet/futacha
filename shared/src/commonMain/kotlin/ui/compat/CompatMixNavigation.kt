package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatHost
import com.valoser.futacha.shared.compat.CompatibilityEvent

/** Only an explicit MIX row opening may create a MAIN thread with a MIX return. */
internal fun compatMixIdAfterNavigation(id: String?, event: CompatibilityEvent, keepMixReturn: Boolean = false): String? =
    when (event) {
        is CompatibilityEvent.OpenCatalog -> null
        is CompatibilityEvent.OpenHost -> if (event.host == CompatHost.Main) null else id
        is CompatibilityEvent.OpenThread -> if (keepMixReturn) id else null
        else -> id
    }
