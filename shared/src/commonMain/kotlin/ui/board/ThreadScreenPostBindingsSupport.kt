package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference

internal data class ThreadPostActionHandlers(
    val onSaidane: (Post) -> Unit,
    val onDelRequest: (Post) -> Unit,
    val onOpenDeleteDialog: (Post) -> Unit,
    val onOpenQuoteSelection: (Post) -> Unit,
    val onNgRegister: (Post) -> Unit
)

internal data class ThreadScreenPostActionInputs(
    val currentOverlayState: () -> ThreadPostOverlayState,
    val setOverlayState: (ThreadPostOverlayState) -> Unit,
    val lastUsedDeleteKey: String,
    val currentSaidaneLabel: (Post) -> String?,
    val isSelfPost: (Post) -> Boolean,
    val onShowOptionalMessage: (String?) -> Unit,
    val onSaidaneLabelUpdated: (Post, String) -> Unit,
    val launchUnitAction: (
        successMessage: String,
        failurePrefix: String,
        onSuccess: () -> Unit,
        block: suspend () -> Unit
    ) -> Unit,
    val voteSaidane: suspend (Post) -> Unit,
    val requestDeletion: suspend (Post) -> Unit
)

internal fun buildThreadScreenPostActionHandlers(
    inputs: ThreadScreenPostActionInputs
): ThreadPostActionHandlers {
    return ThreadPostActionHandlers(
        onSaidane = saidane@{ post ->
            val actionState = resolveThreadSaidaneActionState(
                isSelfPost = inputs.isSelfPost(post),
                currentLabel = inputs.currentSaidaneLabel(post)
            )
            inputs.setOverlayState(dismissThreadPostActionOverlay(inputs.currentOverlayState()))
            if (!actionState.shouldProceed) {
                inputs.onShowOptionalMessage(actionState.blockedMessage)
                return@saidane
            }
            inputs.launchUnitAction(
                actionState.successMessage,
                actionState.failurePrefix,
                {
                    actionState.updatedLabel?.let { updatedLabel ->
                        inputs.onSaidaneLabelUpdated(post, updatedLabel)
                    }
                }
            ) {
                inputs.voteSaidane(post)
            }
        },
        onDelRequest = { post ->
            inputs.setOverlayState(dismissThreadPostActionOverlay(inputs.currentOverlayState()))
            val actionState = resolveThreadDelRequestActionState()
            inputs.launchUnitAction(
                actionState.successMessage,
                actionState.failurePrefix,
                {}
            ) {
                inputs.requestDeletion(post)
            }
        },
        onOpenDeleteDialog = { post ->
            inputs.setOverlayState(
                openThreadDeleteOverlay(
                    currentState = inputs.currentOverlayState(),
                    post = post,
                    lastUsedDeleteKey = inputs.lastUsedDeleteKey
                )
            )
        },
        onOpenQuoteSelection = { post ->
            inputs.setOverlayState(
                openThreadQuoteOverlay(
                    currentState = inputs.currentOverlayState(),
                    post = post
                )
            )
        },
        onNgRegister = { post ->
            val overlayState = resolveThreadNgRegistrationOverlayState(
                currentState = inputs.currentOverlayState(),
                post = post
            )
            inputs.setOverlayState(overlayState.overlayState)
            inputs.onShowOptionalMessage(overlayState.message)
        }
    )
}

internal data class ThreadGalleryCallbacks(
    val onDismiss: () -> Unit,
    val onImageClick: (Post) -> Unit
)

internal fun buildThreadScreenGalleryCallbacks(
    currentPosts: List<Post>,
    onDismiss: () -> Unit,
    onScrollToPostIndex: (Int) -> Unit
): ThreadGalleryCallbacks {
    return ThreadGalleryCallbacks(
        onDismiss = onDismiss,
        onImageClick = { post ->
            val index = currentPosts.indexOfFirst { it.id == post.id }
            if (index >= 0) {
                onScrollToPostIndex(index)
            }
        }
    )
}

internal data class ThreadPostCardCallbacks(
    val onQuoteClick: (QuoteReference) -> Unit,
    val onQuoteRequested: () -> Unit,
    val onPosterIdClick: (() -> Unit)?,
    val onReferencedByClick: (() -> Unit)?,
    val onSaidaneClick: () -> Unit,
    val onMediaClick: ((String, MediaType) -> Unit)?,
    val onLongPress: () -> Unit
)

internal fun buildThreadScreenPostCardCallbacks(
    post: Post,
    normalizedPosterId: String?,
    postIndex: Map<String, Post>,
    referencedByMap: Map<String, List<Post>>,
    postsByPosterId: Map<String, List<Post>>,
    quotePreviewState: QuotePreviewState?,
    onShowQuotePreview: (String, List<Post>) -> Unit,
    onQuoteRequestedForPost: (Post) -> Unit,
    onSaidaneClick: (Post) -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)?,
    onPostLongPress: (Post) -> Unit
): ThreadPostCardCallbacks {
    return ThreadPostCardCallbacks(
        onQuoteClick = { reference ->
            val targets = resolveQuotePreviewTargets(reference.targetPostIds, postIndex)
            onShowQuotePreview(reference.text, targets)
        },
        onQuoteRequested = { onQuoteRequestedForPost(post) },
        onPosterIdClick = normalizedPosterId
            ?.let { normalizedId ->
                postsByPosterId[normalizedId]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { sameIdPosts ->
                        {
                            onShowQuotePreview("ID:$normalizedId のレス", sameIdPosts)
                        }
                    }
            },
        onReferencedByClick = referencedByMap[post.id]
            ?.takeIf { it.isNotEmpty() }
            ?.let { referencingPosts ->
                {
                    onShowQuotePreview(">>${post.id} を引用したレス", referencingPosts)
                }
            },
        onSaidaneClick = { onSaidaneClick(post) },
        onMediaClick = onMediaClick,
        onLongPress = {
            if (canHandleThreadPostLongPress(quotePreviewState)) {
                onPostLongPress(post)
            }
        }
    )
}

internal data class ThreadQuotePreviewCallbacks(
    val onDismiss: () -> Unit,
    val onQuoteClick: (QuoteReference) -> Unit
)

internal fun buildThreadScreenQuotePreviewPresenter(
    isScrolling: () -> Boolean,
    posterIdLabels: Map<String, PosterIdLabel>,
    setState: (QuotePreviewState?) -> Unit
): (String, List<Post>) -> Unit {
    return { quoteText, targets ->
        setState(
            resolveQuotePreviewState(
                isScrolling = isScrolling(),
                quoteText = quoteText,
                targetPosts = targets,
                posterIdLabels = posterIdLabels
            )
        )
    }
}

internal fun buildThreadScreenQuotePreviewCallbacks(
    postIndex: Map<String, Post>,
    onDismiss: () -> Unit,
    onShowQuotePreview: (String, List<Post>) -> Unit
): ThreadQuotePreviewCallbacks {
    return ThreadQuotePreviewCallbacks(
        onDismiss = onDismiss,
        onQuoteClick = { reference ->
            val targets = resolveQuotePreviewTargets(reference.targetPostIds, postIndex)
            onShowQuotePreview(reference.text, targets)
        }
    )
}

internal fun buildThreadScreenQuoteSelectionConfirmHandler(
    replyDialogBinding: ThreadReplyDialogStateBinding,
    currentOverlayState: () -> ThreadPostOverlayState,
    setOverlayState: (ThreadPostOverlayState) -> Unit
): (List<String>) -> Unit {
    return { selectedLines ->
        val updatedState = appendQuoteSelectionToReplyDialog(
            state = replyDialogBinding.currentState(),
            selectedLines = selectedLines
        )
        if (updatedState != null) {
            replyDialogBinding.setState(updatedState)
        }
        setOverlayState(dismissThreadQuoteOverlay(currentOverlayState()))
    }
}

internal data class ThreadMediaPreviewDialogCallbacks(
    val onDismiss: () -> Unit,
    val onNavigateNext: () -> Unit,
    val onNavigatePrevious: () -> Unit,
    val onSave: (MediaPreviewEntry) -> Unit
)

internal fun buildThreadScreenMediaPreviewDialogCallbacks(
    mediaPreviewState: () -> ThreadMediaPreviewState,
    setMediaPreviewState: (ThreadMediaPreviewState) -> Unit,
    totalCount: Int,
    onSave: (MediaPreviewEntry) -> Unit
): ThreadMediaPreviewDialogCallbacks {
    return ThreadMediaPreviewDialogCallbacks(
        onDismiss = {
            setMediaPreviewState(
                dismissThreadMediaPreview(mediaPreviewState())
            )
        },
        onNavigateNext = {
            setMediaPreviewState(
                moveToNextThreadMediaPreview(
                    currentState = mediaPreviewState(),
                    totalCount = totalCount
                )
            )
        },
        onNavigatePrevious = {
            setMediaPreviewState(
                moveToPreviousThreadMediaPreview(
                    currentState = mediaPreviewState(),
                    totalCount = totalCount
                )
            )
        },
        onSave = onSave
    )
}
