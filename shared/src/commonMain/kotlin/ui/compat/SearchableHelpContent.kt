package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchableHelpContent(html: String, modifier: Modifier = Modifier, onLinkClicked: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val result by produceState(Triple("", emptyList<HelpSearchSection>(), html), html, query) {
        if (query.isNotBlank()) kotlinx.coroutines.delay(180)
        value = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
            Triple(query, searchHelp(helpSearchSections(html), query), searchedHelpHtml(html, query))
        }
    }
    val matches = result.second
    val displayHtml = if (query.isBlank()) html else result.third
    val palette = LocalCompatibilityPalette.current
    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(100) },
            label = { Text("ヘルプ内を検索") }, singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("クリア") } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.uiPrimaryText, unfocusedTextColor = palette.uiPrimaryText,
                focusedLabelColor = palette.uiPrimaryText, unfocusedLabelColor = palette.uiSecondaryText
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("help-search-field")
        )
        if (query.isNotBlank()) {
            val summary = when {
                result.first != query -> "検索中…"
                matches.isEmpty() -> "一致する項目がありません"
                else -> "${matches.size}項目が見つかりました"
            }
            Text(summary,
                color = palette.uiPrimaryText, modifier = Modifier.padding(horizontal = 16.dp).testTag("help-search-count"))
        }
        // Platform HTML views load their document at creation. Recreate only the
        // document on a new search so closed matches reopen and scroll to the top.
        key(displayHtml) {
            CompatReferenceChangeLogView(displayHtml, Modifier.weight(1f).fillMaxWidth()
                .testTag(if (query.isBlank()) "compat-help-content" else "help-search-results"), onLinkClicked)
        }
    }
}
