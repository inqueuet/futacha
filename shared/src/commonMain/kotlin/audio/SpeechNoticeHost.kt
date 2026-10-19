package com.valoser.futacha.shared.audio

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay

@Composable
internal fun SpeechNoticeHost(runtime: SpeechRuntime) {
    val notice by runtime.notice.collectAsState()
    LaunchedEffect(notice) { if (notice != null) { delay(5000); runtime.clearNotice() } }
    notice?.let { message ->
        Popup(alignment = Alignment.BottomCenter) {
            Surface(modifier = Modifier.padding(24.dp), shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.inverseSurface) {
                Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}
