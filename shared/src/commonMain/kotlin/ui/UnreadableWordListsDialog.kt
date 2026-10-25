package com.valoser.futacha.shared.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.AppStateWordList
import kotlinx.coroutines.launch

/**
 * 「初期化する」 for an NG / watch word list that is stored but cannot be read (L87, the decision). Such a list shows as
 * empty and its edits are not saved, so this tells it once per list and launch: 「初期化する」 empties the list so it
 * can be edited again, 「後で」 keeps it (and what is stored) as it is. Host it next to [SettingsRecoveryNoticeDialog];
 * it waits behind the app lock like other windows.
 */
@Composable
internal fun UnreadableWordListsDialog(stateStore: AppStateStore?) {
    if (stateStore == null) return
    val unreadable by stateStore.unreadableWordLists.collectAsState(emptySet())
    var postponed by remember { mutableStateOf(emptySet<AppStateWordList>()) }
    val list = unreadable.firstOrNull { it !in postponed } ?: return
    val scope = rememberCoroutineScope()
    var resetting by remember(list) { mutableStateOf(false) }
    FutachaAppLockAwareWindow {
        AlertDialog(
            // An explicit choice, so the notice is not lost to a stray tap.
            onDismissRequest = {},
            title = { Text(unreadableWordListTitle(list)) },
            text = { Text(unreadableWordListMessage(list)) },
            confirmButton = {
                TextButton(enabled = !resetting, onClick = {
                    resetting = true
                    scope.launch {
                        // Once reset (or readable again) the list leaves the set and the dialog closes.
                        try { stateStore.resetUnreadableWordList(list) } finally { resetting = false }
                    }
                }) { Text("初期化する") }
            },
            dismissButton = {
                TextButton(enabled = !resetting, onClick = { postponed = postponed + list }) { Text("後で") }
            }
        )
    }
}

internal fun unreadableWordListTitle(list: AppStateWordList): String = "「${list.label}」を読み込めません"

internal fun unreadableWordListMessage(list: AppStateWordList): String =
    "保存されている「${list.label}」の一覧が壊れているか大きすぎるため、読み込めませんでした。" +
        "このままでは、この一覧への追加・削除は保存されません（保存されている内容はそのまま残します）。\n" +
        "「初期化する」を選ぶと一覧を空にして、また編集できるようにします。読み込めなかった内容は元に戻せません。"
