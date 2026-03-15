package com.valoser.futacha.shared.ui.board

internal data class ThreadNgMutationState(
    val updatedEntries: List<String>,
    val message: String,
    val shouldPersist: Boolean
)

internal data class ThreadNgFilterToggleState(
    val isEnabled: Boolean,
    val message: String
)

internal data class ThreadNgMutationCallbacks(
    val onAddHeader: (String) -> Unit,
    val onAddWord: (String) -> Unit,
    val onRemoveHeader: (String) -> Unit,
    val onRemoveWord: (String) -> Unit,
    val onToggleFiltering: () -> Unit
)

internal fun buildThreadNgMutationCallbacks(
    currentHeaders: () -> List<String>,
    currentWords: () -> List<String>,
    isFilteringEnabled: () -> Boolean,
    setFilteringEnabled: (Boolean) -> Unit,
    persistHeaders: (List<String>) -> Unit,
    persistWords: (List<String>) -> Unit,
    showMessage: (String) -> Unit
): ThreadNgMutationCallbacks {
    return ThreadNgMutationCallbacks(
        onAddHeader = { value ->
            val mutation = addThreadNgHeader(currentHeaders(), value)
            if (mutation.shouldPersist) {
                persistHeaders(mutation.updatedEntries)
            }
            showMessage(mutation.message)
        },
        onAddWord = { value ->
            val mutation = addThreadNgWord(currentWords(), value)
            if (mutation.shouldPersist) {
                persistWords(mutation.updatedEntries)
            }
            showMessage(mutation.message)
        },
        onRemoveHeader = { entry ->
            val mutation = removeThreadNgHeader(currentHeaders(), entry)
            if (mutation.shouldPersist) {
                persistHeaders(mutation.updatedEntries)
            }
            showMessage(mutation.message)
        },
        onRemoveWord = { entry ->
            val mutation = removeThreadNgWord(currentWords(), entry)
            if (mutation.shouldPersist) {
                persistWords(mutation.updatedEntries)
            }
            showMessage(mutation.message)
        },
        onToggleFiltering = {
            val toggleState = toggleThreadNgFiltering(isFilteringEnabled())
            setFilteringEnabled(toggleState.isEnabled)
            showMessage(toggleState.message)
        }
    )
}

internal data class ThreadNgManagementCallbacks(
    val onDismiss: () -> Unit,
    val onAddHeader: (String) -> Unit,
    val onAddWord: (String) -> Unit,
    val onRemoveHeader: (String) -> Unit,
    val onRemoveWord: (String) -> Unit,
    val onToggleFiltering: () -> Unit
)

internal fun buildThreadNgManagementCallbacks(
    onDismiss: () -> Unit,
    onAddHeader: (String) -> Unit,
    onAddWord: (String) -> Unit,
    onRemoveHeader: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onToggleFiltering: () -> Unit
): ThreadNgManagementCallbacks {
    return ThreadNgManagementCallbacks(
        onDismiss = onDismiss,
        onAddHeader = onAddHeader,
        onAddWord = onAddWord,
        onRemoveHeader = onRemoveHeader,
        onRemoveWord = onRemoveWord,
        onToggleFiltering = onToggleFiltering
    )
}

internal fun addThreadNgHeader(
    existingEntries: List<String>,
    input: String
): ThreadNgMutationState {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> ThreadNgMutationState(
            updatedEntries = existingEntries,
            message = "NGヘッダーに含める文字列を入力してください",
            shouldPersist = false
        )
        existingEntries.any { it.equals(trimmed, ignoreCase = true) } -> ThreadNgMutationState(
            updatedEntries = existingEntries,
            message = "そのNGヘッダーはすでに登録されています",
            shouldPersist = false
        )
        else -> ThreadNgMutationState(
            updatedEntries = existingEntries + trimmed,
            message = "NGヘッダーを追加しました",
            shouldPersist = true
        )
    }
}

internal fun removeThreadNgHeader(
    existingEntries: List<String>,
    entry: String
): ThreadNgMutationState {
    return ThreadNgMutationState(
        updatedEntries = existingEntries.filterNot { it == entry },
        message = "NGヘッダーを削除しました",
        shouldPersist = true
    )
}

internal fun addThreadNgWord(
    existingEntries: List<String>,
    input: String
): ThreadNgMutationState {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> ThreadNgMutationState(
            updatedEntries = existingEntries,
            message = "NGワードに含める文字列を入力してください",
            shouldPersist = false
        )
        existingEntries.any { it.equals(trimmed, ignoreCase = true) } -> ThreadNgMutationState(
            updatedEntries = existingEntries,
            message = "そのNGワードはすでに登録されています",
            shouldPersist = false
        )
        else -> ThreadNgMutationState(
            updatedEntries = existingEntries + trimmed,
            message = "NGワードを追加しました",
            shouldPersist = true
        )
    }
}

internal fun removeThreadNgWord(
    existingEntries: List<String>,
    entry: String
): ThreadNgMutationState {
    return ThreadNgMutationState(
        updatedEntries = existingEntries.filterNot { it == entry },
        message = "NGワードを削除しました",
        shouldPersist = true
    )
}

internal fun toggleThreadNgFiltering(currentEnabled: Boolean): ThreadNgFilterToggleState {
    val nextEnabled = !currentEnabled
    return ThreadNgFilterToggleState(
        isEnabled = nextEnabled,
        message = if (nextEnabled) "NG表示を有効にしました" else "NG表示を無効にしました"
    )
}
