package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.BoardSummary

/**
 * NGの登録・編集（原作 NGDialog）。文字列・種類・正規表現・有効・板（全板か1つの板）を選ぶ。
 * 保存の前に [ftutterNgValidationError] で検査し、問題があればダイアログに出して閉じない。
 */
@Composable
internal fun FtutterNgDialog(
    initial: FtutterNgRule,
    boards: List<BoardSummary>,
    existing: List<FtutterNgRule>,
    title: String = "NG・ピックアップの追加",
    onDismiss: () -> Unit,
    onSave: (FtutterNgRule) -> Unit
) {
    val colors = LocalFtutterColors.current
    var text by remember { mutableStateOf(initial.text) }
    var type by remember { mutableStateOf(FtutterNgType.entries.firstOrNull { it.stored == initial.type } ?: FtutterNgType.WORD) }
    var isReg by remember { mutableStateOf(initial.isReg) }
    var isActive by remember { mutableStateOf(initial.isActive) }
    var board by remember { mutableStateOf(initial.ita) }
    var error by remember { mutableStateOf<String?>(null) }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        modifier = Modifier.testTag("ftutter-ng-dialog"),
        title = { Text(title, color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("文字列") },
                    modifier = Modifier.fillMaxWidth().testTag("ftutter-ng-text")
                )
                var typeOpen by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth().clickable { typeOpen = true }.padding(vertical = 8.dp).testTag("ftutter-ng-type"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("種類：${type.label}", color = colors.text, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.text)
                    }
                    DropdownMenu(expanded = typeOpen, onDismissRequest = { typeOpen = false }) {
                        FtutterNgType.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.label) },
                                onClick = { typeOpen = false; type = candidate; error = null },
                                modifier = Modifier.testTag("ftutter-ng-type-${candidate.name}")
                            )
                        }
                    }
                }
                // ID・IPは表示されたIDとの完全一致で、正規表現にはならない（原作どおり）。
                if (type != FtutterNgType.ID) {
                    Row(Modifier.fillMaxWidth().clickable { isReg = !isReg; error = null }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isReg, onCheckedChange = { isReg = it; error = null }, modifier = Modifier.testTag("ftutter-ng-regex"))
                        Text("正規表現", color = colors.text)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { isActive = !isActive }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                    Text("有効", color = colors.text)
                }
                var boardOpen by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth().clickable { boardOpen = true }.padding(vertical = 8.dp).testTag("ftutter-ng-board"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("板：${board?.name?.ifBlank { null } ?: "全板"}", color = colors.text, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.text)
                    }
                    DropdownMenu(expanded = boardOpen, onDismissRequest = { boardOpen = false }) {
                        DropdownMenuItem(text = { Text("全板") }, onClick = { boardOpen = false; board = null })
                        boards.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.name) },
                                onClick = { boardOpen = false; board = FtutterNgBoard(candidate.name, candidate.url) }
                            )
                        }
                    }
                }
                error?.let { Text(it, color = colors.deleted, modifier = Modifier.testTag("ftutter-ng-error")) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rule = FtutterNgRule(text, type.stored, isReg && type != FtutterNgType.ID, isActive, board)
                // 編集では、元のルール自身は重複に数えない。
                val problem = ftutterNgValidationError(rule, existing - initial)
                if (problem != null) error = problem else onSave(rule)
            }, modifier = Modifier.testTag("ftutter-ng-save")) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) }
}

/** NG・ピックアップの一覧（設定の「ピックアップ・NG」）。行のタップで編集、ゴミ箱で削除、右上の＋で追加。 */
@Composable
internal fun FtutterNgListScreen(
    rules: List<FtutterNgRule>,
    boards: List<BoardSummary>,
    onSave: (List<FtutterNgRule>) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalFtutterColors.current
    var editing by remember { mutableStateOf<Pair<Int, FtutterNgRule>?>(null) }
    Column(Modifier.fillMaxSize().testTag("ftutter-ng-list")) {
        FtutterTopBar(title = "ピックアップ・NG", onBack = onBack) {
            IconButton(onClick = { editing = -1 to FtutterNgRule("", FtutterNgType.WORD.stored) }, modifier = Modifier.testTag("ftutter-ng-add")) {
                Icon(Icons.Rounded.Add, contentDescription = "追加", tint = colors.onToolBar)
            }
        }
        if (rules.isEmpty()) {
            Text("登録はありません", color = colors.text, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            FtutterNgType.entries.forEach { type ->
                val ofType = rules.withIndex().filter { it.value.type == type.stored }
                if (ofType.isEmpty()) return@forEach
                item(key = "h-${type.name}") {
                    Text("${type.label}（${ofType.size}）", color = colors.headerText, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                    HorizontalDivider(color = colors.outline)
                }
                itemsIndexed(ofType, key = { _, it -> "r${it.index}" }) { _, (index, rule) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = index to rule }.padding(start = 16.dp).testTag("ftutter-ng-row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(rule.text, color = if (rule.isActive) colors.text else colors.headerText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val notes = listOfNotNull(
                                rule.ita?.name?.ifBlank { null } ?: "全板",
                                "正規表現".takeIf { rule.isReg },
                                "無効".takeIf { !rule.isActive }
                            )
                            Text(notes.joinToString("・"), color = colors.headerText, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onSave(rules.filterIndexed { i, _ -> i != index }) }, modifier = Modifier.testTag("ftutter-ng-delete")) {
                            Icon(Icons.Rounded.Delete, contentDescription = "削除", tint = colors.text)
                        }
                    }
                    HorizontalDivider(color = colors.outline)
                }
            }
        }
    }
    editing?.let { (index, rule) ->
        FtutterNgDialog(
            initial = rule,
            boards = boards,
            existing = rules,
            title = if (index < 0) "NG・ピックアップの追加" else "NG・ピックアップの編集",
            onDismiss = { editing = null },
            onSave = { saved ->
                editing = null
                onSave(if (index < 0) rules + saved else rules.mapIndexed { i, old -> if (i == index) saved else old })
            }
        )
    }
}
