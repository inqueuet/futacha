package com.valoser.futacha.shared.ui.futaber.mht

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher
import com.valoser.futacha.shared.ui.futaber.FutaberShapes
import com.valoser.futacha.shared.ui.futaber.FutaberThreadRef
import com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey
import com.valoser.futacha.shared.ui.futaber.futaberHistoryThreadUrl
import com.valoser.futacha.shared.ui.futaber.LocalFutaberColors

/**
 * "MHTで保存" as the original app does it: ask whether to take the full-size pictures too, save, then
 * offer to share the file. Whatever is chosen, the file also appears in the saved box's MHT list.
 */
@Composable
internal fun FutaberMhtSaveFlow(
    library: FutaberMhtLibrary,
    board: BoardSummary,
    ref: FutaberThreadRef,
    page: ThreadPage,
    onDismiss: () -> Unit
) {
    val controller = rememberFutaberMhtSaveController(
        library,
        FutaberMhtSaveRequest(
            boardKey = futaberCompatBoardKey(board), boardName = board.name, boardUrl = board.url,
            threadId = ref.threadId, title = ref.title, threadUrl = futaberHistoryThreadUrl(board, ref.threadId), page = page
        )
    )
    val share = rememberCompatShareLauncher()

    val current = controller.step
    // While the file is being written, leaving the dialog (the button or Back) stops the save: the file being written is
    // removed, and a file saved before for the same thread stays as it was.
    val leave: () -> Unit = {
        if (current is MhtSaveStep.Running) controller.cancel()
        onDismiss()
    }
    FutachaAppLockAwareWindow {
        Dialog(
            onDismissRequest = leave,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            when (current) {
                MhtSaveStep.Choose -> SheetCard(
                    title = "MHT保存",
                    body = "サムネイルのみダウンロードしますか？",
                    actions = listOf(
                        "サムネイルのみ" to { controller.start(false) },
                        "本体画像もダウンロードする" to { controller.start(true) },
                        "キャンセル" to onDismiss
                    ),
                    tags = listOf("futaber-mht-choose-thumbs", "futaber-mht-choose-full", "futaber-mht-choose-cancel"),
                    boldLast = true
                )
                is MhtSaveStep.Running -> SheetCard(
                    title = "MHT保存",
                    body = if (current.total > 0) "保存しています… ${current.done}/${current.total}" else "保存しています…",
                    actions = listOf("キャンセル" to leave),
                    tags = listOf("futaber-mht-cancel"),
                    progress = true
                )
                is MhtSaveStep.Done -> SheetCard(
                    title = "MHTの保存が完了しました",
                    body = if (current.result.missingPictures > 0) "画像${current.result.missingPictures}枚は取得できませんでした" else null,
                    actions = listOf(
                        "共有" to {
                            share(current.result.entry.title, "multipart/related", controller.absolutePath(current.result))
                        },
                        "とじる" to onDismiss
                    ),
                    tags = listOf("futaber-mht-share-file", "futaber-mht-close"),
                    sideBySide = true
                )
                is MhtSaveStep.Failed -> SheetCard(
                    title = "MHTを保存できませんでした",
                    body = current.message,
                    actions = listOf("とじる" to onDismiss),
                    tags = listOf("futaber-mht-close"),
                    sideBySide = true
                )
            }
        }
    }
}

/** The look of the system alert the original app shows: a centred title and message over stacked or paired buttons. */
@Composable
private fun SheetCard(
    title: String,
    body: String?,
    actions: List<Pair<String, () -> Unit>>,
    tags: List<String>,
    progress: Boolean = false,
    boldLast: Boolean = false,
    sideBySide: Boolean = false
) {
    val colors = LocalFutaberColors.current
    Surface(
        shape = FutaberShapes.card,
        color = colors.bar,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("futaber-mht-dialog")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title, color = colors.body, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = if (body == null && !progress) 18.dp else 6.dp)
            )
            if (body != null) {
                Text(
                    body, color = colors.body, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp).testTag("futaber-mht-message")
                )
            }
            if (progress) CircularProgressIndicator(
                color = colors.accent, modifier = Modifier.padding(bottom = 22.dp).testTag("futaber-mht-progress")
            )
            if (actions.isNotEmpty()) HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            if (sideBySide) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                    actions.forEachIndexed { index, (label, onClick) ->
                        TextButton(onClick = onClick, modifier = Modifier.weight(1f).testTag(tags[index])) {
                            Text(label, color = colors.link, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                actions.forEachIndexed { index, (label, onClick) ->
                    if (index > 0) HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tags[index])) {
                        Text(
                            label, color = colors.link, fontSize = 16.sp,
                            fontWeight = if (boldLast && index == actions.lastIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
