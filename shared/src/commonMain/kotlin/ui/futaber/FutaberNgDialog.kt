package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

private enum class NgSection(val label: String, val hint: String) {
    Words("ワード", "本文に含まれる語句"),
    Headers("名前・ID等", "名前・題名・ID・日時に含まれる語句")
}

/**
 * Edits the word and header NG lists that ふたちゃ also uses. Detailed thread rules made in other
 * modes still apply, but are not edited here.
 */
@Composable
internal fun FutaberNgDialog(
    words: List<String>,
    headers: List<String>,
    /** Posts hidden in the open thread; null when the dialog is opened from the settings. */
    hiddenCount: Int?,
    onChangeWords: (List<String>) -> Unit,
    onChangeHeaders: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    /** Opens on the 名前・ID等 list with this text ready to add (the poster of a long-pressed post); blank = the word list. */
    initialHeader: String = ""
) {
    val colors = LocalFutaberColors.current
    var section by rememberSaveable { mutableStateOf(if (initialHeader.isNotBlank()) NgSection.Headers else NgSection.Words) }
    var input by rememberSaveable { mutableStateOf(initialHeader.futaberTakeChars(FUTABER_NG_MAX_LENGTH)) }
    val current = if (section == NgSection.Words) words else headers
    val change = if (section == NgSection.Words) onChangeWords else onChangeHeaders
    val canAdd = futaberAddNgEntry(current, input) !== current

    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text("NG編集") },
            text = {
                // Scrolls when the dialog does not fit (a landscape screen, large text); the list keeps its own bounded height.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    hiddenCount?.let {
                        Text("このスレッドで非表示: ${it}件", color = colors.meta, fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NgSection.entries.forEach { item ->
                            val selected = item == section
                            TextButton(
                                onClick = { section = item; input = "" },
                                modifier = Modifier.selectable(selected = selected, role = Role.Tab, onClick = { section = item; input = "" })
                                    .testTag("futaber-ng-section-${item.name}")
                            ) {
                                Text(
                                    item.label,
                                    color = if (selected) colors.accent else colors.link,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Text(section.hint, color = colors.meta, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.futaberTakeChars(FUTABER_NG_MAX_LENGTH) },
                            singleLine = true,
                            label = { Text("追加する語句") },
                            modifier = Modifier.weight(1f).testTag("futaber-ng-input")
                        )
                        TextButton(
                            onClick = { change(futaberAddNgEntry(current, input)); input = "" },
                            enabled = canAdd,
                            modifier = Modifier.testTag("futaber-ng-add")
                        ) { Text("追加", color = if (canAdd) colors.link else colors.meta) }
                    }
                    futaberNgFullMessage(current)?.let {
                        Text(it, color = colors.meta, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).testTag("futaber-ng-full"))
                    }
                    if (current.isEmpty()) {
                        Text("登録はありません", color = colors.meta, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    } else {
                        LazyColumn(Modifier.heightIn(max = 240.dp).padding(top = 8.dp).testTag("futaber-ng-list")) {
                            items(current, key = { it }) { entry ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry, color = colors.body, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { change(futaberRemoveNgEntry(current, entry)) },
                                        modifier = Modifier.testTag("futaber-ng-delete")
                                    ) {
                                        FutaberIcon(Icons.Outlined.RemoveCircleOutline, contentDescription = "「$entry」を削除", tint = colors.accent)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = colors.link) } }
        )
    }
}
