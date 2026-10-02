package com.valoser.futacha.shared.ai

private const val CONFIRMATION_VALUE_MAX_CHARS = 200

/**
 * The confirmation text of [command] (S4-2). A link can name any board URL, word
 * or mode, so the dialog shows the values the command will store, and says so
 * when the command came from an external link (a web page can open one).
 *
 * [details] are lines like "URL: …"; pass [confirmationDetailLines] or values
 * the caller has already resolved.
 */
fun buildFutachaAiConfirmationMessage(
    command: FutachaAiCommand,
    details: List<String> = command.confirmationDetailLines()
): String = buildString {
    append("「${command.action.label}」を実行します。${command.action.confirmationReason()}、ユーザー確認後にだけ進めます。")
    if (details.isNotEmpty()) {
        append("\n\n")
        append(details.joinToString("\n"))
    }
    if (command.isFromExternalLink()) {
        append("\n\n外部のリンク（Webページなど）から届いた操作です。内容に心当たりがない場合はキャンセルしてください。")
    }
}

/** The values of [command] that the user must see before confirming it (S4-2). */
fun FutachaAiCommand.confirmationDetailLines(): List<String> = buildList {
    fun addValue(label: String, value: String?) {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { add("$label: ${it.toConfirmationValue()}") }
    }
    when (action) {
        FutachaAiAction.AddBoard -> {
            add("板名: ${parameter("name", "board", "title", "label")?.toConfirmationValue() ?: "（指定なし）"}")
            add("URL: ${boardUrlParameter()?.toConfirmationValue() ?: "（指定なし）"}")
        }
        FutachaAiAction.DeleteBoard -> addValue("板", boardSelectorParameter())
        FutachaAiAction.SetCatalogMode -> {
            add("板: ${boardSelectorParameter()?.toConfirmationValue() ?: "表示中の板"}")
            addValue("モード", catalogModeParameter())
        }
        FutachaAiAction.AddWatchWord -> addValue("監視ワード", wordParameter())
        FutachaAiAction.AddNgWord -> addValue("NGワード", wordParameter())
        FutachaAiAction.AddNgHeader -> addValue("NGヘッダー", parameter("header") ?: wordParameter())
        FutachaAiAction.SaveThread,
        FutachaAiAction.DeleteHistoryEntry,
        FutachaAiAction.DeleteSavedThread -> {
            addValue("スレURL", threadUrlParameter())
            addValue("スレ", threadIdParameter())
            addValue("タイトル", parameter("title", "subject"))
        }
        else -> Unit
    }
}

/** Quoted on one line so the value stands out from the text; long values are shortened. */
internal fun String.toConfirmationValue(): String {
    val singleLine = replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
    val shown = if (singleLine.length > CONFIRMATION_VALUE_MAX_CHARS) {
        singleLine.take(CONFIRMATION_VALUE_MAX_CHARS) + "…"
    } else {
        singleLine
    }
    return "「$shown」"
}
