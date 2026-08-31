package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.model.EmbeddedHtmlPlacement

@Composable
internal expect fun PlatformEmbeddedHtmlSection(
    snippets: List<EmbeddedHtmlContent>,
    modifier: Modifier = Modifier
)

@Composable
internal fun EmbeddedHtmlSection(
    snippets: List<EmbeddedHtmlContent>,
    placement: EmbeddedHtmlPlacement,
    modifier: Modifier = Modifier
) {
    PlatformEmbeddedHtmlSection(
        snippets = snippets.filter { it.placement == placement },
        modifier = modifier
    )
}
