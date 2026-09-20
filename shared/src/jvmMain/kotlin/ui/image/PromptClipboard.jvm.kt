package com.valoser.futacha.shared.ui.image

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun promptClipEntry(value: String): ClipEntry = ClipEntry(StringSelection(value))
