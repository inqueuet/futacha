package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable

@Composable
actual fun rememberCompatShareLauncher(): (
    text: String,
    mimeType: String,
    absoluteFilePath: String?
) -> Unit = { _, _, _ -> }
