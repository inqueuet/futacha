package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Only decorates the IME-owned range; text, offsets and the composition itself stay intact. */
internal class ImeCompositionHighlight(private val range: TextRange?, private val background: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val decorated = AnnotatedString.Builder(text)
        range?.let {
            val start = it.min.coerceIn(0, text.length)
            val end = it.max.coerceIn(start, text.length)
            if (start < end) decorated.addStyle(SpanStyle(background = background), start, end)
        }
        return TransformedText(decorated.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
internal fun ImeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    colors: TextFieldColors = TextFieldDefaults.colors()
) {
    val input = rememberStableTextInputState(value, onValueChange)
    TextField(input.value, input.onValueChange, modifier = modifier, label = label, singleLine = singleLine,
        colors = colors, visualTransformation = ImeCompositionHighlight(input.value.composition, MaterialTheme.colorScheme.primaryContainer))
}
