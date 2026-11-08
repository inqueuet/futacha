package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How an alert's button reads: the system blue, the bold キャンセル, or the destructive red. */
internal enum class FutaberAlertStyle { Default, Cancel, Destructive }

/** One button of [FutaberAlert]. */
internal data class FutaberAlertButton(
    val id: String,
    val label: String,
    val style: FutaberAlertStyle = FutaberAlertStyle.Default,
    val enabled: Boolean = true,
    /** The button's test tag; `futaber-alert-<id>` unless a screen keeps its older one (the MHT choice, M15). */
    val tag: String = "futaber-alert-$id",
    val onClick: () -> Unit
)

/** The one text field an alert may have (the board's address of 「板の追加」, S05). */
internal data class FutaberAlertField(
    val value: String,
    val onValueChange: (String) -> Unit,
    val placeholder: String = "",
    /** The field's test tag (the board list keeps its own, L-B10). */
    val tag: String = "futaber-alert-field",
    val keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
)

/**
 * The buttons in the order iOS draws them: two side by side with キャンセル on the left; three or more one under the
 * other with キャンセル last (futaber's 「MHT保存」: サムネイルのみ／本体画像もダウンロードする／キャンセル).
 */
internal fun futaberAlertButtonOrder(buttons: List<FutaberAlertButton>): List<FutaberAlertButton> {
    val (cancels, others) = buttons.partition { it.style == FutaberAlertStyle.Cancel }
    return if (buttons.size == 2) cancels + others else others + cancels
}

/**
 * True when the buttons go one under the other: three or more, or two whose longer label does not fit half the alert
 * ([halfWidthChars] characters of the button text).
 */
internal fun futaberAlertButtonsStacked(buttons: List<FutaberAlertButton>, halfWidthChars: Int = FUTABER_ALERT_HALF_WIDTH_CHARS): Boolean =
    buttons.size >= 3 || (buttons.size == 2 && buttons.any { futaberAlertLabelWidth(it.label) > halfWidthChars })

/** A label's width in half-width characters (a full-width character counts two). */
internal fun futaberAlertLabelWidth(label: String): Int = label.fold(0) { width, c -> width + if (c.code < 0x2E80) 1 else 2 }

/** The button the system's Back presses: キャンセル, else nothing (the alert stays, as a choice is asked for). */
internal fun futaberAlertBackButton(buttons: List<FutaberAlertButton>): FutaberAlertButton? =
    buttons.firstOrNull { it.style == FutaberAlertStyle.Cancel && it.enabled }

/** iOS's alert: 270 wide, 44 per button. */
internal const val FUTABER_ALERT_WIDTH_DP = 270
internal const val FUTABER_ALERT_BUTTON_DP = 44

/** How many half-width characters of 17sp fit half of [FUTABER_ALERT_WIDTH_DP] (135 less the padding). */
internal const val FUTABER_ALERT_HALF_WIDTH_CHARS = 14

/**
 * An iOS-style alert (§3.6): a light rounded box in the middle of the screen over a light veil, a bold title, the
 * message, an optional text field, and the buttons under a hairline (two side by side, three or more stacked,
 * [futaberAlertButtonOrder]). A tap outside does nothing (a choice is asked for); the system's Back presses
 * キャンセル ([futaberAlertBackButton]). It keeps above the keyboard while the field is typed in. Drawn by the caller in
 * a layer over the whole screen (as [FutaberActionSheet]).
 */
@Composable
internal fun FutaberAlert(
    title: String,
    message: String? = null,
    buttons: List<FutaberAlertButton>,
    field: FutaberAlertField? = null,
    onDismiss: () -> Unit = {}
) {
    val colors = LocalFutaberColors.current
    val back = futaberAlertBackButton(buttons)
    FutaberBackHandler(onBack = { back?.onClick?.invoke() ?: onDismiss() })
    Box(Modifier.fillMaxSize()) {
        // The veil takes a tap outside and does nothing with it.
        Box(
            Modifier.fillMaxSize().background(colors.sheetScrim)
                .clickable(interactionSource = null, indication = null, onClick = {})
                .testTag("futaber-alert-scrim")
        )
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars).union(WindowInsets.ime))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.width(FUTABER_ALERT_WIDTH_DP.dp).clip(FUTABER_ALERT_SHAPE).background(colors.fill)
                    .semantics { paneTitle = title }
                    .testTag("futaber-alert")
            ) {
                FutaberAlertTexts(title, message, field, Modifier.weight(1f, fill = false))
                HorizontalDivider(color = colors.sheetSeparator, thickness = 0.5.dp)
                FutaberAlertButtons(futaberAlertButtonOrder(buttons), futaberAlertButtonsStacked(buttons))
            }
        }
    }
}

private val FUTABER_ALERT_SHAPE = RoundedCornerShape(14.dp)

@Composable
private fun FutaberAlertTexts(title: String, message: String?, field: FutaberAlertField?, modifier: Modifier) {
    val colors = LocalFutaberColors.current
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = colors.body, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.testTag("futaber-alert-title"))
        if (!message.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(message, color = colors.body, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.testTag("futaber-alert-message"))
        }
        if (field != null) {
            Spacer(Modifier.height(14.dp))
            FutaberAlertTextField(field)
        }
    }
}

@Composable
private fun FutaberAlertTextField(field: FutaberAlertField) {
    val colors = LocalFutaberColors.current
    val focus = remember { FocusRequester() }
    // The field is ready to type in when the alert opens, as iOS's is.
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; runCatching { focus.requestFocus() } }
    BasicTextField(
        value = field.value,
        onValueChange = field.onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.body, fontSize = 13.sp),
        cursorBrush = SolidColor(colors.iosBlue),
        keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
            .semantics { contentDescription = field.placeholder.ifEmpty { "入力" } }
            .testTag(field.tag),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 28.dp).clip(RoundedCornerShape(5.dp)).background(colors.background)
                    .border(0.5.dp, colors.sheetSeparator, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (field.value.isEmpty() && field.placeholder.isNotEmpty()) Text(field.placeholder, color = colors.sheetTitle, fontSize = 13.sp)
                inner()
            }
        }
    )
}

@Composable
private fun FutaberAlertButtons(buttons: List<FutaberAlertButton>, stacked: Boolean) {
    val colors = LocalFutaberColors.current
    if (stacked) {
        Column(Modifier.fillMaxWidth()) {
            buttons.forEachIndexed { index, button ->
                if (index > 0) HorizontalDivider(color = colors.sheetSeparator, thickness = 0.5.dp)
                FutaberAlertButtonCell(button, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.Start) {
            buttons.forEachIndexed { index, button ->
                if (index > 0) Box(Modifier.width(0.5.dp).fillMaxHeight().background(colors.sheetSeparator))
                FutaberAlertButtonCell(button, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FutaberAlertButtonCell(button: FutaberAlertButton, modifier: Modifier) {
    val colors = LocalFutaberColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.heightIn(min = FUTABER_ALERT_BUTTON_DP.dp)
            .background(if (pressed && button.enabled) colors.sheetPressed else Color.Transparent)
            .clickable(interaction, indication = null, enabled = button.enabled, onClickLabel = button.label,
                role = Role.Button, onClick = button.onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(button.tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            button.label,
            color = when {
                !button.enabled -> colors.sheetTitle
                button.style == FutaberAlertStyle.Destructive -> colors.destructiveOnSheet
                else -> colors.iosBlueOnSheet
            },
            fontSize = 17.sp,
            fontWeight = if (button.style == FutaberAlertStyle.Cancel) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
