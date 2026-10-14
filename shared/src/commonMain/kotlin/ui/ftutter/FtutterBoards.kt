package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.buildAddBoardValidationState
import com.valoser.futacha.shared.ui.futaber.futaberDefaultBoardName
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 板の追加ダイアログの入力の種類。 */
internal sealed interface FtutterBoardInput {
    /** 掲示板全体（`www.2chan.net` のトップ・bbsmenu）：一括追加。 */
    data object AllBoards : FtutterBoardInput
    data class OneBoard(val url: String) : FtutterBoardInput
    data class Invalid(val message: String) : FtutterBoardInput
}

private val FTUTTER_ALL_BOARDS_URL =
    Regex("""^https?://www\.2chan\.net((/)|(/bbsmenu\.html)|(/index2\.html)|(/i\.htm))?$""", RegexOption.IGNORE_CASE)

/** 原作の判定（ItaAddDialog）：掲示板全体のURLなら一括追加、`http` で始まり `.2chan.net/` を含めば1板、それ以外は形式エラー。 */
internal fun classifyFtutterBoardInput(raw: String, existing: List<BoardSummary>): FtutterBoardInput {
    val input = raw.trim()
    if (FTUTTER_ALL_BOARDS_URL.matches(input)) return FtutterBoardInput.AllBoards
    if (!input.startsWith("http", ignoreCase = true) || !input.contains(".2chan.net/", ignoreCase = true)) {
        return FtutterBoardInput.Invalid("URLの形式が正しくありません")
    }
    // 原作は正規化前の文字列で重複を判定し、同じ板を二重に登録しうる。ここでは正規化したURLで判定する。
    // 板名は原作どおり入力させず、ページから取る。共有の検証からは URL の形と重複だけを使う。
    val state = buildAddBoardValidationState(name = "-", url = input, existingBoards = existing)
    if (!state.isValidUrl) return FtutterBoardInput.Invalid("URLの形式が正しくありません")
    if (state.isDuplicateUrl) return FtutterBoardInput.Invalid("この板は登録済みです")
    // 他のモードと同じ正規化（`…/板/futaba.php`）で保存する。原作どおり https にそろえる。
    val normalized = state.normalizedInputUrl
    return FtutterBoardInput.OneBoard(
        if (normalized.startsWith("http://", ignoreCase = true)) "https://" + normalized.substring(7) else normalized
    )
}

/** 原作の板名の取り方：`#tit` の中身から「＠ふたば」を除き、「二次元裏」にはホストの先頭を添える。 */
internal fun ftutterBoardNameFromPage(html: String, boardUrl: String): String? {
    val match = Regex("""id\s*=\s*["']?tit["']?[^>]*>(.*?)<""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        ?: return null
    val name = match.groupValues[1].replace(Regex("<[^>]*>"), "").trim().removeSuffix("＠ふたば").trim()
    if (name.isEmpty()) return null
    if (name == "二次元裏") {
        val host = boardUrl.substringAfter("://").substringBefore('.')
        return "二次元裏（$host）"
    }
    return name
}

/** 板のページを取得して名前を得る。取れない時は URL から付けた名前。 */
internal suspend fun fetchFtutterBoardName(httpClient: HttpClient?, boardUrl: String): String {
    val fallback = futaberDefaultBoardName(boardUrl)
    val client = httpClient ?: return fallback
    return try {
        val response = client.get(ftutterBoardBaseUrl(boardUrl) + "futaba.htm")
        if (!response.status.isSuccess()) return fallback
        val html = TextEncoding.decodeToString(response.bodyAsBytes(), response.contentType()?.toString())
        ftutterBoardNameFromPage(html, boardUrl) ?: fallback
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        fallback
    }
}

/** 区切りの帯（原作の DividerCard：「お気に入り」「2chan.net」）。 */
@Composable
internal fun FtutterDividerCard(text: String, modifier: Modifier = Modifier) {
    val colors = LocalFtutterColors.current
    Text(
        text,
        color = colors.onDividerCard,
        fontSize = 13.sp,
        modifier = modifier.fillMaxWidth().background(colors.dividerCard).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * 板一覧（原作の ItaSelect）。お気に入り（共有の板の「ピン留め」）を上、その他を下に分けて並べる。並びは保存順のまま。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FtutterBoardListScreen(
    boards: List<BoardSummary>,
    httpClient: HttpClient?,
    onOpenBoard: (BoardSummary) -> Unit,
    onOpenSettings: () -> Unit,
    onEditBoards: ((List<BoardSummary>) -> List<BoardSummary>) -> Unit,
    onBulkAdd: (suspend () -> Result<Int>)?,
    onOpenUrl: (String) -> Unit,
    onNotice: (String) -> Unit
) {
    val colors = LocalFtutterColors.current
    var adding by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<BoardSummary?>(null) }
    var confirmDelete by remember { mutableStateOf<BoardSummary?>(null) }
    var confirmMove by remember { mutableStateOf<Pair<BoardSummary, Boolean>?>(null) }
    val favorites = boards.filter { it.pinned }
    val others = boards.filterNot { it.pinned }
    val showUrl = boards.size > 1

    Column(Modifier.fillMaxSize().testTag("ftutter-boards")) {
        FtutterTopBar(title = "ふたったー") {
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("ftutter-open-settings")) {
                Icon(Icons.Rounded.Settings, contentDescription = "設定", tint = colors.onToolBar)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ftutter-board-list")) {
            item(key = "favorites-header") { FtutterDividerCard("お気に入り") }
            items(favorites, key = { "fav-" + it.id }) { board ->
                FtutterBoardRow(board, showUrl, onOpenBoard, onEditBoards, { menuFor = it }, { b, up -> confirmMove = b to up })
            }
            item(key = "others-header") { FtutterDividerCard("2chan.net") }
            items(others, key = { "other-" + it.id }) { board ->
                FtutterBoardRow(board, showUrl, onOpenBoard, onEditBoards, { menuFor = it }, { b, up -> confirmMove = b to up })
            }
        }
        Box(
            Modifier.fillMaxWidth().background(colors.toolBar).navigationBarsPadding().height(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { adding = true },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.highlight),
                modifier = Modifier.testTag("ftutter-add-board")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("板追加", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }

    if (adding) {
        FtutterAddBoardDialog(
            existing = boards,
            httpClient = httpClient,
            onDismiss = { adding = false },
            onAdd = { board ->
                onEditBoards { latest -> if (latest.any { ftutterBoardBaseUrl(it.url) == ftutterBoardBaseUrl(board.url) }) latest else latest + board }
                onNotice("板を追加しました")
                adding = false
            },
            onBulkAdd = onBulkAdd,
            onNotice = onNotice
        )
    }
    menuFor?.let { board ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(board.name) },
            text = {
                Column {
                    TextButton(onClick = { menuFor = null; onOpenUrl(ftutterBoardBaseUrl(board.url) + "futaba.htm") }, modifier = Modifier.fillMaxWidth()) {
                        Text("ブラウザで開く", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { menuFor = null; confirmDelete = board }, modifier = Modifier.fillMaxWidth().testTag("ftutter-board-delete")) {
                        Text("削除", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuFor = null }) { Text("閉じる") } }
        ) }
    }
    // 原作は確認なしで消す。誤操作で板を失わないよう確認を挟む（原作との差）。
    confirmDelete?.let { board ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text("「${board.name}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    onEditBoards { latest -> latest.filterNot { it.id == board.id } }
                    confirmDelete = null
                }, modifier = Modifier.testTag("ftutter-board-delete-confirm")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("キャンセル") } }
        ) }
    }
    confirmMove?.let { (board, toTop) ->
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { confirmMove = null },
            text = { Text(if (toTop) "一番上へ移動しますか？" else "一番下へ移動しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    onEditBoards { latest -> ftutterMoveBoardToEnd(latest, board.id, toTop) }
                    confirmMove = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { confirmMove = null }) { Text("キャンセル") } }
        ) }
    }
}

/** 1つ上・下へ（原作の swap）。お気に入りとその他の区分の中で入れ替える。 */
internal fun ftutterMoveBoard(boards: List<BoardSummary>, id: String, up: Boolean): List<BoardSummary> {
    val index = boards.indexOfFirst { it.id == id }
    if (index < 0) return boards
    val pinned = boards[index].pinned
    val step = if (up) -1 else 1
    var target = index + step
    while (target in boards.indices && boards[target].pinned != pinned) target += step
    if (target !in boards.indices) return boards
    return boards.toMutableList().apply { add(target, removeAt(index)) }
}

/** 一番上・下へ（原作の swapToTop／swapToBottom）。区分の中の端へ。 */
internal fun ftutterMoveBoardToEnd(boards: List<BoardSummary>, id: String, toTop: Boolean): List<BoardSummary> {
    val board = boards.firstOrNull { it.id == id } ?: return boards
    val rest = boards.filterNot { it.id == id }.toMutableList()
    val group = rest.withIndex().filter { it.value.pinned == board.pinned }.map { it.index }
    val insertAt = when {
        group.isEmpty() -> if (toTop) 0 else rest.size
        toTop -> group.first()
        else -> group.last() + 1
    }
    rest.add(insertAt, board)
    return rest
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FtutterBoardRow(
    board: BoardSummary,
    showUrl: Boolean,
    onOpenBoard: (BoardSummary) -> Unit,
    onEditBoards: ((List<BoardSummary>) -> List<BoardSummary>) -> Unit,
    onMenu: (BoardSummary) -> Unit,
    onMoveToEnd: (BoardSummary, Boolean) -> Unit
) {
    val colors = LocalFtutterColors.current
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = { onOpenBoard(board) }, onLongClick = { onMenu(board) })
            .padding(vertical = 4.dp)
            .testTag("ftutter-board-row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onEditBoards { latest -> latest.map { if (it.id == board.id) it.copy(pinned = !it.pinned) else it } } },
            modifier = Modifier.testTag("ftutter-board-star")
        ) {
            Icon(
                if (board.pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (board.pinned) "お気に入りから外す" else "お気に入りに追加",
                tint = colors.text
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.Center) {
            Text(board.name, color = colors.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showUrl) {
                Text(ftutterBoardBaseUrl(board.url), color = colors.headerText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        FtutterLongPressIcon(
            onClick = { onEditBoards { latest -> ftutterMoveBoard(latest, board.id, up = true) } },
            onLongClick = { onMoveToEnd(board, true) },
            tag = "ftutter-board-up"
        ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上へ", tint = colors.text) }
        FtutterLongPressIcon(
            onClick = { onEditBoards { latest -> ftutterMoveBoard(latest, board.id, up = false) } },
            onLongClick = { onMoveToEnd(board, false) },
            tag = "ftutter-board-down"
        ) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下へ", tint = colors.text) }
    }
}

/** タップと長押しを持つアイコン。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FtutterLongPressIcon(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier.size(48.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick).testTag(tag),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun FtutterAddBoardDialog(
    existing: List<BoardSummary>,
    httpClient: HttpClient?,
    onDismiss: () -> Unit,
    onAdd: (BoardSummary) -> Unit,
    onBulkAdd: (suspend () -> Result<Int>)?,
    onNotice: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("板追加") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    placeholder = { Text("板または掲示板のURL") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth().testTag("ftutter-add-board-input")
                )
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    when (val kind = classifyFtutterBoardInput(input, existing)) {
                        is FtutterBoardInput.Invalid -> error = kind.message
                        FtutterBoardInput.AllBoards -> {
                            val bulk = onBulkAdd
                            if (bulk == null) {
                                error = "この環境では板一覧を取得できません"
                            } else {
                                busy = true
                                scope.launch {
                                    val result = bulk()
                                    busy = false
                                    result.onSuccess { onNotice("追加しました"); onDismiss() }
                                        .onFailure { error = "板一覧を取得できませんでした" }
                                }
                            }
                        }
                        is FtutterBoardInput.OneBoard -> {
                            busy = true
                            scope.launch {
                                val name = fetchFtutterBoardName(httpClient, kind.url)
                                busy = false
                                onAdd(
                                    com.valoser.futacha.shared.ui.board.createCustomBoardSummary(
                                        name = name,
                                        url = kind.url,
                                        existingBoards = existing
                                    )
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("ftutter-add-board-ok")
            ) { Text("追加") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("キャンセル") } }
    ) }
}
