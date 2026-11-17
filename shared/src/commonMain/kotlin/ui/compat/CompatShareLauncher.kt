package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable

@Composable
expect fun rememberCompatShareLauncher(): (text: String, mimeType: String, absoluteFilePath: String?) -> Unit
