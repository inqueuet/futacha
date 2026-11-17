package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.compatGoogleSearchTerms
import com.valoser.futacha.shared.compat.compatPostActionCandidates
import com.valoser.futacha.shared.compat.compatQuickQuoteText
import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.util.rememberUrlLauncher
import io.ktor.http.encodeURLParameter

/** What the item picker opened from the post action sheet does with the chosen items. */
internal enum class FutachaPostItemSelectionMode { COPY, SEARCH }

/** One selectable row of the picker; [label] is only shown, [value] is copied / searched. */
internal data class FutachaPostItemCandidate(val label: String, val value: String)

internal data class FutachaPostItemSelection(
    val postNo: String,
    val mode: FutachaPostItemSelectionMode,
    val candidates: List<FutachaPostItemCandidate>,
    val selected: Set<Int> = emptySet()
)

/** Lines appended to the reply form by 「クイック引用返信」 (same text as としあき(仮)). */
internal fun futachaQuickQuoteLines(post: Post): List<String> =
    compatQuickQuoteText(post.toCompatPostSnapshot(post.order ?: 0)).trimEnd('\n').split('\n')

internal fun futachaPostItemSelection(post: Post, mode: FutachaPostItemSelectionMode): FutachaPostItemSelection {
    val snapshot = post.toCompatPostSnapshot(post.order ?: 0)
    val candidates = when (mode) {
        FutachaPostItemSelectionMode.COPY ->
            compatPostActionCandidates(snapshot).map { FutachaPostItemCandidate(it.label, it.value) }
        FutachaPostItemSelectionMode.SEARCH ->
            compatGoogleSearchTerms(snapshot).map { FutachaPostItemCandidate("", it) }
    }
    return FutachaPostItemSelection(post.id, mode, candidates)
}

/** Copy joins the chosen items by line (としあき(仮)と同じ); search joins them by a space. */
internal fun futachaSelectedItemsText(selection: FutachaPostItemSelection): String =
    selection.candidates
        .mapIndexedNotNull { index, item -> item.value.takeIf { index in selection.selected } }
        .joinToString(if (selection.mode == FutachaPostItemSelectionMode.COPY) "\n" else " ")

internal fun futachaGoogleSearchUrl(query: String): String? =
    query.trim().takeIf(String::isNotBlank)?.let { "https://www.google.com/search?q=${it.encodeURLParameter()}" }

/** Rows appended to the end of the existing post action sheet. */
@Composable
internal fun FutachaPostExtraActionRows(
    onQuickQuote: () -> Unit,
    onSelectCopy: () -> Unit,
    onSelectSearch: () -> Unit
) {
    ListItem(
        leadingContent = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null) },
        headlineContent = { Text("クイック引用返信") },
        supportingContent = { Text("本文を引用して返信欄を開く") },
        modifier = Modifier.testTag("futacha-post-quick-quote").clickable(onClick = onQuickQuote)
    )
    ListItem(
        leadingContent = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
        headlineContent = { Text("項目を選んでコピー") },
        supportingContent = { Text("No・ID・本文の行などを選んでクリップボードへ") },
        modifier = Modifier.testTag("futacha-post-select-copy").clickable(onClick = onSelectCopy)
    )
    ListItem(
        leadingContent = { Icon(Icons.Outlined.Search, contentDescription = null) },
        headlineContent = { Text("語句を選んでGoogle検索") },
        supportingContent = { Text("本文の語句を選んでブラウザで検索") },
        modifier = Modifier.testTag("futacha-post-select-search").clickable(onClick = onSelectSearch)
    )
}

/** Picker for 「項目を選んでコピー」／「語句を選んでGoogle検索」, plus its completion message. */
@Composable
internal fun FutachaPostItemSelectionHost(
    selection: FutachaPostItemSelection?,
    onSelectionChange: (FutachaPostItemSelection?) -> Unit
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val openUrl = rememberUrlLauncher()
    var message by remember { mutableStateOf<String?>(null) }
    selection?.let { current ->
        val isCopy = current.mode == FutachaPostItemSelectionMode.COPY
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { onSelectionChange(null) },
            title = { Text(if (isCopy) "No.${current.postNo} の項目をコピー" else "No.${current.postNo} の語句で検索") },
            text = {
                if (current.candidates.isEmpty()) {
                    Text(if (isCopy) "コピーできる項目がありません" else "検索できる語句がありません")
                } else Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                ) {
                    current.candidates.forEachIndexed { index, item ->
                        val checked = index in current.selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .testTag("futacha-post-item-$index")
                                .clickable {
                                    onSelectionChange(current.copy(
                                        selected = if (checked) current.selected - index else current.selected + index
                                    ))
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                text = if (item.label.isBlank()) item.value else "${item.label}: ${item.value}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = current.selected.isNotEmpty(),
                    modifier = Modifier.testTag("futacha-post-item-confirm"),
                    onClick = {
                        val text = futachaSelectedItemsText(current)
                        if (isCopy) {
                            clipboard.setText(AnnotatedString(text))
                            message = "クリップボードにコピーしました"
                        } else {
                            futachaGoogleSearchUrl(text)?.let(openUrl)
                        }
                        onSelectionChange(null)
                    }
                ) { Text(if (isCopy) "クリップボードにコピー" else "Google検索") }
            },
            dismissButton = { TextButton(onClick = { onSelectionChange(null) }) { Text("キャンセル") } }
        ) }
    }
    message?.let { text -> FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { message = null },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("閉じる") } }
    ) } }
}
