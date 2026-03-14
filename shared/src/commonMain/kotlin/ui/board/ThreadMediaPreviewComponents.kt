package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun ThreadMediaPreviewDialog(
    state: ThreadMediaPreviewDialogState,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onSave: (MediaPreviewEntry) -> Unit
) {
    when (state.entry.mediaType) {
        MediaType.Image -> ImagePreviewDialog(
            entry = state.entry,
            currentIndex = state.currentIndex,
            totalCount = state.totalCount,
            onDismiss = onDismiss,
            onNavigateNext = onNavigateNext,
            onNavigatePrevious = onNavigatePrevious,
            onSave = { onSave(state.entry) },
            isSaveEnabled = state.isSaveEnabled,
            isSaveInProgress = state.isSaveInProgress
        )

        MediaType.Video -> VideoPreviewDialog(
            entry = state.entry,
            currentIndex = state.currentIndex,
            totalCount = state.totalCount,
            onDismiss = onDismiss,
            onNavigateNext = onNavigateNext,
            onNavigatePrevious = onNavigatePrevious,
            onSave = { onSave(state.entry) },
            isSaveEnabled = state.isSaveEnabled,
            isSaveInProgress = state.isSaveInProgress
        )
    }
}

@Composable
internal fun ThreadMediaPreviewDialogFrame(
    navigationKey: String,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    containerModifier: Modifier = Modifier,
    content: @Composable BoxScope.(IntSize) -> Unit
) {
    var swipeDistance by remember { mutableStateOf(0f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { previewSize = it }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeDistance += dragAmount
                            if (swipeDistance < -200f) {
                                swipeDistance = 0f
                                onDismiss()
                            }
                        },
                        onDragEnd = { swipeDistance = 0f },
                        onDragCancel = { swipeDistance = 0f }
                    )
                }
                .then(containerModifier)
                .pointerInput(navigationKey, previewSize.width) {
                    detectTapGestures { offset ->
                        val width = previewSize.width.toFloat()
                        if (width <= 0f) return@detectTapGestures
                        if (offset.x < width / 2f) {
                            onNavigatePrevious()
                        } else {
                            onNavigateNext()
                        }
                    }
                }
        ) {
            content(previewSize)
        }
    }
}

@Composable
internal fun ThreadMediaPreviewHeader(
    title: String,
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        modifier = modifier
            .padding(top = 32.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${currentIndex + 1}/${totalCount}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun ThreadMediaPreviewCloseButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onDismiss,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "プレビューを閉じる"
        )
    }
}
