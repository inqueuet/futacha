package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadPostImageSize

@Composable
internal fun QuotePreviewDialog(
    state: QuotePreviewState,
    onDismiss: () -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onUrlClick: (String) -> Unit,
    onQuoteClick: (QuoteReference) -> Unit,
    bodyTextSize: ThreadBodyTextSize = ThreadBodyTextSize.Standard,
    postImageSize: ThreadPostImageSize = ThreadPostImageSize.Small
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            var visibleLimit by remember(state.quoteText, state.targetPosts) {
                mutableIntStateOf(initialQuotePreviewVisibleLimit(state.targetPosts.size))
            }
            val visiblePosts = state.targetPosts.take(visibleLimit)
            val remainingCount = remainingQuotePreviewTargetCount(
                totalCount = state.targetPosts.size,
                visibleLimit = visibleLimit
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    text = state.quoteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    itemsIndexed(
                        items = visiblePosts,
                        key = { index, post -> quotePreviewPostLazyKey(post, index) }
                    ) { index, post ->
                        ThreadPostCard(
                            post = post,
                            isOp = post.order == 0,
                            posterIdLabel = state.posterIdLabels[post.id],
                            posterIdValue = normalizePosterIdValue(post.posterId),
                            saidaneLabelOverride = null,
                            onQuoteClick = onQuoteClick,
                            onUrlClick = onUrlClick,
                            onSaidaneClick = null,
                            onLongPress = null,
                            onMediaClick = onMediaClick,
                            bodyTextSize = bodyTextSize,
                            postImageSize = postImageSize,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        if (index != visiblePosts.lastIndex || remainingCount > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }
                    }
                    if (remainingCount > 0) {
                        item(key = "quote-preview-more") {
                            TextButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                onClick = {
                                    visibleLimit = nextQuotePreviewVisibleLimit(
                                        currentLimit = visibleLimit,
                                        totalCount = state.targetPosts.size
                                    )
                                }
                            ) {
                                Text("他にも $remainingCount 件あります")
                            }
                        }
                    }
                }
            }
        }
    }
}
