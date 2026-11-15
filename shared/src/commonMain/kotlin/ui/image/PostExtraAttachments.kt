package com.valoser.futacha.shared.ui.image

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.valoser.futacha.shared.model.PostAttachment

/**
 * The URL to draw for an extra attachment of a post: its thumbnail when the site gave one, else the file itself
 * (only when the file is a still image; a video without a thumbnail has nothing drawable).
 */
internal fun postExtraAttachmentPreviewUrl(attachment: PostAttachment): String? {
    attachment.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { return it }
    val url = attachment.url.takeIf { it.isNotBlank() } ?: return null
    val mime = attachment.mimeType.orEmpty().lowercase()
    if (mime.startsWith("video/")) return null
    val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
    return if (mime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")) {
        url
    } else null
}

/**
 * The 2nd and later files of a post (あいもげ allows up to 3), drawn as small thumbnails under the first one.
 * Futaba posts have no extra attachments, so for them this draws nothing and takes no space.
 *
 * [onClick] receives the attachment's file URL and its position among the extras (0 = the post's 2nd file).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PostExtraAttachments(
    attachments: List<PostAttachment>,
    postKey: String,
    size: Dp,
    onClick: (url: String, extraIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    onLongClick: ((url: String, extraIndex: Int) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (attachments.isEmpty()) return
    val imageLoader = LocalFutachaImageLoader.current
    val context = LocalPlatformContext.current
    FlowRow(
        modifier = modifier.padding(contentPadding).testTag("post-extra-attachments-$postKey"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attachments.forEachIndexed { index, attachment ->
            val previewUrl = postExtraAttachmentPreviewUrl(attachment)
            Box(
                modifier = Modifier
                    .size(size)
                    .background(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.12f))
                    .then(imageModifier)
                    .semantics { contentDescription = "${index + 2}枚目の画像" }
                    .testTag("post-extra-attachment-$postKey-${index + 2}")
                    .combinedClickable(
                        onClick = { onClick(attachment.url, index) },
                        onLongClick = onLongClick?.let { handler -> { handler(attachment.url, index) } }
                    )
            ) {
                if (previewUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(context).data(previewUrl).build(),
                            imageLoader = imageLoader
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(size)
                    )
                } else {
                    androidx.compose.material3.Text(
                        text = "▶",
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                }
            }
        }
    }
}
