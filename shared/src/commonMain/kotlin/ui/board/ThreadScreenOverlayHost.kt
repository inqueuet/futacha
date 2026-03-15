package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem

internal data class ThreadScreenOverlayHostBindings(
    val postOverlayState: ThreadPostOverlayState,
    val sheetOverlayState: ThreadSheetOverlayState,
    val modalOverlayState: ThreadModalOverlayState,
    val history: List<ThreadHistoryEntry>,
    val boardName: String,
    val resolvedThreadTitle: String,
    val replyDialogState: ThreadReplyDialogState,
    val mediaPreviewState: ThreadMediaPreviewState,
    val mediaPreviewEntries: List<MediaPreviewEntry>,
    val galleryPosts: List<Post>?,
    val isSingleMediaSaveInProgress: Boolean,
    val ngHeaders: List<String>,
    val ngWords: List<String>,
    val ngFilteringEnabled: Boolean,
    val readAloudSegments: List<ReadAloudSegment>,
    val currentReadAloudIndex: Int,
    val firstVisibleSegmentIndex: Int,
    val readAloudStatus: ReadAloudStatus,
    val isPrivacyFilterEnabled: Boolean,
    val saveProgress: SaveProgress?,
    val preferencesState: ScreenPreferencesState,
    val uiBindings: ThreadScreenUiBindingsBundle,
    val filterUiState: ThreadFilterUiState,
    val fileSystem: FileSystem?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?,
    val appColorScheme: ColorScheme,
    val onDismissPostActionSheet: () -> Unit,
    val isSaidaneEnabled: (Post) -> Boolean,
    val onQuoteFromActionSheet: (Post) -> Unit,
    val onNgRegisterFromActionSheet: (Post) -> Unit,
    val onSaidaneFromActionSheet: (Post) -> Unit,
    val onDelRequestFromActionSheet: (Post) -> Unit,
    val onDeleteFromActionSheet: (Post) -> Unit,
    val onDeleteDialogPasswordChange: (String) -> Unit,
    val onDeleteDialogImageOnlyChange: (Boolean) -> Unit,
    val onDeleteDialogDismiss: () -> Unit,
    val onDeleteDialogConfirm: (Post) -> Unit,
    val onQuoteSelectionDismiss: () -> Unit,
    val onReplySubmit: () -> Unit
)

@Composable
internal fun ThreadScreenOverlayHost(
    bindings: ThreadScreenOverlayHostBindings
) {
    val sheetTarget = bindings.postOverlayState.actionSheetState.targetPost
    if (bindings.postOverlayState.actionSheetState.isActionSheetVisible && sheetTarget != null) {
        ThreadPostActionSheet(
            post = sheetTarget,
            onDismiss = bindings.onDismissPostActionSheet,
            onQuote = { bindings.onQuoteFromActionSheet(sheetTarget) },
            onNgRegister = { bindings.onNgRegisterFromActionSheet(sheetTarget) },
            onSaidane = { bindings.onSaidaneFromActionSheet(sheetTarget) },
            isSaidaneEnabled = bindings.isSaidaneEnabled(sheetTarget),
            onDelRequest = { bindings.onDelRequestFromActionSheet(sheetTarget) },
            onDelete = { bindings.onDeleteFromActionSheet(sheetTarget) }
        )
    }

    val deleteTarget = bindings.postOverlayState.deleteDialogState.targetPost
    if (deleteTarget != null) {
        DeleteByUserDialog(
            post = deleteTarget,
            password = bindings.postOverlayState.deleteDialogState.password,
            onPasswordChange = bindings.onDeleteDialogPasswordChange,
            imageOnly = bindings.postOverlayState.deleteDialogState.imageOnly,
            onImageOnlyChange = bindings.onDeleteDialogImageOnlyChange,
            onDismiss = bindings.onDeleteDialogDismiss,
            onConfirm = { bindings.onDeleteDialogConfirm(deleteTarget) }
        )
    }

    val quoteTarget = bindings.postOverlayState.quoteSelectionState.targetPost
    if (quoteTarget != null) {
        QuoteSelectionDialog(
            post = quoteTarget,
            onDismiss = bindings.onQuoteSelectionDismiss,
            onConfirm = bindings.uiBindings.quoteSelectionConfirm
        )
    }

    if (bindings.replyDialogState.isVisible) {
        val emailPresets = remember { listOf("ID表示", "IP表示", "sage") }
        val subtitle = remember(bindings.boardName, bindings.resolvedThreadTitle) {
            listOfNotNull(
                bindings.boardName.takeIf { it.isNotBlank() },
                bindings.resolvedThreadTitle.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { null }
        }
        MaterialTheme(
            colorScheme = bindings.appColorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            ThreadFormDialog(
                title = "返信",
                subtitle = subtitle,
                barColorScheme = bindings.appColorScheme,
                attachmentPickerPreference = bindings.preferencesState.attachmentPickerPreference,
                preferredFileManagerPackage = bindings.preferencesState.preferredFileManagerPackage,
                emailPresets = emailPresets,
                comment = bindings.replyDialogState.draft.comment,
                onCommentChange = bindings.uiBindings.replyDialogCallbacks.onCommentChange,
                name = bindings.replyDialogState.draft.name,
                onNameChange = bindings.uiBindings.replyDialogCallbacks.onNameChange,
                email = bindings.replyDialogState.draft.email,
                onEmailChange = bindings.uiBindings.replyDialogCallbacks.onEmailChange,
                subject = bindings.replyDialogState.draft.subject,
                onSubjectChange = bindings.uiBindings.replyDialogCallbacks.onSubjectChange,
                password = bindings.replyDialogState.draft.password,
                onPasswordChange = bindings.uiBindings.replyDialogCallbacks.onPasswordChange,
                selectedImage = bindings.replyDialogState.draft.imageData,
                onImageSelected = bindings.uiBindings.replyDialogCallbacks.onImageSelected,
                onDismiss = bindings.uiBindings.replyDialogCallbacks.onDismiss,
                onSubmit = bindings.onReplySubmit,
                onClear = bindings.uiBindings.replyDialogCallbacks.onClear,
                isSubmitEnabled = bindings.replyDialogState.draft.comment.trim().isNotBlank() &&
                    hasDeleteKeyForSubmit(bindings.replyDialogState.draft.password),
                sendDescription = "返信",
                showSubject = true,
                showPassword = true
            )
        }
    }

    val mediaPreviewDialogState = resolveThreadMediaPreviewDialogState(
        state = bindings.mediaPreviewState,
        entries = bindings.mediaPreviewEntries,
        isSaveInProgress = bindings.isSingleMediaSaveInProgress
    )
    mediaPreviewDialogState?.let { dialogState ->
        ThreadMediaPreviewDialog(
            state = dialogState,
            onDismiss = bindings.uiBindings.mediaPreviewDialogCallbacks.onDismiss,
            onNavigateNext = bindings.uiBindings.mediaPreviewDialogCallbacks.onNavigateNext,
            onNavigatePrevious = bindings.uiBindings.mediaPreviewDialogCallbacks.onNavigatePrevious,
            onSave = bindings.uiBindings.mediaPreviewDialogCallbacks.onSave
        )
    }

    if (bindings.modalOverlayState.isGalleryVisible && bindings.galleryPosts != null) {
        bindings.uiBindings.galleryCallbacks?.let { galleryCallbacks ->
            ThreadImageGallery(
                posts = bindings.galleryPosts,
                onDismiss = galleryCallbacks.onDismiss,
                onImageClick = galleryCallbacks.onImageClick
            )
        }
    }

    if (bindings.sheetOverlayState.isSettingsVisible) {
        ThreadSettingsSheet(
            onDismiss = bindings.uiBindings.settingsSheetCallbacks.onDismiss,
            menuEntries = bindings.preferencesState.threadMenuEntries,
            onAction = bindings.uiBindings.settingsSheetCallbacks.onAction
        )
    }

    if (bindings.sheetOverlayState.isFilterVisible) {
        ThreadFilterSheet(
            selectedOptions = bindings.filterUiState.options,
            activeSortOption = bindings.filterUiState.sortOption,
            keyword = bindings.filterUiState.keyword,
            onOptionToggle = bindings.uiBindings.filterSheetCallbacks.onOptionToggle,
            onKeywordChange = bindings.uiBindings.filterSheetCallbacks.onKeywordChange,
            onClear = bindings.uiBindings.filterSheetCallbacks.onClear,
            onDismiss = bindings.uiBindings.filterSheetCallbacks.onDismiss
        )
    }

    if (bindings.sheetOverlayState.isReadAloudControlsVisible) {
        ReadAloudControlSheet(
            segments = bindings.readAloudSegments,
            currentIndex = bindings.currentReadAloudIndex,
            visibleSegmentIndex = bindings.firstVisibleSegmentIndex,
            status = bindings.readAloudStatus,
            onSeek = bindings.uiBindings.readAloudControlCallbacks.onSeek,
            onSeekToVisible = bindings.uiBindings.readAloudControlCallbacks.onSeekToVisible,
            onPlay = bindings.uiBindings.readAloudControlCallbacks.onPlay,
            onPause = bindings.uiBindings.readAloudControlCallbacks.onPause,
            onStop = bindings.uiBindings.readAloudControlCallbacks.onStop,
            onDismiss = bindings.uiBindings.readAloudControlCallbacks.onDismiss
        )
    }

    if (bindings.postOverlayState.isNgManagementVisible) {
        NgManagementSheet(
            onDismiss = bindings.uiBindings.ngManagementCallbacks.onDismiss,
            ngHeaders = bindings.ngHeaders,
            ngWords = bindings.ngWords,
            ngFilteringEnabled = bindings.ngFilteringEnabled,
            onAddHeader = bindings.uiBindings.ngManagementCallbacks.onAddHeader,
            onAddWord = bindings.uiBindings.ngManagementCallbacks.onAddWord,
            onRemoveHeader = bindings.uiBindings.ngManagementCallbacks.onRemoveHeader,
            onRemoveWord = bindings.uiBindings.ngManagementCallbacks.onRemoveWord,
            onToggleFiltering = bindings.uiBindings.ngManagementCallbacks.onToggleFiltering,
            initialInput = bindings.postOverlayState.ngHeaderPrefill
        )
    }

    if (bindings.isPrivacyFilterEnabled) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawRect(color = Color.White.copy(alpha = 0.5f))
        }
    }

    SaveProgressDialog(
        progress = bindings.saveProgress,
        onDismissRequest = bindings.uiBindings.saveProgressDialogCallbacks.onDismissRequest,
        onCancelRequest = bindings.uiBindings.saveProgressDialogCallbacks.onCancelRequest
    )

    if (bindings.modalOverlayState.isGlobalSettingsVisible) {
        GlobalSettingsScreen(
            onBack = bindings.uiBindings.globalSettingsCallbacks.onBack,
            preferencesState = bindings.preferencesState,
            preferencesCallbacks = bindings.uiBindings.globalSettingsCallbacks.preferencesCallbacks,
            onOpenCookieManager = bindings.uiBindings.globalSettingsCallbacks.onOpenCookieManager,
            historyEntries = bindings.history,
            fileSystem = bindings.fileSystem,
            autoSavedThreadRepository = bindings.autoSavedThreadRepository
        )
    }

    if (bindings.modalOverlayState.isCookieManagementVisible && bindings.cookieRepository != null) {
        CookieManagementScreen(
            onBack = bindings.uiBindings.cookieManagementCallbacks.onBack,
            repository = bindings.cookieRepository
        )
    }
}
