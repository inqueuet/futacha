package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

internal fun ftutterGestureMenuActions(isThread: Boolean): List<FtutterGestureAction> =
    FtutterGestureAction.entries.filter { it != FtutterGestureAction.NONE &&
        (isThread || it !in setOf(FtutterGestureAction.TREE, FtutterGestureAction.POPULAR, FtutterGestureAction.IMAGES)) }

internal fun ftutterGestureMenuLabel(action: FtutterGestureAction, isThread: Boolean): String = when (action) {
    FtutterGestureAction.PREVIOUS -> if (isThread) "前のタブ" else "前のお気に入りの板"
    FtutterGestureAction.NEXT -> if (isThread) "次のタブ" else "次のお気に入りの板"
    FtutterGestureAction.EDIT -> if (isThread) "書き込む" else "スレ立て"
    else -> action.label
}

/** The same dispatcher as a drawn gesture, reachable with touch or a screen reader. */
@Composable
internal fun FtutterGestureActionDialog(isThread: Boolean, onDismiss: () -> Unit, onAction: (FtutterGestureAction) -> Unit) {
    val colors = LocalFtutterColors.current
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        title = { Text("ジェスチャーの操作を選ぶ", color = colors.text) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("ftutter-gesture-action-list")) {
                item { Text("線を描かずに同じ操作を実行できます。設定済みのジェスチャーは変わりません。", color = colors.text) }
                items(ftutterGestureMenuActions(isThread), key = { it.stored }) { action ->
                    TextButton(onClick = { onDismiss(); onAction(action) },
                        modifier = Modifier.fillMaxWidth().testTag("ftutter-gesture-action-${action.stored}")) {
                        Text(ftutterGestureMenuLabel(action, isThread), color = colors.text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    ) }
}
