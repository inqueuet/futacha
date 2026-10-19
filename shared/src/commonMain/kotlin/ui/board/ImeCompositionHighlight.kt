package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Only decorates the IME-owned range; text, offsets and the composition itself stay intact. */
internal class ImeCompositionHighlight(
    private val range: TextRange?,
    private val background: Color,
    private val foreground: Color = if (background.luminance() < .179f) Color.White else Color.Black
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val decorated = AnnotatedString.Builder(text)
        range?.let {
            val start = it.min.coerceIn(0, text.length)
            val end = it.max.coerceIn(start, text.length)
            if (start < end) decorated.addStyle(SpanStyle(color = foreground, background = background), start, end)
        }
        return TransformedText(decorated.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** Input decoration has its own readable pair; a toolbar colour is not an input background. */
internal data class ImeCompositionColors(val background: Color, val foreground: Color)

internal fun imeCompositionColors(surface: Color): ImeCompositionColors =
    if (surface.luminance() < .179f) ImeCompositionColors(Color(0xFF394B66), Color.White)
    else ImeCompositionColors(Color(0xFFD7E4F7), Color(0xFF1A1C1E))

@Composable
internal fun imeCompositionHighlight(range: TextRange?): VisualTransformation {
    val pair = imeCompositionColors(MaterialTheme.colorScheme.background)
    return ImeCompositionHighlight(range, pair.background, pair.foreground)
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
        colors = colors, visualTransformation = imeCompositionHighlight(input.value.composition))
}

@Composable
internal fun ImeOutlinedTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null, singleLine: Boolean = false
) {
    val input = rememberStableTextInputState(value, onValueChange)
    OutlinedTextField(input.value, input.onValueChange, modifier = modifier, label = label,
        singleLine = singleLine, visualTransformation = imeCompositionHighlight(input.value.composition))
}
