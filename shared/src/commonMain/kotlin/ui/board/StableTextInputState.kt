package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket

internal data class StableTextInputState(
    val value: TextFieldValue,
    val onValueChange: (TextFieldValue) -> Unit
)

@Composable
internal fun rememberStableTextInputState(
    text: String,
    onTextChange: (String) -> Unit,
    analyticsFieldLabel: String = "入力欄"
): StableTextInputState {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }
    val latestOnTextChange by rememberUpdatedState(onTextChange)

    LaunchedEffect(text) {
        if (text != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = text,
                selection = TextRange(text.length)
            )
        }
    }

    return StableTextInputState(
        value = fieldValue,
        onValueChange = { nextValue ->
            val previousText = fieldValue.text
            fieldValue = nextValue
            if (nextValue.text != previousText) {
                AnalyticsTracker.uiControl(
                    "text_field_edit",
                    "$analyticsFieldLabel を${analyticsEditLabel(previousText, nextValue.text)}",
                    mapOf(
                        "field_label" to analyticsFieldLabel,
                        "edit_type" to analyticsEditType(previousText, nextValue.text),
                        "previous_length_bucket" to analyticsTextLengthBucket(previousText),
                        "new_length_bucket" to analyticsTextLengthBucket(nextValue.text),
                        "edit_size" to analyticsEditSize(previousText, nextValue.text)
                    )
                )
                latestOnTextChange(nextValue.text)
            }
        }
    )
}

private fun analyticsEditType(previous: String, next: String): String = when {
    next.length > previous.length && next.startsWith(previous) -> "追加入力"
    next.length < previous.length && previous.startsWith(next) -> "削除"
    next.length > previous.length -> "複数文字入力・貼り付け"
    next.length < previous.length -> "範囲削除"
    else -> "置換"
}

private fun analyticsEditLabel(previous: String, next: String): String = analyticsEditType(previous, next)

private fun analyticsEditSize(previous: String, next: String): String = when (kotlin.math.abs(next.length - previous.length)) {
    0 -> "同じ長さ"
    1 -> "1文字"
    in 2..5 -> "2〜5文字"
    else -> "6文字以上"
}
