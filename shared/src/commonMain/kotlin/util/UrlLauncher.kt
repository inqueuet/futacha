package com.valoser.futacha.shared.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberUrlLauncher(customTabs: Boolean = false): (String) -> Unit
