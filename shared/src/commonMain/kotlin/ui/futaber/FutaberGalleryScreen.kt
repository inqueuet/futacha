package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.compat.compatMediaIdentity
import com.valoser.futacha.shared.ui.compat.compatUniqueMediaKeys
import com.valoser.futacha.shared.ui.compat.resolveCompatPostPreviewUrl
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

private val GalleryBackground = Color(0xFF000000)
private val GalleryBar = Color(0xFF111111)
private val GalleryText = Color(0xFFFFFFFF)
private const val GALLERY_COLUMNS = 3

/**
 * The thread's images as the original app shows them: always dark, three square columns, and a
 * bar with the settings gear, the count and a close button. A tap opens the viewer on that image.
 * [mediaPosts] is the viewer's own media list, so an index here is the viewer's index.
 */
@Composable
internal fun FutaberGalleryScreen(
    mediaPosts: List<CompatPostSnapshot>,
    gridState: LazyGridState,
    onOpenViewer: (index: Int, postNo: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    PlatformBackHandler(onBack = onClose)
    val keys = androidx.compose.runtime.remember(mediaPosts) { compatUniqueMediaKeys(mediaPosts) }
    Column(Modifier.fillMaxSize().background(GalleryBackground).testTag("futaber-gallery")) {
        Row(
            Modifier.fillMaxWidth().background(GalleryBar).statusBarsPadding().height(FUTABER_TOP_BAR_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("futaber-gallery-settings")) {
                FutaberIcon(Icons.Outlined.Settings, contentDescription = "画像ビューアの設定", tint = GalleryText)
            }
            Text(
                "${mediaPosts.size}枚", color = GalleryText, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("futaber-gallery-count")
            )
            IconButton(onClick = onClose, modifier = Modifier.testTag("futaber-gallery-close")) {
                FutaberIcon(Icons.Outlined.Close, contentDescription = "画像一覧を閉じる", tint = GalleryText)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(GALLERY_COLUMNS),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            itemsIndexed(mediaPosts, key = { index, _ -> keys[index] }) { index, post ->
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).background(GalleryBar)
                        .clickable(onClickLabel = "No.${post.postNo}の画像を開く") { onOpenViewer(index, compatMediaIdentity(post)) }
                        .testTag("futaber-gallery-item")
                ) {
                    CatalogPreviewImage(
                        thumbnailUrl = resolveCompatPostPreviewUrl(post),
                        fullImageUrl = null,
                        targetSizePx = 400,
                        contentDescription = "No.${post.postNo}の画像",
                        modifier = Modifier.fillMaxSize(),
                        fallbackTint = Color(0xFFBBBBBB)
                    )
                }
            }
        }
    }
}
