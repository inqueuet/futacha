package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.util.ImageData

internal data class CatalogCreateThreadDialogCallbacks(
    val onNameChange: (String) -> Unit,
    val onEmailChange: (String) -> Unit,
    val onTitleChange: (String) -> Unit,
    val onCommentChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onImageSelected: (ImageData?) -> Unit,
    val onDismiss: () -> Unit,
    val onSubmit: () -> Unit,
    val onClear: () -> Unit
)

internal fun buildCatalogCreateThreadDialogCallbacks(
    currentDraft: () -> CreateThreadDraft,
    setDraft: (CreateThreadDraft) -> Unit,
    setImage: (ImageData?) -> Unit,
    setShowCreateThreadDialog: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit
): CatalogCreateThreadDialogCallbacks {
    return CatalogCreateThreadDialogCallbacks(
        onNameChange = { value ->
            setDraft(updateCreateThreadDraftName(currentDraft(), value))
        },
        onEmailChange = { value ->
            setDraft(updateCreateThreadDraftEmail(currentDraft(), value))
        },
        onTitleChange = { value ->
            setDraft(updateCreateThreadDraftTitle(currentDraft(), value))
        },
        onCommentChange = { value ->
            setDraft(updateCreateThreadDraftComment(currentDraft(), value))
        },
        onPasswordChange = { value ->
            setDraft(updateCreateThreadDraftPassword(currentDraft(), value))
        },
        onImageSelected = setImage,
        onDismiss = { setShowCreateThreadDialog(false) },
        onSubmit = onSubmit,
        onClear = onClear
    )
}

internal data class CatalogModeDialogCallbacks(
    val onDismiss: () -> Unit,
    val onModeSelected: (CatalogMode) -> Unit
)

internal fun buildCatalogModeDialogCallbacks(
    persistCatalogMode: (CatalogMode) -> Unit,
    setShowModeDialog: (Boolean) -> Unit
): CatalogModeDialogCallbacks {
    return CatalogModeDialogCallbacks(
        onDismiss = { setShowModeDialog(false) },
        onModeSelected = { mode ->
            persistCatalogMode(mode)
            setShowModeDialog(false)
        }
    )
}

internal data class CatalogDisplayStyleDialogCallbacks(
    val onStyleSelected: (CatalogDisplayStyle) -> Unit,
    val onGridColumnsSelected: (Int) -> Unit,
    val onDismiss: () -> Unit
)

internal fun buildCatalogDisplayStyleDialogCallbacks(
    updateCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit,
    updateCatalogGridColumns: (Int) -> Unit,
    setShowDisplayStyleDialog: (Boolean) -> Unit
): CatalogDisplayStyleDialogCallbacks {
    return CatalogDisplayStyleDialogCallbacks(
        onStyleSelected = { style ->
            updateCatalogDisplayStyle(style)
            setShowDisplayStyleDialog(false)
        },
        onGridColumnsSelected = updateCatalogGridColumns,
        onDismiss = { setShowDisplayStyleDialog(false) }
    )
}

internal data class CatalogPastThreadSearchDialogCallbacks(
    val onDismiss: () -> Unit,
    val onSearch: (String) -> Unit
)

internal fun buildCatalogPastThreadSearchDialogCallbacks(
    currentArchiveSearchScope: () -> ArchiveSearchScope?,
    setLastArchiveSearchScope: (ArchiveSearchScope?) -> Unit,
    setArchiveSearchQuery: (String) -> Unit,
    setShowPastThreadSearchDialog: (Boolean) -> Unit,
    setIsPastSearchSheetVisible: (Boolean) -> Unit,
    runPastThreadSearch: (String, ArchiveSearchScope?) -> Boolean
): CatalogPastThreadSearchDialogCallbacks {
    return CatalogPastThreadSearchDialogCallbacks(
        onDismiss = { setShowPastThreadSearchDialog(false) },
        onSearch = { query ->
            val searchStartState = buildPastThreadSearchStartState(
                query = query,
                scope = currentArchiveSearchScope()
            )
            setLastArchiveSearchScope(searchStartState.scope)
            setArchiveSearchQuery(searchStartState.normalizedQuery)
            setShowPastThreadSearchDialog(searchStartState.shouldShowDialog)
            if (runPastThreadSearch(searchStartState.normalizedQuery, searchStartState.scope)) {
                setIsPastSearchSheetVisible(searchStartState.shouldShowSheet)
            }
        }
    )
}

internal data class CatalogGlobalSettingsCallbacks(
    val onBack: () -> Unit,
    val onOpenCookieManager: (() -> Unit)?
)

internal fun buildCatalogGlobalSettingsCallbacks(
    cookieRepository: CookieRepository?,
    setIsGlobalSettingsVisible: (Boolean) -> Unit,
    setIsCookieManagementVisible: (Boolean) -> Unit
): CatalogGlobalSettingsCallbacks {
    return CatalogGlobalSettingsCallbacks(
        onBack = { setIsGlobalSettingsVisible(false) },
        onOpenCookieManager = cookieRepository?.let {
            {
                setIsGlobalSettingsVisible(false)
                setIsCookieManagementVisible(true)
            }
        }
    )
}

internal data class CatalogNgManagementCallbacks(
    val onDismiss: () -> Unit,
    val onAddWord: (String) -> Unit,
    val onRemoveWord: (String) -> Unit,
    val onToggleFiltering: () -> Unit
)

internal fun buildCatalogNgManagementCallbacks(
    setIsNgManagementVisible: (Boolean) -> Unit,
    onAddWord: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onToggleFiltering: () -> Unit
): CatalogNgManagementCallbacks {
    return CatalogNgManagementCallbacks(
        onDismiss = { setIsNgManagementVisible(false) },
        onAddWord = onAddWord,
        onRemoveWord = onRemoveWord,
        onToggleFiltering = onToggleFiltering
    )
}

internal data class CatalogWatchWordsCallbacks(
    val onAddWord: (String) -> Unit,
    val onRemoveWord: (String) -> Unit,
    val onDismiss: () -> Unit
)

internal fun buildCatalogWatchWordsCallbacks(
    onAddWord: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    setIsWatchWordsVisible: (Boolean) -> Unit
): CatalogWatchWordsCallbacks {
    return CatalogWatchWordsCallbacks(
        onAddWord = onAddWord,
        onRemoveWord = onRemoveWord,
        onDismiss = { setIsWatchWordsVisible(false) }
    )
}
