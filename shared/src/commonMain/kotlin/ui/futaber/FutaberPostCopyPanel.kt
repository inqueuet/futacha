package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.Post

/** The copy panel takes the lower half of the screen (`F2:post-sheet-023`: 333.5pt of the SE's 667). */
internal const val FUTABER_COPY_PANEL_HEIGHT_FRACTION = 0.5f

/** The items of the panel's selection menu, as the original's edit menu lists them (コピー／共有…／引用／NGに追加). */
internal enum class FutaberCopyMenuItem(val id: String, val label: String) {
    Copy("copy", "コピー"),
    Share("share", "共有…"),
    Quote("quote", "引用"),
    Ng("ng", "NGに追加")
}

/**
 * M23: the original's copy panel for a post's 「コピー」 (`F2:post-sheet-023`, `P/out-press/60-02`): the thread veiled
 * white above, the lower half a white panel with × at the top left and 「すべてコピー」 at the top right, the post's
 * header line ([futaberCopyPanelText]) in grey and its body under it, the body selected when it opens. While text is
 * selected the panel's own menu sits over the text: コピー, 共有…, 引用 (into the writing screen) and NGに追加 (the NG
 * dialog with the words ready). The text cannot be edited; its selection can be changed (a long press picks a word, the
 * handles move it), and the menu works on what is selected.
 *
 * The text is a read-only field rather than a SelectionContainer: only a field's selection can be set when the panel
 * opens and read when a menu item is pressed. The platform's own selection toolbar is left out ([FutaberNoTextToolbar])
 * so the original's items are the only menu. [onCopyAll] copies the header and the body and closes the panel.
 */
@Composable
internal fun FutaberPostCopyPanel(
    post: Post,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onQuote: (String) -> Unit,
    onNg: (String) -> Unit
) {
    val colors = LocalFutaberColors.current
    val display = LocalFutaberDisplaySettings.current
    val model = remember(post) { futaberCopyPanelText(post) }
    val annotated = remember(model, colors) {
        buildAnnotatedString {
            if (model.header.isNotEmpty()) {
                withStyle(SpanStyle(color = colors.meta)) { append(model.header) }
                append('\n')
            }
            withStyle(SpanStyle(color = colors.body)) { append(model.body) }
        }
    }
    var value by remember(post.id) { mutableStateOf(TextFieldValue(annotated, TextRange(model.bodyStart, annotated.length))) }
    // The menu goes away after コピー / 共有… (as iOS's edit menu does) and comes back when the selection changes.
    var menuHidden by remember(post.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // The selection is drawn and its handles answer while the field has the focus (read-only: no keyboard comes up).
    LaunchedEffect(post.id) { runCatching { focus.requestFocus() } }
    FutaberBackHandler(onDismiss)
    val selection = value.selection
    val selected = if (selection.collapsed) "" else value.text.substring(selection.min, selection.max)
    Column(Modifier.fillMaxSize().testTag("futaber-copy-panel-layer")) {
        // The thread above, veiled white as in the original; a tap there closes the panel.
        Box(
            Modifier.fillMaxWidth().weight(1f - FUTABER_COPY_PANEL_HEIGHT_FRACTION)
                .background(colors.background.copy(alpha = FUTABER_COPY_PANEL_VEIL_ALPHA))
                .clickable(interactionSource = null, indication = null, onClickLabel = "閉じる", onClick = onDismiss)
                .testTag("futaber-copy-veil")
        )
        Column(
            Modifier.fillMaxWidth().weight(FUTABER_COPY_PANEL_HEIGHT_FRACTION)
                .background(colors.background)
                .border(0.5.dp, colors.separator)
                .navigationBarsPadding()
                .testTag("futaber-copy-panel")
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(90.dp).height(44.dp)
                        .clickable(onClickLabel = "閉じる", role = Role.Button, onClick = onDismiss)
                        .padding(start = 16.dp)
                        .testTag("futaber-copy-close"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    FutaberIcon(Icons.Outlined.Close, contentDescription = "閉じる", tint = colors.iosBlue, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.heightIn(min = 44.dp)
                        .clickable(onClickLabel = "すべてコピー", role = Role.Button) { onCopy(model.text); onDismiss() }
                        .padding(horizontal = 16.dp)
                        .testTag("futaber-copy-all"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("すべてコピー", color = colors.iosBlue, fontSize = 20.sp)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                CompositionLocalProvider(
                    LocalTextToolbar provides FutaberNoTextToolbar,
                    // The selection's ground is a light tint of the system blue (a ground, not text); the letters keep their colours.
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = colors.iosBlue, backgroundColor = colors.iosBlue.copy(alpha = 0.22f)
                    )
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { changed ->
                            if (changed.selection != value.selection) menuHidden = false
                            value = value.copy(selection = changed.selection)
                        },
                        readOnly = true,
                        textStyle = TextStyle(fontSize = display.sp(17f), lineHeight = 1.36.em, color = colors.body),
                        modifier = Modifier.fillMaxSize().focusRequester(focus)
                            .padding(horizontal = 5.dp, vertical = 8.dp)
                            .testTag("futaber-copy-text")
                    )
                }
                if (selected.isNotEmpty() && !menuHidden) {
                    FutaberCopyMenu(Modifier.align(Alignment.TopCenter)) { item ->
                        when (item) {
                            FutaberCopyMenuItem.Copy -> { onCopy(selected); menuHidden = true }
                            FutaberCopyMenuItem.Share -> { onShare(selected); menuHidden = true }
                            FutaberCopyMenuItem.Quote -> onQuote(futaberQuoteSelection(selected))
                            FutaberCopyMenuItem.Ng -> onNg(selected.trim())
                        }
                    }
                }
            }
        }
    }
}

/** How strongly the thread above the panel is veiled white (the original's thread goes pale behind the panel). */
private const val FUTABER_COPY_PANEL_VEIL_ALPHA = 0.7f

/** The selection menu: the original's edit menu items in a row, light, rounded, over the text. */
@Composable
private fun FutaberCopyMenu(modifier: Modifier, onItem: (FutaberCopyMenuItem) -> Unit) {
    val colors = LocalFutaberColors.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.padding(top = 4.dp).widthIn(max = 420.dp)
            .shadow(6.dp, shape).clip(shape).background(colors.fill)
            .testTag("futaber-copy-menu"),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FutaberCopyMenuItem.entries.forEachIndexed { index, item ->
            Box(
                Modifier.heightIn(min = 40.dp)
                    .drawBehind {
                        if (index > 0) drawLine(colors.separator, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 0.5.dp.toPx())
                    }
                    .clickable(onClickLabel = item.label, role = Role.Button) { onItem(item) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .testTag("futaber-copy-menu-${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text(item.label, color = colors.body, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}

/** No platform selection toolbar over the copy panel's text: the panel's own menu has the original's items. */
private object FutaberNoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun hide() = Unit
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) = Unit
}
