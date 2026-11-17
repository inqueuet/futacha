package com.valoser.futacha.shared.ui.futaber

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.Post

/** One round button of the operation menu. [active] marks a toggle that is currently on (said to a screen reader). */
internal data class FutaberMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val onClick: () -> Unit
)

/**
 * What the menu's search word does. By default the original's (M08): while the field is used the grid gives way to the
 * found posts' first lines, and the search key (or a line) shows them as a bubble ([onShowFound]); the word is
 * forgotten when the menu closes. With the old behaviour switched on ([legacy], 設定→スレッド「従来の動き」) the word
 * narrows the thread as before ([legacyQuery], with the "N件が一致" count) and the grid stays.
 */
internal class FutaberMenuSearch(
    val legacy: Boolean,
    val legacyQuery: String,
    val onLegacyQueryChange: (String) -> Unit,
    val legacyResultCount: Int?,
    val posts: List<Post>,
    val hidden: Set<String>,
    val texts: FutaberPostTexts?,
    val onShowFound: (List<Post>) -> Unit
)

// The original's card (P/out-menu/40-01-menu.txt): 8 from the sides, its bottom on the bar's top; inside, 20 at the
// sides, 30 over the 30-high search field, 20 under it, the 300-high grid and 60 under that.
private const val MENU_CARD_SIDE_DP = 8
private const val MENU_INNER_SIDE_DP = 20
private const val MENU_INNER_TOP_DP = 30
private const val MENU_FIELD_HEIGHT_DP = 30
private const val MENU_FIELD_GAP_DP = 20
private const val MENU_INNER_BOTTOM_DP = 60
internal const val FUTABER_MENU_CELL_HEIGHT_DP = 100
internal const val FUTABER_MENU_CIRCLE_DP = 50
private const val MENU_LABEL_WIDTH_DP = 50
private const val MENU_LABEL_HEIGHT_DP = 30

/**
 * The operation menu (V33・M54): a flat white card whose bottom is the bottom bar's top, over a black veil of 40% that
 * covers the whole screen, the bar included (the bar does not react while the menu is open; a tap on the veil closes
 * the menu). The search field on top, then the grid of round buttons in four columns and three rows (80×100 cells, a
 * 50 circle, a 50×30 label broken by its width, V34); more items scroll inside the grid, and the whole card scrolls
 * when the screen is too short for it (landscape, large text). iOS's edge swipe does not close it (G34); Android's
 * system back does. Drawn by the thread in its layer over the whole screen.
 */
@Composable
internal fun FutaberOperationMenu(
    items: List<FutaberMenuItem?>,
    search: FutaberMenuSearch,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchScope = androidx.compose.runtime.rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // F-B2: after the original's eleven slots the 12th cell is 「その他の機能」; it opens what this app adds (the cells
    // after the eleventh, futaberOperationMenuExtras) as the second step, in the same grid.
    var showMore by remember { mutableStateOf(false) }
    val fold = LocalFutaberDisplaySettings.current.foldExtras
    val extraCells = remember(items, fold) { if (fold) items.drop(FUTABER_MENU_SLOT_IDS.size).filterNotNull() else emptyList() }
    val cells: List<FutaberMenuItem?> = when {
        extraCells.isEmpty() -> items
        showMore -> extraCells
        else -> items.take(FUTABER_MENU_SLOT_IDS.size) + FutaberMenuItem(FUTABER_MORE_ACTION_ID, FUTABER_MORE_LABEL, Icons.Outlined.Tune) { showMore = true }
    }
    val searching = !search.legacy && (focused || query.isNotEmpty())
    val cancelSearch: () -> Unit = {
        query = ""
        focusManager.clearFocus()
        keyboard?.hide()
    }
    // Back (Android) leaves the search first, then closes the menu; the edge swipe of iOS does neither (G34).
    FutaberBackHandler(onBack = { if (searching) cancelSearch() else if (showMore) showMore = false else onDismiss() }, iosEdgeGestureEnabled = false)
    // The card stands on the bar's top (the bar is 44 over the navigation bar), or on the keyboard when it is higher.
    val barBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() } + FUTABER_BOTTOM_BAR_HEIGHT_DP.dp
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val bottom = if (imeBottom > barBottom) imeBottom else barBottom
    val top = with(density) { WindowInsets.statusBars.getTop(density).toDp() } + FUTABER_THREAD_TITLE_ROW_DP.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A sibling of the card, so a clickable parent does not merge the card's semantics. It covers the bar too.
        // A tap closes the menu; a swipe across the veil (the edge swipe of iOS, G34) does not.
        Box(
            Modifier.fillMaxSize()
                .background(colors.scrim)
                .futaberTapOnly(onDismiss)
                .semantics { onClick(label = "操作メニューを閉じる") { onDismiss(); true } }
                .testTag("futaber-menu-scrim")
        )
        val room = (maxHeight - top - bottom).coerceAtLeast(0.dp)
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = MENU_CARD_SIDE_DP.dp, end = MENU_CARD_SIDE_DP.dp, bottom = bottom)
                .fillMaxWidth()
                // Searching, the card reaches up to the title row (futaber's 40-02); else it wraps the grid.
                .then(if (searching) Modifier.height(room) else Modifier.heightIn(max = room))
                .testTag("futaber-operation-menu"),
            // The original's card: white, flat (no shadow), corners of about 10.
            shape = FutaberShapes.card,
            color = colors.menuCard,
            shadowElevation = 0.dp
        ) {
            // One field for both states, so typing goes on when the card turns into the search list.
            Column(Modifier.padding(top = MENU_INNER_TOP_DP.dp)) {
                MenuSearchRow(
                    query = if (search.legacy) search.legacyQuery else query,
                    onQueryChange = if (search.legacy) search.onLegacyQueryChange else { next: String -> query = next },
                    onFocus = { focused = it },
                    searching = searching,
                    onSubmit = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        when {
                            // The old behaviour: the search key puts the keyboard away and shows the narrowed thread.
                            search.legacy -> onDismiss()
                            query.isNotBlank() -> {
                                // Matched off the main thread: a long thread's search took a moment (audit B7).
                                val submitted = query
                                searchScope.launch {
                                    val found = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                                        futaberSearchMatches(search.posts, submitted, search.hidden, search.texts)
                                    }
                                    search.onShowFound(found)
                                }
                            }
                        }
                    },
                    onCancel = cancelSearch
                )
                if (search.legacy) search.legacyResultCount?.let {
                    Text(
                        "${it}件が一致", color = colors.meta, fontSize = 12.sp,
                        modifier = Modifier.padding(start = (MENU_INNER_SIDE_DP + 4).dp, top = 6.dp).testTag("futaber-menu-result-count")
                    )
                }
                if (searching) {
                    MenuSearchResults(query, search) { post -> search.onShowFound(listOf(post)) }
                } else {
                    // The grid and the space under it; on a screen too short for the card (landscape, large text) they scroll.
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                            .padding(start = MENU_INNER_SIDE_DP.dp, end = MENU_INNER_SIDE_DP.dp, top = MENU_FIELD_GAP_DP.dp, bottom = MENU_INNER_BOTTOM_DP.dp)
                    ) {
                        if (showMore) Text(
                            FUTABER_MORE_LABEL, color = colors.meta, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).testTag("futaber-menu-more-title")
                        )
                        MenuGrid(cells)
                    }
                }
            }
        }
    }
}

/** A tap (the finger lifted where it went down, within the touch slop) runs [onTap]; a drag does not. */
private fun Modifier.futaberTapOnly(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val up = waitForUpOrCancellation()
        if (up != null && (up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
            up.consume()
            onTap()
        }
    }
}

/** The grid: four columns, three rows in sight; the items after the 12th scroll inside it. */
@Composable
private fun MenuGrid(items: List<FutaberMenuItem?>) {
    Column(
        Modifier.fillMaxWidth().height((FUTABER_MENU_CELL_HEIGHT_DP * FUTABER_MENU_VISIBLE_ROWS).dp)
            .verticalScroll(rememberScrollState()).testTag("futaber-menu-grid")
    ) {
        items.chunked(FUTABER_MENU_COLUMNS).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    if (item == null) Box(Modifier.weight(1f)) else MenuButton(item, Modifier.weight(1f))
                }
                repeat(FUTABER_MENU_COLUMNS - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** The search field (30 high, grey, rounded) and, while searching, 「キャンセル」 on its right (futaber's 40-02). */
@Composable
private fun MenuSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    searching: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalFutaberColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = MENU_INNER_SIDE_DP.dp), verticalAlignment = Alignment.CenterVertically) {
        MenuSearchField(query, onQueryChange, onFocus, onSubmit, Modifier.weight(1f))
        if (searching) {
            Text(
                "キャンセル", color = colors.body, fontSize = 15.sp,
                modifier = Modifier.clickable(onClickLabel = "検索をやめる", onClick = onCancel)
                    .padding(start = 18.dp, top = 6.dp, bottom = 6.dp).testTag("futaber-menu-search-cancel")
            )
        }
    }
}

@Composable
private fun MenuSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier
) {
    val colors = LocalFutaberColors.current
    Row(
        modifier.clip(FutaberShapes.field).background(colors.menuSearchField)
            .heightIn(min = MENU_FIELD_HEIGHT_DP.dp).padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FutaberIcon(Icons.Outlined.Search, contentDescription = null, tint = colors.menuSearchPlaceholder, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.body, fontSize = 15.sp, fontFamily = LocalFutaberCustomFont.current),
                cursorBrush = SolidColor(colors.iosBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { onFocus(it.isFocused) }
                    .semantics { contentDescription = "スレッド内を検索" }
                    .testTag("futaber-menu-search"),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("検索ワード", color = colors.menuSearchPlaceholder, fontSize = 15.sp, maxLines = 1)
                    inner()
                }
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).clickable(onClickLabel = "検索ワードを消す") { onQueryChange("") }
                    .testTag("futaber-menu-search-clear"),
                contentAlignment = Alignment.Center
            ) {
                FutaberIcon(Icons.Outlined.Close, contentDescription = "検索ワードを消す", tint = colors.menuSearchPlaceholder, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** While typing: 「Nレスが見つかりました」 and the found posts' first lines; a line shows that post as a bubble. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MenuSearchResults(
    query: String,
    search: FutaberMenuSearch,
    onOpen: (Post) -> Unit
) {
    val colors = LocalFutaberColors.current
    val applied = rememberFutaberDebouncedQuery(query)
    // Matched off the main thread (it ran over every post on each word typed; audit B7). Shown only once the result
    // is for the word in the field, so a count never belongs to the word before.
    val result by androidx.compose.runtime.produceState<Pair<String, List<Post>>?>(null, search.posts, applied, search.hidden, search.texts) {
        value = if (applied.isBlank()) null else applied to kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
            futaberSearchMatches(search.posts, applied, search.hidden, search.texts)
        }
    }
    if (applied.isBlank()) return
    val found = result?.takeIf { it.first == applied }?.second ?: return
    Text(
        futaberSearchFoundLabel(found.size), color = colors.meta, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp).testTag("futaber-menu-result-count")
    )
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("futaber-menu-search-results")) {
        itemsIndexed(found, key = { index, post -> "${post.id}#$index" }) { _, post ->
            Column(Modifier.fillMaxWidth().clickable(onClickLabel = "このレスを表示") { onOpen(post) }.testTag("futaber-menu-search-result")) {
                Text(
                    futaberSearchMatchLine(post), color = colors.body, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 13.dp, bottom = 13.dp)
                )
                HorizontalDivider(Modifier.padding(start = 24.dp), color = colors.separator, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun MenuButton(item: FutaberMenuItem, modifier: Modifier) {
    val colors = LocalFutaberColors.current
    val iconSize = if (item.id in FUTABER_MENU_TEXT_ICON_IDS) 30.dp else 24.dp
    Column(
        modifier
            .heightIn(min = FUTABER_MENU_CELL_HEIGHT_DP.dp)
            .clickable(enabled = item.enabled, onClickLabel = item.label, onClick = item.onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = item.label
                if (item.active) stateDescription = "オン"
            }
            .testTag("futaber-menu-${item.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            // A thin grey ring on the card, as in the original (whose buttons keep the same look when on).
            Modifier.padding(top = 5.dp).size(FUTABER_MENU_CIRCLE_DP.dp).clip(CircleShape)
                .background(colors.menuCard)
                .border(1.dp, colors.menuCircleBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FutaberIcon(item.icon, contentDescription = null, tint = if (item.enabled) colors.icon else colors.iconDisabled, modifier = Modifier.size(iconSize))
        }
        // V34: the label is broken by its width (50), character by character for Japanese, as the original's
        // ("画像を小さ／くする"): no line break is put in by hand.
        Box(Modifier.padding(top = 4.dp).width(MENU_LABEL_WIDTH_DP.dp).heightIn(min = MENU_LABEL_HEIGHT_DP.dp), contentAlignment = Alignment.Center) {
            Text(
                item.label,
                color = if (item.enabled) colors.body else colors.meta,
                style = TextStyle(fontSize = FUTABER_MENU_LABEL_SP.sp, lineHeight = 1.2.em, lineBreak = LineBreak.Simple, textAlign = TextAlign.Center),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("futaber-menu-label")
            )
        }
    }
}

/** The labels' size: five full-width characters fit the 50 width (futaber's are about 9.2pt, 46 for five). */
internal const val FUTABER_MENU_LABEL_SP = 9.5f
