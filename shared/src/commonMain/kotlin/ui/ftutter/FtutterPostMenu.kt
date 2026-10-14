package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** レスの長押しメニューの項目（原作 ResuD。保存名と既定の並び＝すべてON）。 */
internal enum class FtutterPostMenuItem(val stored: String, val label: String) {
    QUOTE("rep", "本文返信"),
    QUOTE_NO("repNo", "No返信"),
    QUOTE_SELECT("repSelect", "選択返信"),
    SOUDANE("soudane", "そうだね"),
    DELETE("delete", "削除"),
    DEL("del", "del"),
    MARK("mark", "マーク"),
    COPY("copy", "コピー"),
    NG("ng", "NG");
}

internal val FTUTTER_POST_MENU_DEFAULT: List<FtutterToolbarEntry<FtutterPostMenuItem>> =
    FtutterPostMenuItem.entries.map { FtutterToolbarEntry(it, true) }

internal fun parseFtutterPostMenu(p: Map<String, String>) = parseFtutterToolbar(
    decodeFtutterList(p[FtutterKeys.POST_MENU_ORDER]),
    decodeFtutterList(p[FtutterKeys.POST_MENU_ENABLED]),
    FTUTTER_POST_MENU_DEFAULT
) { it.stored }

private fun FtutterPostMenuItem.icon(): ImageVector = when (this) {
    FtutterPostMenuItem.QUOTE, FtutterPostMenuItem.QUOTE_NO -> Icons.AutoMirrored.Rounded.Reply
    FtutterPostMenuItem.QUOTE_SELECT -> Icons.AutoMirrored.Rounded.ReplyAll
    FtutterPostMenuItem.SOUDANE -> Icons.Rounded.ThumbUp
    FtutterPostMenuItem.DELETE -> Icons.Rounded.DeleteForever
    FtutterPostMenuItem.DEL -> Icons.Rounded.ThumbDown
    FtutterPostMenuItem.MARK -> Icons.Rounded.Flag
    FtutterPostMenuItem.COPY -> Icons.Rounded.ContentCopy
    FtutterPostMenuItem.NG -> Icons.Rounded.NoAccounts
}

/**
 * レスの長押しメニュー（原作 ResuDialog）：上にそのレス（文字を選べる、高さは画面の3割まで）、中央に3列のボタン、
 * 下に「並び替え」。
 */
@Composable
internal fun FtutterPostMenuDialog(
    header: @Composable () -> Unit,
    entries: List<FtutterToolbarEntry<FtutterPostMenuItem>>,
    marked: Boolean,
    maxPreviewHeightDp: Int,
    onDismiss: () -> Unit,
    onItem: (FtutterPostMenuItem) -> Unit,
    onReorder: () -> Unit
) {
    val colors = LocalFtutterColors.current
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        modifier = Modifier.testTag("ftutter-post-menu-dialog"),
        text = {
            Column {
                SelectionContainer(Modifier.heightIn(max = maxPreviewHeightDp.dp).verticalScroll(rememberScrollState())) { header() }
                HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = 8.dp))
                val shown = entries.filter { it.enabled }.map { it.tool }
                LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.stored }) { item ->
                        Column(
                            Modifier.aspectRatio(1.6f).clickable { onItem(item) }.testTag("ftutter-post-menu-${item.stored}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(item.icon(), contentDescription = null, tint = colors.text)
                            Text(
                                if (item == FtutterPostMenuItem.MARK && marked) "マーク解除" else item.label,
                                color = colors.text, fontSize = 13.sp, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onReorder, modifier = Modifier.testTag("ftutter-post-menu-reorder")) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, tint = colors.text)
                        Spacer(Modifier.width(4.dp))
                        Text("並び替え", color = colors.text)
                    }
                }
            }
        },
        confirmButton = {}
    ) }
}

/** 並べ替えとON/OFF（原作 ResuDialogOrder。ツールバー編集でも同じ形で使う）。 */
@Composable
internal fun <T> FtutterOrderEditorDialog(
    title: String,
    entries: List<FtutterToolbarEntry<T>>,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSave: (List<FtutterToolbarEntry<T>>) -> Unit
) {
    val colors = LocalFtutterColors.current
    var current by remember { mutableStateOf(entries) }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        modifier = Modifier.testTag("ftutter-order-editor"),
        title = { Text(title, color = colors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                current.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("ftutter-order-row")) {
                        Checkbox(
                            checked = entry.enabled,
                            onCheckedChange = { checked -> current = current.toMutableList().also { it[index] = entry.copy(enabled = checked) } }
                        )
                        Text(label(entry.tool), color = colors.text, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            if (index > 0) current = current.toMutableList().also { it.add(index - 1, it.removeAt(index)) }
                        }, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上へ", tint = colors.text)
                        }
                        IconButton(onClick = {
                            if (index < current.lastIndex) current = current.toMutableList().also { it.add(index + 1, it.removeAt(index)) }
                        }, enabled = index < current.lastIndex, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下へ", tint = colors.text)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current) }, modifier = Modifier.testTag("ftutter-order-save")) { Text("OK") } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("リセット") }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        }
    ) }
}

/** 選択返信（原作 ChooseReplyDialog）：ID・ファイル名・本文の各行を選んで、`>`を付けて書き込みへ。 */
@Composable
internal fun FtutterChooseQuoteDialog(candidates: List<String>, onDismiss: () -> Unit, onQuote: (String) -> Unit) {
    val colors = LocalFtutterColors.current
    var chosen by remember { mutableStateOf(setOf<Int>()) }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        modifier = Modifier.testTag("ftutter-choose-quote"),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                candidates.forEachIndexed { index, text ->
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = if (index in chosen) chosen - index else chosen + index },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = index in chosen, onCheckedChange = null, modifier = Modifier.testTag("ftutter-choose-quote-$index"))
                        Text(text, color = colors.text)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onQuote(ftutterQuoteLines(candidates.filterIndexed { i, _ -> i in chosen }))
            }, enabled = chosen.isNotEmpty(), modifier = Modifier.testTag("ftutter-choose-quote-ok")) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) }
}

/** コピー（原作）：E-Mail・No・ID・ファイル・本文のうち値のあるものを選ぶ。 */
@Composable
internal fun FtutterCopyDialog(values: List<Pair<String, String>>, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    val colors = LocalFtutterColors.current
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        modifier = Modifier.testTag("ftutter-copy-dialog"),
        text = {
            Column {
                values.forEach { (label, value) ->
                    Text(
                        label, color = colors.text, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onCopy(value) }.padding(vertical = 12.dp).testTag("ftutter-copy-$label")
                    )
                }
            }
        },
        confirmButton = {}
    ) }
}

/** 1つ選ぶだけの小さなダイアログ（NGの種類など）。 */
@Composable
internal fun FtutterChoiceDialog(title: String?, choices: List<String>, onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    val colors = LocalFtutterColors.current
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        title = title?.let { { Text(it, color = colors.text) } },
        text = {
            Column {
                choices.forEachIndexed { index, label ->
                    Text(
                        label, color = colors.text, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onChoose(index) }.padding(vertical = 12.dp).testTag("ftutter-choice-$index")
                    )
                }
            }
        },
        confirmButton = {}
    ) }
}

/** そうだね以外の送信の確認（原作は del も確認なしで送るが、誤操作を防ぐため確認する）。 */
@Composable
internal fun FtutterConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = LocalFtutterColors.current
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        title = { Text(title, color = colors.text) },
        text = { Text(message, color = colors.text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("ftutter-confirm-ok")) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        modifier = Modifier.background(colors.popUp)
    ) }
}
