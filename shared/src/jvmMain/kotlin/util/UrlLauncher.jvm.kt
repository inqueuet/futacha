package com.valoser.futacha.shared.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberUrlLauncher(): (String) -> Unit {
    return remember {
        { _: String -> Unit }
    }
}
