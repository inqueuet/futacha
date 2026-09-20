package com.valoser.futacha.shared.ui.image

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun promptClipEntry(value: String): ClipEntry = ClipEntry.withPlainText(value)
