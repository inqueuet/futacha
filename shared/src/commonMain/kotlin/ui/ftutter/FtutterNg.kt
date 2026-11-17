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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch
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
 * NGの登録・編集。M56：原作の形は種類ごとの題（ピックアップは「ピックアップ」）、下線の欄、「正規表現」（NGID・IPには無い）、
 * 板の選択（「全ての板」）と「キャンセル／OK」。検査で断った時はダイアログを閉じて理由を知らせる（[onRejected]、原作のトースト）。
 * [legacy]（従来のダイアログ）の時は前の「NG・ピックアップの追加」（種類・有効を選べ、理由はダイアログの中）。
 */
@Composable
internal fun FtutterNgDialog(
    initial: FtutterNgRule,
    boards: List<BoardSummary>,
    existing: List<FtutterNgRule>,
    title: String = "NG・ピックアップの追加",
    onDismiss: () -> Unit,
    legacy: Boolean = false,
    onRejected: ((String) -> Unit)? = null,
    onSave: (FtutterNgRule) -> Unit
) {
    if (legacy || onRejected == null) FtutterNgLegacyDialog(initial, boards, existing, title, onDismiss, onSave)
    else FtutterNgOriginalDialog(initial, boards, existing, onDismiss, onRejected, onSave)
}

/** 種類ごとの頁の説明の文（原作の文）。 */
internal fun ftutterNgTypeDescription(type: FtutterNgType): String = when (type) {
    FtutterNgType.TITLE -> "この文字列が含まれるスレをカタログから非表示にします"
    FtutterNgType.WORD -> "この文字列が含まれるレスを非表示にします"
    FtutterNgType.ID -> "このID・IPからの投稿を非表示にします"
    FtutterNgType.PICKUP -> "この文字列が含まれるスレをカタログの上部に強調表示します"
}

/** ダイアログの題（原作はピックアップだけ「ピックアップ」）。 */
internal fun ftutterNgDialogTitle(type: FtutterNgType): String = if (type == FtutterNgType.PICKUP) "ピックアップ" else type.label

internal const val FTUTTER_NG_ALL_BOARDS = "全ての板"
internal const val FTUTTER_NG_ID_HINT = "'ID:'または'IP:'を含めて入力"

@Composable
private fun FtutterNgOriginalDialog(
    initial: FtutterNgRule,
    boards: List<BoardSummary>,
    existing: List<FtutterNgRule>,
    onDismiss: () -> Unit,
    onRejected: (String) -> Unit,
    onSave: (FtutterNgRule) -> Unit
) {
    val colors = LocalFtutterColors.current
    val type = FtutterNgType.of(initial) ?: FtutterNgType.WORD
    var text by remember { mutableStateOf(initial.text) }
    var isReg by remember { mutableStateOf(initial.isReg) }
    var board by remember { mutableStateOf(initial.ita) }
    var checking by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.popUp,
        modifier = Modifier.testTag("ftutter-ng-dialog"),
        title = { Text(ftutterNgDialogTitle(type), color = colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = if (type == FtutterNgType.ID) ({ Text(FTUTTER_NG_ID_HINT) }) else null,
                    singleLine = true,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedPlaceholderColor = colors.text, unfocusedPlaceholderColor = colors.text
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("ftutter-ng-text")
                )
                if (type != FtutterNgType.ID) {
                    Row(Modifier.fillMaxWidth().clickable { isReg = !isReg }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isReg, onCheckedChange = { isReg = it }, modifier = Modifier.testTag("ftutter-ng-regex"))
                        Text("正規表現", color = colors.text)
                    }
                }
                var boardOpen by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.clickable { boardOpen = true }.padding(vertical = 8.dp).testTag("ftutter-ng-board"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(board?.name?.ifBlank { null } ?: FTUTTER_NG_ALL_BOARDS, color = colors.text)
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.text)
                    }
                    DropdownMenu(expanded = boardOpen, onDismissRequest = { boardOpen = false }) {
                        DropdownMenuItem(text = { Text(FTUTTER_NG_ALL_BOARDS) }, onClick = { boardOpen = false; board = null })
                        boards.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.name) },
                                onClick = { boardOpen = false; board = FtutterNgBoard(candidate.name, candidate.url) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !checking, onClick = {
                val rule = FtutterNgRule(text, type.stored, isReg && type != FtutterNgType.ID, initial.isActive, board)
                checking = true
                scope.launch {
                    val problem = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        ftutterNgValidationError(rule, existing - initial)
                    } ?: ftutterNgCapacityError((existing - initial) + rule)
                    checking = false
                    // 原作：断った理由はトーストで出し、ダイアログは閉じる。
                    if (problem != null) { onDismiss(); onRejected(problem) } else onSave(rule)
                }
            }, modifier = Modifier.testTag("ftutter-ng-save")) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("ftutter-ng-cancel")) { Text("キャンセル") } }
    ) }
}

@Composable
private fun FtutterNgLegacyDialog(
    initial: FtutterNgRule,
    boards: List<BoardSummary>,
    existing: List<FtutterNgRule>,
    title: String,
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
    // 正規表現の試しは重くなり得るので、メインスレッドの外で行う（再監査H1：OKを押すと画面が止まった）。
    var checking by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
            TextButton(enabled = !checking, onClick = {
                val rule = FtutterNgRule(text, type.stored, isReg && type != FtutterNgType.ID, isActive, board)
                checking = true
                scope.launch {
                    // 編集では、元のルール自身は重複に数えない。
                    val problem = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        ftutterNgValidationError(rule, existing - initial)
                    }
                        // 保存値の上限（2万字）に入らなければ、登録の時点で断る（監査A9。以前は保存が黙って失敗した）。
                        ?: ftutterNgCapacityError((existing - initial) + rule)
                    checking = false
                    if (problem != null) error = problem else onSave(rule)
                }
            }, modifier = Modifier.testTag("ftutter-ng-save")) { Text(if (checking) "確認中…" else "OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) }
}

/**
 * M56：原作の種類ごとの頁。上部バーの題と右上の＋、説明の帯、行は左にON／OFFのスイッチ・文字列と板・右に×（「削除しますか？」）。
 * 行のタップで編集。
 */
@Composable
internal fun FtutterNgTypeScreen(
    type: FtutterNgType,
    rules: List<FtutterNgRule>,
    boards: List<BoardSummary>,
    onEdit: ((List<FtutterNgRule>) -> List<FtutterNgRule>) -> Unit,
    onNotice: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalFtutterColors.current
    var editing by remember { mutableStateOf<FtutterNgRule?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FtutterNgRule?>(null) }
    val ofType = rules.filter { it.type == type.stored }
    Column(Modifier.fillMaxSize().testTag("ftutter-ng-type-page")) {
        FtutterTopBar(title = type.label, onBack = onBack) {
            androidx.compose.material3.FilledTonalButton(
                onClick = { adding = true },
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = colors.onToolBar, contentColor = colors.toolBar),
                modifier = Modifier.padding(end = 8.dp).testTag("ftutter-ng-add")
            ) { Icon(Icons.Rounded.Add, contentDescription = "追加") }
        }
        Text(
            ftutterNgTypeDescription(type), color = colors.text, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().background(colors.outline).padding(horizontal = 8.dp, vertical = 2.dp).testTag("ftutter-ng-type-description")
        )
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(ofType, key = { i, _ -> "r$i" }) { _, rule ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = rule }.padding(horizontal = 12.dp, vertical = 6.dp).testTag("ftutter-ng-row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Switch(
                        checked = rule.isActive,
                        onCheckedChange = { on -> onEdit { stored -> ftutterNgReplaced(stored, rule, rule.copy(isActive = on)) } },
                        modifier = Modifier.testTag("ftutter-ng-row-switch")
                    )
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(rule.text, color = colors.text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(rule.ita?.name?.ifBlank { null } ?: FTUTTER_NG_ALL_BOARDS, color = colors.headerText, fontSize = 13.sp)
                        // こちらだけ：使われていないルール（正規表現が作れない・時間がかかりすぎて止めた）。
                        rememberFtutterNgRuleProblem(rule)?.let { problem ->
                            Text(problem, color = colors.deleted, fontSize = 12.sp, modifier = Modifier.testTag("ftutter-ng-row-problem"))
                        }
                    }
                    IconButton(onClick = { deleting = rule }, modifier = Modifier.testTag("ftutter-ng-delete")) {
                        Icon(Icons.Rounded.Close, contentDescription = "削除", tint = colors.text)
                    }
                }
            }
        }
    }
    deleting?.let { rule ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = colors.popUp,
            title = { Text("削除しますか？", color = colors.text) },
            confirmButton = {
                TextButton(onClick = { deleting = null; onEdit { stored -> ftutterNgWithout(stored, rule) } },
                    modifier = Modifier.testTag("ftutter-ng-delete-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } }
        ) }
    }
    if (adding || editing != null) {
        val initial = editing ?: FtutterNgRule("", type.stored)
        FtutterNgDialog(
            initial = initial, boards = boards, existing = rules,
            onDismiss = { adding = false; editing = null },
            onRejected = onNotice,
            onSave = { saved ->
                val old = editing
                adding = false; editing = null
                onEdit { stored -> if (old == null) stored + saved else ftutterNgReplaced(stored, old, saved) }
            }
        )
    }
}

/** NG・ピックアップの一覧（従来の形。その他の機能から開く）。行のタップで編集、ゴミ箱で削除、右上の＋で追加。 */
@Composable
internal fun FtutterNgListScreen(
    rules: List<FtutterNgRule>,
    boards: List<BoardSummary>,
    /** 保存されている一覧に当てる変更（監査M2）。 */
    onEdit: ((List<FtutterNgRule>) -> List<FtutterNgRule>) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalFtutterColors.current
    var editing by remember { mutableStateOf<Pair<Int, FtutterNgRule>?>(null) }
    // 削除は確認してから（監査L15：ゴミ箱を押すとすぐ消え、戻せなかった）。
    var deleting by remember { mutableStateOf<FtutterNgRule?>(null) }
    deleting?.let { rule ->
        FtutterConfirmDialog(
            title = "削除しますか？",
            message = rule.text,
            confirmLabel = "削除",
            onDismiss = { deleting = null }
        ) {
            deleting = null
            onEdit { stored -> ftutterNgWithout(stored, rule) }
        }
    }
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
                            // 使われていないルール（正規表現が作れない・照合に時間がかかりすぎて止めた）。
                            rememberFtutterNgRuleProblem(rule)?.let { problem ->
                                Text(problem, color = colors.deleted, fontSize = 12.sp, modifier = Modifier.testTag("ftutter-ng-row-problem"))
                            }
                        }
                        IconButton(onClick = { deleting = rule }, modifier = Modifier.testTag("ftutter-ng-delete")) {
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
                onEdit { stored -> if (index < 0) stored + saved else ftutterNgReplaced(stored, rule, saved) }
            }
        )
    }
}

@Composable
private fun rememberFtutterNgRuleProblem(rule: FtutterNgRule): String? {
    val result by androidx.compose.runtime.produceState<Pair<FtutterNgRule, String?>?>(null, rule) {
        value = rule to kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
            ftutterNgRuleProblem(rule)
        }
    }
    return result?.takeIf { it.first == rule }?.second
}
