package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchableHelpContent(html: String, modifier: Modifier = Modifier, onLinkClicked: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val sections = remember(html) { helpSearchSections(html) }
    val matches = remember(sections, query) { searchHelp(sections, query) }
    val palette = LocalCompatibilityPalette.current
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
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
        if (query.isBlank()) {
            CompatReferenceChangeLogView(html, Modifier.weight(1f).fillMaxWidth().testTag("compat-help-content"), onLinkClicked)
        } else {
            Text(if (matches.isEmpty()) "一致する項目がありません" else "${matches.size}項目が見つかりました",
                color = palette.uiPrimaryText, modifier = Modifier.padding(horizontal = 16.dp).testTag("help-search-count"))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("help-search-results"), state = listState,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(matches) { _, match ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(highlightHelpMatch(match.title, query), style = MaterialTheme.typography.titleSmall, color = palette.uiPrimaryText)
                        Text(highlightHelpMatch(match.body, query), style = MaterialTheme.typography.bodyMedium, color = palette.uiPrimaryText)
                        match.links.forEach { (label, url) ->
                            TextButton(onClick = { onLinkClicked(url) }, colors = ButtonDefaults.textButtonColors(contentColor = palette.uiPrimaryText)) { Text(label) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun highlightHelpMatch(text: String, query: String) = buildAnnotatedString {
    append(text)
    val word = query.trim()
    if (word.isNotEmpty()) {
        var start = text.indexOf(word, ignoreCase = true)
        while (start >= 0) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline), start, start + word.length)
            start = text.indexOf(word, start + word.length, ignoreCase = true)
        }
    }
}
