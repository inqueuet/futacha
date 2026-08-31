package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.pickFontFromDocuments
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberCompatFontPickerLauncher(
    onSelected: (ImageData) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnSelected = rememberUpdatedState(onSelected)
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        {
            scope.launch {
                runCatching { pickFontFromDocuments() }
                    .onSuccess { selected ->
                        if (selected != null) currentOnSelected.value(selected)
                    }
                    .onFailure { error ->
                        currentOnError.value(error.message ?: "フォントを読み込めませんでした")
                    }
            }
        }
    }
}
