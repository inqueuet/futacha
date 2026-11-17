package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.desktop.openDesktopUrl
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

@Composable
internal actual fun PlatformEmbeddedHtmlSection(snippets: List<EmbeddedHtmlContent>, modifier: Modifier) {
    if (snippets.isEmpty()) return
    SwingPanel(modifier = modifier, factory = {
        JEditorPane("text/html", snippets.joinToString("\n") { it.html }).apply {
            isEditable = false
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) runCatching { openDesktopUrl(event.url.toString()) }
            }
        }
    })
}
