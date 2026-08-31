package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.model.BoardSummary

@Composable
internal fun BoardSummaryCard(
    board: BoardSummary,
    onClick: () -> Unit,
    onPinToggle: (() -> Unit)? = null
) {
    Card(
        onClick = {
            AnalyticsTracker.uiControl("board_card", "板を開く", boardCardAnalyticsParams(board))
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        BoardSummaryCardContent(
            board = board,
            leadingContent = { BoardSummaryLeadingIcon(board = board) },
            trailingContent = onPinToggle?.let { toggle ->
                {
                    BoardPinToggleButton(board = board, onToggle = toggle)
                }
            }
        )
    }
}

@Composable
internal fun DeleteBoardDialog(
    board: BoardSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            AnalyticsTracker.uiControl("board_delete", "板の削除確認を閉じる", boardCardAnalyticsParams(board))
            onDismiss()
        },
        title = { Text("板を削除") },
        text = {
            Text("「${board.name}」を削除してもよろしいですか？")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AnalyticsTracker.uiControl("board_delete", "板を削除する", boardCardAnalyticsParams(board))
                    onConfirm()
                },
                colors = futachaDialogTextButtonColors()
            ) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    AnalyticsTracker.uiControl("board_delete", "板の削除をキャンセル", boardCardAnalyticsParams(board))
                    onDismiss()
                },
                colors = futachaDialogTextButtonColors()
            ) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
internal fun BoardSummaryCardWithDelete(
    board: BoardSummary,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        BoardSummaryCardContent(
            board = board,
            leadingContent = { BoardSummaryLeadingIcon(board = board) },
            trailingContent = {
                IconButton(onClick = {
                    AnalyticsTracker.uiControl("board_card", "板の削除確認を開く", boardCardAnalyticsParams(board))
                    onDelete()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "削除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }
}

@Composable
internal fun BoardSummaryCardWithReorder(
    board: BoardSummary,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPinToggle: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        BoardSummaryCardContent(
            board = board,
            leadingContent = { BoardSummaryLeadingIcon(board = board) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "長押しして移動",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BoardPinToggleButton(board = board, onToggle = onPinToggle)
                    Column {
                        IconButton(
                            onClick = {
                                AnalyticsTracker.uiControl("board_reorder", "板を上へ移動", boardCardAnalyticsParams(board))
                                onMoveUp()
                            },
                            enabled = canMoveUp
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "上へ移動"
                            )
                        }
                        IconButton(
                            onClick = {
                                AnalyticsTracker.uiControl("board_reorder", "板を下へ移動", boardCardAnalyticsParams(board))
                                onMoveDown()
                            },
                            enabled = canMoveDown
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "下へ移動"
                            )
                        }
                    }
                }
            }
        )
    }
}

private fun boardCardAnalyticsParams(board: BoardSummary): Map<String, String> = mapOf(
    "board_context" to analyticsSessionContextId("board", board.id, board.url),
    "board_pinned" to if (board.pinned) "enabled" else "disabled"
)

@Composable
private fun BoardPinToggleButton(
    board: BoardSummary,
    onToggle: () -> Unit
) {
    IconButton(
        onClick = {
            AnalyticsTracker.uiControl(
                "board_pinned",
                if (board.pinned) "板のピン留めを解除" else "板をピン留め",
                boardCardAnalyticsParams(board)
            )
            onToggle()
        }
    ) {
        Icon(
            imageVector = Icons.Outlined.PushPin,
            contentDescription = if (board.pinned) "ピン留めを解除" else "ピン留め",
            tint = if (board.pinned) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun BoardSummaryCardContent(
    board: BoardSummary,
    leadingContent: @Composable () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leadingContent()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = board.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = board.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun BoardSummaryLeadingIcon(board: BoardSummary) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = if (board.pinned) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (board.pinned) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (board.pinned) {
                    Icons.Outlined.PushPin
                } else {
                    Icons.Outlined.Folder
                },
                contentDescription = null
            )
        }
    }
}
