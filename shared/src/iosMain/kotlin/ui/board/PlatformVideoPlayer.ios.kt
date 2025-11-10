package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "動画再生は未対応です",
            textAlign = TextAlign.Center
        )
    }
}
