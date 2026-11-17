package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtSaveRequest
import com.valoser.futacha.shared.ui.futaber.mht.MhtSaveStep
import com.valoser.futacha.shared.ui.futaber.mht.rememberFutaberMhtSaveController

/**
 * The MHT save of ふたちゃ and としあき(仮): the format was chosen in the save dialog ([fullImages] says whether
 * the full-size pictures are taken too), so this only shows the progress and the result, with a share button.
 * It uses the file of the MHT library (the same files ふたばー風モード lists).
 */
@Composable
internal fun FutachaMhtSaveRun(
    library: FutaberMhtLibrary,
    request: FutaberMhtSaveRequest,
    fullImages: Boolean,
    onDismiss: () -> Unit
) {
    val controller = rememberFutaberMhtSaveController(library, request, startWith = fullImages)
    val share = rememberCompatShareLauncher()
    val current = controller.step
    // While the file is being written, leaving the dialog (the button or Back) stops the save: the file being written is
    // removed, and a file saved before for the same thread stays as it was.
    val cancelSave: () -> Unit = {
        controller.cancel()
        onDismiss()
    }
    when (current) {
        MhtSaveStep.Choose, is MhtSaveStep.Running -> FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = cancelSave,
            title = { Text("MHT保存") },
            text = {
                val running = current as? MhtSaveStep.Running
                Text(if (running != null && running.total > 0) "保存しています… ${running.done}/${running.total}" else "保存しています…")
            },
            confirmButton = { CircularProgressIndicator() },
            dismissButton = { TextButton(onClick = cancelSave, modifier = Modifier.testTag("futacha-mht-cancel")) { Text("キャンセル") } }
        ) }
        is MhtSaveStep.Done -> FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("MHTの保存が完了しました") },
            text = {
                Text(
                    buildString {
                        append(current.result.entry.title)
                        if (current.result.missingPictures > 0) append("\n画像${current.result.missingPictures}枚は取得できませんでした")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { share(current.result.entry.title, "multipart/related", controller.absolutePath(current.result)) },
                    modifier = Modifier.testTag("futacha-mht-share")
                ) { Text("共有") }
            },
            dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("futacha-mht-close")) { Text("とじる") } }
        ) }
        is MhtSaveStep.Failed -> FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("MHTを保存できませんでした") },
            text = { Text(current.message) },
            confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("futacha-mht-close")) { Text("閉じる") } }
        ) }
    }
}
