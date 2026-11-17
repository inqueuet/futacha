package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatWatchRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * L-B9 (C45・C11): futaber's NG lists and its 新規スレッド通知 as iOS tables. A list: the section 「新規」 with the field
 * 「追加」, the section 「追加済み」 with the entries; 「編集」 at the top right gives each entry its red ⊖ and, at the bottom,
 * 「すべて削除」 (asked first). The lists are lazy: they can hold thousands of entries.
 */

/** What an NG list shows and does, worked out from the kind ([futaberSettingsNgListModel]). */
internal class FutaberNgListModel(
    val rows: List<FutaberNgRow>,
    /** Null for a list nothing is typed into (rules made elsewhere). */
    val onAdd: ((String) -> Unit)?,
    val canAdd: (String) -> Boolean,
    val onRemove: (FutaberNgRow) -> Unit,
    val onRemoveAll: () -> Unit,
    val hint: String
)

/** Asked through the settings' own alert (drawn over the whole screen by the settings). */
internal class FutaberSettingsAlertRequest(
    val title: String,
    val message: String?,
    val confirmLabel: String,
    val destructive: Boolean = false,
    /** A text field in the alert (its placeholder); null = none. */
    val fieldPlaceholder: String? = null,
    val onConfirm: (String) -> Unit
)

/** The settings' alert: キャンセル on the left, the action on the right; the field's text goes to [FutaberSettingsAlertRequest.onConfirm]. */
@Composable
internal fun FutaberSettingsAlert(request: FutaberSettingsAlertRequest, onDismiss: () -> Unit) {
    var text by remember(request) { mutableStateOf("") }
    FutaberAlert(
        title = request.title,
        message = request.message,
        buttons = listOf(
            FutaberAlertButton("cancel", "キャンセル", FutaberAlertStyle.Cancel, tag = "futaber-settings-alert-cancel") { onDismiss() },
            FutaberAlertButton(
                "confirm", request.confirmLabel,
                if (request.destructive) FutaberAlertStyle.Destructive else FutaberAlertStyle.Default,
                enabled = request.fieldPlaceholder == null || text.isNotBlank(),
                tag = "futaber-settings-alert-confirm"
            ) {
                onDismiss()
                request.onConfirm(text)
            }
        ),
        field = request.fieldPlaceholder?.let { placeholder ->
            FutaberAlertField(text, { text = it.take(com.valoser.futacha.shared.compat.MAX_COMPAT_WATCH_RULE_WORD_CHARS) }, placeholder)
        },
        onDismiss = onDismiss
    )
}

/**
 * A list of shared NG rules (made elsewhere, only taken back here). 「すべて削除」 asks the store once:
 * deleting rule by rule rewrote the whole state (iOS, desktop) and re-sent the NG list for each of up
 * to 10,000 rules (the 2026-11-08 audit's B1).
 */
internal fun futaberNgRuleListModel(
    rows: List<FutaberNgRow>,
    hint: String,
    onDeleteNgRule: (String) -> Unit,
    onDeleteNgRules: (Collection<String>) -> Unit
): FutaberNgListModel = FutaberNgListModel(
    rows = futaberNgRows(rows), onAdd = null, canAdd = { false },
    onRemove = { onDeleteNgRule(it.id) },
    onRemoveAll = { if (rows.isNotEmpty()) onDeleteNgRules(rows.map(FutaberNgRow::id)) },
    hint = hint
)

/** The model of each list: its rows, how an entry is added, taken back, and how the whole list is emptied. */
@Composable
internal fun futaberSettingsNgListModel(
    kind: FutaberNgListKind,
    ngWords: List<String>,
    ngHeaders: List<String>,
    catalogNgWords: List<String>,
    onEditNgWords: ((List<String>) -> List<String>) -> Unit,
    onEditNgHeaders: ((List<String>) -> List<String>) -> Unit,
    onEditCatalogNgWords: ((List<String>) -> List<String>) -> Unit,
    watchWords: List<String>,
    boardWatchWordCount: Int,
    onEditWatchWords: (((List<String>) -> List<String>) -> Unit)?,
    catalogThreadRules: List<CompatNgRule>,
    imageNgRules: List<CompatNgRule>,
    catalogWordRules: List<CompatNgRule>,
    catalogImageRules: List<CompatNgRule>,
    threadDetailRules: List<CompatNgRule>,
    threadNgScopeTitles: (Set<String>) -> Map<String, String>,
    onDeleteNgRule: (String) -> Unit,
    /** 「すべて削除」: one write for the whole list (deleting one by one rewrote the store per rule). */
    onDeleteNgRules: (Collection<String>) -> Unit,
    ngThreadTarget: (String) -> FutaberNgThreadTarget?,
    onAddNgThread: ((FutaberNgThreadTarget) -> Unit)?
): FutaberNgListModel {
    fun words(entries: List<String>, edit: ((List<String>) -> List<String>) -> Unit, hint: String = kind.hint) = FutaberNgListModel(
        rows = futaberNgRows(entries.map { FutaberNgRow(it, it) }),
        onAdd = { input -> edit { latest -> futaberAddNgEntry(latest, input) } },
        canAdd = { input -> futaberAddNgEntry(entries, input) !== entries },
        onRemove = { row -> edit { latest -> futaberRemoveNgEntry(latest, row.id) } },
        onRemoveAll = { edit { latest -> latest.filterNot { it in entries } } },
        hint = hint
    )
    fun rules(rows: List<FutaberNgRow>) = futaberNgRuleListModel(rows, kind.hint, onDeleteNgRule, onDeleteNgRules)
    return when (kind) {
        FutaberNgListKind.ResImages -> rules(imageNgRules.mapIndexed { index, rule -> FutaberNgRow(rule.id, "${index + 1}. " + futaberImageNgRuleLabel(rule)) })
        FutaberNgListKind.CatalogThreads -> {
            val base = rules(catalogThreadRules.map { FutaberNgRow(it.id, futaberCatalogThreadRuleLabel(it)) })
            if (onAddNgThread == null) base else FutaberNgListModel(
                rows = base.rows,
                onAdd = { input -> ngThreadTarget(input)?.let(onAddNgThread) },
                canAdd = { input ->
                    val target = ngThreadTarget(input)
                    target != null && catalogThreadRules.none { it.scopeKey == target.scopeKey && it.normalizedValue.equals(target.threadUrl, ignoreCase = true) }
                },
                onRemove = base.onRemove, onRemoveAll = base.onRemoveAll, hint = base.hint
            )
        }
        FutaberNgListKind.CatalogWordRules -> rules(catalogWordRules.map { FutaberNgRow(it.id, futaberCatalogWordRuleLabel(it)) })
        FutaberNgListKind.CatalogImages -> rules(catalogImageRules.map { FutaberNgRow(it.id, futaberCatalogImageRuleLabel(it)) })
        FutaberNgListKind.ThreadDetails -> {
            // Worked out off the main thread, only for the scopes the rules use (L110: every history row was keyed in
            // composition when the list opened).
            val wanted = remember(threadDetailRules) { threadDetailRules.mapNotNullTo(HashSet()) { it.scopeKey.takeIf { k -> k != "*" } } }
            val titles by androidx.compose.runtime.produceState(emptyMap<String, String>(), wanted) {
                value = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) { threadNgScopeTitles(wanted) }
            }
            rules(threadDetailRules.map { FutaberNgRow(it.id, futaberThreadDetailNgRuleLabel(it, titles)) })
        }
        FutaberNgListKind.CatalogWords -> words(catalogNgWords, onEditCatalogNgWords)
        FutaberNgListKind.ResWords -> words(ngWords, onEditNgWords)
        // C37: one shared list shown apart; each page adds and empties only its own part of it.
        FutaberNgListKind.ResNames -> words(ngHeaders.filterNot(::futaberNgHeaderIsIdIp), onEditNgHeaders).let { names ->
            FutaberNgListModel(
                names.rows, names.onAdd,
                canAdd = { input -> futaberAddNgEntry(ngHeaders, input) !== ngHeaders },
                onRemove = names.onRemove,
                onRemoveAll = { onEditNgHeaders { latest -> latest.filter(::futaberNgHeaderIsIdIp) } },
                hint = names.hint
            )
        }
        FutaberNgListKind.ResIdIp -> {
            val ids = ngHeaders.filter(::futaberNgHeaderIsIdIp)
            FutaberNgListModel(
                rows = futaberNgRows(ids.map { FutaberNgRow(it, it) }),
                onAdd = { input -> onEditNgHeaders { latest -> futaberAddNgEntry(latest, futaberNgIdIpEntry(input)) } },
                canAdd = { input -> input.isNotBlank() && futaberAddNgEntry(ngHeaders, futaberNgIdIpEntry(input)) !== ngHeaders },
                onRemove = { row -> onEditNgHeaders { latest -> futaberRemoveNgEntry(latest, row.id) } },
                onRemoveAll = { onEditNgHeaders { latest -> latest.filterNot(::futaberNgHeaderIsIdIp) } },
                hint = kind.hint
            )
        }
        FutaberNgListKind.WatchWords -> words(watchWords, onEditWatchWords ?: {}, futaberWatchWordsHint(boardWatchWordCount))
    }
}

/** One NG list as futaber's table (C45). */
@Composable
internal fun ColumnScope.FutaberSettingsNgList(
    kind: FutaberNgListKind,
    model: FutaberNgListModel,
    editing: Boolean,
    onStopEditing: () -> Unit,
    onAlert: (FutaberSettingsAlertRequest) -> Unit
) {
    var input by rememberSaveable(kind) { mutableStateOf("") }
    val add = model.onAdd
    val submit: () -> Unit = { if (add != null && model.canAdd(input)) { add(input); input = "" } }
    // Both namespaces use saveable String keys. Prefix every row ID as well as every chrome key:
    // words/rule IDs such as "hint" or even "chrome:hint" must stay editable without key collisions.
    LazyColumn(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
        if (add != null) {
            item(key = "chrome:new") {
                Column {
                    FutaberTableHeading("新規")
                    FutaberTableGroupEdge()
                    FutaberNgInputRow(input, { input = futaberSafeTake(it, FUTABER_NG_MAX_LENGTH) }, model.canAdd(input), submit)
                    FutaberTableGroupEdge()
                }
            }
        }
        if (model.hint.isNotEmpty()) item(key = "chrome:hint") { FutaberTableNote(model.hint, tag = "futaber-ng-hint") }
        item(key = "chrome:added") {
            Column {
                FutaberTableHeading("追加済み")
                if (model.rows.isNotEmpty()) FutaberTableGroupEdge()
            }
        }
        if (model.rows.isEmpty()) {
            item(key = "chrome:empty") {
                Text(
                    "登録はありません", color = LocalFutaberColors.current.settingsHeader, fontSize = FUTABER_TABLE_TEXT_SP.sp,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("futaber-ng-empty")
                )
            }
        } else {
            futaberNgRowItems(model.rows, editing, model.onRemove)
            item(key = "chrome:end") { FutaberTableGroupEdge() }
            if (editing) {
                item(key = "chrome:delete-all") {
                    FutaberTableDeleteAll {
                        onAlert(
                            FutaberSettingsAlertRequest(
                                title = "すべて削除しますか？", message = "「${kind.title}」の${model.rows.size}件を削除します",
                                confirmLabel = "削除", destructive = true
                            ) { model.onRemoveAll(); onStopEditing() }
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.futaberNgRowItems(rows: List<FutaberNgRow>, editing: Boolean, onRemove: (FutaberNgRow) -> Unit) {
    itemsIndexed(rows, key = { _, row -> "entry:${row.id}" }) { index, row ->
        FutaberTableEntryRow(row.label, editing, last = index == rows.lastIndex) { onRemove(row) }
    }
}

/** The field of 「新規」: futaber's placeholder 「追加」, the keyboard's enter adds; 「追加」 at the right does the same. */
@Composable
private fun FutaberNgInputRow(input: String, onInput: (String) -> Unit, canAdd: Boolean, onSubmit: () -> Unit) {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.settingsRow).heightIn(min = 50.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = input,
            onValueChange = onInput,
            singleLine = true,
            textStyle = TextStyle(color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, fontFamily = LocalFutaberCustomFont.current),
            cursorBrush = SolidColor(colors.iosBlue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp).semantics { contentDescription = "追加する語句" }.testTag("futaber-ng-input"),
            decorationBox = { inner ->
                Box { if (input.isEmpty()) Text("追加", color = colors.meta, fontSize = FUTABER_TABLE_TEXT_SP.sp); inner() }
            }
        )
        if (input.isNotBlank()) {
            Box(
                Modifier.heightIn(min = 44.dp).clickable(enabled = canAdd, onClickLabel = "追加", role = Role.Button, onClick = onSubmit)
                    .padding(horizontal = 12.dp).testTag("futaber-ng-add"),
                contentAlignment = Alignment.Center
            ) { Text("追加", color = if (canAdd) colors.iosBlue else colors.meta, fontSize = FUTABER_TABLE_TEXT_SP.sp) }
        }
    }
}

/** A grey section heading inside a lazy list (the same as [FutaberTableSection]'s). */
@Composable
internal fun FutaberTableHeading(title: String, gap: androidx.compose.ui.unit.Dp = 28.dp) {
    Text(
        title, color = LocalFutaberColors.current.settingsHeader, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = gap, bottom = 8.dp).semantics { heading() }
    )
}

/** The hairline at the top and bottom of a group. */
@Composable
internal fun FutaberTableGroupEdge() {
    HorizontalDivider(color = LocalFutaberColors.current.settingsSeparator, thickness = 0.5.dp)
}

/**
 * An entry of a list. While [editing], the red ⊖ before it takes it back (read as 「「label」を削除」); the hairline is
 * inset 16 (or after the ⊖).
 */
@Composable
internal fun FutaberTableEntryRow(label: String, editing: Boolean, last: Boolean, dim: Boolean = false, onRemove: () -> Unit) {
    val colors = LocalFutaberColors.current
    Column(Modifier.fillMaxWidth().background(colors.settingsRow)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = if (editing) 8.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                Box(
                    Modifier.size(44.dp).clickable(onClickLabel = "「$label」を削除", role = Role.Button, onClick = onRemove)
                        .semantics { contentDescription = "「$label」を削除" }.testTag("futaber-ng-delete"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(colors.destructive), contentAlignment = Alignment.Center) {
                        FutaberIcon(Icons.Outlined.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(
                label, color = if (dim) colors.meta else colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
        }
        if (!last) HorizontalDivider(
            color = colors.settingsSeparator, thickness = 0.5.dp, modifier = Modifier.padding(start = if (editing) 52.dp else 16.dp)
        )
    }
}

/** 「すべて削除」 at the bottom of an editing list (futaber's, in the blue). */
@Composable
internal fun FutaberTableDeleteAll(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(min = 44.dp).clickable(onClickLabel = "すべて削除", role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp).testTag("futaber-ng-delete-all"),
            contentAlignment = Alignment.Center
        ) { Text("すべて削除", color = LocalFutaberColors.current.iosBlueOnSheet, fontSize = 17.sp, textAlign = TextAlign.Center) }
    }
}

/**
 * C11: futaber's 新規スレッド通知. A section for every board (the shared patrol's keywords without a board) and one for each
 * board of the shared store, each with its keywords and 「＋ 新規キーワード」 (an alert with a field). 「編集」 gives each keyword
 * its ⊖. No limit of three boards (look plan §1.6). The patrol's other settings stay in 「キーワード通知・巡回」 (the last row).
 */
@Composable
internal fun ColumnScope.FutaberNewThreadNotifyPage(
    store: CompatibilityStore,
    scope: CoroutineScope,
    editing: Boolean,
    onNotice: (String) -> Unit,
    onShareBoards: () -> Unit,
    onOpenPatrol: () -> Unit,
    onAlert: (FutaberSettingsAlertRequest) -> Unit
) {
    LaunchedEffect(Unit) { onShareBoards() }
    val preferences by store.preferences.collectAsState(emptyMap())
    val boards by store.boards.collectAsState(emptyList())
    val sections = remember(preferences, boards) { futaberWatchSections(compatWatchRules(preferences), boards) }
    val repository = remember(store) { CompatWatcherRepository(store) }
    // Each edit is applied to the rules stored at that moment (two quick edits both count).
    val edit: (suspend (List<com.valoser.futacha.shared.compat.CompatWatchRule>) -> List<com.valoser.futacha.shared.compat.CompatWatchRule>) -> Unit = { change ->
        scope.launch {
            try { repository.saveRules(change(compatWatchRules(store.preferences.first()))) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) { onNotice(error.message ?: FUTABER_STORE_FAILURE_NOTICE) }
        }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().testTag("futaber-settings-new-thread-notify")) {
        sections.forEachIndexed { sectionIndex, section ->
            item(key = "head:$sectionIndex:${section.boardKey}") {
                Column { FutaberTableHeading(section.title); FutaberTableGroupEdge() }
            }
            itemsIndexed(section.words, key = { _, word -> futaberWatchWordRowKey(sectionIndex, section.boardKey, word.index) }) { _, word ->
                FutaberTableEntryRow(if (word.enabled) word.word else "${word.word}（オフ）", editing, last = false, dim = !word.enabled) {
                    edit { latest ->
                        val at = latest.indexOfFirst { it.boardKey == section.boardKey && it.word == word.word }
                        if (at < 0) latest else latest.toMutableList().also { it.removeAt(at) }
                    }
                }
            }
            item(key = "add:$sectionIndex:${section.boardKey}") {
                Column {
                    FutaberWatchAddRow(sectionIndex) {
                        onAlert(
                            FutaberSettingsAlertRequest(
                                title = "新規キーワード", message = section.title, confirmLabel = "追加", fieldPlaceholder = "キーワード"
                            ) { word -> edit { latest -> futaberWatchAdd(latest, section.boardKey, word) } }
                        )
                    }
                    FutaberTableGroupEdge()
                }
            }
        }
        item(key = "note") { FutaberTableNote(futaberNewThreadNotifyNoteLines.joinToString("\n"), tag = "futaber-settings-new-thread-notify-note") }
        item(key = "patrol") {
            Column {
                Spacer(Modifier.height(20.dp))
                FutaberTableGroupEdge()
                Box(Modifier.background(LocalFutaberColors.current.settingsRow)) {
                    FutaberTableNavRow("キーワード通知・巡回", "futaber-settings-row-patrol-detail", last = true, onClick = onOpenPatrol)
                }
                FutaberTableGroupEdge()
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** futaber's 「＋ 新規キーワード」: the green ⊕ and the label. */
@Composable
private fun FutaberWatchAddRow(sectionIndex: Int, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.settingsRow).heightIn(min = 50.dp)
            .clickable(onClickLabel = "新規キーワードを挿入", role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp).testTag("futaber-watch-add-$sectionIndex"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(colors.switchOn), contentAlignment = Alignment.Center) {
            FutaberIcon(Icons.Outlined.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text("新規キーワード", color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

/**
 * The list key of a watch word's row: the section's place is part of it, so two sections of the same board (two stored
 * keys of one board) never give two rows one key, which made the list throw (audit B6).
 */
internal fun futaberWatchWordRowKey(sectionIndex: Int, boardKey: String?, wordIndex: Int): String = "word:$sectionIndex:$boardKey:$wordIndex"
