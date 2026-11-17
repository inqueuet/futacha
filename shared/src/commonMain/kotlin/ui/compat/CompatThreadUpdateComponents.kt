package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Both browsing modes use the same new-reply presentation. */
@Composable
internal fun CompatNewRepliesDivider(count: Int, modifier: Modifier = Modifier) {
    val palette = LocalCompatibilityPalette.current
    Text(
        text = "新着レス ${count}件",
        modifier = modifier.fillMaxWidth()
            .background(palette.newReplyBackground)
            .padding(vertical = 1.dp),
        color = palette.newReplyContent,
        fontSize = 13.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}
