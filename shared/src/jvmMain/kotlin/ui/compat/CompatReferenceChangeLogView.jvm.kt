package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.event.HyperlinkEvent

@Composable
internal actual fun CompatReferenceChangeLogView(html: String, modifier: Modifier, onLinkClicked: (String) -> Unit) {
    SwingPanel(modifier = modifier, factory = {
        JScrollPane(JEditorPane("text/html", html).apply {
            isEditable = false
            addHyperlinkListener { event -> if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) onLinkClicked(event.url.toString()) }
        })
    })
}
