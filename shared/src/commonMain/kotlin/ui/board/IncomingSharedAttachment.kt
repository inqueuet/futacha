package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One bounded, transient attachment. It is offered in the editor and never submitted automatically. */
object IncomingSharedAttachment {
    private val state = MutableStateFlow<ImageData?>(null)
    val pending = state.asStateFlow()
    fun offer(image: ImageData) { require(image.bytes.size in 1..32_000_000); state.value = image }
    fun consume(image: ImageData): Boolean = state.compareAndSet(image, null)
}

@Composable
internal fun IncomingSharedAttachmentButton(enabled: Boolean = true, onAttach: (ImageData) -> Unit) {
    val image by IncomingSharedAttachment.pending.collectAsState()
    image?.let { pending ->
        Row {
            TextButton(enabled = enabled, onClick = { if (IncomingSharedAttachment.consume(pending)) onAttach(pending) }) {
                Text("共有された画像を添付")
            }
            TextButton(enabled = enabled, onClick = { IncomingSharedAttachment.consume(pending) }) { Text("破棄") }
        }
    }
}
