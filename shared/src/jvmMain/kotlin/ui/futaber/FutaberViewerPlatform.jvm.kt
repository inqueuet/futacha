package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable

/** The desktop has no storage-provider picker here: the export says it could not write (the viewer's own save and share stay). */
@Composable
internal actual fun rememberFutaberFileExporter(): (path: String, fileName: String, onDone: (Boolean) -> Unit) -> Unit =
    { _, _, onDone -> onDone(false) }

/** The desktop window is not turned: nothing to hold. */
@Composable
internal actual fun ApplyFutaberViewerOrientation(portrait: Boolean) = Unit
