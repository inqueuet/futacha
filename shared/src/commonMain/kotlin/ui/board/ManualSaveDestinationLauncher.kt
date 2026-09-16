package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.ui.isDefaultManualSaveRoot
import com.valoser.futacha.shared.util.isAndroid

/** Keep the requested operation with this picker, including when the same folder is reselected. */
@Composable
internal fun rememberManualSaveDestinationLauncher(
    location: SaveLocation?,
    onSelected: (SaveLocation) -> Unit,
    preferredFileManagerPackage: String? = null
): ((SaveLocation?) -> Unit) -> Unit {
    var selected by remember(location) { mutableStateOf(location) }
    var pending by remember { mutableStateOf<((SaveLocation?) -> Unit)?>(null) }
    val currentOnSelected by rememberUpdatedState(onSelected)
    val picker = rememberDirectoryPickerLauncher(
        onDirectorySelected = { picked ->
            selected = picked
            currentOnSelected(picked)
            val action = pending
            pending = null
            action?.invoke(picked)
        },
        preferredFileManagerPackage = preferredFileManagerPackage
    )
    return { action ->
        if (requiresVisibleManualSaveDestination(isAndroid(), selected)) {
            pending = action
            picker()
        } else {
            action(selected)
        }
    }
}

internal fun requiresVisibleManualSaveDestination(isAndroid: Boolean, location: SaveLocation?): Boolean =
    isAndroid && (location == null || location is SaveLocation.Path && isDefaultManualSaveRoot(location.path))
