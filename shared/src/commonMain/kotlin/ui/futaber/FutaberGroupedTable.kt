package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * L-B8 (S16) builds the full-screen 書き込み設定 with these; they are the iOS grouped table of §3.6 (`FutaberGroupedTable`)
 * that L-B9 is to use for the whole settings screen. Measured on futaber's tables (iPhone SE 3, appendix A and
 * `P/out-post/90-12`): the page `settingsPage` (#F2F2F2), a section heading 13sp grey at 16 above the group, rows of
 * about 50 on white across the whole width, the hairline between rows inset 16, 「完了」 at the top left in the blue.
 */

/** V39: futaber's row text (about 14pt, the line 17.5pt; no letter spacing, as the mode's typography). */
internal const val FUTABER_TABLE_TEXT_SP = 14

/**
 * One table page over the whole screen: 「完了」 (or [doneLabel]) at the top left, the title in the middle, the rows.
 * While it is up the band under the page (Android's navigation bar, iPhone's home-indicator strip) takes the page's
 * grey instead of the white bar (L §8.2's handoff), and gives it back when the page goes.
 */
@Composable
internal fun FutaberTableScreen(
    title: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = "完了",
    doneTag: String = "futaber-table-done",
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalFutaberColors.current
    FutaberTablePageBand()
    Column(
        modifier.fillMaxSize().background(colors.settingsPage)
            // A tap or drag on the page's empty parts ends here, not on the screen under it.
            .pointerInput(Unit) {}
    ) {
        FutaberTableTopBar(title, leading = { FutaberTableDoneButton(doneLabel, doneTag, onDone) })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
            content = content
        )
    }
}

/** The band under a table page takes the page's grey while [FutaberTablePageBand] is in the composition. */
@Composable
internal fun FutaberTablePageBand() {
    val override = LocalFutaberStatusBarOverride.current ?: return
    val page = LocalFutaberColors.current.settingsPage
    androidx.compose.runtime.DisposableEffect(override, page) {
        val before = override.bottomColor
        override.bottomColor = page
        onDispose { override.bottomColor = before }
    }
}

/**
 * The white navigation bar of a table page: [leading] at the top left (「完了」 or 「‹ 設定」), the title in the middle,
 * [trailing] at the top right (「編集」), the hairline under it.
 */
@Composable
internal fun FutaberTableTopBar(
    title: String,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    titleTag: String? = null
) {
    val colors = LocalFutaberColors.current
    Box(
        Modifier.fillMaxWidth().background(colors.settingsRow).statusBarsPadding()
            .heightIn(min = FUTABER_TOP_BAR_HEIGHT_DP.dp)
    ) {
        Text(
            title, color = colors.body, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp).semantics { heading() }
                .then(if (titleTag != null) Modifier.testTag(titleTag) else Modifier)
        )
        Box(Modifier.align(Alignment.CenterStart)) { leading() }
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
    HorizontalDivider(color = colors.settingsSeparator, thickness = 0.5.dp)
}

/** 「完了」 at the top left (bold, the blue). */
@Composable
internal fun FutaberTableDoneButton(label: String, tag: String, onClick: () -> Unit) {
    FutaberTableBarButton(label, tag, bold = true, onClick = onClick)
}

/** A text button of the navigation bar (「編集」・「完了」 at the top right). */
@Composable
internal fun FutaberTableBarButton(label: String, tag: String, bold: Boolean = false, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Box(
        Modifier.heightIn(min = 44.dp)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp).testTag(tag),
        contentAlignment = Alignment.Center
    ) { Text(label, color = colors.iosBlue, fontSize = 17.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal) }
}

/**
 * futaber's back button: the large chevron and the page it goes back to (「設定」・「NG設定」). A screen reader reads
 * [spoken] (「‹ 設定」, as the button read before the table).
 */
@Composable
internal fun FutaberTableBackButton(label: String, tag: String, onClick: () -> Unit, spoken: String = "‹ $label") {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.heightIn(min = 44.dp)
            .clickable(onClickLabel = spoken, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(start = 4.dp, end = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FutaberIcon(
            Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null, tint = colors.iosBlue,
            modifier = Modifier.size(30.dp)
        )
        // Read once, as the button's description.
        Text(label, color = colors.iosBlue, fontSize = 17.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** A section: the grey heading (none = only the gap), then the white group across the width with hairlines. */
@Composable
internal fun FutaberTableSection(
    title: String?,
    tag: String? = null,
    /** The gap above the heading (wider before this app's own groups, so they read apart from futaber's). */
    gap: androidx.compose.ui.unit.Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalFutaberColors.current
    if (title != null) {
        Text(
            title, color = colors.settingsHeader, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = gap, bottom = 8.dp)
                .semantics { heading() }.then(if (tag != null) Modifier.testTag(tag) else Modifier)
        )
    } else Spacer(Modifier.height(gap))
    HorizontalDivider(color = colors.settingsSeparator, thickness = 0.5.dp)
    Column(Modifier.fillMaxWidth().background(colors.settingsRow), content = content)
    HorizontalDivider(color = colors.settingsSeparator, thickness = 0.5.dp)
}

/** The hairline under a row but the last (inset 16, as the original's). */
@Composable
internal fun FutaberTableDivider(last: Boolean) {
    if (!last) HorizontalDivider(color = LocalFutaberColors.current.settingsSeparator, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
}

/** A row that is a switch (the whole row is the switch for touch and a screen reader). */
@Composable
internal fun FutaberTableSwitchRow(label: String, tag: String, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    val colors = LocalFutaberColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = checked, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.switchOn, checkedThumbColor = Color.White)
            )
        }
        FutaberTableDivider(last)
    }
}

/** A row of a choice: the blue ✓ on the chosen one. */
@Composable
internal fun FutaberTableCheckRow(label: String, tag: String, selected: Boolean, last: Boolean = false, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .clickable(onClickLabel = label, onClick = onClick)
                .semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton }
                .padding(horizontal = 16.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, modifier = Modifier.weight(1f))
            if (selected) FutaberIcon(Icons.Outlined.Check, contentDescription = "選択中", tint = colors.iosBlue)
        }
        FutaberTableDivider(last)
    }
}

/** A row that does something when pressed (a plain label, no ›). */
@Composable
internal fun FutaberTableActionRow(label: String, tag: String, last: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Column {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp).testTag(tag),
            contentAlignment = Alignment.CenterStart
        ) { Text(label, color = if (enabled) colors.body else colors.meta, fontSize = FUTABER_TABLE_TEXT_SP.sp) }
        FutaberTableDivider(last)
    }
}

/**
 * A row with a text field on its right (the delete key, the name): the label on the left, what was typed after it.
 * The field has no placeholder; its label names it for a screen reader.
 */
@Composable
internal fun FutaberTableFieldRow(
    label: String,
    tag: String,
    value: String,
    onValueChange: (String) -> Unit,
    last: Boolean = false,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    FutaberTableFieldRowFrame(label, last) { modifier, style, cursor ->
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions, textStyle = style, cursorBrush = cursor,
            modifier = modifier.semantics { contentDescription = label }.testTag(tag)
        )
    }
}

/** The same row for a text typed through an input method (the value keeps its composition, as the other fields do). */
@Composable
internal fun FutaberTableFieldRow(
    label: String,
    tag: String,
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    last: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    FutaberTableFieldRowFrame(label, last) { modifier, style, cursor ->
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true, visualTransformation = visualTransformation,
            textStyle = style, cursorBrush = cursor,
            modifier = modifier.semantics { contentDescription = label }.testTag(tag)
        )
    }
}

@Composable
private fun FutaberTableFieldRowFrame(
    label: String,
    last: Boolean,
    field: @Composable (Modifier, TextStyle, SolidColor) -> Unit
) {
    val colors = LocalFutaberColors.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, modifier = Modifier.widthIn(min = 96.dp).padding(end = 12.dp))
            field(
                Modifier.weight(1f).padding(vertical = 12.dp),
                TextStyle(color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, textAlign = TextAlign.End, fontFamily = LocalFutaberCustomFont.current),
                SolidColor(colors.iosBlue)
            )
        }
        FutaberTableDivider(last)
    }
}

/** A small grey note under a group (what a row does). */
@Composable
internal fun FutaberTableNote(text: String, tag: String? = null) {
    Text(
        text, color = LocalFutaberColors.current.settingsHeader, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    )
}

/** A row that opens a page: the label, an optional grey value ([detail], e.g. a count), the ›. */
@Composable
internal fun FutaberTableNavRow(
    label: String,
    tag: String,
    detail: String? = null,
    detailTag: String? = null,
    last: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalFutaberColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(onClickLabel = label, onClick = onClick)
                .padding(start = 16.dp, end = 12.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (detail != null) {
                Text(
                    detail, color = colors.meta, fontSize = FUTABER_TABLE_TEXT_SP.sp,
                    modifier = Modifier.padding(end = 6.dp).then(if (detailTag != null) Modifier.testTag(detailTag) else Modifier)
                )
            }
            FutaberIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.chevron, modifier = Modifier.size(20.dp))
        }
        FutaberTableDivider(last)
    }
}

/** A row that only shows a value (the version). */
@Composable
internal fun FutaberTableInfoRow(label: String, tag: String, value: String, valueTag: String? = null, last: Boolean = false) {
    val colors = LocalFutaberColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, modifier = Modifier.weight(1f))
            Text(value, color = colors.meta, fontSize = FUTABER_TABLE_TEXT_SP.sp,
                modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier)
        }
        FutaberTableDivider(last)
    }
}

/**
 * A row with futaber's −＋ (「フォントサイズ = 14」): the label with its value, the grey capsule of two buttons
 * (46×32 each, a hairline between). A button that can do nothing more is drawn faint and does nothing.
 */
@Composable
internal fun FutaberTableStepperRow(
    label: String,
    rowTag: String,
    valueTag: String,
    minusTag: String,
    plusTag: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    last: Boolean = false
) {
    val colors = LocalFutaberColors.current
    // A button that only says "減らす" tells a screen reader nothing about what it changes.
    val name = label.substringBefore(" =").trim()
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 16.dp, end = 16.dp).testTag(rowTag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 0.dp).padding(end = 8.dp).testTag(valueTag))
            Row(Modifier.clip(FutaberShapes.button).background(colors.fill), verticalAlignment = Alignment.CenterVertically) {
                FutaberTableStepButton(Icons.Outlined.Remove, "${name}を減らす", minusTag, canDecrease, onDecrease)
                Box(Modifier.width(0.5.dp).height(18.dp).background(colors.separator))
                FutaberTableStepButton(Icons.Outlined.Add, "${name}を増やす", plusTag, canIncrease, onIncrease)
            }
        }
        FutaberTableDivider(last)
    }
}

@Composable
private fun FutaberTableStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalFutaberColors.current
    Box(
        Modifier.width(FUTABER_TABLE_STEP_WIDTH_DP.dp).height(FUTABER_TABLE_STEP_HEIGHT_DP.dp)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { FutaberIcon(icon, contentDescription = null, tint = if (enabled) colors.icon else colors.iconDisabled, modifier = Modifier.size(20.dp)) }
}

/** futaber's −＋ capsule: two buttons of 46.5×32 (appendix A). */
internal const val FUTABER_TABLE_STEP_WIDTH_DP = 46
internal const val FUTABER_TABLE_STEP_HEIGHT_DP = 32
