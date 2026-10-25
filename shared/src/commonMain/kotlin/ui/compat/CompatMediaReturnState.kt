package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.lazy.grid.LazyGridState
import com.valoser.futacha.shared.compat.CompatHost

/** Settings/help/editor keep the same media visit; leaving it starts a new launch position. */
internal tailrec fun compatMediaReturnOrigin(host: CompatHost): CompatHost? = when (host) {
    is CompatHost.Gallery, is CompatHost.Viewer -> host
    is CompatHost.Settings -> compatMediaReturnOrigin(host.origin)
    is CompatHost.Help -> compatMediaReturnOrigin(host.origin)
    is CompatHost.ToolbarEditor -> compatMediaReturnOrigin(host.origin)
    is CompatHost.ChangeLog -> compatMediaReturnOrigin(host.origin)
    is CompatHost.License -> compatMediaReturnOrigin(host.origin)
    else -> null
}

internal class CompatViewerReturnPosition {
    var index: Int? = null
        private set
    var mediaIdentity: String? = null
        private set

    fun update(index: Int, mediaIdentity: String) {
        this.index = index
        this.mediaIdentity = mediaIdentity
    }
}

internal class CompatMediaReturnState {
    val galleryGridState = LazyGridState()
    var galleryInitialPositionApplied = false
    val viewerPosition = CompatViewerReturnPosition()
}
