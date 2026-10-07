package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One round button of the operation menu. [active] marks a toggle that is currently on. */
internal data class FutaberMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val onClick: () -> Unit
)

private const val MENU_COLUMNS = 4

/**
 * The card above the bottom bar: a search word field and a four-column grid of round
 * buttons. The rest of the screen dims; tapping it closes the menu.
 */
@Composable
internal fun FutaberOperationMenu(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int?,
    items: List<FutaberMenuItem>,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A sibling of the card, so a clickable parent does not merge the card's semantics.
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
                .clickable(onClickLabel = "操作メニューを閉じる", onClick = onDismiss)
                .testTag("futaber-menu-scrim")
        )
        Surface(
            // Taller than the screen only when extensions add rows on a small phone: then the grid scrolls.
            // Lifted above the keyboard while the search box is being typed into, so the box and the grid stay in view.
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().heightIn(max = maxHeight).padding(8.dp)
                .testTag("futaber-operation-menu"),
            shape = FutaberShapes.sheet,
            color = colors.background,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(FutaberShapes.pill).background(colors.catalogGap)
                        .heightIn(min = 44.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FutaberIcon(Icons.Outlined.Search, contentDescription = null, tint = colors.meta)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = colors.body, fontSize = 15.sp),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                // The search key puts the keyboard away and shows the result (the menu closes, the filter stays).
                                focusManager.clearFocus()
                                keyboard?.hide()
                                onDismiss()
                            }),
                            modifier = Modifier.fillMaxWidth()
                                .semantics { contentDescription = "スレッド内を検索" }
                                .testTag("futaber-menu-search"),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("検索ワード", color = colors.meta, fontSize = 15.sp)
                                inner()
                            }
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("futaber-menu-search-clear")) {
                            FutaberIcon(Icons.Outlined.Close, contentDescription = "検索ワードを消す", tint = colors.icon)
                        }
                    }
                }
                resultCount?.let {
                    Text(
                        "${it}件が一致", color = colors.meta, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp).testTag("futaber-menu-result-count")
                    )
                }
                Column(
                    Modifier.weight(1f, fill = false).padding(top = 12.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items.chunked(MENU_COLUMNS).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth()) {
                            rowItems.forEach { item -> MenuButton(item, Modifier.weight(1f)) }
                            repeat(MENU_COLUMNS - rowItems.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuButton(item: FutaberMenuItem, modifier: Modifier) {
    val colors = LocalFutaberColors.current
    val tint = when {
        !item.enabled -> colors.meta
        item.active -> colors.onAction
        else -> colors.icon
    }
    Column(
        modifier
            .clickable(enabled = item.enabled, onClickLabel = item.label, onClick = item.onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = item.label
                if (item.active) stateDescription = "オン"
            }
            .padding(horizontal = 2.dp)
            .testTag("futaber-menu-${item.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (item.active) colors.action else colors.background)
                .border(1.dp, if (item.active) colors.action else colors.separator, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FutaberIcon(item.icon, contentDescription = null, tint = tint)
        }
        Text(
            futaberBalancedLabel(item.label),
            color = if (item.enabled) colors.body else colors.meta,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = if (item.active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private const val LABEL_ONE_LINE_CHARS = 6
private const val LABEL_BREAK_AFTER = "をにがのはへでと"
private const val LABEL_BREAK_TOLERANCE = 1.5
private val LABEL_UNBREAKABLE = listOf("お気に入り", "スクロール", "コピー")

/**
 * Two balanced lines for a long label, broken after a particle close to the middle and never
 * inside a word, as the original app's menu does ("URLを／コピーする", "お気に入り／に追加").
 * A short label stays on one line.
 */
internal fun futaberBalancedLabel(label: String): String {
    if (label.length <= LABEL_ONE_LINE_CHARS) return label
    val middle = label.length / 2.0
    val afterParticle = label.indices
        .filter { it in 2..label.length - 3 && label[it] in LABEL_BREAK_AFTER }
        .map { it + 1 }
        .minByOrNull { kotlin.math.abs(it - middle) }
        ?.takeIf { kotlin.math.abs(it - middle) <= LABEL_BREAK_TOLERANCE }
    var cut = afterParticle ?: (label.length / 2)
    // A cut inside a word moves to the end of that word.
    LABEL_UNBREAKABLE.forEach { word ->
        var from = label.indexOf(word)
        while (from >= 0) {
            if (cut > from && cut < from + word.length) cut = from + word.length
            from = label.indexOf(word, from + 1)
        }
    }
    if (cut <= 0 || cut >= label.length) return label
    return label.substring(0, cut) + "\n" + label.substring(cut)
}
