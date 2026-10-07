package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Quote mode: the thread is shown for tapping lines to quote, and what has been written so far
 * waits in a small card at the bottom. Tapping the card goes back to writing.
 */
@Composable
internal fun FutaberQuoteStrip(
    comment: String,
    onBackToWriting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFutaberColors.current
    val shown = comment.trimEnd().lines().takeLast(3).joinToString("\n")
    // The original shows the draft as a flat panel across the bottom, with no frame and no caption.
    Column(
        modifier.fillMaxWidth()
            .background(colors.background)
            .clickable(onClickLabel = "書き込みに戻る", onClick = onBackToWriting)
            .semantics { contentDescription = "下書き。タップすると書き込みに戻ります。" + shown }
            .testTag("futaber-quote-strip")
    ) {
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        Text(
            if (shown.isBlank()) "引用したい行をタップしてください" else shown,
            color = if (shown.isBlank()) colors.meta else colors.body,
            fontSize = 17.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("futaber-quote-strip-text")
        )
    }
}
