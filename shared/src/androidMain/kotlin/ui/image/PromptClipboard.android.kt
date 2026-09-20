package com.valoser.futacha.shared.ui.image

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun promptClipEntry(value: String): ClipEntry = ClipEntry(ClipData.newPlainText("", value))
