package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.aimogeBoardShapeOf
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardRepository
import com.valoser.futacha.shared.ui.aimoge.loadAimogeBoardsOffMain
import com.valoser.futacha.shared.ui.aimoge.lookupAimogeBoard
import com.valoser.futacha.shared.network.aimoge.createAimogeHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

private class MixR18Prompt(val names: List<String>, val answer: CompletableDeferred<Boolean>)

/** Also used by automatic creation: no definition is saved before its new R18 members are confirmed. */
@Composable
internal fun rememberMixR18Confirmation(
    store: CompatibilityStore?,
    loadBoards: (suspend () -> List<com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo>)? = null
): suspend (List<String>) -> Boolean {
    val latestLoadBoards by rememberUpdatedState(loadBoards)
    var prompt by remember(store) { mutableStateOf<MixR18Prompt?>(null) }
    var error by remember(store) { mutableStateOf<String?>(null) }
    DisposableEffect(store) { onDispose { prompt?.answer?.complete(false) } }
    prompt?.let { question ->
        com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = { question.answer.complete(false) },
                title = { Text("R18の板") },
                text = { Text("成人向けの内容が表示されることがあります。まとめカタログに追加しますか？\n" + question.names.joinToString("\n")) },
                confirmButton = { TextButton(onClick = { question.answer.complete(true) }) { Text("追加") } },
                dismissButton = { TextButton(onClick = { question.answer.complete(false) }) { Text("キャンセル") } }
            )
        }
    }
    error?.let { text ->
        com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow {
            AlertDialog(onDismissRequest = { error = null }, title = { Text("板を確認できませんでした") },
                text = { Text(text) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
        }
    }
    return remember(store) {
        confirm@{ members: List<String> ->
            val urls = members.filter { aimogeBoardShapeOf(it) != null }.distinct()
            if (urls.isEmpty()) return@confirm true
            val boards = try {
                val loader = latestLoadBoards
                if (loader != null) loader() else {
                    val repository = AimogeBoardRepository.owningClient({ createAimogeHttpClient(appVersion = "") })
                    try { loadAimogeBoardsOffMain(repository) } finally { repository.close() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "あいもげの板の情報を確認できません。通信を確認して、もう一度追加してください。"
                return@confirm false
            }
            val r18 = urls.map { lookupAimogeBoard(it, loadBoards = { boards }, store = store) }.filter { it.r18 }
            if (r18.isEmpty()) return@confirm true
            if (prompt != null) return@confirm false
            val question = MixR18Prompt(r18.map { it.name }, CompletableDeferred())
            prompt = question
            try { question.answer.await() } finally { if (prompt === question) prompt = null }
        }
    }
}
