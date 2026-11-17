package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.ui.board.resolveThreadSearchDebounceMillis
import kotlinx.coroutines.delay

/**
 * The search word that takes effect: [query] a moment after the last key (the wait ふたちゃ' thread search uses), so a
 * fast typist does not search after every letter. A blank word takes effect at once, so clearing the search never lags.
 * The field itself keeps showing what was typed; only the search reads this.
 */
@Composable
internal fun rememberFutaberDebouncedQuery(query: String): String {
    var applied by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != applied) {
            val wait = resolveThreadSearchDebounceMillis(query)
            if (wait > 0L) delay(wait)
            applied = query
        }
    }
    return if (query.isBlank()) query else applied
}
