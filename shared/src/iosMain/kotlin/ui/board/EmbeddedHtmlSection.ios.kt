package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.EmbeddedHtmlContent

@Composable
internal actual fun PlatformEmbeddedHtmlSection(
    snippets: List<EmbeddedHtmlContent>,
    modifier: Modifier
) {
    // iOS版ではWebViewを使用しないため、何も表示しません
}
