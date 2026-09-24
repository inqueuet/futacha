@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    kotlinx.coroutines.FlowPreview::class,
    kotlin.time.ExperimentalTime::class
)

package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ui.image.rememberGenerationMetadata
import com.valoser.futacha.shared.ui.image.PromptAiBadge
import com.valoser.futacha.shared.ui.image.InlinePrompt

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.ImageLoader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import com.valoser.futacha.shared.ui.image.refreshImageOnce
import coil3.request.ImageRequest
import coil3.request.crossfade
import futacha.shared.generated.resources.Res
import futacha.shared.generated.resources.board_listview_ico_default
import futacha.shared.generated.resources.cmn_listview_delete
import futacha.shared.generated.resources.cmn_listview_handle
import futacha.shared.generated.resources.cmn_no_thumb
import futacha.shared.generated.resources.thread_header_quote
import org.jetbrains.compose.resources.painterResource
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatBoardDefaultText
import com.valoser.futacha.shared.compat.compatBoardDefaultNamePreferenceKey
import com.valoser.futacha.shared.compat.compatBoardDefaultSubjectPreferenceKey
import com.valoser.futacha.shared.compat.learnCompatBoardDefaultText
import com.valoser.futacha.shared.compat.shouldHideCompatDefaultName
import com.valoser.futacha.shared.compat.shouldHideCompatDefaultSubject
import com.valoser.futacha.shared.compat.CompatCatalogLayout
import com.valoser.futacha.shared.compat.CompatCatalogPreference
import com.valoser.futacha.shared.compat.CompatCatalogSnapshot
import com.valoser.futacha.shared.compat.CompatCatalogItemState
import com.valoser.futacha.shared.compat.CompatCatalogDroppedClass
import com.valoser.futacha.shared.compat.CompatDroppedCatalogItem
import com.valoser.futacha.shared.compat.appendCompatDroppedCatalogItems
import com.valoser.futacha.shared.compat.calculateCompatCatalogProjection
import com.valoser.futacha.shared.compat.CompatCatalogProjectionRequest
import com.valoser.futacha.shared.compat.diffCompatCatalogGenerations
import com.valoser.futacha.shared.compat.truncateCompatCatalogSourceTitle
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CanonicalThreadUrl
import com.valoser.futacha.shared.compat.ClosedTabBatch
import com.valoser.futacha.shared.compat.toVisitedHistoryEntry
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatExtractionKind
import com.valoser.futacha.shared.compat.CompatNgExtractionAction
import com.valoser.futacha.shared.compat.compatNgExtractionAction
import com.valoser.futacha.shared.compat.CompatHeaderExtractionKind
import com.valoser.futacha.shared.compat.CompatHeaderTapTarget
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_MEMO_CHARS
import com.valoser.futacha.shared.compat.appliesToThreadImage
import com.valoser.futacha.shared.compat.compatThreadImageNgScopeKey
import com.valoser.futacha.shared.compat.compatCatalogMatchedWords
import com.valoser.futacha.shared.compat.compatCatalogManagementDisplayValue
import com.valoser.futacha.shared.compat.compatCatalogManagementKinds
import com.valoser.futacha.shared.compat.compatCatalogManagementRules
import com.valoser.futacha.shared.compat.compatCatalogRefuseDisplayText
import com.valoser.futacha.shared.compat.compatCatalogRulesForBoard
import com.valoser.futacha.shared.compat.hasCompatCatalogManagementDuplicate
import com.valoser.futacha.shared.compat.cleanCompatThreadReferenceWord
import com.valoser.futacha.shared.compat.compatThreadReferenceDisplayValue
import com.valoser.futacha.shared.compat.compatThreadReferenceKinds
import com.valoser.futacha.shared.compat.compatThreadReferenceRules
import com.valoser.futacha.shared.compat.compatReferenceThreadNgCandidates
import com.valoser.futacha.shared.compat.hasCompatThreadReferenceDuplicate
import com.valoser.futacha.shared.compat.isCompatThreadRefuseForbidden
import com.valoser.futacha.shared.compat.buildCompatCatalogRuleIndex
import com.valoser.futacha.shared.compat.buildCompatCatalogReplyDeltas
import com.valoser.futacha.shared.compat.compatCatalogReplyDeltaKey
import com.valoser.futacha.shared.compat.CompatCatalogReplyIndicator
import com.valoser.futacha.shared.compat.CompatCatalogReplyIndicatorKind
import com.valoser.futacha.shared.compat.resolveCompatCatalogReplyIndicator
import com.valoser.futacha.shared.compat.mergeCompatCatalogTab
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.compat.compatImagePhashCachePreferenceKey
import com.valoser.futacha.shared.compat.isValidCompatImagePhash
import com.valoser.futacha.shared.compat.CompatImageNgSource
import com.valoser.futacha.shared.compat.compatImageNgBoardLabel
import com.valoser.futacha.shared.compat.compatImageNgDisplayTitle
import com.valoser.futacha.shared.compat.compatImageNgFirstUrl
import com.valoser.futacha.shared.compat.compatImageNgKinds
import com.valoser.futacha.shared.compat.compatImageNgManagementRules
import com.valoser.futacha.shared.compat.compatImageNgMatchesSearch
import com.valoser.futacha.shared.compat.CompatOtherMenuItem
import com.valoser.futacha.shared.compat.CompatOtherMenuRoute
import com.valoser.futacha.shared.compat.CompatHost
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.compatInlineLinks
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatPostActionCandidate
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadOrigin
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatSelectorActionEffect
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatViewerCaller
import com.valoser.futacha.shared.compat.CompatibilityEvent
import com.valoser.futacha.shared.compat.CompatibilityEffect
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.resolveCompatCloseToastDurationMillis
import com.valoser.futacha.shared.compat.shouldShowCompatCloseToast
import com.valoser.futacha.shared.compat.CompatAutoScrollAction
import com.valoser.futacha.shared.compat.COMPAT_AUTO_SCROLL_TOUCH_PAUSE_MILLIS
import com.valoser.futacha.shared.compat.COMPAT_AUTO_SCROLL_RELOAD_WAIT_MILLIS
import com.valoser.futacha.shared.compat.resolveCompatAutoScrollAction
import com.valoser.futacha.shared.compat.CompatibilityWorkspaceState
import com.valoser.futacha.shared.compat.distinctCompatBoards
import com.valoser.futacha.shared.compat.distinctCompatTabs
import com.valoser.futacha.shared.compat.distinctCompatHistory
import com.valoser.futacha.shared.compat.prependCompatTab
import com.valoser.futacha.shared.compat.CompatWorkspaceRecord
import com.valoser.futacha.shared.compat.CompatDrawerPage
import com.valoser.futacha.shared.compat.CompatVolumeKey
import com.valoser.futacha.shared.compat.CompatVolumeKeyBus
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.SelectorPresentation
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.applyCatalogReplyCount
import com.valoser.futacha.shared.compat.reconcileCompatToolbar
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.compat.reduceCompatibilityWorkspace
import com.valoser.futacha.shared.compat.refreshCompatTabsInBackground
import com.valoser.futacha.shared.compat.compatQuoteQueryForLine
import com.valoser.futacha.shared.compat.resolveCompatQuotePosts
import com.valoser.futacha.shared.compat.resolveCompatSelectorLongTapEffect
import com.valoser.futacha.shared.compat.resolveCompatSelectorMenuEffect
import com.valoser.futacha.shared.compat.compatSelectorContextChoices
import com.valoser.futacha.shared.compat.resolveCompatScrollPosition
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.compat.normalizeCompatSearchText
import com.valoser.futacha.shared.util.extractFirstUsableTitleLine
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.compat.toModernThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.buildImportedHistoryRepository
import com.valoser.futacha.shared.ui.clearHistory
import com.valoser.futacha.shared.ui.dismissHistoryEntry
import com.valoser.futacha.shared.ui.LocalIosReviewCompliance
import com.valoser.futacha.shared.compat.compatPostActionCandidates
import com.valoser.futacha.shared.compat.compatReferencePostContextLabels
import com.valoser.futacha.shared.compat.compatQuickQuoteText
import com.valoser.futacha.shared.compat.compatMissingQuoteNotice
import com.valoser.futacha.shared.compat.hasCompatTabToolbarUpdate
import com.valoser.futacha.shared.compat.resolveCompatThreadBottomScrollIndex
import com.valoser.futacha.shared.compat.compatQuoteSelection
import com.valoser.futacha.shared.compat.compatGoogleSearchTerms
import com.valoser.futacha.shared.compat.extractCompatPosts
import com.valoser.futacha.shared.compat.extractCompatHeaderPosts
import com.valoser.futacha.shared.compat.buildCompatThreadNgRuleIndex
import com.valoser.futacha.shared.ui.compat.buildCompatForestUrl
import com.valoser.futacha.shared.ui.compat.buildCompatFtbucketUrl
import com.valoser.futacha.shared.ui.compat.buildCompatFutapoUrl
import com.valoser.futacha.shared.ui.compat.registerCompatTsumanne
import com.valoser.futacha.shared.compat.compatHeaderExtractionKinds
import com.valoser.futacha.shared.compat.compatHeaderTapTarget
import com.valoser.futacha.shared.compat.compatHeaderText
import com.valoser.futacha.shared.compat.compatPosterIdentity
import com.valoser.futacha.shared.compat.compatPosterIdentityProgressByPost
import com.valoser.futacha.shared.compat.CompatPosterIdentityProgress
import com.valoser.futacha.shared.compat.parseCompatPosterIdentity
import com.valoser.futacha.shared.compat.matchesCompatThreadNg
import com.valoser.futacha.shared.compat.COMPAT_FOREGROUND_TICK_MILLIS
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_THREAD_EXISTENCE_STALE_MILLIS
import com.valoser.futacha.shared.compat.compatReplyCount
import com.valoser.futacha.shared.compat.compatForegroundLastCheckStoredValue
import com.valoser.futacha.shared.compat.parseCompatForegroundNetworkPolicy
import com.valoser.futacha.shared.compat.parseCompatForegroundLastCheckEpochMillis
import com.valoser.futacha.shared.compat.parseCompatWatchWords
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.compat.compatWatchAllowed
import com.valoser.futacha.shared.compat.compatWatchWordsForBoard
import com.valoser.futacha.shared.compat.collectCompatWatchMatches
import com.valoser.futacha.shared.ui.compat.CompatTouchScrollAction
import com.valoser.futacha.shared.ui.compat.compatTouchScrollAction
import com.valoser.futacha.shared.compat.planCompatForegroundChecks
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.compat.shouldPreferLocalCompatSnapshot
import com.valoser.futacha.shared.compat.applyCompatOwnDeletion
import com.valoser.futacha.shared.compat.CompatNewReplyNotice
import com.valoser.futacha.shared.compat.compatThreadFooterLabel
import com.valoser.futacha.shared.compat.parseCompatThreadStatusFlags
import com.valoser.futacha.shared.compat.CompatManualRefreshNotice
import com.valoser.futacha.shared.compat.resolveCompatThreadUpdateNotices
import com.valoser.futacha.shared.compat.message
import com.valoser.futacha.shared.compat.compatCatalogCachedAtForSession
import com.valoser.futacha.shared.compat.compatCatalogOtherMenu
import com.valoser.futacha.shared.compat.compatThreadOtherMenu
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.normalizeCatalogSearchText
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrlFromUrl
import com.valoser.futacha.shared.ui.compat.COMPAT_ARCHIVE_SEARCH_HISTORY_KEY
import com.valoser.futacha.shared.ui.compat.COMPAT_ARCHIVE_SEARCH_NOTICE_HIDDEN_KEY
import com.valoser.futacha.shared.ui.compat.COMPAT_CACHE_BASE_URL_KEY
import com.valoser.futacha.shared.ui.compat.COMPAT_CACHE_ENABLED_KEY
import com.valoser.futacha.shared.ui.compat.parseCompatArchiveSearchHistory
import com.valoser.futacha.shared.ui.compat.serializeCompatArchiveSearchHistory
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.audio.TextSpeaker
import com.valoser.futacha.shared.audio.createTextSpeaker
import com.valoser.futacha.shared.audio.JAPANESE_TTS_UNAVAILABLE_MESSAGE
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandRisk
import com.valoser.futacha.shared.ai.boardSelectorParameter
import com.valoser.futacha.shared.ai.boardUrlParameter
import com.valoser.futacha.shared.ai.catalogModeParameter
import com.valoser.futacha.shared.ai.threadIdParameter
import com.valoser.futacha.shared.ai.threadUrlParameter
import com.valoser.futacha.shared.ai.wordParameter
import com.valoser.futacha.shared.service.RawHtmlSaveOptions
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.service.buildCompatManualImageFolderName
import com.valoser.futacha.shared.service.ThreadSaveLimits
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.service.runProtectedThreadSave
import com.valoser.futacha.shared.service.ImageZipSaveService
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.media.FutabaMediaKind
import com.valoser.futacha.shared.media.classifyFutabaMedia
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.ui.util.ThreadDrawerBackGestureHandler
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.VideoThumbnailRequestPriority
import com.valoser.futacha.shared.ui.image.videoThumbnailRequestPriority
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.board.buildManualThreadSaveStorageOptions
import com.valoser.futacha.shared.ui.board.buildCatalogItemLazyKeys
import com.valoser.futacha.shared.ui.util.platformSystemGestureExclusion
import com.valoser.futacha.shared.util.rememberUrlLauncher
import com.valoser.futacha.shared.util.isLegacyCompatImeBackBehavior
import com.valoser.futacha.shared.util.isAndroid
import com.valoser.futacha.shared.util.shouldResolveCatalogItemTitleFromHead
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

// HttpBoardApi deliberately gets two attempts for Futaba's occasionally
// stale keep-alive sockets. The former 15-second screen timeout cancelled that
// retry loop midway, leaving a cold thread dependent on a manual reload.
private const val COMPAT_THREAD_LOAD_TIMEOUT_MILLIS = 75_000L
private const val COMPAT_CATALOG_REFRESH_TIMEOUT_MILLIS = 20_000L
private const val COMPAT_CATALOG_MAX_ROLLBACK_GENERATIONS = 4
private const val COMPAT_CATALOG_LAYOUT_SWITCH_LOADING_MIN_MILLIS = 320L
private const val COMPAT_DROPPED_PROBE_TOTAL_TIMEOUT_MILLIS = 5_000L
private const val COMPAT_DROPPED_PROBE_MAX_ITEMS = 64
private const val COMPAT_PHASH_REQUEST_TIMEOUT_MILLIS = 3_000L
private const val COMPAT_PHASH_BATCH_TIMEOUT_MILLIS = 15_000L
private const val COMPAT_PHASH_SAVE_BATCH = 16
private const val COMPAT_READ_ALOUD_TIMER_TICK_MILLIS = 5_000L
private const val COMPAT_CLOSED_BATCH_DEADLINE_RECHECK_MILLIS = 1_000L
private val COMPAT_THREAD_PLATFORM_AI_ACTIONS = setOf(
    FutachaAiAction.RefreshCurrentThread,
    FutachaAiAction.ScrollThreadToTop,
    FutachaAiAction.ScrollThreadToBottom,
    FutachaAiAction.StartThreadSearch,
    FutachaAiAction.SearchThread,
    FutachaAiAction.NextSearchResult,
    FutachaAiAction.PreviousSearchResult,
    FutachaAiAction.OpenGallery,
    FutachaAiAction.OpenThreadSettings,
    FutachaAiAction.SaveCurrentThread,
    FutachaAiAction.DraftReply,
    FutachaAiAction.StartThreadReadAloud,
    FutachaAiAction.PauseThreadReadAloud,
    FutachaAiAction.StopThreadReadAloud,
    FutachaAiAction.NextThreadReadAloud,
    FutachaAiAction.PreviousThreadReadAloud
)

private val COMPAT_CATALOG_PLATFORM_AI_ACTIONS = setOf(
    FutachaAiAction.ScrollCatalogToTop,
    FutachaAiAction.StartCatalogSearch,
    FutachaAiAction.SearchCatalog,
    FutachaAiAction.OpenBoardExternally
)
private const val COMPAT_EDGE_SWIPE_WIDTH_DP = 64

private fun compatCatalogLastFetchCountKey(boardKey: String, sort: CompatCatalogSort): String =
    "compat.catalog.lastFetchThreadCount.$boardKey.${sort.name}"

private class CompatCatalogRefreshTimeoutException : IllegalStateException()

private fun compatFixtureThreadUrl(board: CompatBoard, item: CatalogItem): String =
    "${board.canonicalUrl.trimEnd('/')}/res/${item.id}.htm"

private fun formatCompatCatalogTime(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

@Composable
fun CompatibilityApp(
    store: CompatibilityStore,
    repository: BoardRepository?,
    stateStore: AppStateStore? = null,
    historyAutoSavedThreadRepository: SavedThreadRepository? = null,
    httpClient: HttpClient? = null,
    fileSystem: FileSystem? = null,
    cookieRepository: CookieRepository? = null,
    savedThreadRepository: SavedThreadRepository? = null,
    compatibilityHistoryRefresh: (suspend () -> Result<String>)? = null,
    appVersion: String = "1.0",
    imageLoader: ImageLoader? = null,
    catalogImageLoader: ImageLoader? = null,
    initialThreadDeepLink: String? = null,
    initialThreadDeepLinkPreapprovedBoardRegistration: Boolean = false,
    onThreadDeepLinkConsumed: (String) -> Unit = {},
    initialBoardDeepLink: String? = null,
    onBoardDeepLinkConsumed: (String) -> Unit = {},
    platformAiCommand: FutachaAiCommand? = null,
    onPlatformAiCommandConsumed: (FutachaAiCommand) -> Unit = {},
    onArchiveReportEnqueued: (Int) -> Unit = {},
    onArchiveReportEnabledChanged: (Boolean) -> Unit = {},
    onExitApplication: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    val effectiveImageLoader = imageLoader ?: remember(platformContext) {
        // CompatibilityApp is also used directly by host/instrumentation tests.
        // Keep that supported without requiring every caller to know about the
        // application-level image loader provider.
        ImageLoader.Builder(platformContext).build()
    }
    val effectiveCatalogImageLoader = catalogImageLoader ?: effectiveImageLoader
    DisposableEffect(effectiveImageLoader, imageLoader) {
        onDispose {
            if (imageLoader == null) effectiveImageLoader.shutdown()
        }
    }
    CompositionLocalProvider(
        LocalFutachaImageLoader provides effectiveImageLoader,
        LocalFutachaCatalogImageLoader provides effectiveCatalogImageLoader
    ) {
        CompatibilityAppContent(
            store = store,
            repository = repository,
            stateStore = stateStore,
            historyAutoSavedThreadRepository = historyAutoSavedThreadRepository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository,
            savedThreadRepository = savedThreadRepository,
            compatibilityHistoryRefresh = compatibilityHistoryRefresh,
            appVersion = appVersion,
            catalogImageLoader = effectiveCatalogImageLoader,
            initialThreadDeepLink = initialThreadDeepLink,
            initialThreadDeepLinkPreapprovedBoardRegistration =
                initialThreadDeepLinkPreapprovedBoardRegistration,
            onThreadDeepLinkConsumed = onThreadDeepLinkConsumed,
            initialBoardDeepLink = initialBoardDeepLink,
            onBoardDeepLinkConsumed = onBoardDeepLinkConsumed,
            platformAiCommand = platformAiCommand,
            onPlatformAiCommandConsumed = onPlatformAiCommandConsumed,
            onArchiveReportEnqueued = onArchiveReportEnqueued,
            onArchiveReportEnabledChanged = onArchiveReportEnabledChanged,
            onExitApplication = onExitApplication
        )
    }
}

@Composable
private fun CompatibilityAppContent(
    store: CompatibilityStore,
    repository: BoardRepository?,
    stateStore: AppStateStore? = null,
    historyAutoSavedThreadRepository: SavedThreadRepository? = null,
    httpClient: HttpClient? = null,
    fileSystem: FileSystem? = null,
    cookieRepository: CookieRepository? = null,
    savedThreadRepository: SavedThreadRepository? = null,
    compatibilityHistoryRefresh: (suspend () -> Result<String>)? = null,
    appVersion: String = "1.0",
    catalogImageLoader: ImageLoader,
    initialThreadDeepLink: String? = null,
    initialThreadDeepLinkPreapprovedBoardRegistration: Boolean = false,
    onThreadDeepLinkConsumed: (String) -> Unit = {},
    initialBoardDeepLink: String? = null,
    onBoardDeepLinkConsumed: (String) -> Unit = {},
    platformAiCommand: FutachaAiCommand? = null,
    onPlatformAiCommandConsumed: (FutachaAiCommand) -> Unit = {},
    onArchiveReportEnqueued: (Int) -> Unit = {},
    onArchiveReportEnabledChanged: (Boolean) -> Unit = {},
    onExitApplication: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val apngMarkerCache = remember(scope) { CompatApngMarkerCache(scope) }
    val updateCheckEnabled by produceState(initialValue = true, stateStore) {
        stateStore?.isUpdateCheckEnabled?.collect { value = it }
    }
    val importedHistoryRepository = remember(fileSystem) {
        buildImportedHistoryRepository(fileSystem)
    }
    var state by remember { mutableStateOf(CompatibilityWorkspaceState()) }
    var boards by remember { mutableStateOf<List<CompatBoard>>(emptyList()) }
    var boardsLoaded by remember { mutableStateOf(false) }
    var histories by remember { mutableStateOf<List<CompatHistoryEntry>>(emptyList()) }
    var historiesLoaded by remember { mutableStateOf(false) }
    var workspaceLoaded by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var preferencesLoaded by remember { mutableStateOf(false) }
    var changeLogChecked by remember { mutableStateOf(false) }
    var catalogSelectorOpen by rememberSaveable { mutableStateOf(false) }
    var threadSelectorOpen by rememberSaveable { mutableStateOf(false) }
    var ngRules by remember { mutableStateOf<List<CompatNgRule>>(emptyList()) }
    var toolbarRefreshToken by remember { mutableStateOf(0L) }
    var toolbarItemsBySurface by remember {
        mutableStateOf<Map<CompatToolbarSurface, List<CompatToolbarItem>>>(emptyMap())
    }
    // Settings screens are host-level destinations and therefore leave the
    // composition while a child page is open. Keep only the root anchor at
    // workspace level so returning from a child restores the exact root
    // position. Child pages intentionally start at the top each time, as in
    // the APK's separate PreferenceActivity instances (#39).
    // This value is only consumed after navigation changes the host. Keeping it
    // outside snapshot state avoids recomposing the whole app for every pixel
    // scrolled in Settings while preserving the exact return position.
    val settingsRootScrollPosition = remember { arrayOfNulls<Pair<Int, Int>>(1) }
    // Thread screens report an exact close/Undo anchor on every scroll frame.
    // Retain that operational value without making the workspace snapshot
    // observable; persistence still runs after the existing debounce.
    val latestThreadScrollAnchors = remember { mutableMapOf<String, ScrollAnchor>() }
    var previousCompatHost by remember { mutableStateOf<CompatHost?>(null) }
    var threadRefreshToken by remember { mutableStateOf(0L) }
    var scrollToBottomRequest by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var pendingUnregisteredDeepLink by remember { mutableStateOf<Pair<String, CanonicalThreadUrl>?>(null) }
    var deepLinkError by remember { mutableStateOf<String?>(null) }
    var pendingPlatformAiConfirmation by remember { mutableStateOf<FutachaAiCommand?>(null) }
    var pendingThreadAiCommand by remember { mutableStateOf<FutachaAiCommand?>(null) }
    var pendingCatalogAiCommand by remember { mutableStateOf<FutachaAiCommand?>(null) }
    var platformAiFeedback by remember { mutableStateOf<String?>(null) }
    var boardUpdateDialogOpen by remember { mutableStateOf(false) }
    var boardUpdateNotice by remember { mutableStateOf<String?>(null) }
    var closeToastVisible by remember { mutableStateOf(false) }
    var isCompatHostForeground by remember { mutableStateOf(true) }
    var workspaceRecord by remember { mutableStateOf(CompatWorkspaceRecord()) }
    // A committed refresh is fresh for this Activity session. Persisted
    // snapshots regain their cache timestamp after process recreation (#46).
    var freshCatalogRevisions by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var threadDrawerOpeningAllowed by remember { mutableStateOf(true) }
    val toolbarLoadingStyle = preferences.compatPreferenceValue(
        "design", "designLoading", "ローディング"
    ) ?: "デフォルト"
    LaunchedEffect(boardUpdateNotice) {
        val notice = boardUpdateNotice ?: return@LaunchedEffect
        delay(2_000)
        if (boardUpdateNotice == notice) boardUpdateNotice = null
    }
    // ViewPager keeps each fragment instance alive while another tab is selected.
    // SaveableStateHolder gives the Compose implementation the same per-tab lifetime
    // for LazyList/search/dialog state instead of reusing the active tab's state.
    val threadStateHolder = rememberSaveableStateHolder()
    // Catalog is removed from the host tree while a thread, settings, or another
    // secondary screen is visible. Keep one saveable state per board so returning
    // to the catalog restores the exact grid/list position instead of recreating a
    // new LazyGridState at item 0.
    val catalogStateHolder = rememberSaveableStateHolder()
    val compatPlatformContext = LocalPlatformContext.current
    val externalWatcher = rememberCompatExternalWatcher(store)
    var watcherManagerOpen by remember { mutableStateOf(false) }
    var externalWatcherSnapshot by remember {
        mutableStateOf(CompatExternalWatcherSnapshot())
    }
    val latestCompatTabs by rememberUpdatedState(state.tabs)
    val latestCompatPreferences by rememberUpdatedState(preferences)
    val latestCompatBoards by rememberUpdatedState(boards)
    val latestCompatHistories by rememberUpdatedState(histories)
    // A reference Activity starts the status AsyncTask from onCreate and does
    // not cancel it merely because the user opens Settings. Keep the probe in
    // the root composition scope rather than tying its lifetime to one host.
    val cacheStatusProbeJob = remember { arrayOfNulls<Job>(1) }
    val referenceTimerHostActive = when (state.host) {
        CompatHost.Main, is CompatHost.Catalog, is CompatHost.ThreadWorkspace -> true
        else -> false
    }
    CompatForegroundLifecycleEffect { isCompatHostForeground = it }

    /**
     * UI callbacks can outlive the Android compatibility store during an
     * Activity recreation or test teardown.  A failed persistence callback
     * must become a visible/logged operation failure, not an uncaught
     * exception on the Compose scope (which used to take down the Activity).
     * Cancellation is deliberately rethrown so normal composition disposal
     * still cancels work promptly.
     */
    fun launchStoreSafely(
        operation: String,
        userMessage: String? = null,
        block: suspend () -> Unit
    ) {
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Logger.e("CompatibilityApp", "$operation failed", failure)
                userMessage?.let { message ->
                    deepLinkError = "$message: ${failure.message.orEmpty()}"
                }
            }
        }
    }

    suspend fun persistStoreSafely(operation: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatibilityApp", "$operation failed", failure)
        }
    }

    suspend fun loadPendingClosedTabsSafely(): ClosedTabBatch? = try {
        store.loadPendingClosedTabs(Clock.System.now().toEpochMilliseconds())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        // Activity recreation and store teardown can overlap the expiry read.
        // A stale Undo batch is recoverable on the next app start; it must not
        // take down the Compose host with an uncaught SQLite exception.
        Logger.e("CompatibilityApp", "Failed to load pending closed tabs", failure)
        null
    }

    fun restoreOptimisticTab(tabKey: String, previousTabs: List<CompatTab>) {
        val previousTab = previousTabs.firstOrNull { it.key == tabKey }
        val currentWithoutOptimistic = state.tabs.filterNot { it.key == tabKey }.toMutableList()
        if (previousTab != null) {
            val previousIndex = previousTabs.indexOfFirst { it.key == tabKey }
            currentWithoutOptimistic.add(
                previousIndex.coerceIn(0, currentWithoutOptimistic.size),
                previousTab
            )
        }
        state = state.copy(tabs = distinctCompatTabs(currentWithoutOptimistic))
    }

    fun dispatch(event: CompatibilityEvent) {
        if (
            event == CompatibilityEvent.Back &&
            state.drawerPage == null &&
            state.selectorPresentation == SelectorPresentation.OVER
        ) {
            val activeSelectorOpen = when (state.host) {
                is CompatHost.Catalog -> catalogSelectorOpen
                is CompatHost.ThreadWorkspace -> threadSelectorOpen
                else -> false
            }
            if (activeSelectorOpen) {
                when (state.host) {
                    is CompatHost.Catalog -> catalogSelectorOpen = false
                    is CompatHost.ThreadWorkspace -> threadSelectorOpen = false
                    else -> Unit
                }
                state = state.copy(selectorOpen = false)
                return
            }
            // selectorOpen is retained in the pure reducer for its Back-order
            // tests. Keep it synchronized with the active surface before the
            // reducer handles navigation; another surface may have been the
            // last one to toggle its independent selector.
            state = state.copy(selectorOpen = false)
        }
        if (event == CompatibilityEvent.UndoClose) {
            val batch = state.pendingClose ?: return
            // Do not expose the restored tab before SQLite has restored its draft. Doing
            // so lets Post read an empty draft during the small transaction/Flow window.
            state = state.copy(pendingClose = null)
            scope.launch {
                try {
                    store.restoreClosedTabs(batch)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    state = state.copy(pendingClose = batch)
                    deepLinkError = "タブを元に戻せませんでした: ${failure.message.orEmpty()}"
                }
            }
            return
        }
        val closingTabKeys = when (event) {
            is CompatibilityEvent.CloseTab -> setOf(event.tabKey)
            is CompatibilityEvent.CloseTabs -> event.tabKeys
            else -> emptySet()
        }
        if (closingTabKeys.isNotEmpty()) {
            state = state.copy(
                tabs = state.tabs.map { tab ->
                    latestThreadScrollAnchors[tab.key]?.let { anchor -> tab.copy(scrollAnchor = anchor) } ?: tab
                }
            )
        }
        val reduction = reduceCompatibilityWorkspace(state, event)
        state = reduction.state.copy(tabs = distinctCompatTabs(reduction.state.tabs))
        closingTabKeys.forEach(latestThreadScrollAnchors::remove)
        reduction.effects.forEach { effect ->
            when (effect) {
                is CompatibilityEffect.PersistActiveTab -> launchStoreSafely("active tab persistence") {
                    store.selectTab(effect.tabKey)
                }
                is CompatibilityEffect.PersistCatalogHost -> launchStoreSafely("catalog host persistence") {
                    val next = workspaceRecord.copy(
                        activeTabKey = state.activeTabKey,
                        catalogHostBoardKey = effect.boardKey,
                        generation = workspaceRecord.generation + 1L
                    )
                    workspaceRecord = next
                    store.updateWorkspace(next)
                }
                is CompatibilityEffect.PersistClosedTabs -> launchStoreSafely(
                    "closed tab persistence",
                    "タブの保存に失敗しました"
                ) {
                    val persisted = store.closeTabs(
                        effect.tabKeys,
                        effect.nowEpochMillis,
                        effect.finalScrollAnchors
                    )
                    if (persisted != null) state = state.copy(pendingClose = persisted)
                }
                is CompatibilityEffect.RestoreClosedTabs -> launchStoreSafely("closed tab restore") {
                    store.restoreClosedTabs(effect.batch)
                }
                is CompatibilityEffect.FinishApplication -> onExitApplication()
                CompatibilityEffect.HideIme -> Unit
                is CompatibilityEffect.ScrollTabToBottom -> {
                    scrollToBottomRequest = effect.tabKey to Clock.System.now().toEpochMilliseconds()
                }
            }
        }
    }

    fun preferredDrawerPage(): CompatDrawerPage = state.lastDrawerPage ?: when (state.host) {
        // The tab drawer is the thread-screen default in the reference app;
        // other hosts open the history page on their first use.
        is CompatHost.ThreadWorkspace -> CompatDrawerPage.TABS
        else -> CompatDrawerPage.HISTORY
    }

    fun openPreferredDrawer() {
        dispatch(CompatibilityEvent.OpenDrawer(preferredDrawerPage()))
    }

    LaunchedEffect(store) {
        store.boards.collectLatest {
            boards = distinctCompatBoards(it)
            boardsLoaded = true
        }
    }
    LaunchedEffect(store) {
        store.history.collectLatest {
            histories = distinctCompatHistory(it)
            historiesLoaded = true
        }
    }
    LaunchedEffect(store) {
        store.preferences.collectLatest {
            preferences = it
            preferencesLoaded = true
        }
    }
    LaunchedEffect(
        preferencesLoaded,
        workspaceLoaded,
        boardsLoaded,
        historiesLoaded,
        appVersion,
        initialThreadDeepLink,
        initialBoardDeepLink,
        state.host
    ) {
        if (changeLogChecked || !preferencesLoaded || !workspaceLoaded ||
            !boardsLoaded || !historiesLoaded || state.host != CompatHost.Main ||
            initialThreadDeepLink != null || initialBoardDeepLink != null
        ) return@LaunchedEffect
        changeLogChecked = true
        persistStoreSafely("change log startup check") {
            if (consumeCompatChangeLogUpdate(store, appVersion)) {
                dispatch(CompatibilityEvent.OpenHost(CompatHost.ChangeLog()))
            }
        }
    }
    LaunchedEffect(store) { store.ngRules.collectLatest { ngRules = it } }
    LaunchedEffect(store, toolbarRefreshToken) {
        persistStoreSafely("toolbar load") {
            toolbarItemsBySurface = CompatToolbarSurface.entries.associateWith { surface ->
                store.loadToolbar(surface)
            }
        }
    }
    var customFontPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fileSystem, preferences[compatPreferenceStorageKey("design", "dummyCustomFont")]) {
        val selectedFont = preferences[compatPreferenceStorageKey("design", "dummyCustomFont")]
            ?.takeUnless { it.isBlank() || it == "デフォルト" }
        customFontPath = if (fileSystem == null || selectedFont == null) {
            null
        } else {
            withContext(AppDispatchers.io) {
                listOf("private/compat_font/font.ttf", "private/compat_font/font.otf")
                    .firstOrNull { path ->
                        runSuspendCatchingPreservingCancellation { fileSystem.exists(path) }
                            .getOrDefault(false)
                    }
                    ?.let(fileSystem::resolveAbsolutePath)
            }
        }
    }
    val customFontFamily = rememberCompatCustomFontFamily(customFontPath)
    LaunchedEffect(
        httpClient,
        store,
        isCompatHostForeground,
        state.host,
        referenceTimerHostActive,
        preferencesLoaded
    ) {
        val client = httpClient ?: return@LaunchedEffect
        if (!isCompatHostForeground || !referenceTimerHostActive || !preferencesLoaded) {
            return@LaunchedEffect
        }
        val currentPreferences = latestCompatPreferences
        val now = Clock.System.now().toEpochMilliseconds()
        val available = currentPreferences[COMPAT_CACHE_AVAILABLE_KEY] == "ON"
        val checkTime = currentPreferences[COMPAT_CACHE_CHECK_TIME_KEY]
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: now
        if (!shouldProbeCompatCacheServer(
                nowEpochMillis = now,
                storedStatusDate = currentPreferences[COMPAT_CACHE_STATUS_DATE_KEY],
                storedAvailable = available,
                storedCheckTimeEpochMillis = checkTime
            )
        ) return@LaunchedEffect

        if (cacheStatusProbeJob[0]?.isActive == true) return@LaunchedEffect
        cacheStatusProbeJob[0] = scope.launch {
            val result = probeCompatCacheServer(
                client,
                effectiveCompatCacheBaseUrl(currentPreferences[COMPAT_CACHE_BASE_URL_KEY]),
                now
            )
            val statusDate = formatCompatCacheStatusDate(result.checkedAtEpochMillis)
            persistStoreSafely("cache-server status persistence") {
                store.savePreference(COMPAT_CACHE_AVAILABLE_KEY, if (result.available) "ON" else "OFF")
                store.savePreference(COMPAT_CACHE_CHECK_TIME_KEY, result.checkedAtEpochMillis.toString())
                store.savePreference(COMPAT_CACHE_STATUS_DATE_KEY, statusDate)
                store.savePreference(
                    COMPAT_CACHE_STATUS_KEY,
                    formatCompatCacheStatusSummary(statusDate, result.message)
                )
            }
        }
    }
    LaunchedEffect(
        repository,
        store,
        compatPlatformContext,
        isCompatHostForeground,
        state.host,
        referenceTimerHostActive,
        preferencesLoaded,
        boardsLoaded,
        historiesLoaded,
        workspaceLoaded
    ) {
        if (
            !isCompatHostForeground || !referenceTimerHostActive || !preferencesLoaded || !boardsLoaded ||
            !historiesLoaded || !workspaceLoaded
        ) return@LaunchedEffect
        val activeRepository = repository ?: return@LaunchedEffect
        val initialPreferences = latestCompatPreferences
        var lastUpdateCheckAt = parseCompatForegroundLastCheckEpochMillis(
            initialPreferences[COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE]
        )
        var lastExistenceCheckAt = parseCompatForegroundLastCheckEpochMillis(
            initialPreferences[COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE]
        )
        var lastWatchCheckAt = Clock.System.now().toEpochMilliseconds()
        var firstTick = true
        while (true) {
            // The reference TimerTask is scheduled with delay=0 and then every
            // minute. Hidden persisted timestamps prevent an app restart from
            // repeating a check before its five/fifteen-minute deadline.
            if (firstTick) firstTick = false else delay(COMPAT_FOREGROUND_TICK_MILLIS)
            val currentPreferences = latestCompatPreferences
            val now = Clock.System.now().toEpochMilliseconds()
            val plan = withContext(AppDispatchers.io) { planCompatForegroundChecks(
                nowEpochMillis = now,
                lastUpdateCheckEpochMillis = lastUpdateCheckAt,
                lastExistenceCheckEpochMillis = lastExistenceCheckAt,
                updatePolicy = parseCompatForegroundNetworkPolicy(
                    currentPreferences.compatPreferenceValue(
                        "background", "backgroundThreadUpdateCheck", "スレッドの更新確認"
                    )
                ),
                existencePolicy = parseCompatForegroundNetworkPolicy(
                    currentPreferences.compatPreferenceValue(
                        "background", "backgroundThreadExistCheck", "スレッドの生存確認"
                    )
                ),
                isWifiConnected = isCompatWifiConnected(compatPlatformContext)
            ) }
            if (plan.checkUpdates) {
                lastUpdateCheckAt = now
                persistStoreSafely("foreground update-check timestamp") {
                    store.savePreference(
                        COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE,
                        compatForegroundLastCheckStoredValue(now)
                    )
                }
            }
            if (plan.checkExistence) {
                lastExistenceCheckAt = now
                persistStoreSafely("foreground existence-check timestamp") {
                    store.savePreference(
                        COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE,
                        compatForegroundLastCheckStoredValue(now)
                    )
                }
            }
            val watchDue = compatWatchAllowed(currentPreferences, isCompatWifiConnected(compatPlatformContext)) && hasEpochIntervalElapsed(
                nowMillis = now,
                startedAtMillis = lastWatchCheckAt,
                intervalMillis = com.valoser.futacha.shared.compat.COMPAT_THREAD_UPDATE_INTERVAL_MILLIS
            )
            if (watchDue) lastWatchCheckAt = now
            val tabsToCheck = latestCompatTabs.filterNot(CompatTab::isDead)
            // A watcher board may have no open tab.  Do not return before the
            // catalog-only watcher pass below, otherwise the setting appears
            // to work only while a thread tab happens to be open.
            if (tabsToCheck.isEmpty() && !watchDue) continue
            if (!plan.hasWork && !watchDue) continue
            if (plan.checkUpdates || watchDue) {
                withContext(AppDispatchers.io) {
                // The APK does not download every thread here. It fetches each board's
                // catalog once and reflects only the latest reply count in tabs/history.
                tabsToCheck.groupBy(CompatTab::boardKey).forEach boardLoop@{ (boardKey, boardTabs) ->
                    val board = latestCompatBoards.firstOrNull { it.key == boardKey }
                        ?: return@boardLoop
                    runSuspendCatchingPreservingCancellation {
                        activeRepository.getCatalog(board.originalUrl, CatalogMode.Catalog)
                    }
                        .onSuccess { catalog ->
                            val byCanonicalUrl = catalog.mapNotNull { item ->
                                canonicalizeThreadUrl(item.threadUrl)?.canonicalUrl?.let { it to item }
                            }.toMap()
                            val byThreadId = catalog.associateBy(CatalogItem::id)
                            boardTabs.forEach tabLoop@{ checkedTab ->
                                val item = byCanonicalUrl[checkedTab.canonicalUrl]
                                    ?: byThreadId[checkedTab.threadNo]
                                    ?: return@tabLoop
                                if (item.replyCount != checkedTab.replyCount) {
                                    persistStoreSafely("foreground tab refresh") {
                                        store.applyCatalogReplyCount(
                                            checkedTab.key,
                                            checkedTab.canonicalUrl,
                                            item.replyCount
                                        )
                                    }
                                }
                            }
                            if (watchDue) {
                                collectCompatWatchMatches(
                                    board = board,
                                    items = catalog,
                                    watchWords = compatWatchWordsForBoard(currentPreferences, board.key),
                                    existingHistory = latestCompatHistories,
                                    nowEpochMillis = now
                                ).takeIf { it.isNotEmpty() }?.let { matches ->
                                    persistStoreSafely("foreground watch history refresh") {
                                        CompatWatcherRepository(store).recordAll(matches)
                                    }
                                }
                            }
                        }
                }
                // A watched board can have no open tabs. It still must be checked
                // and added to the compatibility watcher's history page.
                if (watchDue) {
                    val tabBoardKeys = tabsToCheck.mapTo(hashSetOf(), CompatTab::boardKey)
                    latestCompatBoards.filterNot { board -> board.key in tabBoardKeys }
                        .forEach { board ->
                            runSuspendCatchingPreservingCancellation {
                                activeRepository.getCatalog(board.originalUrl, CatalogMode.New)
                            }
                                .onSuccess { catalog ->
                                    collectCompatWatchMatches(
                                        board = board,
                                        items = catalog,
                                        watchWords = compatWatchWordsForBoard(currentPreferences, board.key),
                                        existingHistory = latestCompatHistories,
                                        nowEpochMillis = now
                                    ).forEach { match ->
                                        persistStoreSafely("foreground watch history refresh") {
                                            CompatWatcherRepository(store).record(match)
                                        }
                                    }
                                }
                        }
                }
                }
            }
            // BackgroundThreadUpdateCheckAsyncTask also performs this stale existence
            // pass; the dedicated 15-minute setting can trigger the same pass alone.
            if (plan.checkExistence || plan.checkUpdates) {
                withContext(AppDispatchers.io) {
                tabsToCheck.filter { tab ->
                    hasEpochIntervalElapsed(
                        nowMillis = now,
                        startedAtMillis = tab.contentUpdatedAtEpochMillis,
                        intervalMillis = COMPAT_THREAD_EXISTENCE_STALE_MILLIS
                    )
                }.forEach { checkedTab ->
                    runSuspendCatchingPreservingCancellation {
                        activeRepository.probeThreadGone(checkedTab.originalUrl)
                    }
                        .onSuccess { isGone ->
                            if (isGone) persistStoreSafely("foreground dead-thread update") {
                                store.updateTab(checkedTab.copy(isDead = true))
                            }
                        }
                }
                }
            }
        }
    }
    LaunchedEffect(store) {
        loadPendingClosedTabsSafely()?.let { batch ->
            state = state.copy(pendingClose = batch)
        }
    }
    LaunchedEffect(store) {
        combine(store.tabs, store.workspace) { tabs, workspace -> tabs to workspace }
            .collectLatest { (tabs, workspace) ->
                workspaceRecord = workspace
                state = reduceCompatibilityWorkspace(
                    state,
                    CompatibilityEvent.ReplaceTabs(tabs, workspace.activeTabKey)
                ).state.copy(
                    catalogHostBoardKey = workspace.catalogHostBoardKey,
                    selectorPresentation = if (
                        preferences.compatPreferenceValue(
                            "design", "designTabSelectorLocation", "タブ一覧の表示位置"
                        )?.let { compatPreferenceDisplayValue("designTabSelectorLocation", it) } ==
                            "ツールバーの上に重ねる"
                    ) SelectorPresentation.OVER else SelectorPresentation.ABOVE
                )
                workspaceLoaded = true
            }
    }
    LaunchedEffect(
        preferencesLoaded,
        preferences.compatPreferenceValue("design", "designTabSelectorOpened", "最初から表示する")
    ) {
        if (!preferencesLoaded) return@LaunchedEffect
        val initiallyOpen = preferences.compatPreferenceValue(
            "design", "designTabSelectorOpened", "最初から表示する"
        ) == "ON"
        state = state.copy(selectorOpen = initiallyOpen)
        // This preference is the initial visibility for both activities in the
        // reference app. Toolbar toggles are temporary UI state: they must not
        // turn an OFF startup preference into a permanently open selector.
        // Re-apply both values when the preference itself changes so switching
        // OFF in Settings takes effect for Catalog and Thread together.
        catalogSelectorOpen = initiallyOpen
        threadSelectorOpen = initiallyOpen
    }
    LaunchedEffect(
        preferencesLoaded,
        preferences.compatPreferenceValue("design", "designTabSelectorLocation", "タブ一覧の表示位置")
    ) {
        if (!preferencesLoaded) return@LaunchedEffect
        state = state.copy(
            selectorPresentation = if (
                preferences.compatPreferenceValue(
                    "design", "designTabSelectorLocation", "タブ一覧の表示位置"
                )?.let { compatPreferenceDisplayValue("designTabSelectorLocation", it) } ==
                    "ツールバーの上に重ねる"
            ) SelectorPresentation.OVER else SelectorPresentation.ABOVE
        )
    }
    LaunchedEffect(state.drawerPage) {
        if (state.drawerPage != null) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed && state.drawerPage != null) {
            dispatch(CompatibilityEvent.CloseDrawer)
        }
    }
    LaunchedEffect(state.pendingClose?.id) {
        val batch = state.pendingClose ?: return@LaunchedEffect
        while (true) {
            val remaining = remainingCompatDeadlineMillis(
                batch.expiresAtEpochMillis,
                Clock.System.now().toEpochMilliseconds()
            )
            if (remaining <= 0L) break
            // delay() may resume a fraction early; re-check the absolute deadline so an
            // early wake-up cannot strand the serialized batch and its attachment.
            delay(remaining.coerceIn(1L, COMPAT_CLOSED_BATCH_DEADLINE_RECHECK_MILLIS))
        }
        if (state.pendingClose?.id == batch.id) {
            state = state.copy(pendingClose = null)
            // Expiry is a permanent discard boundary: remove the serialized draft and
            // let the platform store release its private attachment payload immediately.
            loadPendingClosedTabsSafely()
        }
    }
    val closeToastDurationMillis = resolveCompatCloseToastDurationMillis(
        preferences.compatPreferenceValue(
            "control", "controlCloseToastDuration", "タブを閉じた時の通知"
        )
    )
    LaunchedEffect(state.pendingClose?.id, closeToastDurationMillis) {
        val batchId = state.pendingClose?.id
        val shouldShow = batchId != null && shouldShowCompatCloseToast(
            state.pendingClose?.tabs?.size ?: 0,
            closeToastDurationMillis
        )
        closeToastVisible = shouldShow
        if (shouldShow) {
            delay(closeToastDurationMillis)
            if (state.pendingClose?.id == batchId) closeToastVisible = false
        }
    }
    // Keep the workspace callback out of the thread screen.  Android 8/10's
    // OnBackPressedDispatcher ordering can deliver the callback registered by
    // the parent before the nested search callback, which skips the APK-style
    // IME/focus/close stages.  The thread surface installs its own fallback
    // below, after its search/quote handlers.
    val compatibilityWorkspaceOwnsIosLeftEdge = when (state.host) {
        CompatHost.Main,
        is CompatHost.Catalog,
        is CompatHost.ThreadWorkspace -> true
        else -> false
    }
    PlatformBackHandler(
        enabled = state.host !is CompatHost.ThreadWorkspace,
        iosEdgeGestureEnabled = !compatibilityWorkspaceOwnsIosLeftEdge
    ) {
        // The toolbar editor persists each change immediately, but the screen
        // behind it must also re-read the toolbar when Android's system Back
        // (gesture or 3-button navigation) is used.  The top-left arrow takes
        // this same refresh path in the editor callback; keep both paths
        // equivalent so a restart is never required to see the new toolbar.
        if (state.host is CompatHost.ToolbarEditor) {
            toolbarRefreshToken += 1L
        }
        dispatch(CompatibilityEvent.Back)
    }

    suspend fun closeCompatDrawerSurface() {
        try {
            // Keep drawerContent composed until the modal surface has finished
            // closing. Removing it first can cancel close() on Android 11 and
            // leave a transparent layer intercepting the destination (#10).
            drawerState.close()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // The activity may already be disposing the drawer. Callers still
            // clear the logical drawer state below.
        }
    }

    /**
     * Clear the logical drawer state before waiting for Material's close
     * animation.  Waiting first made history/tab/settings taps look dead when
     * the drawer animation was delayed by a busy frame (#10).
     */
    fun closeDrawerForNavigation() {
        dispatch(CompatibilityEvent.CloseDrawer)
        scope.launch { closeCompatDrawerSurface() }
    }

    fun openHistory(entry: CompatHistoryEntry) {
        val now = Clock.System.now().toEpochMilliseconds()
        val tab = CompatTab(
            key = compatTabKey(entry.canonicalUrl),
            canonicalUrl = entry.canonicalUrl,
            originalUrl = entry.originalUrl,
            boardKey = entry.boardKey,
            boardName = entry.boardName,
            threadNo = entry.threadNo,
            title = entry.title,
            thumbnailUrl = entry.thumbnailUrl,
            replyCount = entry.replyCount,
            insertedAtEpochMillis = now,
            contentUpdatedAtEpochMillis = entry.contentUpdatedAtEpochMillis,
            scrollAnchor = entry.scrollAnchor
        )
        val origin = when (val host = state.host) {
            CompatHost.Main -> CompatThreadOrigin.MAIN
            is CompatHost.Catalog -> CompatThreadOrigin.CATALOG
            is CompatHost.ThreadWorkspace -> host.origin
            else -> CompatThreadOrigin.MAIN
        }
        val previousTabs = state.tabs
        val durableTab = previousTabs.firstOrNull { it.key == tab.key }
            ?.let { tab.copy(scrollAnchor = it.scrollAnchor) }
            ?: tab
        state = state.copy(tabs = previousTabs.prependCompatTab(durableTab))
        // Close the logical surface and navigate immediately. The history
        // write is persistence, not a prerequisite for rendering the thread;
        // waiting for SQLite here made a drawer tap look dead on devices where
        // the compatibility database was briefly busy.
        closeDrawerForNavigation()
        dispatch(CompatibilityEvent.OpenThread(tab.key, origin))
        scope.launch {
            try {
                store.openTab(tab, entry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                restoreOptimisticTab(tab.key, previousTabs)
                deepLinkError = "履歴のスレッドを開けませんでした: ${failure.message.orEmpty()}"
                Logger.e("CompatibilityApp", "Failed to open history thread", failure)
            }
        }
    }

    fun openExternalWatcherEntry(entry: CompatExternalWatcherEntry) {
        val parsed = canonicalizeThreadUrl(entry.threadUrl)
        if (parsed == null) return
        val board = boards.firstOrNull { it.canonicalUrl == parsed.canonicalBoardUrl }
        if (board == null) {
            pendingUnregisteredDeepLink = entry.threadUrl to parsed
            closeDrawerForNavigation()
            return
        }
        val now = Clock.System.now().toEpochMilliseconds()
        openHistory(
            CompatHistoryEntry(
                canonicalUrl = parsed.canonicalUrl,
                originalUrl = entry.threadUrl,
                boardKey = board.key,
                boardName = entry.boardName.orEmpty().ifBlank { board.name },
                threadNo = parsed.threadNo,
                title = entry.title.ifBlank { "No.${parsed.threadNo}" },
                thumbnailUrl = entry.thumbnailUrl,
                replyCount = entry.replyCount,
                contentUpdatedAtEpochMillis = entry.updatedAtEpochMillis.takeIf { it > 0L } ?: now
            )
        )
    }

    fun refreshExternalWatcher() {
        scope.launch {
            externalWatcherSnapshot = externalWatcher.load().getOrElse { failure ->
                CompatExternalWatcherSnapshot(message = failure.message ?: "巡回結果を取得できませんでした")
            }
        }
    }

    LaunchedEffect(state.drawerPage, externalWatcher) {
        if (state.drawerPage == CompatDrawerPage.WATCHER) refreshExternalWatcher()
    }

    if (watcherManagerOpen) {
        CompatWatcherManager(
            store = store, repository = repository,
            onDismiss = { watcherManagerOpen = false; refreshExternalWatcher() },
            onResultsChanged = ::refreshExternalWatcher,
            onOpenExternal = if (isAndroid()) ({ externalWatcher.openManager() }) else null,
            onOpenHelp = {
                watcherManagerOpen = false
                dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(state.host)))
            }
        )
    }

    // Settings is a host-level screen, not a child of the navigation drawer.
    // Some entry points (notably the toolbar/settings path on older state
    // restores) can change the host without going through the drawer callback.
    // Close the modal drawer whenever Settings becomes the active host so it
    // cannot remain over the settings screen or intercept its back gesture.
    LaunchedEffect(state.host, state.drawerPage) {
        if (state.host is CompatHost.Settings && state.drawerPage != null) {
            closeCompatDrawerSurface()
            dispatch(CompatibilityEvent.CloseDrawer)
        }
    }

    // A settings child page is an in-place navigation within one settings
    // session, so the root anchor survives that round trip. Once the entire
    // settings host is left, start a fresh session on the next entry instead
    // of restoring an old root offset (#39).
    LaunchedEffect(state.host) {
        val wasSettings = previousCompatHost is CompatHost.Settings
        val isSettings = state.host is CompatHost.Settings
        if (wasSettings && !isSettings) {
            settingsRootScrollPosition[0] = null
        }
        previousCompatHost = state.host
    }

    fun openCanonicalThread(
        parsed: CanonicalThreadUrl,
        board: CompatBoard,
        origin: CompatThreadOrigin = CompatThreadOrigin.DEEP_LINK,
        onOpened: (() -> Unit)? = null
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val tab = CompatTab(
            key = compatTabKey(parsed.canonicalUrl),
            canonicalUrl = parsed.canonicalUrl,
            originalUrl = parsed.canonicalUrl,
            boardKey = board.key,
            boardName = board.name,
            threadNo = parsed.threadNo,
            title = "No.${parsed.threadNo}",
            insertedAtEpochMillis = now,
            contentUpdatedAtEpochMillis = now
        )
        val entry = CompatHistoryEntry(
            canonicalUrl = tab.canonicalUrl,
            originalUrl = tab.originalUrl,
            boardKey = tab.boardKey,
            boardName = tab.boardName,
            threadNo = tab.threadNo,
            title = tab.title,
            contentUpdatedAtEpochMillis = now
        )
        val previousTabs = state.tabs
        val durableTab = previousTabs.firstOrNull { it.key == tab.key }
            ?.let { tab.copy(scrollAnchor = it.scrollAnchor) }
            ?: tab
        state = state.copy(tabs = previousTabs.prependCompatTab(durableTab))
        scope.launch {
            // A deep link may introduce a board at the same time as its first
            // tab.  The Android store enforces compat_tab.board_key as a
            // foreign key, so the board must be committed before the tab.
            // Keeping both writes in this coroutine also removes the race that
            // used to crash when "追加して開く" was tapped on a new board.
            try {
                store.upsertBoard(board)
                store.openTab(tab, entry)
                dispatch(CompatibilityEvent.OpenThread(tab.key, origin))
                onOpened?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                restoreOptimisticTab(tab.key, previousTabs)
                deepLinkError = "スレッドを開けませんでした: ${failure.message.orEmpty()}"
                Logger.e("CompatibilityApp", "Failed to open deep-linked thread", failure)
            }
        }
    }

    fun currentPlatformAiTab(): CompatTab? = state.activeTabKey?.let { key ->
        state.tabs.firstOrNull { it.key == key }
    }

    fun runPlatformAiCommand(command: FutachaAiCommand, confirmed: Boolean = false) {
        if (command.action.risk == FutachaAiCommandRisk.Confirm && !confirmed) {
            pendingPlatformAiConfirmation = command
            return
        }
        when (command.action) {
            FutachaAiAction.OpenBoardList -> dispatch(CompatibilityEvent.OpenHost(CompatHost.Main))
            FutachaAiAction.OpenBoard, FutachaAiAction.SearchAndOpenBoard -> {
                val board = resolvePlatformAiBoard(command, state, boards)
                if (board == null) deepLinkError = "対象板を特定できませんでした"
                else dispatch(CompatibilityEvent.OpenCatalog(board.key))
            }
            FutachaAiAction.OpenThread, FutachaAiAction.OpenThreadFromUrl -> {
                val target = resolvePlatformAiThread(command, state, boards)
                if (target == null) deepLinkError = "対象スレを特定できませんでした"
                else openCanonicalThread(target.first, target.second)
            }
            FutachaAiAction.OpenThreadExternally -> {
                val target = resolvePlatformAiThread(command, state, boards)
                if (target != null) {
                    pendingThreadAiCommand = command.copy(
                        parameters = command.parameters + ("threadId" to target.first.threadNo)
                    )
                    openCanonicalThread(target.first, target.second)
                } else if (state.activeTabKey != null) {
                    pendingThreadAiCommand = command
                } else {
                    deepLinkError = "先に対象スレを開いてください"
                }
            }
            FutachaAiAction.SaveThread -> {
                val target = resolvePlatformAiThread(command, state, boards)
                if (target == null) deepLinkError = "対象スレを特定できませんでした"
                else {
                    openCanonicalThread(target.first, target.second)
                    pendingThreadAiCommand = command.copy(
                        action = FutachaAiAction.SaveCurrentThread,
                        parameters = command.parameters + ("threadId" to target.first.threadNo)
                    )
                }
            }
            FutachaAiAction.OpenHistoryDrawer -> dispatch(CompatibilityEvent.OpenDrawer(CompatDrawerPage.HISTORY))
            FutachaAiAction.RefreshHistory -> {
                val refresh = compatibilityHistoryRefresh
                if (refresh == null) {
                    deepLinkError = "履歴更新サービスを利用できません"
                } else {
                    launchStoreSafely("AI command", "操作に失敗しました") {
                        refresh()
                            .onSuccess { message -> platformAiFeedback = message }
                            .onFailure { failure ->
                                deepLinkError = "履歴を更新できませんでした: ${failure.message.orEmpty()}"
                            }
                    }
                }
            }
            FutachaAiAction.OpenSavedThreads -> dispatch(CompatibilityEvent.OpenHost(CompatHost.SavedThreads(state.host)))
            FutachaAiAction.OpenGlobalSettings,
            FutachaAiAction.OpenVersionInfo,
            FutachaAiAction.OpenCookieManagement,
            FutachaAiAction.OpenFileManagerSettings -> dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(origin = state.host)))
            FutachaAiAction.OpenCatalogSettings,
            FutachaAiAction.OpenCatalogDisplaySettings,
            FutachaAiAction.OpenNgManagement,
            FutachaAiAction.OpenWatchWords -> {
                val board = resolvePlatformAiBoard(command, state, boards)
                if (board == null) deepLinkError = "対象板を特定できませんでした"
                else dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(path = "catalog", origin = CompatHost.Catalog(board.key))))
            }
            FutachaAiAction.OpenThreadSettings -> dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(path = "thread", origin = state.host)))
            FutachaAiAction.DraftThread -> {
                val board = resolvePlatformAiBoard(command, state, boards)
                if (board == null) deepLinkError = "対象板を特定できませんでした"
                else dispatch(CompatibilityEvent.OpenHost(CompatHost.PostBuild(board.key)))
            }
            FutachaAiAction.DraftReply -> {
                val tab = state.activeTabKey
                if (tab == null) deepLinkError = "先に対象スレを開いてください"
                else dispatch(CompatibilityEvent.OpenHost(CompatHost.Post(tab)))
            }
            FutachaAiAction.SaveCurrentThread -> {
                if (state.activeTabKey == null) deepLinkError = "先に対象スレを開いてください"
                else pendingThreadAiCommand = command
            }
            FutachaAiAction.RefreshCurrentBoard,
            FutachaAiAction.RefreshCatalog -> {
                val board = resolvePlatformAiBoard(command, state, boards)
                if (board == null) deepLinkError = "対象板を特定できませんでした"
                else {
                    toolbarRefreshToken += 1L
                    dispatch(CompatibilityEvent.OpenCatalog(board.key))
                }
            }
            FutachaAiAction.ScrollCatalogToTop,
            FutachaAiAction.StartCatalogSearch,
            FutachaAiAction.SearchCatalog,
            FutachaAiAction.OpenBoardExternally -> {
                val board = resolvePlatformAiBoard(command, state, boards)
                if (board == null) deepLinkError = "対象板を特定できませんでした"
                else {
                    pendingCatalogAiCommand = command
                    dispatch(CompatibilityEvent.OpenCatalog(board.key))
                }
            }
            FutachaAiAction.RefreshCurrentThread -> {
                threadRefreshToken += 1L
                if (state.activeTabKey == null) deepLinkError = "先に対象スレを開いてください"
            }
            FutachaAiAction.OpenGallery -> {
                val tab = state.activeTabKey
                if (tab == null) deepLinkError = "先に対象スレを開いてください"
                else dispatch(CompatibilityEvent.OpenHost(CompatHost.Gallery(tab)))
            }
            FutachaAiAction.EnablePrivacyFilter -> launchStoreSafely("AI command", "操作に失敗しました") { stateStore?.setPrivacyFilterEnabled(true) }
            FutachaAiAction.DisablePrivacyFilter -> launchStoreSafely("AI command", "操作に失敗しました") { stateStore?.setPrivacyFilterEnabled(false) }
            FutachaAiAction.EnableBackgroundRefresh -> launchStoreSafely("AI command", "操作に失敗しました") { stateStore?.setBackgroundRefreshEnabled(true) }
            FutachaAiAction.DisableBackgroundRefresh -> launchStoreSafely("AI command", "操作に失敗しました") { stateStore?.setBackgroundRefreshEnabled(false) }
            FutachaAiAction.EnableThreadSummaryMode -> launchStoreSafely("AI command", "操作に失敗しました") { stateStore?.setThreadSummaryModeEnabled(true) }
            FutachaAiAction.DisableThreadSummaryMode -> launchStoreSafely("AI command", "操作に失敗しました") { stateStore?.setThreadSummaryModeEnabled(false) }
            FutachaAiAction.EnableAiPostFilter -> launchStoreSafely("AI command", "操作に失敗しました") { stateStore?.setAiPostFilterEnabled(true) }
            FutachaAiAction.DisableAiPostFilter -> launchStoreSafely("AI command", "操作に失敗しました") { stateStore?.setAiPostFilterEnabled(false) }
            FutachaAiAction.SetCatalogMode -> {
                val board = resolvePlatformAiBoard(command, state, boards)
                val sort = resolvePlatformAiCatalogSort(command)
                when {
                    board == null -> deepLinkError = "対象板を特定できませんでした"
                    sort == null -> deepLinkError = "カタログモードを特定できませんでした"
                    else -> launchStoreSafely("AI command", "操作に失敗しました") {
                        val current = store.loadCatalogPreference(board.key)
                        store.saveCatalogPreference(current.copy(sort = sort))
                        dispatch(CompatibilityEvent.OpenCatalog(board.key))
                    }
                }
            }
            FutachaAiAction.AddWatchWord -> {
                val word = command.wordParameter()
                if (word.isNullOrBlank()) deepLinkError = "追加する監視ワードを指定してください"
                else launchStoreSafely("AI command", "操作に失敗しました") {
                    val existing = com.valoser.futacha.shared.compat.compatWatchRules(preferences)
                    CompatWatcherRepository(store).saveRules(
                        (existing + com.valoser.futacha.shared.compat.CompatWatchRule(word.trim())).distinct()
                    )
                }
            }
            FutachaAiAction.AddNgWord,
            FutachaAiAction.AddNgHeader -> {
                val value = if (command.action == FutachaAiAction.AddNgHeader) {
                    command.parameter("header") ?: command.wordParameter()
                } else {
                    command.wordParameter()
                }?.normalizeCompatNgValue()
                if (value.isNullOrBlank()) {
                    deepLinkError = if (command.action == FutachaAiAction.AddNgHeader) {
                        "追加するNGヘッダーを指定してください"
                    } else {
                        "追加するNGワードを指定してください"
                    }
                } else {
                    val kind = if (command.action == FutachaAiAction.AddNgHeader) {
                        CompatNgKind.THREAD_REFUSE
                    } else {
                        CompatNgKind.THREAD_WORD
                    }
                    launchStoreSafely("AI command", "操作に失敗しました") {
                        val added = store.upsertNgRule(
                            CompatNgRule(
                                id = compatNgRuleId(kind, "*", value),
                                kind = kind,
                                scopeKey = "*",
                                normalizedValue = value,
                                createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                            )
                        )
                        if (!added) deepLinkError = "NGルールを保存できませんでした"
                    }
                }
            }
            FutachaAiAction.ClearHistory -> launchStoreSafely("AI command", "操作に失敗しました") {
                val modernStore = stateStore
                if (modernStore != null) {
                    clearHistory(
                        stateStore = modernStore,
                        autoSavedThreadRepository = historyAutoSavedThreadRepository,
                        importedHistoryRepository = importedHistoryRepository,
                        compatibilityStore = store,
                        onSkippedThreadsCleared = {},
                        onAutoSavedThreadDeleteFailure = { failure ->
                            Logger.e("CompatibilityApp", "AI history payload purge failed", failure)
                        }
                    )
                } else {
                    store.clearHistory()
                    listOfNotNull(historyAutoSavedThreadRepository, importedHistoryRepository)
                        .distinct()
                        .forEach { repository ->
                            repository.purgeAllStorage().exceptionOrNull()?.let { failure ->
                                Logger.e("CompatibilityApp", "AI history payload purge failed", failure)
                            }
                        }
                }
            }
            FutachaAiAction.DeleteHistoryEntry -> {
                val target = resolvePlatformAiThread(command, state, boards)
                if (target == null) deepLinkError = "削除する履歴を特定できませんでした"
                else launchStoreSafely("AI command", "操作に失敗しました") {
                    val compatibilityEntry = histories.firstOrNull { entry ->
                        entry.canonicalUrl == target.first.canonicalUrl
                    }
                    if (compatibilityEntry == null) {
                        deepLinkError = "削除する履歴を特定できませんでした"
                        return@launchStoreSafely
                    }
                    val modernStore = stateStore
                    val modernEntry = modernStore?.history?.first()
                        ?.firstOrNull { candidate ->
                            candidate.toCompatHistoryEntry()?.canonicalUrl == compatibilityEntry.canonicalUrl
                        }
                        ?: compatibilityEntry.toModernThreadHistoryEntry()
                    if (modernStore != null && modernEntry != null) {
                        dismissHistoryEntry(
                            stateStore = modernStore,
                            autoSavedThreadRepository = historyAutoSavedThreadRepository,
                            importedHistoryRepository = importedHistoryRepository,
                            compatibilityStore = store,
                            entry = modernEntry,
                            onAutoSavedThreadDeleteFailure = { failure ->
                                Logger.e("CompatibilityApp", "AI history payload deletion failed", failure)
                            }
                        )
                    } else {
                        store.deleteHistory(compatibilityEntry.canonicalUrl)
                        listOfNotNull(historyAutoSavedThreadRepository, importedHistoryRepository)
                            .distinct()
                            .forEach { repository ->
                                repository.purgeThreadStorage(
                                    threadId = compatibilityEntry.threadNo,
                                    boardId = compatibilityEntry.boardKey
                                ).exceptionOrNull()?.let { failure ->
                                    Logger.e("CompatibilityApp", "AI history payload deletion failed", failure)
                                }
                            }
                    }
                }
            }
            FutachaAiAction.DeleteSavedThread -> {
                val repository = savedThreadRepository
                if (repository == null) {
                    deepLinkError = "保存済みスレの保存先を利用できません"
                } else {
                    val active = currentPlatformAiTab()
                    val requestedThread = command.threadIdParameter() ?: active?.threadNo
                    val requestedBoard = resolvePlatformAiBoard(command, state, boards)?.key ?: active?.boardKey
                    launchStoreSafely("AI command", "操作に失敗しました") {
                        val matches = repository.getAllThreads().filter { saved ->
                            (requestedThread == null || saved.threadId == requestedThread) &&
                                (requestedBoard == null || saved.boardId.equals(requestedBoard, ignoreCase = true))
                        }
                        when (matches.size) {
                            0 -> deepLinkError = "削除する保存済みスレを特定できませんでした"
                            1 -> repository.deleteThread(matches.single().threadId, matches.single().boardId)
                                .onFailure { failure ->
                                    deepLinkError = "保存済みスレを削除できませんでした: ${failure.message.orEmpty()}"
                                }
                            else -> deepLinkError = "同じ条件の保存済みスレが複数あります。板も指定してください"
                        }
                    }
                }
            }
            FutachaAiAction.ClearSavedThreads -> {
                val repository = savedThreadRepository
                if (repository == null) deepLinkError = "保存済みスレの保存先を利用できません"
                else launchStoreSafely("AI command", "操作に失敗しました") {
                    repository.deleteAllThreads().onFailure { failure ->
                        deepLinkError = "保存済みスレを全削除できませんでした: ${failure.message.orEmpty()}"
                    }
                }
            }
            FutachaAiAction.AddBoard -> {
                val url = command.boardUrlParameter()
                val canonical = url?.let(::canonicalizeBoardUrl)
                if (canonical == null) {
                    deepLinkError = "追加する板URLを解釈できませんでした"
                } else if (boards.any { it.canonicalUrl.equals(canonical, ignoreCase = true) }) {
                    deepLinkError = "その板はすでに追加されています"
                } else {
                    val name = command.parameter("name", "board", "title", "label")
                        ?.takeIf { it.isNotBlank() }
                        ?: canonical.substringAfterLast('/')
                    launchStoreSafely("AI command", "操作に失敗しました") {
                        store.upsertBoard(
                            CompatBoard(
                                key = compatBoardKey(canonical),
                                name = name,
                                canonicalUrl = canonical,
                                originalUrl = url,
                                sortOrder = boards.size
                            )
                        )
                    }
                }
            }
            FutachaAiAction.DeleteBoard -> {
                val board = resolvePlatformAiBoard(command, state, boards)
                if (board == null) deepLinkError = "削除する板を特定できませんでした"
                else launchStoreSafely("AI command", "操作に失敗しました") {
                    store.deleteBoard(board.key)
                    if ((state.host as? CompatHost.Catalog)?.boardKey == board.key) {
                        dispatch(CompatibilityEvent.OpenHost(CompatHost.Main))
                    }
                }
            }
            else -> {
                // Every remaining action has an equivalent visible destination
                // in the compatibility workspace. Keep the command explicit
                // instead of silently consuming it while that destination
                // performs its own confirmation or user-initiated operation.
                deepLinkError = "${command.action.label} は対象画面を開いてから実行してください"
            }
        }
    }

    LaunchedEffect(platformAiCommand) {
        val command = platformAiCommand ?: return@LaunchedEffect
        if (command.action in COMPAT_THREAD_PLATFORM_AI_ACTIONS &&
            command.action.risk != FutachaAiCommandRisk.Confirm
        ) {
            pendingThreadAiCommand = command
        } else if (command.action in COMPAT_CATALOG_PLATFORM_AI_ACTIONS) {
            runPlatformAiCommand(command)
        } else {
            runPlatformAiCommand(command)
        }
        onPlatformAiCommandConsumed(command)
    }

    fun openSavedThread(saved: SavedThread) {
        val repositoryForSavedThread = savedThreadRepository
        val fs = fileSystem
        if (repositoryForSavedThread == null || fs == null) {
            deepLinkError = "保存済みスレッドの保存先を利用できません"
            return
        }
        scope.launch {
            try {
                val metadata = repositoryForSavedThread
                    .loadThreadMetadata(saved.threadId, saved.boardId)
                    .getOrThrow()
                val rawUrl = "${metadata.boardUrl.trimEnd('/')}/res/${metadata.threadId}.htm"
                val parsed = canonicalizeThreadUrl(rawUrl)
                    ?: error("保存済みスレッドのURLを復元できませんでした")
                val board = boards.firstOrNull { it.canonicalUrl == parsed.canonicalBoardUrl }
                    ?: CompatBoard(
                        key = compatBoardKey(parsed.canonicalBoardUrl),
                        name = metadata.boardName.ifBlank { parsed.boardPath.substringAfterLast('/') },
                        canonicalUrl = parsed.canonicalBoardUrl,
                        originalUrl = metadata.boardUrl,
                        sortOrder = boards.size
                    ).also { store.upsertBoard(it) }
                val now = Clock.System.now().toEpochMilliseconds()
                val tab = CompatTab(
                    key = compatTabKey(parsed.canonicalUrl),
                    canonicalUrl = parsed.canonicalUrl,
                    originalUrl = parsed.canonicalUrl,
                    boardKey = board.key,
                    boardName = metadata.boardName.ifBlank { board.name },
                    threadNo = metadata.threadId,
                    title = metadata.title,
                    replyCount = metadata.posts.size,
                    insertedAtEpochMillis = now,
                    contentUpdatedAtEpochMillis = metadata.savedAt
                )
                val page = metadata.toThreadPage(
                    fileSystem = fs,
                    baseDirectory = MANUAL_SAVE_DIRECTORY
                )
                val snapshot = withContext(AppDispatchers.parsing) {
                    page.toCompatThreadSnapshot(tab.key, now)
                }
                store.openTab(
                    tab,
                    CompatHistoryEntry(
                        canonicalUrl = tab.canonicalUrl,
                        originalUrl = tab.originalUrl,
                        boardKey = tab.boardKey,
                        boardName = tab.boardName,
                        threadNo = tab.threadNo,
                        title = tab.title,
                        replyCount = tab.replyCount,
                        contentUpdatedAtEpochMillis = now
                    )
                )
                // The Android store validates snapshot ownership against the
                // tab table. Persist the tab first; saving the snapshot before
                // openTab() caused saved-thread launches to fail with
                // "Unknown compatibility tab" on real databases (#29).
                store.saveThreadSnapshot(snapshot)
                val durableTab = state.tabs.firstOrNull { it.key == tab.key }
                    ?.let { tab.copy(scrollAnchor = it.scrollAnchor) }
                    ?: tab
                state = state.copy(tabs = state.tabs.prependCompatTab(durableTab))
                dispatch(CompatibilityEvent.OpenThread(tab.key, CompatThreadOrigin.MAIN))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                deepLinkError = "保存済みスレッドを開けませんでした: ${error.message.orEmpty()}"
            }
        }
    }

    LaunchedEffect(
        initialThreadDeepLink,
        initialThreadDeepLinkPreapprovedBoardRegistration,
        boards,
        boardsLoaded
    ) {
        val raw = initialThreadDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!boardsLoaded) return@LaunchedEffect
        val parsed = canonicalizeThreadUrl(raw)
        if (parsed == null) {
            deepLinkError = "スレッドURLを解釈できませんでした"
            onThreadDeepLinkConsumed(raw)
            return@LaunchedEffect
        }
        val board = boards.firstOrNull { it.canonicalUrl == parsed.canonicalBoardUrl }
        if (board != null) {
            openCanonicalThread(parsed, board) {
                onThreadDeepLinkConsumed(raw)
            }
        } else if (initialThreadDeepLinkPreapprovedBoardRegistration) {
            val approvedBoard = CompatBoard(
                key = compatBoardKey(parsed.canonicalBoardUrl),
                name = parsed.boardPath.substringAfterLast('/'),
                canonicalUrl = parsed.canonicalBoardUrl,
                originalUrl = parsed.canonicalBoardUrl,
                sortOrder = boards.size
            )
            openCanonicalThread(parsed, approvedBoard) {
                onThreadDeepLinkConsumed(raw)
            }
        } else {
            pendingUnregisteredDeepLink = raw to parsed
        }
    }

    LaunchedEffect(initialBoardDeepLink, boards, boardsLoaded) {
        val raw = initialBoardDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!boardsLoaded) return@LaunchedEffect
        val canonical = canonicalizeBoardUrl(raw)
        val board = canonical?.let { target ->
            boards.firstOrNull { it.canonicalUrl.equals(target, ignoreCase = true) }
        }
        if (board == null) {
            deepLinkError = "板URLを解釈できませんでした"
        } else {
            dispatch(CompatibilityEvent.OpenCatalog(board.key))
        }
        onBoardDeepLinkConsumed(raw)
    }

    // Material3's ModalNavigationDrawer installs an anchored drag recognizer on
    // the whole content surface.  That makes a catalog swipe start from the
    // second grid column on some devices, and it also competes with the thread
    // pager.  Keep the close gesture provided by Material while closed-state
    // opening is owned by this narrow legacy-compatible edge recognizer.
    val drawerEdgeWidthPx = with(LocalDensity.current) {
        // In landscape a display cutout can move to the physical left edge.
        // Compose lays the safe content out after that inset, so a physical
        // edge touch arrives with the inset already included in
        // PointerInputChange.position.x. Include it in the hit region or the
        // drawer silently rejects the first gesture on notched devices (#36).
        COMPAT_DRAWER_EDGE_GESTURE_WIDTH_DP.dp.toPx() +
            maxOf(
                WindowInsets.statusBars.getLeft(this, LayoutDirection.Ltr),
                WindowInsets.displayCutout.getLeft(this, LayoutDirection.Ltr)
            )
    }
    val drawerEdgeWidthDp = with(LocalDensity.current) { drawerEdgeWidthPx.toDp() }
    val drawerSwipeTriggerPx = with(LocalDensity.current) {
        COMPAT_DRAWER_SWIPE_TRIGGER_DP.dp.toPx()
    }
    val drawerPreviewWidthPx = with(LocalDensity.current) { 320.dp.toPx() }
    val drawerTouchSlopPx = LocalViewConfiguration.current.touchSlop
    // Settings is a separate host-level surface. It must not expose the
    // compatibility drawer's edge or modal drag recognizers (#43).
    val drawerGesturesEnabled = state.host !is CompatHost.Settings
    val drawerOpeningGesturesEnabled = drawerGesturesEnabled &&
        (state.host !is CompatHost.ThreadWorkspace || threadDrawerOpeningAllowed)
    var drawerPreviewOffsetPx by remember { mutableFloatStateOf(0f) }
    var drawerPreviewAnimationJob by remember { mutableStateOf<Job?>(null) }
    val edgeDrawerPage = preferredDrawerPage()
    val latestDispatch = rememberUpdatedState<(CompatibilityEvent) -> Unit> { event ->
        dispatch(event)
    }
    val latestEdgeDrawerPage = rememberUpdatedState(edgeDrawerPage)
    fun settleDrawerPreview(targetPx: Float, openAfter: Boolean = false) {
        drawerPreviewAnimationJob?.cancel()
        val startPx = drawerPreviewOffsetPx
        drawerPreviewAnimationJob = scope.launch {
            Animatable(startPx).animateTo(
                targetValue = targetPx,
                animationSpec = tween(COMPAT_DRAWER_SWIPE_ANIMATION_MILLIS)
            ) { drawerPreviewOffsetPx = value }
            if (openAfter) {
                // Switch the real Material drawer only after the preview has
                // reached its final position. This keeps the visible motion
                // continuous instead of jumping from closed to fully open at
                // the trigger point.
                drawerState.snapTo(DrawerValue.Open)
                drawerPreviewOffsetPx = 0f
                latestDispatch.value(
                    CompatibilityEvent.OpenDrawer(latestEdgeDrawerPage.value)
                )
            }
            drawerPreviewAnimationJob = null
        }
    }
    val edgeDrawerModifier = Modifier.pointerInput(
        drawerState.isOpen,
        drawerState.targetValue,
        drawerEdgeWidthPx,
        drawerSwipeTriggerPx,
        drawerPreviewWidthPx,
        drawerOpeningGesturesEnabled
    ) {
        if (!drawerOpeningGesturesEnabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            if (!drawerState.isOpen && drawerState.targetValue == DrawerValue.Closed) {
                var totalDx = 0f
                var totalDy = 0f
                var draggingDrawer = false
                var horizontalEdgeGesture = false
                drawerPreviewAnimationJob?.cancel()
                drawerPreviewOffsetPx = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    totalDx += change.position.x - change.previousPosition.x
                    totalDy += change.position.y - change.previousPosition.y
                    // Claim a rightward edge gesture before the thread pager
                    // sees enough movement to change tabs or scroll the page.
                    // Do not consume the initial down event: a tap on the
                    // hamburger button must remain clickable.
                    if (!horizontalEdgeGesture &&
                        totalDx > 2f &&
                        totalDx >= kotlin.math.abs(totalDy) * COMPAT_PAGER_DIRECTION_RATIO &&
                        down.position.x <= drawerEdgeWidthPx
                    ) {
                        horizontalEdgeGesture = true
                    }
                    if (horizontalEdgeGesture) {
                        change.consume()
                    }
                    if (!draggingDrawer && horizontalEdgeGesture && compatDrawerSwipeShouldOpen(
                            startX = down.position.x,
                            totalDx = totalDx,
                            totalDy = totalDy,
                            edgeWidthPx = drawerEdgeWidthPx,
                            // Use touch slop as the preview start. The drawer
                            // must visibly follow the finger before the
                            // short-open threshold is reached (#25).
                            triggerPx = drawerTouchSlopPx
                        )
                    ) {
                        draggingDrawer = true
                    }
                    if (draggingDrawer) {
                        change.consume()
                        drawerPreviewOffsetPx = totalDx.coerceIn(0f, drawerPreviewWidthPx)
                    }
                }
                if (draggingDrawer) {
                    if (drawerPreviewOffsetPx >= drawerSwipeTriggerPx) {
                        settleDrawerPreview(drawerPreviewWidthPx, openAfter = true)
                    } else {
                        settleDrawerPreview(0f)
                    }
                }
            }
        }
    }

    LaunchedEffect(drawerOpeningGesturesEnabled) {
        if (!drawerOpeningGesturesEnabled) {
            drawerPreviewAnimationJob?.cancel()
            drawerPreviewOffsetPx = 0f
        }
    }
    LaunchedEffect(drawerGesturesEnabled) {
        if (!drawerGesturesEnabled) {
            drawerPreviewAnimationJob?.cancel()
            drawerPreviewOffsetPx = 0f
            closeCompatDrawerSurface()
            if (state.drawerPage != null) {
                dispatch(CompatibilityEvent.CloseDrawer)
            }
        }
    }

    @Composable
    fun renderCompatibilityDrawer(pageOverride: CompatDrawerPage? = null) {
        CompatNavigationDrawer(
            // Drawer content remains composed while closed. Use the remembered
            // page for both that hidden content and the edge preview so a
            // swipe never flashes the default tab page (#41).
            page = pageOverride ?: state.drawerPage ?: preferredDrawerPage(),
            tabs = distinctCompatTabs(state.tabs),
            histories = histories,
            externalWatcherSnapshot = externalWatcherSnapshot,
            // ModalNavigationDrawer keeps drawerContent composed while it is
            // closed. Only expose its undo snackbar while the drawer is
            // actually open; the root owns the closed-drawer snackbar.
            pendingClose = null,
            onPageSelected = { dispatch(CompatibilityEvent.OpenDrawer(it)) },
            onTabSelected = { tab ->
                val origin = when (val host = state.host) {
                    CompatHost.Main -> CompatThreadOrigin.MAIN
                    is CompatHost.Catalog -> CompatThreadOrigin.CATALOG
                    is CompatHost.ThreadWorkspace -> host.origin
                    else -> CompatThreadOrigin.MAIN
                }
                // Selection is a UI navigation action, not a database action.
                // Dispatch it synchronously so a slow/contended SQLite write
                // cannot leave the user staring at the drawer.
                closeDrawerForNavigation()
                dispatch(CompatibilityEvent.OpenThread(tab.key, origin))
            },
            onHistorySelected = ::openHistory,
            onTabFavoriteToggle = { tab ->
                launchStoreSafely("favorite tab update") {
                    store.updateTab(tab.copy(favorite = !tab.favorite))
                }
            },
            onTabsClosed = { keys ->
                dispatch(CompatibilityEvent.CloseTabs(keys, Clock.System.now().toEpochMilliseconds()))
            },
            onCheckDeadTabs = { protectFavorites ->
                checkCompatDeadTabCloseKeys(
                    tabs = store.tabs.first(),
                    protectFavorites = protectFavorites,
                    currentTabs = { store.tabs.first() },
                    probeGone = { url -> repository?.probeThreadGone(url) == true }
                )
            },
            onHistoryDeleted = { entry ->
                launchStoreSafely("history deletion", "履歴を削除できませんでした") {
                    val modernStore = stateStore
                    val modernEntry = modernStore?.history?.first()
                        ?.firstOrNull { candidate ->
                            candidate.toCompatHistoryEntry()?.canonicalUrl == entry.canonicalUrl
                        }
                        ?: entry.toModernThreadHistoryEntry()
                    if (modernStore != null && modernEntry != null) {
                        dismissHistoryEntry(
                            stateStore = modernStore,
                            autoSavedThreadRepository = historyAutoSavedThreadRepository,
                            importedHistoryRepository = importedHistoryRepository,
                            compatibilityStore = store,
                            entry = modernEntry,
                            onAutoSavedThreadDeleteFailure = { failure ->
                                Logger.e("CompatibilityApp", "History payload deletion failed", failure)
                            }
                        )
                    } else {
                        store.deleteHistory(entry.canonicalUrl)
                    }
                }
            },
            onHistoryCleared = {
                launchStoreSafely("history clear", "履歴をすべて削除できませんでした") {
                    val modernStore = stateStore
                    if (modernStore != null) {
                        clearHistory(
                            stateStore = modernStore,
                            autoSavedThreadRepository = historyAutoSavedThreadRepository,
                            importedHistoryRepository = importedHistoryRepository,
                            compatibilityStore = store,
                            onSkippedThreadsCleared = {},
                            onAutoSavedThreadDeleteFailure = { failure ->
                                Logger.e("CompatibilityApp", "History payload purge failed", failure)
                            }
                        )
                    } else {
                        store.clearHistory()
                    }
                }
            },
            onExternalWatcherSelected = ::openExternalWatcherEntry,
            onExternalWatcherDelete = { entry ->
                launchStoreSafely("watcher result deletion", "巡回結果を削除できませんでした") {
                    externalWatcher.delete(entry.key).getOrThrow()
                    refreshExternalWatcher()
                }
            },
            onExternalWatcherDeleteAll = {
                launchStoreSafely("watcher result clear", "巡回結果をすべて削除できませんでした") {
                    externalWatcher.deleteAll().getOrThrow()
                    refreshExternalWatcher()
                }
            },
            onOpenExternalWatcherManager = { watcherManagerOpen = true },
            onRefreshExternalWatcher = ::refreshExternalWatcher,
            onRefreshAllTabs = {
                val activeRepository = repository
                if (activeRepository != null) {
                    launchStoreSafely("refresh all tabs") {
                        refreshCompatTabsInBackground(
                            store = store,
                            repository = activeRepository,
                            nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            maxTabs = Int.MAX_VALUE,
                            checkUpdates = true,
                            checkExistence = true,
                            existenceStaleMillis = 0L,
                            checkWatchWords = false
                        )
                    }
                }
            },
            onOpenSettings = {
                val origin = state.host
                closeDrawerForNavigation()
                scope.launch {
                    dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(origin = origin)))
                }
            },
            onClose = { dispatch(CompatibilityEvent.CloseDrawer) },
            onUndoClose = { dispatch(CompatibilityEvent.UndoClose) }
        )
    }

    // Do not apply the default compatibility palette while the persisted
    // palette is still loading. In particular, a saved black/futaba theme
    // must never receive a teal/gray intermediate frame on Android 11 (#53).
    if (!preferencesLoaded) return

    CompatibilityProfileTheme(
        theme = preferences.compatPreferenceValue("design", "designTheme", "カラーテーマ"),
        textColor = preferences.compatPreferenceValue("design", "designTextColor", "文字色"),
        navigationBarBackground = preferences.compatPreferenceValue(
            "design",
            "designNavigationBar",
            "ナビゲーションバー背景色"
        ) == "ON",
        customFontFamily = customFontFamily
    ) {
    if (!boardsLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalCompatibilityPalette.current.background)
                .testTag("compat-startup-loading"),
            contentAlignment = Alignment.Center
        ) {
            CompatLoadingIndicator(
                style = toolbarLoadingStyle,
                modifier = Modifier,
                size = 50.dp
            )
        }
        return@CompatibilityProfileTheme
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(edgeDrawerModifier)
    ) {
    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize(),
        drawerState = drawerState,
        // Keep Material's close gesture and scrim dismissal while the drawer
        // is open. When closed, its whole-surface opener is disabled so only
        // the edge recognizer above can start the drawer.
        gesturesEnabled = drawerGesturesEnabled &&
            (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open),
        // A barely visible color keeps Material's dismissible Scrim in the
        // hit-test tree; fully transparent is treated as absent on Android.
        scrimColor = Color.Black.copy(alpha = 0.001f),
        drawerContent = { renderCompatibilityDrawer() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
        when (val host = state.host) {
            CompatHost.Main -> CompatMainScreen(
                boards = boards,
                isDrawerOpen = drawerState.currentValue == DrawerValue.Open,
                onOpenDrawer = ::openPreferredDrawer,
                onCloseDrawer = { dispatch(CompatibilityEvent.CloseDrawer) },
                onOpenSettings = {
                    closeDrawerForNavigation()
                    dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(origin = CompatHost.Main)))
                },
                onOpenHelp = { dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(CompatHost.Main))) },
                onOpenSavedThreads = {
                    dispatch(CompatibilityEvent.OpenHost(CompatHost.SavedThreads(CompatHost.Main)))
                },
                onUpdateBoards = { boardUpdateDialogOpen = true },
                onBoardSelected = { board -> dispatch(CompatibilityEvent.OpenCatalog(board.key)) },
                onBoardUpsert = { board -> launchStoreSafely("board save") { store.upsertBoard(board) } },
                onBoardDelete = { board -> launchStoreSafely("board deletion") { store.deleteBoard(board.key) } },
                onBoardsReordered = { ordered ->
                    launchStoreSafely("board reorder") { store.reorderBoards(ordered.map { it.key }) }
                }
            )
            is CompatHost.Catalog -> {
                val board = boards.firstOrNull { it.key == host.boardKey }
                if (board == null) {
                    LaunchedEffect(host.boardKey) { dispatch(CompatibilityEvent.OpenHost(CompatHost.Main)) }
                } else if (toolbarItemsBySurface.isEmpty()) {
                    // Do not compose a default toolbar and then replace it
                    // after the database read. That one-frame fallback is the
                    // icon flash visible when a custom toolbar is configured.
                    CompatLoadingIndicator(
                        style = toolbarLoadingStyle,
                        modifier = Modifier.fillMaxSize().testTag("compat-toolbar-loading"),
                        size = 40.dp
                    )
                } else {
                    fun addCatalogItemAsTab(item: CatalogItem, navigate: Boolean) {
                        val parsed = canonicalizeThreadUrl(item.threadUrl) ?: return
                        val now = Clock.System.now().toEpochMilliseconds()
                        val tab = CompatTab(
                            key = compatTabKey(parsed.canonicalUrl),
                            canonicalUrl = parsed.canonicalUrl,
                            originalUrl = item.threadUrl,
                            boardKey = board.key,
                            boardName = board.name,
                            threadNo = parsed.threadNo,
                            title = item.title.orEmpty().ifBlank { "No.${parsed.threadNo}" },
                            thumbnailUrl = item.thumbnailUrl,
                            replyCount = item.replyCount,
                            insertedAtEpochMillis = now,
                            contentUpdatedAtEpochMillis = now,
                            refreshOnActivation = preferences.compatPreferenceValue(
                                "catalog", "catalogOpenWithReload", "開く時に再読み込み"
                            ) == "ON"
                        )
                        val entry = CompatHistoryEntry(
                            canonicalUrl = tab.canonicalUrl,
                            originalUrl = tab.originalUrl,
                            boardKey = tab.boardKey,
                            boardName = tab.boardName,
                            threadNo = tab.threadNo,
                            title = tab.title,
                            thumbnailUrl = tab.thumbnailUrl,
                            replyCount = tab.replyCount,
                            contentUpdatedAtEpochMillis = now
                        )
                        val previousTabs = state.tabs
                        val durableTab = mergeCompatCatalogTab(
                            existing = previousTabs.firstOrNull { it.key == tab.key },
                            candidate = tab,
                            markCatalogCountRead = navigate
                        )
                        state = state.copy(tabs = previousTabs.prependCompatTab(durableTab))
                        scope.launch {
                            try {
                                store.openTab(durableTab, entry.takeIf { navigate })
                                if (navigate) {
                                    dispatch(CompatibilityEvent.OpenThread(tab.key, CompatThreadOrigin.CATALOG))
                                }
                                // Some img/dat catalog responses expose the
                                // reply count where the subject should be.  Do
                                // not make opening a thread wait for a second
                                // network request; repair the tab and history in
                                // place once the bounded head lookup completes.
                                val titleRepository = repository
                                if (
                                    titleRepository != null &&
                                    !isCompatFixtureBoard(board) &&
                                    shouldResolveCatalogItemTitleFromHead(item.title, item.replyCount)
                                ) {
                                    val resolvedTitle = withTimeoutOrNull(1_800L) {
                                        runSuspendCatchingPreservingCancellation {
                                            titleRepository.resolveCatalogDisplayTitle(
                                                board = board.originalUrl,
                                                item = item,
                                                allowFallbackHeadScan = true
                                            )
                                        }.getOrNull()
                                    }?.takeIf(String::isNotBlank)
                                    if (resolvedTitle != null && resolvedTitle != tab.title) {
                                        val repairedTab = tab.copy(title = resolvedTitle)
                                        state = state.copy(
                                            tabs = state.tabs.map { current ->
                                                if (current.key == repairedTab.key) repairedTab else current
                                            }
                                        )
                                        store.updateTab(repairedTab)
                                        if (navigate) store.upsertHistory(entry.copy(title = resolvedTitle))
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Throwable) {
                                restoreOptimisticTab(tab.key, previousTabs)
                                deepLinkError = "スレッドを開けませんでした: ${failure.message.orEmpty()}"
                                Logger.e("CompatibilityApp", "Failed to open catalog thread", failure)
                            }
                        }
                    }
                    catalogStateHolder.SaveableStateProvider("catalog:${board.key}") {
                    CompositionLocalProvider(LocalFutachaImageLoader provides catalogImageLoader) {
                    CompatCatalogScreen(
                        board = board,
                        boards = boards,
                        store = store,
                        isDrawerOpen = drawerState.currentValue == DrawerValue.Open,
                        platformAiCommand = pendingCatalogAiCommand,
                        onPlatformAiCommandConsumed = { command ->
                            if (pendingCatalogAiCommand === command) pendingCatalogAiCommand = null
                        },
                        toolbarRefreshToken = toolbarRefreshToken,
                        initialToolbarItems = toolbarItemsBySurface[CompatToolbarSurface.CATALOG],
                        preferences = preferences,
                        ngRules = ngRules,
                        tabs = distinctCompatTabs(state.tabs),
                        activeTabKey = state.activeTabKey,
                        repository = repository,
                        httpClient = httpClient,
                        archiveBaseUrl = preferences[COMPAT_CACHE_BASE_URL_KEY],
                        archiveSearchHistory = parseCompatArchiveSearchHistory(
                            preferences[COMPAT_ARCHIVE_SEARCH_HISTORY_KEY]
                        ),
                        archiveSearchNoticeHidden =
                            preferences[COMPAT_ARCHIVE_SEARCH_NOTICE_HIDDEN_KEY] == "ON",
                        localHistory = histories,
                        freshSnapshotRevision = freshCatalogRevisions[board.key],
                        onFreshSnapshotCommitted = { boardKey, revision ->
                            freshCatalogRevisions = freshCatalogRevisions + (boardKey to revision)
                        },
                        selectorOpen = catalogSelectorOpen,
                        onToggleSelector = {
                            val next = !catalogSelectorOpen
                            catalogSelectorOpen = next
                            state = reduceCompatibilityWorkspace(
                                state,
                                CompatibilityEvent.SetSelector(next, state.selectorPresentation)
                            ).state
                        },
                        onBack = { dispatch(CompatibilityEvent.Back) },
                        onBoardSelected = { selected ->
                            dispatch(CompatibilityEvent.OpenCatalog(selected.key))
                        },
                        onOpenDrawer = ::openPreferredDrawer,
                        onCloseDrawer = { dispatch(CompatibilityEvent.CloseDrawer) },
                        onOpenWatcher = { dispatch(CompatibilityEvent.OpenDrawer(CompatDrawerPage.WATCHER)) },
                        onCheckUpdates = {
                            repository?.let { activeRepository ->
                                launchStoreSafely("force thread update check") {
                                    refreshCompatTabsInBackground(
                                        store = store,
                                        repository = activeRepository,
                                        nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
                                        maxTabs = Int.MAX_VALUE,
                                        checkUpdates = true,
                                        checkExistence = true,
                                        existenceStaleMillis = 0L,
                                        checkWatchWords = false
                                    )
                                }
                            }
                        },
                        onOpenSettings = {
                            closeDrawerForNavigation()
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(origin = host)))
                        },
                        onOpenDisplayOptions = {
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.Settings(path = "catalog", origin = host)
                                )
                            )
                        },
                        onOpenHelp = { dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(host))) },
                        onToolbarEdit = {
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.ToolbarEditor(CompatToolbarSurface.CATALOG, host)
                                )
                            )
                        },
                        onOpenBuild = { dispatch(CompatibilityEvent.OpenHost(CompatHost.PostBuild(board.key))) },
                        onOpenThread = { item -> addCatalogItemAsTab(item, navigate = true) },
                        onAddTab = { item -> addCatalogItemAsTab(item, navigate = false) },
                        onSelectTab = { tab ->
                            dispatch(CompatibilityEvent.OpenThread(tab.key, CompatThreadOrigin.CATALOG))
                        },
                        onCloseTab = { tab ->
                            dispatch(CompatibilityEvent.CloseTab(tab.key, Clock.System.now().toEpochMilliseconds()))
                        },
                        canUndoClose = state.pendingClose != null,
                        onUndoClose = { dispatch(CompatibilityEvent.UndoClose) }
                    )
                    }
                    }
                }
            }
            is CompatHost.ThreadWorkspace -> {
                val tab = state.tabs.firstOrNull { it.key == state.activeTabKey }
                LaunchedEffect(tab?.key) {
                    tab?.let { visibleTab ->
                        val visit = visibleTab.toVisitedHistoryEntry(Clock.System.now().toEpochMilliseconds())
                        persistStoreSafely("history visit") {
                            store.recordHistoryVisit(visit)
                        }
                    }
                }
                if (tab == null) {
                    LaunchedEffect(state.activeTabKey) { dispatch(CompatibilityEvent.OpenHost(CompatHost.Main)) }
                } else if (toolbarItemsBySurface.isEmpty()) {
                    CompatLoadingIndicator(
                        style = toolbarLoadingStyle,
                        modifier = Modifier.fillMaxSize().testTag("compat-toolbar-loading"),
                        size = 40.dp
                    )
                } else {
                    val threadBoard = boards.firstOrNull { it.key == tab.boardKey }
                    val threadRepository = if (threadBoard != null && isCompatFixtureBoard(threadBoard)) {
                        remember(threadBoard.key) { FakeBoardRepository() }
                    } else {
                        repository
                    }
                    threadStateHolder.SaveableStateProvider(tab.key) {
                        CompatThreadScreen(
                            tab = tab,
                            tabs = distinctCompatTabs(state.tabs),
                            isDrawerOpen = drawerState.currentValue == DrawerValue.Open,
                            onDrawerGestureAvailabilityChanged = { threadDrawerOpeningAllowed = it },
                            repository = threadRepository,
                            httpClient = httpClient,
                            cookieRepository = cookieRepository,
                            toolbarRefreshToken = toolbarRefreshToken,
                            initialToolbarItems = toolbarItemsBySurface[CompatToolbarSurface.THREAD],
                            threadRefreshToken = threadRefreshToken,
                            archiveBaseUrl = preferences[COMPAT_CACHE_BASE_URL_KEY],
                            archiveSearchHistory = parseCompatArchiveSearchHistory(
                                preferences[COMPAT_ARCHIVE_SEARCH_HISTORY_KEY]
                            ),
                            archiveSearchNoticeHidden =
                                preferences[COMPAT_ARCHIVE_SEARCH_NOTICE_HIDDEN_KEY] == "ON",
                            localHistory = histories,
                            fileSystem = fileSystem,
                            longRunningScope = scope,
                            store = store,
                            preferences = preferences,
                            ngRules = ngRules,
                            selectorOpen = threadSelectorOpen,
                            selectorPresentation = state.selectorPresentation,
                            platformAiCommand = pendingThreadAiCommand,
                            onPlatformAiCommandConsumed = { consumed ->
                                if (pendingThreadAiCommand === consumed) pendingThreadAiCommand = null
                            },
                            scrollToBottomRequest = scrollToBottomRequest?.takeIf { it.first == tab.key }?.second,
                            onToggleSelector = {
                                val next = !threadSelectorOpen
                                threadSelectorOpen = next
                                state = reduceCompatibilityWorkspace(
                                    state,
                                    CompatibilityEvent.SetSelector(next, state.selectorPresentation)
                                ).state
                            },
                            onSelectTab = { selected ->
                                state = state.copy(
                                    tabs = state.tabs.map { current ->
                                        if (current.key == selected.key) selected else current
                                    }
                                )
                                dispatch(CompatibilityEvent.SelectTab(selected.key))
                            },
                            onPersistScrollAnchor = { tabKey, anchor ->
                                latestThreadScrollAnchors[tabKey] = anchor
                                launchStoreSafely("scroll position persistence") {
                                    store.updateScrollAnchor(tabKey, anchor)
                                }
                            },
                            onScrollAnchorObserved = { tabKey, anchor ->
                                latestThreadScrollAnchors[tabKey] = anchor
                            },
                            onCloseTab = { selected ->
                                dispatch(CompatibilityEvent.CloseTab(selected.key, Clock.System.now().toEpochMilliseconds()))
                            },
                            onOpenDrawer = ::openPreferredDrawer,
                            onCloseDrawer = { dispatch(CompatibilityEvent.CloseDrawer) },
                            onCheckUpdates = {
                                threadRepository?.let { activeRepository ->
                                    launchStoreSafely("force thread update check") {
                                        refreshCompatTabsInBackground(
                                            store = store,
                                            repository = activeRepository,
                                            nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
                                            maxTabs = Int.MAX_VALUE,
                                            checkUpdates = true,
                                            checkExistence = true,
                                            existenceStaleMillis = 0L,
                                            checkWatchWords = false
                                        )
                                    }
                                }
                            },
                            onOpenSettings = {
                                closeDrawerForNavigation()
                                dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(origin = host)))
                            },
                            onOpenDisplayOptions = {
                                dispatch(
                                    CompatibilityEvent.OpenHost(
                                        CompatHost.Settings(path = "thread", origin = host)
                                    )
                                )
                            },
                            onOpenHelp = { dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(host))) },
                            onToolbarEdit = {
                                dispatch(
                                    CompatibilityEvent.OpenHost(
                                        CompatHost.ToolbarEditor(CompatToolbarSurface.THREAD, host)
                                    )
                                )
                            },
                        onOpenPost = { dispatch(CompatibilityEvent.OpenHost(CompatHost.Post(tab.key))) },
                        onOpenPostWithText = { text, append ->
                            launchStoreSafely("reply draft persistence", "返信画面を開けませんでした") {
                                val old = store.loadDraft(tab.key)
                                val merged = if (append && old?.comment?.isNotBlank() == true) {
                                    old.comment + "\n" + text
                                } else {
                                    text
                                }
                                store.saveDraft(
                                    old?.copy(comment = merged, updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds())
                                        ?: CompatReplyDraft(
                                            tabKey = tab.key,
                                            comment = merged,
                                            updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                                        )
                                )
                                dispatch(CompatibilityEvent.OpenHost(CompatHost.Post(tab.key)))
                            }
                        },
                        onOpenGallery = { dispatch(CompatibilityEvent.OpenHost(CompatHost.Gallery(tab.key))) },
                            onOpenViewer = { index, postNo ->
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.Viewer(
                                        tabKey = tab.key,
                                        index = index,
                                        caller = CompatViewerCaller.THREAD,
                                        postNo = postNo
                                    )
                                )
                            )
                        },
                        onOpenSingleViewer = { mediaUrl, postNo, sourcePosition ->
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.Viewer(
                                        tabKey = tab.key,
                                        index = 0,
                                        caller = CompatViewerCaller.THREAD,
                                        postNo = postNo,
                                        directMediaUrl = mediaUrl,
                                        directSourcePosition = sourcePosition
                                    )
                                )
                            )
                        },
                        onOpenInlineUrl = { rawUrl ->
                            when (
                                val route = com.valoser.futacha.shared.compat.resolveCompatInlineUrlRoute(
                                    rawUrl,
                                    boards.associate { it.canonicalUrl to it.key }
                                )
                            ) {
                                com.valoser.futacha.shared.compat.CompatInlineUrlRoute.External -> false
                                is com.valoser.futacha.shared.compat.CompatInlineUrlRoute.UnregisteredThread -> {
                                    deepLinkError = "未登録の板です"
                                    true
                                }
                                is com.valoser.futacha.shared.compat.CompatInlineUrlRoute.RegisteredThread -> {
                                    boards.firstOrNull { it.key == route.boardKey }?.let { board ->
                                        state.tabs.firstOrNull {
                                            it.canonicalUrl == route.thread.canonicalUrl
                                        }?.let { existing ->
                                            dispatch(CompatibilityEvent.OpenThread(existing.key, host.origin))
                                        } ?: openCanonicalThread(route.thread, board, host.origin)
                                    } ?: run {
                                        deepLinkError = "未登録の板です"
                                    }
                                    true
                                }
                            }
                        },
                        canUndoClose = state.pendingClose != null,
                        onUndoClose = { dispatch(CompatibilityEvent.UndoClose) },
                        onArchiveReportEnqueued = onArchiveReportEnqueued,
                        onOpenArchiveItem = { item ->
                            val sourceUrl = buildCompatArchiveSourceThreadUrl(item)
                            val parsed = sourceUrl?.let(::canonicalizeThreadUrl)
                            if (parsed == null) {
                                return@CompatThreadScreen
                            }
                            val matchingBoard = boards.firstOrNull { board ->
                                extractArchiveSearchScope(board.originalUrl)?.let { archiveScope ->
                                    archiveScope.server == item.server && archiveScope.board == item.board
                                } == true
                            } ?: boards.firstOrNull { it.key == tab.boardKey }
                            val board = matchingBoard ?: return@CompatThreadScreen
                            val now = Clock.System.now().toEpochMilliseconds()
                            val openedTab = CompatTab(
                                key = compatTabKey(parsed.canonicalUrl),
                                canonicalUrl = parsed.canonicalUrl,
                                originalUrl = sourceUrl,
                                boardKey = board.key,
                                boardName = board.name,
                                threadNo = parsed.threadNo,
                                title = item.title.orEmpty().ifBlank { "No.${parsed.threadNo}" },
                                thumbnailUrl = item.thumbUrl,
                                replyCount = item.replyCount,
                                insertedAtEpochMillis = now,
                                contentUpdatedAtEpochMillis = now
                            )
                            val history = CompatHistoryEntry(
                                canonicalUrl = openedTab.canonicalUrl,
                                originalUrl = openedTab.originalUrl,
                                boardKey = openedTab.boardKey,
                                boardName = openedTab.boardName,
                                threadNo = openedTab.threadNo,
                                title = openedTab.title,
                                thumbnailUrl = openedTab.thumbnailUrl,
                                replyCount = openedTab.replyCount,
                                contentUpdatedAtEpochMillis = now
                            )
                            val previousTabs = state.tabs
                            val durableTab = previousTabs.firstOrNull { it.key == openedTab.key }
                                ?.let { openedTab.copy(scrollAnchor = it.scrollAnchor) }
                                ?: openedTab
                            state = state.copy(tabs = previousTabs.prependCompatTab(durableTab))
                            scope.launch {
                                try {
                                    store.openTab(openedTab, history)
                                    dispatch(CompatibilityEvent.OpenThread(openedTab.key, CompatThreadOrigin.CATALOG))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Throwable) {
                                    restoreOptimisticTab(openedTab.key, previousTabs)
                                    deepLinkError = "過去ログのスレッドを開けませんでした: ${failure.message.orEmpty()}"
                                    Logger.e("CompatibilityApp", "Failed to open archive thread", failure)
                                }
                            }
                        },
                            onBack = {
                                // Android 11 may deliver Back after the edge
                                // recognizer has already moved the preview but
                                // before the exclusion rect wins. Treat that
                                // Back as a cancelled drawer drag; navigating
                                // to Catalog here is the original #36 failure.
                                if (drawerState.isClosed && drawerPreviewOffsetPx > 0f) {
                                    settleDrawerPreview(0f)
                                } else {
                                    dispatch(CompatibilityEvent.Back)
                                }
                            }
                        )
                    }
                }
            }
            is CompatHost.Post -> {
                val tab = state.tabs.firstOrNull { it.key == host.tabKey }
                val board = tab?.let { active -> boards.firstOrNull { it.key == active.boardKey } }
                if (tab == null || board == null) {
                    LaunchedEffect(host.tabKey) { dispatch(CompatibilityEvent.Back) }
                } else {
                        CompatPostScreen(
                            tab = tab,
                            board = board,
                            repository = if (isCompatFixtureBoard(board)) {
                                remember(board.key) { FakeBoardRepository() }
                            } else repository,
                            httpClient = httpClient,
                            store = store,
                        toolbarRefreshToken = toolbarRefreshToken,
                        onPostSent = { threadRefreshToken += 1L },
                        preferences = preferences,
                        appVersion = appVersion,
                        fileSystem = fileSystem,
                        onToolbarEdit = {
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.ToolbarEditor(CompatToolbarSurface.POST, host)
                                )
                            )
                        },
                        onOpenDrawing = {
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.PostDrawing(host)))
                        },
                        onBack = { dispatch(CompatibilityEvent.Back) }
                    )
                }
            }
            is CompatHost.PostBuild -> {
                val board = boards.firstOrNull { it.key == host.boardKey }
                if (board == null) {
                    LaunchedEffect(host.boardKey) { dispatch(CompatibilityEvent.Back) }
                } else {
                    val placeholder = remember(board.key) {
                        CompatTab(
                            key = "build:${board.key}",
                            canonicalUrl = "${board.canonicalUrl}build",
                            originalUrl = board.originalUrl,
                            boardKey = board.key,
                            boardName = board.name,
                            threadNo = "",
                            title = "スレ立て",
                            insertedAtEpochMillis = 0L,
                            contentUpdatedAtEpochMillis = 0L
                        )
                    }
                    CompatPostScreen(
                        tab = placeholder,
                        board = board,
                        repository = if (isCompatFixtureBoard(board)) {
                            remember(board.key) { FakeBoardRepository() }
                        } else repository,
                        httpClient = httpClient,
                        store = store,
                        toolbarRefreshToken = toolbarRefreshToken,
                        preferences = preferences,
                        appVersion = appVersion,
                        fileSystem = fileSystem,
                        isBuild = true,
                        onToolbarEdit = {
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.ToolbarEditor(CompatToolbarSurface.POST, host)
                                )
                            )
                        },
                        onBuildCreated = { rawThreadId ->
                            val threadId = rawThreadId?.let { compatAppPostNumberRegex.find(it)?.value }
                            if (threadId == null) {
                                dispatch(CompatibilityEvent.OpenHost(CompatHost.Catalog(board.key)))
                            } else {
                                val now = Clock.System.now().toEpochMilliseconds()
                                val threadUrl = "${board.canonicalUrl.trimEnd('/')}/res/$threadId.htm"
                                val createdTab = CompatTab(
                                    key = compatTabKey(threadUrl),
                                    canonicalUrl = threadUrl,
                                    originalUrl = threadUrl,
                                    boardKey = board.key,
                                    boardName = board.name,
                                    threadNo = threadId,
                                    title = "No.$threadId",
                                    insertedAtEpochMillis = now,
                                    contentUpdatedAtEpochMillis = now
                                )
                                val entry = CompatHistoryEntry(
                                    canonicalUrl = threadUrl,
                                    originalUrl = threadUrl,
                                    boardKey = board.key,
                                    boardName = board.name,
                                    threadNo = threadId,
                                    title = createdTab.title,
                                    contentUpdatedAtEpochMillis = now
                                )
                                val previousTabs = state.tabs
                                val durableTab = previousTabs.firstOrNull { it.key == createdTab.key }
                                    ?.let { createdTab.copy(scrollAnchor = it.scrollAnchor) }
                                    ?: createdTab
                                state = state.copy(tabs = previousTabs.prependCompatTab(durableTab))
                                scope.launch {
                                    try {
                                        store.openTab(createdTab, entry)
                                        dispatch(CompatibilityEvent.OpenThread(createdTab.key, CompatThreadOrigin.CATALOG))
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Throwable) {
                                        restoreOptimisticTab(createdTab.key, previousTabs)
                                        deepLinkError = "作成したスレッドを開けませんでした: ${failure.message.orEmpty()}"
                                        Logger.e("CompatibilityApp", "Failed to open created thread", failure)
                                    }
                                }
                            }
                        },
                        onOpenDrawing = {
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.PostDrawing(host)))
                        },
                        onOpenHelp = {
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(host)))
                        },
                        onBack = { dispatch(CompatibilityEvent.Back) }
                    )
                }
            }
            is CompatHost.PostDrawing -> CompatPostDrawingScreen(
                onSaved = { drawing ->
                    val localFileSystem = fileSystem
                    if (localFileSystem != null) {
                        scope.launch {
                            val now = Clock.System.now().toEpochMilliseconds()
                            val drawingLocation = parseCompatSaveLocation(
                                preferences.compatPreferenceValue(
                                    "storage",
                                    "dummyDrawingDir",
                                    "手書きファイルの保存先"
                                )
                            )
                            // Keep the configured external PNG copy independent
                            // from the private draft attachment.  The latter is
                            // still saved even when a SAF/path write fails.
                            persistCompatDrawingCopy(
                                fileSystem = localFileSystem,
                                location = drawingLocation,
                                drawing = drawing,
                                timestampEpochMillis = now
                            )?.onFailure { failure ->
                                Logger.w(
                                    "CompatibilityApp",
                                    "手書き画像の指定保存先へのコピーに失敗しました: ${failure.message.orEmpty()}"
                                )
                            }
                            when (val origin = host.origin) {
                                is CompatHost.Post -> {
                                    val old = store.loadDraft(origin.tabKey)
                                    persistCompatPostAttachment(localFileSystem, origin.tabKey, drawing)
                                        .onSuccess { locator ->
                                            store.saveDraft(
                                                old?.copy(
                                                    attachmentUri = locator,
                                                    updatedAtEpochMillis = now
                                                ) ?: CompatReplyDraft(
                                                    tabKey = origin.tabKey,
                                                    attachmentUri = locator,
                                                    updatedAtEpochMillis = now
                                                )
                                            )
                                                old?.attachmentUri?.takeIf { it != locator }?.let { previous ->
                                                deleteCompatPostAttachment(localFileSystem, previous, deleteContainer = true)
                                            }
                                            dispatch(CompatibilityEvent.OpenHost(origin))
                                        }
                                }
                                is CompatHost.PostBuild -> {
                                    val old = store.loadBuildDraft(origin.boardKey)
                                    persistCompatPostAttachment(localFileSystem, "build:${origin.boardKey}", drawing)
                                        .onSuccess { locator ->
                                            store.saveBuildDraft(
                                                old?.copy(
                                                    attachmentUri = locator,
                                                    updatedAtEpochMillis = now
                                                ) ?: com.valoser.futacha.shared.compat.CompatBuildDraft(
                                                    boardKey = origin.boardKey,
                                                    attachmentUri = locator,
                                                    updatedAtEpochMillis = now
                                                )
                                            )
                                            old?.attachmentUri?.takeIf { it != locator }?.let { previous ->
                                                deleteCompatPostAttachment(localFileSystem, previous, deleteContainer = true)
                                            }
                                            dispatch(CompatibilityEvent.OpenHost(origin))
                                        }
                                }
                                else -> dispatch(CompatibilityEvent.OpenHost(origin))
                            }
                        }
                    }
                },
                onBack = { dispatch(CompatibilityEvent.OpenHost(host.origin)) }
            )
            is CompatHost.Gallery -> {
                val galleryTab = state.tabs.firstOrNull { it.key == host.tabKey }
                if (galleryTab == null) {
                    LaunchedEffect(host.tabKey) { dispatch(CompatibilityEvent.Back) }
                } else {
                    CompatGalleryScreen(
                        tab = galleryTab,
                        initialIndex = host.index,
                        initialPostNo = host.postNo,
                        store = store,
                        preferences = preferences,
                        ngRules = ngRules,
                        httpClient = httpClient,
                        apngMarkerCache = apngMarkerCache,
                        fileSystem = fileSystem,
                        cookieRepository = cookieRepository,
                        onOpenViewer = { index, postNo ->
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.Viewer(
                                        tabKey = host.tabKey,
                                        index = index,
                                        caller = CompatViewerCaller.GALLERY,
                                        postNo = postNo
                                    )
                                )
                            )
                        },
                        onOpenSettings = {
                            closeDrawerForNavigation()
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(path = "viewer", origin = host)))
                        },
                        onOpenCommonSettings = {
                            closeDrawerForNavigation()
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(origin = host)))
                        },
                        onOpenHelp = {
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(host)))
                        },
                        onBack = { dispatch(CompatibilityEvent.Back) }
                    )
                }
            }
            is CompatHost.Viewer -> {
                val viewerTab = state.tabs.firstOrNull { it.key == host.tabKey }
                if (viewerTab == null) {
                    LaunchedEffect(host.tabKey) { dispatch(CompatibilityEvent.Back) }
                } else {
                    CompatViewerScreen(
                        tab = viewerTab,
                        initialIndex = host.index,
                        initialPostNo = host.postNo,
                        directMediaUrl = host.directMediaUrl,
                        directSourcePosition = host.directSourcePosition,
                        store = store,
                        toolbarRefreshToken = toolbarRefreshToken,
                        preferences = preferences,
                        ngRules = ngRules,
                        httpClient = httpClient,
                        fileSystem = fileSystem,
                        cookieRepository = cookieRepository,
                        onToolbarEdit = {
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.ToolbarEditor(CompatToolbarSurface.VIEWER, host)
                                )
                            )
                        },
                        onShowSourcePost = { anchor ->
                            latestThreadScrollAnchors[host.tabKey] = anchor
                            state = state.copy(
                                tabs = state.tabs.map { tab ->
                                    if (tab.key == host.tabKey) tab.copy(scrollAnchor = anchor) else tab
                                }
                            )
                            launchStoreSafely("viewer source-post persistence") {
                                store.updateScrollAnchor(host.tabKey, anchor)
                            }
                            dispatch(
                                CompatibilityEvent.OpenThread(
                                    host.tabKey,
                                    CompatThreadOrigin.CATALOG
                                )
                            )
                        },
                        onOpenGallery = { index, postNo ->
                            dispatch(
                                CompatibilityEvent.OpenHost(
                                    CompatHost.Gallery(host.tabKey, index, postNo)
                                )
                            )
                        },
                        onOpenSettings = {
                            closeDrawerForNavigation()
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(path = "viewer", origin = host)))
                        },
                        onOpenCommonSettings = {
                            closeDrawerForNavigation()
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.Settings(origin = host)))
                        },
                        onOpenHelp = {
                            dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(host)))
                        },
                        onBack = { dispatch(CompatibilityEvent.Back) }
                    )
                }
            }
            is CompatHost.SavedThreads -> {
                val savedRepository = savedThreadRepository
                if (savedRepository == null) {
                    LaunchedEffect(Unit) {
                        deepLinkError = "保存済みスレッドを利用できません"
                        dispatch(CompatibilityEvent.Back)
                    }
                } else {
                    com.valoser.futacha.shared.ui.board.SavedThreadsScreen(
                        repository = savedRepository,
                        recoverUnindexedThreads = true,
                        onThreadClick = ::openSavedThread,
                        onBack = { dispatch(CompatibilityEvent.Back) }
                    )
                }
            }
            is CompatHost.ChangeLog -> CompatChangeLogScreen(
                appVersion = appVersion,
                store = store,
                onOpenHelp = {
                    dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(host)))
                },
                onBack = { dispatch(CompatibilityEvent.Back) }
            )
            is CompatHost.License -> CompatLicenseScreen(
                onBack = { dispatch(CompatibilityEvent.Back) }
            )
            is CompatHost.Settings -> CompatSettingsScreen(
                path = host.path,
                store = store,
                preferences = preferences,
                fileSystem = fileSystem,
                httpClient = httpClient,
                cookieRepository = cookieRepository,
                appVersion = appVersion,
                isUpdateCheckEnabled = updateCheckEnabled,
                onUpdateCheckChanged = { enabled ->
                    stateStore?.let { sharedStore ->
                        launchStoreSafely("update setting", "設定を保存できませんでした") { sharedStore.setUpdateCheckEnabled(enabled) }
                    }
                },
                onArchiveReportEnabledChanged = onArchiveReportEnabledChanged,
                onOpenHelp = { dispatch(CompatibilityEvent.OpenHost(CompatHost.Help(host))) },
                onOpenSavedThreads = {
                    dispatch(CompatibilityEvent.OpenHost(CompatHost.SavedThreads(host)))
                },
                onOpenChangeLog = {
                    dispatch(CompatibilityEvent.OpenHost(CompatHost.ChangeLog(host)))
                },
                onOpenLicense = {
                    dispatch(CompatibilityEvent.OpenHost(CompatHost.License(host)))
                },
                onNavigate = { path ->
                    if (path == "watcher") watcherManagerOpen = true
                    else dispatch(CompatibilityEvent.OpenHost(host.copy(path = path)))
                },
                onBack = { dispatch(CompatibilityEvent.Back) },
                initialScrollPosition = settingsRootScrollPosition[0].takeIf { host.path == "root" },
                onScrollPositionChanged = { position ->
                    if (host.path == "root") settingsRootScrollPosition[0] = position
                }
            )
            is CompatHost.Help -> CompatHelpScreen(
                onOpenChangeLog = {
                    dispatch(CompatibilityEvent.OpenHost(CompatHost.ChangeLog(host)))
                },
                onBack = { dispatch(CompatibilityEvent.Back) }
            )
            is CompatHost.ToolbarEditor -> CompatToolbarEditorScreen(
                surface = host.surface,
                store = store,
                onBack = {
                    toolbarRefreshToken += 1L
                    dispatch(CompatibilityEvent.Back)
                }
            )
        }
        if (boardUpdateDialogOpen) {
            CompatBoardUpdateDialog(
                initialUrl = preferences[COMPAT_BOARD_MENU_URL_KEY]
                    .orEmpty()
                    .let { saved ->
                        if (saved.contains("2chan")) COMPAT_REFERENCE_BOARD_UPDATE_URL else saved
                    }
                    .ifBlank { COMPAT_DEFAULT_BOARD_MENU_URL },
                onDismiss = { boardUpdateDialogOpen = false },
                onExecute = { input ->
                    boardUpdateDialogOpen = false
                    launchStoreSafely("board menu update", "板一覧を更新できませんでした") {
                        store.savePreference(COMPAT_BOARD_MENU_URL_KEY, input)
                        if (!isCompatBoardUpdateUrlAccepted(input)) {
                            boardUpdateNotice = "アドレスを確認して下さい"
                            // The reference positive button closes the first
                            // DialogFragment, shows the toast, then creates a
                            // new dialog retaining the invalid input.
                            yield()
                            boardUpdateDialogOpen = true
                            return@launchStoreSafely
                        }
                        store.savePreference(
                            COMPAT_BOARD_MENU_URL_KEY,
                            COMPAT_REFERENCE_BOARD_UPDATE_URL
                        )
                        boardUpdateNotice = "しばらくお待ち下さい"
                        val result = httpClient?.let { client ->
                            updateCompatBoardsFromMenu(client, store, boards)
                        } ?: Result.failure(IllegalStateException("通信エラー"))
                        boardUpdateNotice = result.fold(
                            onSuccess = { "更新しました" },
                            onFailure = { failure ->
                                failure.message
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { message ->
                                        if (message.contains("timeout", ignoreCase = true)) {
                                            "通信タイムアウト"
                                        } else {
                                            message
                                        }
                                    }
                                    ?: "通信エラー"
                            }
                        )
                    }
                }
            )
        }
        }
        // The sheet position changes on every drag and animation frame. Read it
        // only in the layout/draw lambdas below; composition observes just
        // whether the scrim is shown, so the drawer content is not recomposed
        // per frame.
        val drawerVisibleWidthPx: () -> Float = {
            compatDrawerVisibleWidthPx(
                previewOffsetPx = drawerPreviewOffsetPx,
                isClosed = drawerState.isClosed,
                isOpen = drawerState.isOpen,
                currentOffset = drawerState.currentOffset,
                widthPx = drawerPreviewWidthPx
            )
        }
        val drawerScrimShown by remember(drawerState, drawerPreviewWidthPx) {
            derivedStateOf { drawerVisibleWidthPx() > 0f }
        }
        if (drawerScrimShown) {
            // Keep the action bar and drawer surface undimmed. The reference
            // APK dims only the catalog area to the right of the visible
            // drawer, starting below the platform status-bar inset plus the
            // 56dp action-bar region. The old currentValue-only overlay
            // appeared/disappeared abruptly and left a bright strip while
            // closing.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(drawerVisibleWidthPx().roundToInt(), 0) }
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .navigationBarsPadding()
                    .clickable { dispatch(CompatibilityEvent.CloseDrawer) }
                    .drawBehind {
                        val fraction = (drawerVisibleWidthPx() / drawerPreviewWidthPx).coerceIn(0f, 1f)
                        drawRect(Color.Black.copy(alpha = compatDrawerScrimAlpha(fraction)))
                    }
            )
        }
        // ApplyCompatSystemBars owns the platform status-bar surface. A second
        // Compose inset overlay here was measured as 95px on API 37 while the
        // actual status bar is 63px, tinting the first part of the toolbar and
        // producing the black/transparent top band visible in the drawer.
    }
    if (closeToastVisible) {
        state.pendingClose?.let { batch ->
            val density = LocalDensity.current
            val toastBottomInset = with(density) {
                40.dp.roundToPx() + WindowInsets.navigationBars.getBottom(this)
            }
            Popup(
                alignment = Alignment.BottomCenter,
                // Keep the popup window itself above the bottom toolbar. The
                // old implementation put this value in Surface.padding(),
                // leaving a transparent hit-target over the toolbar and
                // making "スレを閉じる" appear disabled while the toast was
                // visible (#33).
                offset = IntOffset(0, -toastBottomInset),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = false
                )
            ) {
                // Do not use Snackbar's adaptive action slot here. On older
                // Material3 versions it can move the action to the start when
                // the message is long, which makes the two strings overlap.
                Surface(
                    modifier = Modifier.width(320.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF323232),
                    contentColor = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            closedThreadUndoMessage(batch),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp
                        )
                        TextButton(onClick = { dispatch(CompatibilityEvent.UndoClose) }) {
                            Text(
                                "元に戻す",
                                color = LocalCompatibilityPalette.current.closedThreadUndoAction,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
    boardUpdateNotice?.let { message ->
        Popup(
            alignment = Alignment.BottomCenter,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .navigationBarsPadding()
                    .testTag("compat-board-update-toast"),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF323232),
                contentColor = Color.White,
                shadowElevation = 4.dp
            ) {
                Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
    val drawerPreviewShown by remember(drawerState) {
        derivedStateOf { drawerPreviewOffsetPx > 0f && drawerState.isClosed }
    }
    if (drawerPreviewShown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .testTag("compat-drawer-preview")
                    .zIndex(20f)
            ) {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .testTag("compat-drawer-preview-sheet")
                    .graphicsLayer {
                        translationX = drawerPreviewOffsetPx - drawerPreviewWidthPx
                    }
            ) {
                renderCompatibilityDrawer(pageOverride = edgeDrawerPage)
            }
        }
    }
    // Android 11's edge-to-edge Back gesture owns the same physical strip as
    // the legacy drawer. Without an exclusion rect, a slow swipe can be
    // delivered to Back after the preview has already moved, navigating from
    // ThreadWorkspace to Catalog (#36). Reserve only the drawer's narrow start
    // region; the rest of the screen keeps normal Back.
    if (drawerOpeningGesturesEnabled || drawerState.isOpen) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                // Keep the exclusion rect exactly as wide as the edge
                // recognizer above, including a landscape cutout inset.
                // A narrower rect lets Android Back reclaim the final
                // inset pixels and recreates the partial-open navigation
                // seen on Android 11 (#36).
                .width(drawerEdgeWidthDp)
                .fillMaxHeight()
                .platformSystemGestureExclusion()
        )
    }
    }
    pendingUnregisteredDeepLink?.let { (raw, parsed) ->
        AlertDialog(
            onDismissRequest = {
                onThreadDeepLinkConsumed(raw)
                pendingUnregisteredDeepLink = null
            },
            title = { Text("未登録の板") },
            text = { Text("${parsed.canonicalBoardUrl} を板一覧へ追加してスレッドを開きますか？") },
            confirmButton = {
                TextButton(onClick = {
                    val board = CompatBoard(
                        key = compatBoardKey(parsed.canonicalBoardUrl),
                        name = parsed.boardPath.substringAfterLast('/'),
                        canonicalUrl = parsed.canonicalBoardUrl,
                        originalUrl = parsed.canonicalBoardUrl,
                        sortOrder = boards.size
                    )
                    openCanonicalThread(parsed, board) {
                        onThreadDeepLinkConsumed(raw)
                    }
                    pendingUnregisteredDeepLink = null
                }) { Text("追加して開く") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onThreadDeepLinkConsumed(raw)
                    pendingUnregisteredDeepLink = null
                }) { Text("キャンセル") }
            }
        )
    }
    pendingPlatformAiConfirmation?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingPlatformAiConfirmation = null },
            title = { Text("AI操作の確認") },
            text = { Text("「${command.action.label}」を実行します。ユーザー確認後にだけ進めます。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPlatformAiConfirmation = null
                    runPlatformAiCommand(command, confirmed = true)
                }) { Text("続行") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlatformAiConfirmation = null }) { Text("キャンセル") }
            }
        )
    }
    deepLinkError?.let { message ->
        AlertDialog(
            onDismissRequest = { deepLinkError = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { deepLinkError = null }) { Text("OK") } }
        )
    }
    platformAiFeedback?.let { message ->
        AlertDialog(
            onDismissRequest = { platformAiFeedback = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { platformAiFeedback = null }) { Text("OK") } }
        )
    }
    // ApplyCompatSystemBars sets the platform navigation-bar color. Do not add
    // a Compose navigation-inset-sized box here: on Android 15+/API 35+ the
    // reported inset can include the mandatory gesture region as well, which
    // creates the oversized black/transparent band reported in the history
    // drawer and catalog screenshots.
    }
}

private fun remainingCompatDeadlineMillis(deadlineMillis: Long, nowMillis: Long): Long {
    if (deadlineMillis <= nowMillis) return 0L
    val remaining = deadlineMillis - nowMillis
    return if (remaining < 0L) Long.MAX_VALUE else remaining
}

@Composable
private fun CompatCatalogScreen(
    board: CompatBoard,
    boards: List<CompatBoard>,
    store: CompatibilityStore,
    isDrawerOpen: Boolean = false,
    platformAiCommand: FutachaAiCommand? = null,
    onPlatformAiCommandConsumed: (FutachaAiCommand) -> Unit = {},
    toolbarRefreshToken: Long = 0L,
    initialToolbarItems: List<CompatToolbarItem>? = null,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    tabs: List<CompatTab>,
    activeTabKey: String?,
    repository: BoardRepository?,
    httpClient: HttpClient?,
    archiveBaseUrl: String?,
    archiveSearchHistory: List<String>,
    archiveSearchNoticeHidden: Boolean,
    localHistory: List<CompatHistoryEntry>,
    freshSnapshotRevision: Long?,
    onFreshSnapshotCommitted: (String, Long) -> Unit,
    selectorOpen: Boolean,
    onToggleSelector: () -> Unit,
    onBack: () -> Unit,
    onBoardSelected: (CompatBoard) -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenWatcher: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDisplayOptions: () -> Unit,
    onOpenHelp: () -> Unit,
    onToolbarEdit: () -> Unit,
    onOpenBuild: () -> Unit,
    onOpenThread: (CatalogItem) -> Unit,
    onAddTab: (CatalogItem) -> Unit,
    onSelectTab: (CompatTab) -> Unit,
    onCloseTab: (CompatTab) -> Unit,
    canUndoClose: Boolean,
    onUndoClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val effectiveRepository = remember(board.key, repository) {
        if (isCompatFixtureBoard(board)) FakeBoardRepository() else repository
    }
    var items by remember(board.key) { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var catalogReplyDeltas by remember(board.key) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var resolvedCatalogTitles by remember(board.key) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var catalogItemStates by remember(board.key) { mutableStateOf<Map<String, CompatCatalogItemState>>(emptyMap()) }
    var loading by remember(board.key) { mutableStateOf(true) }
    var catalogContentReady by remember(board.key) { mutableStateOf(false) }
    val refreshMutex = remember(board.key) { Mutex() }
    var showLoadingIndicator by remember(board.key) { mutableStateOf(true) }
    var layoutSwitchLoading by remember(board.key) { mutableStateOf(false) }
    var layoutSwitchGeneration by remember(board.key) { mutableIntStateOf(0) }
    var lastUpdatedAtEpochMillis by remember(board.key) { mutableStateOf<Long?>(null) }
    var lastSnapshotRevision by remember(board.key) { mutableStateOf(0L) }
    var catalogHistoryGeneration by remember(board.key) { mutableIntStateOf(0) }
    var catalogRefreshGeneration by remember(board.key) { mutableIntStateOf(0) }
    var error by remember(board.key) { mutableStateOf<String?>(null) }
    var searchActive by remember(board.key) { mutableStateOf(false) }
    var searchQuery by remember(board.key) { mutableStateOf("") }
    var sortDialogOpen by remember { mutableStateOf(false) }
    var contextItem by remember { mutableStateOf<CatalogItem?>(null) }
    var catalogImageNgRegistrationItem by remember { mutableStateOf<CatalogItem?>(null) }
    var droppedDialogItems by remember(board.key) { mutableStateOf<List<CompatDroppedCatalogItem>?>(null) }
    var droppedCatalogItems by remember(board.key) { mutableStateOf<List<CompatDroppedCatalogItem>>(emptyList()) }
    var droppedCatalogItemsLoaded by remember(board.key) { mutableStateOf(false) }
    var suppressedDroppedThreadIds by remember(board.key) { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDroppedUndoIds by remember(board.key) { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<CompatCatalogDeleteRequest?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    fun launchCatalogStoreSafely(
        operation: String,
        userMessage: String = "設定の保存に失敗しました",
        block: suspend () -> Unit
    ) {
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Logger.e("CompatibilityCatalog", "$operation failed", failure)
                transientMessage = "$userMessage: ${failure.message.orEmpty()}"
            }
        }
    }
    var preference by remember(board.key) { mutableStateOf(CompatCatalogPreference(board.key)) }
    val catalogLayout = preferences.compatCatalogLayout(preference.layout)
    var preferenceLoaded by remember(board.key) { mutableStateOf(false) }
    var toolbarItems by remember(initialToolbarItems) {
        mutableStateOf(
            initialToolbarItems
                ?: reconcileCompatToolbar(CompatToolbarSurface.CATALOG, emptyList())
        )
    }
    var otherMenuRoute by remember { mutableStateOf<CompatOtherMenuRoute?>(null) }
    var managedNgKind by remember { mutableStateOf<CompatNgKind?>(null) }
    var catalogRuleRequest by remember { mutableStateOf<CompatCatalogRuleRequest?>(null) }
    var catalogImagePhashes by remember(board.key) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var catalogImageNgProgress by remember(board.key) { mutableStateOf<Pair<Int, Int>?>(null) }
    var watchWordsDialogOpen by remember { mutableStateOf(false) }
    var cacheSearchOpen by remember(board.key) { mutableStateOf(false) }
    var archiveSearchOpen by remember(board.key) { mutableStateOf(false) }
    var boardSelectorOpen by remember { mutableStateOf(false) }
    val catalogListState = rememberLazyListState()
    val catalogGridState = rememberLazyGridState()
    val openUrl = rememberUrlLauncher()
    val catalogNgEnabled = preferences["compat.catalog.NG機能"] != "OFF"
    val catalogRules = remember(ngRules, board.key) {
        compatCatalogRulesForBoard(ngRules, board.key)
    }
    val catalogImageNgPhashThreshold = preferences.compatPreferenceValue(
        "thread", "threadImageNgPhashThreshold", "画像NG類似度閾値"
    )?.filter(Char::isDigit)?.toIntOrNull()
        ?.coerceIn(CompatImagePhash.MIN_THRESHOLD, CompatImagePhash.MAX_THRESHOLD)
        ?: CompatImagePhash.DEFAULT_THRESHOLD
    val catalogImagePhashRules = remember(catalogRules) {
        catalogRules.filter { it.kind == CompatNgKind.CATALOG_IMAGE_PHASH }
    }
    val catalogExtractRules = remember(catalogRules) {
        catalogRules.filter { it.kind == CompatNgKind.CATALOG_EXTRACT }
    }
    val catalogRuleIndex = remember(catalogRules) {
        buildCompatCatalogRuleIndex(catalogRules)
    }
    val catalogPrivacyEnabled = preferences.compatPrivacyEnabled()
    val watchWords = remember(preferences, board.key) { compatWatchWordsForBoard(preferences, board.key) }
    val priorityThreshold = preferences
        .compatPreferenceValue("catalog", "delayFewReplies", "レス数で優先ソート")
        ?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(0, 30) ?: 0
    val catalogSourceTitleLength = preferences.compatPreferenceValue(
        "catalog", "catalogTitleLength", "スレッド文の長さ"
    )?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(10, 30) ?: 20
    LaunchedEffect(items, catalogImagePhashRules, httpClient) {
        val client = httpClient
        if (client == null || catalogImagePhashRules.isEmpty()) {
            catalogImagePhashes = emptyMap()
            catalogImageNgProgress = null
        } else {
            val candidates = items.mapNotNull { item ->
                (item.fullImageUrl ?: item.thumbnailUrl)?.let { item.id to it }
            }.distinctBy { it.second }.take(256)
            val storedHashes = store.loadImagePhashes(candidates.map { compatImagePhashCachePreferenceKey(it.second) })
            val cached = candidates.mapNotNull { (itemId, url) ->
                storedHashes[compatImagePhashCachePreferenceKey(url)]
                    ?.takeIf(::isValidCompatImagePhash)
                    ?.let { itemId to it }
            }.toMap()
            val missing = candidates.filterNot { (itemId, _) -> itemId in cached }
            catalogImagePhashes = cached
            if (missing.isEmpty()) {
                catalogImageNgProgress = null
                return@LaunchedEffect
            }
            catalogImageNgProgress = 0 to missing.size
            // New hashes are written in batches instead of one store write each.
            val unsaved = mutableMapOf<String, String>()
            suspend fun flushHashes() {
                if (unsaved.isEmpty()) return
                val batch = unsaved.toMap()
                unsaved.clear()
                store.saveImagePhashes(batch)
            }
            val computed = try {
                withTimeoutOrNull(COMPAT_PHASH_BATCH_TIMEOUT_MILLIS) {
                    buildMap {
                        missing.forEachIndexed { index, (itemId, url) ->
                        withTimeoutOrNull(COMPAT_PHASH_REQUEST_TIMEOUT_MILLIS) {
                            fetchCompatImagePhash(client, url).getOrNull()
                        }?.let { phash ->
                            put(itemId, phash)
                            unsaved[compatImagePhashCachePreferenceKey(url)] = phash
                            if (unsaved.size >= COMPAT_PHASH_SAVE_BATCH) flushHashes()
                        }
                            catalogImageNgProgress = (index + 1) to missing.size
                        }
                    }
                }.orEmpty()
            } finally {
                catalogImageNgProgress = null
                withContext(NonCancellable) { runCatching { flushHashes() } }
            }
            catalogImagePhashes = cached + computed
        }
    }
    val catalogProjectionRequest = remember(
        items,
        resolvedCatalogTitles,
        searchQuery,
        preference,
        priorityThreshold,
        catalogNgEnabled,
        watchWords,
        catalogImagePhashRules,
        catalogImagePhashes,
        catalogImageNgPhashThreshold,
        catalogRuleIndex,
        catalogSourceTitleLength
    ) {
        CompatCatalogProjectionRequest(
            items = items,
            resolvedTitles = resolvedCatalogTitles,
            searchQuery = searchQuery,
            preference = preference,
            priorityThreshold = priorityThreshold,
            catalogNgEnabled = catalogNgEnabled,
            watchWords = watchWords,
            catalogImagePhashRules = catalogImagePhashRules,
            catalogImagePhashes = catalogImagePhashes,
            catalogImageNgPhashThreshold = catalogImageNgPhashThreshold,
            catalogRuleIndex = catalogRuleIndex,
            catalogSourceTitleLength = catalogSourceTitleLength
        )
    }
    val visibleItems by key(board.key) {
        produceState(
            initialValue = emptyList<CatalogItem>(),
            key1 = catalogProjectionRequest
        ) {
            value = calculateCompatCatalogProjection(catalogProjectionRequest)
        }
    }

    // Some boards (notably img.2chan.net) expose only the reply-count badge
    // in the catalog cell. Resolve the subject from the thread head for the
    // first visible batch, just like the modern catalog does. The bounded,
    // concurrent batch prevents a 3000-thread catalog from turning into a
    // request storm while ensuring the initial screen no longer displays the
    // reply count as the title (#32).
    LaunchedEffect(items, effectiveRepository, board.originalUrl) {
        val activeRepository = effectiveRepository ?: return@LaunchedEffect
        val candidates = items.filter { item ->
            shouldResolveCatalogItemTitleFromHead(item.title, item.replyCount) &&
                resolvedCatalogTitles[item.id].isNullOrBlank()
        }.take(12)
        if (candidates.isEmpty() || isCompatFixtureBoard(board)) return@LaunchedEffect
        val resolved = coroutineScope {
            val semaphore = Semaphore(2)
            candidates.map { item ->
                async {
                    val title = semaphore.withPermit {
                        withTimeoutOrNull(1_800L) {
                            runSuspendCatchingPreservingCancellation {
                                activeRepository.resolveCatalogDisplayTitle(
                                    board = board.originalUrl,
                                    item = item,
                                    allowFallbackHeadScan = true
                                )
                            }.getOrNull()
                        }
                    }
                    item.id to title?.takeIf(String::isNotBlank)
                }
            }.awaitAll().mapNotNull { (id, title) -> title?.let { id to it } }.toMap()
        }
        if (resolved.isNotEmpty()) {
            resolvedCatalogTitles = resolvedCatalogTitles + resolved
        }
    }
    val catalogLimit = preferences.compatPreferenceValue("catalog", "catalogThreadSize", "スレッド数")
        ?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(50, 3_000) ?: 300
    val portraitColumns = preferences.compatPreferenceValue("catalog", "catalogGridViewPortraitClmNum", "縦持ちの列数")
        ?.toIntOrNull()?.coerceIn(2, 16) ?: 5
    val landscapeColumns = preferences.compatPreferenceValue("catalog", "catalogGridViewLandscapeClmNum", "横持ちの列数")
        ?.toIntOrNull()?.coerceIn(2, 16) ?: 7
    val gridTitleLength = preferences.compatPreferenceValue("catalog", "catalogGridViewTitleLength", "タイトルの長さ")
        ?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(0, 30) ?: 4
    val gridFontSize = preferences.compatPreferenceValue("catalog", "catalogGridViewTitleFontSize", "フォントサイズ")
        ?.toIntOrNull()?.coerceIn(6, 16) ?: 14
    val listTitleLength = preferences.compatPreferenceValue("catalog", "catalogListViewTitleLength")
        ?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(0, 30) ?: 4
    val listFontSize = preferences.compatPreferenceValue("catalog", "catalogListViewTitleFontSize")
        ?.toIntOrNull()?.coerceIn(6, 16) ?: 14
    val cropCatalogThumbnails = preferences.compatPreferenceValue("catalog", "catalogThumbCrop", "画像のトリミング表示") == "ON"
    val catalogPullRefreshEnabled = preferences.compatPreferenceValue(
        "catalog", "catalogPullToRefresh", "スクロール更新"
    ) != "OFF"
    val catalogFastScrollEnabled = preferences.compatPreferenceValue(
        "catalog", "catalogFastScroll", "高速スクロールバー"
    ) == "ON"
    val catalogPlatformContext = LocalPlatformContext.current
    val catalogLowQuality = shouldUseCompatCatalogLowQuality(
        alwaysLowQuality = preferences.compatPreferenceValue(
            "catalog", "catalogEco", "低画質サムネイル"
        ) == "ON",
        meteredOnlyLowQuality = preferences.compatPreferenceValue(
            "catalog", "catalogMobileEco", "携帯回線時に低画質"
        ) == "ON",
        isUnmeteredConnection = isCompatWifiConnected(catalogPlatformContext)
    )
    val designLoading = preferences.compatPreferenceValue(
        "design", "designLoading", "ローディング"
    ) ?: "デフォルト"
    val catalogVolumeKeyAction = preferences.compatPreferenceValue(
        "control", "controlCatalogVolumeKey", "カタログのボリュームキー"
    )?.let { compatPreferenceDisplayValue("controlCatalogVolumeKey", it) } ?: "何もしない"
    val catalogDroppedTrackingEnabled = preferences.compatPreferenceValue(
        "catalog", "catalogFindThreadDeleted", "スレ落ち・隔離を判定"
    ) == "ON"
    val catalogAppendDroppedEnabled = catalogDroppedTrackingEnabled &&
        preferences.compatPreferenceValue(
            "catalog", "catalogAppendDropped", "消えたスレを末尾に表示"
        ) == "ON"
    val catalogReloadScrollTopEnabled = preferences.compatPreferenceValue(
        "catalog", "catalogReloadScrollTop", "リロード後に先頭へ戻る"
    ) == "ON"
    LaunchedEffect(board.key, catalogDroppedTrackingEnabled) {
        if (!catalogDroppedTrackingEnabled) {
            droppedCatalogItems = emptyList()
            suppressedDroppedThreadIds = emptySet()
            pendingDroppedUndoIds = emptySet()
            droppedCatalogItemsLoaded = true
            return@LaunchedEffect
        }
        try {
            droppedCatalogItems = store.loadDroppedCatalogItems(board.key)
            droppedCatalogItemsLoaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatibilityCatalog", "Failed to load isolated catalog items", failure)
            transientMessage = failure.toCompatUserMessage("落ちたスレを読み込めませんでした")
        }
    }
    val filteredDroppedCatalogItems = remember(
        droppedCatalogItems,
        searchQuery,
        catalogNgEnabled,
        catalogRuleIndex,
        resolvedCatalogTitles,
        catalogSourceTitleLength
    ) {
        val normalizedQuery = normalizeCatalogSearchText(searchQuery)
        droppedCatalogItems.map { dropped ->
            val resolvedTitle = resolvedCatalogTitles[dropped.item.id]
                ?.takeIf(String::isNotBlank)
                ?: dropped.item.title
            val item = dropped.item.copy(
                title = truncateCompatCatalogSourceTitle(
                    resolvedTitle,
                    catalogSourceTitleLength
                )
            )
            dropped.copy(item = item)
        }.filterNot { dropped ->
            catalogNgEnabled && catalogRuleIndex.hides(dropped.item)
        }.filter { dropped ->
            normalizedQuery.isBlank() ||
                normalizeCatalogSearchText(dropped.item.title.orEmpty()).contains(normalizedQuery) ||
                dropped.item.id.contains(searchQuery, ignoreCase = true)
        }
    }
    val displayedCatalogItems = remember(
        visibleItems,
        filteredDroppedCatalogItems,
        catalogAppendDroppedEnabled,
        catalogContentReady,
        suppressedDroppedThreadIds,
        catalogLimit
    ) {
        appendCompatDroppedCatalogItems(
            current = visibleItems
                .distinctBy { item -> item.id.ifBlank { item.threadUrl } }
                .take(catalogLimit),
            dropped = filteredDroppedCatalogItems,
            enabled = catalogAppendDroppedEnabled,
            contentReady = catalogContentReady,
            suppressedThreadIds = suppressedDroppedThreadIds
        )
    }
    val droppedClassByThreadId = remember(filteredDroppedCatalogItems, items) {
        val liveIds = items.mapTo(mutableSetOf()) { it.id }
        filteredDroppedCatalogItems
            .filterNot { it.item.id in liveIds }
            .associate { it.item.id to it.classification }
    }
    val showReplyCountOnThumbnail = preferences.compatPreferenceValue(
        "catalog", "catalogGridViewResCountOnThumb", "画像の上にレス数を重ねる"
    ) != "OFF"
    val catalogLongTapAction = preferences.compatPreferenceValue(
        "control", "controlCatalogLongTap", "カタログのロングタップ"
    )?.let { compatPreferenceDisplayValue("controlCatalogLongTap", it) } ?: "選択メニュー"
    val selectorLongTapAction = preferences.compatPreferenceValue(
        "control", "controlTabSelectorLongTap", "タブ一覧のロングタップ"
    )?.let { compatPreferenceDisplayValue("controlTabSelectorLongTap", it) } ?: "選択メニュー"
    val selectorPresentation = if (
        preferences.compatPreferenceValue(
            "design", "designTabSelectorLocation", "タブ一覧の表示位置"
        )?.let { compatPreferenceDisplayValue("designTabSelectorLocation", it) } ==
            "ツールバーの上に重ねる"
    ) SelectorPresentation.OVER else SelectorPresentation.ABOVE
    val privacyAlpha = parseCompatPercent(
        preferences.compatPreferenceValue("catalog", "commonPrivacyAlpha", "プライバシー透明度")
    )
    fun addNgRules(item: CatalogItem, thread: Boolean, word: Boolean, image: Boolean) {
        val now = Clock.System.now().toEpochMilliseconds()
        val rules = buildList {
            if (thread) {
                val url = item.threadUrl.trim().ifBlank { item.id.trim() }
                if (url.isNotBlank()) {
                    add(Triple(url.normalizeCompatNgValue(), CompatNgKind.CATALOG_REFUSE, item.title.orEmpty().take(4)))
                }
            }
            if (word && item.title.orEmpty().isNotBlank()) {
                val displayedWord = item.title.orEmpty().take(100)
                add(Triple(displayedWord.normalizeCompatNgValue(), CompatNgKind.CATALOG_IGNORE, displayedWord))
            }
            if (image) listOfNotNull(item.fullImageUrl, item.thumbnailUrl).firstOrNull()?.let {
                add(Triple(it.normalizeCompatNgValue(), CompatNgKind.CATALOG_IMAGE, item.title.orEmpty()))
            }
        }
        launchCatalogStoreSafely("catalog NG persistence", "NGの保存に失敗しました") {
            rules.forEach { (value, kind, displayValue) ->
                store.upsertNgRule(
                    CompatNgRule(
                        id = compatNgRuleId(kind, board.key, value),
                        kind = kind,
                        scopeKey = board.key,
                        normalizedValue = value,
                        imageUrl = value.takeIf { kind == CompatNgKind.CATALOG_IMAGE },
                        memo = displayValue.take(MAX_COMPAT_NG_MEMO_CHARS),
                        createdAtEpochMillis = now
                    )
                )
            }
            transientMessage = if (rules.isEmpty()) "登録できる対象がありません" else "NGに登録しました"
        }
    }
    fun addCatalogRule(item: CatalogItem, kind: CompatNgKind, allBoards: Boolean) {
        val raw = when (kind) {
            CompatNgKind.CATALOG_REFUSE -> item.threadUrl
            else -> item.title.orEmpty()
        }.trim()
        if (raw.isBlank()) {
            transientMessage = "登録できる対象がありません"
            return
        }
        val scopeKey = if (allBoards) "*" else board.key
        val value = raw.normalizeCompatNgValue()
        launchCatalogStoreSafely("catalog rule persistence", "NGの保存に失敗しました") {
            store.upsertNgRule(
                CompatNgRule(
                    id = compatNgRuleId(kind, scopeKey, value),
                    kind = kind,
                    scopeKey = scopeKey,
                    normalizedValue = value,
                    memo = when (kind) {
                        CompatNgKind.CATALOG_REFUSE -> item.title.orEmpty().take(4)
                        CompatNgKind.CATALOG_IGNORE,
                        CompatNgKind.CATALOG_EXTRACT -> raw.take(MAX_COMPAT_NG_MEMO_CHARS)
                        else -> ""
                    },
                    createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                )
            )
            transientMessage = if (allBoards) "全板の${kind.compatCatalogRuleLabel()}に登録しました"
            else "この板の${kind.compatCatalogRuleLabel()}に登録しました"
        }
    }
    fun chooseCatalogRule(item: CatalogItem, kind: CompatNgKind) {
        catalogRuleRequest = CompatCatalogRuleRequest(item, kind)
    }
    fun addCatalogImagePhashRule(item: CatalogItem, memo: String, localOnly: Boolean) {
        val client = httpClient
        val url = item.fullImageUrl ?: item.thumbnailUrl
        if (client == null || url.isNullOrBlank()) {
            transientMessage = "画像pHashを作成できる画像がありません"
            return
        }
        transientMessage = "NG画像登録中"
        launchCatalogStoreSafely("catalog image NG persistence", "画像NGの保存に失敗しました") {
            fetchCompatImagePhash(client, url)
                .onSuccess { phash ->
                    val scopeKey = compatThreadImageNgScopeKey(board.key, localOnly)
                    store.upsertNgRule(
                        CompatNgRule(
                            id = compatNgRuleId(CompatNgKind.CATALOG_IMAGE_PHASH, scopeKey, phash),
                            kind = CompatNgKind.CATALOG_IMAGE_PHASH,
                            scopeKey = scopeKey,
                            normalizedValue = phash,
                            imageUrl = url,
                            memo = memo.take(MAX_COMPAT_NG_MEMO_CHARS),
                            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                    transientMessage = "画像pHash NGに登録しました"
                }
                .onFailure { failure -> transientMessage = failure.toCompatUserMessage("画像pHashを作成できませんでした") }
        }
    }
    fun handleCatalogLongTap(item: CatalogItem) {
        when (catalogLongTapAction) {
            "何もしない" -> Unit
            "NGスレッドに登録" -> addNgRules(item, thread = true, word = false, image = false)
            "delを送信する" -> pendingDelete = CompatCatalogDeleteRequest(item)
            "タブに追加する" -> {
                onAddTab(item)
                transientMessage = "タブに追加しました"
            }
            else -> contextItem = item
        }
    }
    suspend fun refresh(sort: CompatCatalogSort = preference.sort) {
        if (!refreshMutex.tryLock()) {
            transientMessage = "読み込み中です"
            return
        }
        try {
            val activeRepository = effectiveRepository
            if (activeRepository == null) {
                error = "通信機能を初期化できませんでした"
                return
            }
            loading = true
            // The compatibility APK keeps its centered loader above the
            // existing catalog while a refresh is running. Hiding it merely
            // because a cached snapshot is visible is what made #69 appear
            // unfixed on Android 11.
            showLoadingIndicator = true
            val refreshResult = try {
                val refreshResult = withTimeoutOrNull(COMPAT_CATALOG_REFRESH_TIMEOUT_MILLIS) {
                    activeRepository.getCatalogWithSettings(
                        board = board.originalUrl,
                        mode = sort.toCatalogMode(),
                        settings = compatCatalogFetchSettings(catalogLimit)
                    )
                }?.let { Result.success(it) }
                    ?: Result.failure<List<CatalogItem>>(CompatCatalogRefreshTimeoutException())
                refreshResult
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
            try {
                refreshResult.onSuccess { rawItems ->
                val loadedItems = rawItems.map { item ->
                    if (isCompatFixtureBoard(board)) {
                        item.copy(threadUrl = compatFixtureThreadUrl(board, item))
                    } else {
                        item
                    }
                }
                val fetchedAt = Clock.System.now().toEpochMilliseconds()
                val revision = maxOf(fetchedAt, lastSnapshotRevision + 1L)
                val previousSnapshot = store.loadCatalogSnapshot(board.key, sort)
                catalogReplyDeltas = buildCompatCatalogReplyDeltas(
                    current = loadedItems,
                    previous = previousSnapshot?.items.orEmpty()
                )
                items = loadedItems
                catalogHistoryGeneration = 0
                // The timestamp in the legacy toolbar describes the cached
                // snapshot being shown. A successful network refresh replaces
                // that snapshot, so clear the cache marker until the next
                // cached open (#46).
                lastUpdatedAtEpochMillis = null
                val watchMatches = collectCompatWatchMatches(
                    board = board,
                    items = loadedItems,
                    watchWords = compatWatchWordsForBoard(preferences, board.key),
                    existingHistory = localHistory,
                    nowEpochMillis = fetchedAt
                )
                watchMatches.forEach { match ->
                    launchCatalogStoreSafely("watch history persistence", "監視履歴の保存に失敗しました") {
                        CompatWatcherRepository(store).record(match)
                    }
                }
                val activeDroppedThreadIds = previousSnapshot?.takeIf { catalogDroppedTrackingEnabled }?.let { previous ->
                    val vanished = diffCompatCatalogGenerations(
                        current = loadedItems,
                        previous = previous.items,
                        requestedThreadCount = catalogLimit,
                        enabled = true
                    ).vanishedWithin.take(COMPAT_DROPPED_PROBE_MAX_ITEMS)
                    withTimeoutOrNull(COMPAT_DROPPED_PROBE_TOTAL_TIMEOUT_MILLIS) {
                        buildSet {
                            vanished.forEach { dropped ->
                                if (activeRepository.probeThreadExists(dropped.threadUrl)) {
                                    add(dropped.id)
                                }
                            }
                        }
                    }.orEmpty()
                }.orEmpty()
                if (runSuspendCatchingPreservingCancellation {
                        store.saveCatalogSnapshot(
                            CompatCatalogSnapshot(
                                boardKey = board.key,
                                sort = sort,
                                revision = revision,
                                fetchedAtEpochMillis = fetchedAt,
                                items = loadedItems
                            ),
                            trackDropped = catalogDroppedTrackingEnabled,
                            requestedThreadCount = catalogLimit,
                            activeDroppedThreadIds = activeDroppedThreadIds
                        )
                    }.getOrDefault(false)
                ) {
                    lastSnapshotRevision = revision
                    onFreshSnapshotCommitted(board.key, revision)
                    store.loadCatalogSnapshot(board.key, sort)?.let { committed ->
                        items = committed.items
                        catalogItemStates = committed.itemStates
                    }
                }
                if (catalogDroppedTrackingEnabled) {
                    val refreshedDropped = store.loadDroppedCatalogItems(board.key)
                    val refreshedIds = refreshedDropped.mapTo(mutableSetOf()) { it.item.id }
                    val newlyDroppedIds = if (droppedCatalogItemsLoaded) {
                        refreshedIds - droppedCatalogItems.mapTo(mutableSetOf()) { it.item.id }
                    } else {
                        emptySet()
                    }
                    droppedCatalogItems = refreshedDropped
                    droppedCatalogItemsLoaded = true
                    suppressedDroppedThreadIds = suppressedDroppedThreadIds.intersect(refreshedIds)
                    if (newlyDroppedIds.isNotEmpty()) pendingDroppedUndoIds = newlyDroppedIds
                }
                // Keep the request size alongside the snapshot. A cached
                // catalog from an older setting must not be presented as if
                // it satisfied the newly selected size (#17).
                launchCatalogStoreSafely("catalog fetch count persistence") {
                    store.savePreference(
                        compatCatalogLastFetchCountKey(board.key, sort),
                        catalogLimit.toString()
                    )
                }
                error = null
                catalogRefreshGeneration++
                }.onFailure { failure ->
                    error = if (failure is CompatCatalogRefreshTimeoutException) {
                        "カタログの更新がタイムアウトしました。再度お試しください"
                    } else {
                        failure.toCompatUserMessage("カタログを取得できませんでした")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                error = failure.toCompatUserMessage("カタログの更新に失敗しました")
            }
        } finally {
            catalogContentReady = true
            loading = false
            showLoadingIndicator = false
            refreshMutex.unlock()
        }
    }
    suspend fun showCachedThenRefresh(sort: CompatCatalogSort, forceRefresh: Boolean = false) {
        val cached = store.loadCatalogSnapshot(board.key, sort)
        cached?.let {
            catalogHistoryGeneration = 0
            catalogReplyDeltas = emptyMap()
            items = it.items
            catalogItemStates = it.itemStates
            lastUpdatedAtEpochMillis = compatCatalogCachedAtForSession(
                snapshotRevision = it.revision,
                fetchedAtEpochMillis = it.fetchedAtEpochMillis,
                freshSnapshotRevision = freshSnapshotRevision
            )
            lastSnapshotRevision = it.revision
            catalogContentReady = true
            loading = false
            showLoadingIndicator = false
        }
        // The legacy setting is specifically about opening an already cached
        // catalog.  Always refreshing here made OFF indistinguishable from ON
        // and caused every return to the board to hit the network.
        val reloadOnOpen = preferences.compatPreferenceValue(
            "catalog", "catalogOpenWithReload", "カタログを開いた時リロードを行う"
        ) == "ON"
        if (cached == null || reloadOnOpen || forceRefresh) refresh(sort)
    }
    suspend fun showPreviousCatalogGeneration() {
        if (loading) {
            transientMessage = "読み込み中です"
            return
        }
        val nextGeneration = catalogHistoryGeneration + 1
        if (nextGeneration > COMPAT_CATALOG_MAX_ROLLBACK_GENERATIONS) {
            transientMessage = "これ以上戻せません"
            return
        }
        val snapshot = store.loadCatalogSnapshot(board.key, preference.sort, nextGeneration)
        if (snapshot == null) {
            transientMessage = "これ以上戻せません"
            return
        }
        catalogHistoryGeneration = nextGeneration
        catalogReplyDeltas = emptyMap()
        items = snapshot.items
        catalogItemStates = snapshot.itemStates
        lastUpdatedAtEpochMillis = snapshot.fetchedAtEpochMillis
        error = null
        transientMessage = "カタログをリロード${nextGeneration}回前の状態に戻しました"
    }
    fun persistPreference(next: CompatCatalogPreference, reload: Boolean = false) {
        preference = next
        launchCatalogStoreSafely("catalog preference persistence") {
            store.saveCatalogPreference(next)
            if (reload) showCachedThenRefresh(next.sort)
        }
    }
    LaunchedEffect(
        board.key,
        catalogLimit,
        preferences.compatPreferenceValue(
            "catalog", "catalogOpenWithReload", "カタログを開いた時リロードを行う"
        )
    ) {
        try {
            preference = store.loadCatalogPreference(board.key)
            preferenceLoaded = true
            val lastRequestedCount = store.loadPreference(
                compatCatalogLastFetchCountKey(board.key, preference.sort)
            )?.toIntOrNull()
            showCachedThenRefresh(
                preference.sort,
                forceRefresh = lastRequestedCount != catalogLimit
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatibilityCatalog", "Failed to load catalog preferences", failure)
            error = failure.toCompatUserMessage("カタログ設定を読み込めませんでした")
        }
    }
    LaunchedEffect(catalogRefreshGeneration, catalogReloadScrollTopEnabled) {
        if (catalogRefreshGeneration == 0 || !catalogReloadScrollTopEnabled) return@LaunchedEffect
        // Run after the new item snapshot has been measured. Calling
        // scrollToItem from the network coroutine can race LazyGrid/LazyColumn
        // layout on Android 11 and leave the old position visible (#27).
        withFrameNanos { }
        if (catalogLayout == CompatCatalogLayout.GRID) {
            catalogGridState.scrollToItem(0)
        } else {
            catalogListState.scrollToItem(0)
        }
    }
    LaunchedEffect(layoutSwitchGeneration) {
        if (layoutSwitchGeneration == 0) return@LaunchedEffect
        // The reference APK recreates CatalogActivity when switching between
        // grid and list, which leaves its centered 50dp loading image visible
        // while the replacement view is built. Keep the Compose layout switch
        // local, but retain the same visible feedback for at least one frame.
        withFrameNanos { }
        delay(COMPAT_CATALOG_LAYOUT_SWITCH_LOADING_MIN_MILLIS)
        layoutSwitchLoading = false
    }
    LaunchedEffect(toolbarRefreshToken, initialToolbarItems) {
        try {
            toolbarItems = initialToolbarItems ?: store.loadToolbar(CompatToolbarSurface.CATALOG)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatibilityCatalog", "Failed to load catalog toolbar", failure)
        }
    }
    LaunchedEffect(platformAiCommand, board.key) {
        val command = platformAiCommand ?: return@LaunchedEffect
        when (command.action) {
            FutachaAiAction.ScrollCatalogToTop -> {
                if (catalogLayout == CompatCatalogLayout.GRID) {
                    catalogGridState.animateScrollToItem(0)
                } else {
                    catalogListState.animateScrollToItem(0)
                }
            }
            FutachaAiAction.StartCatalogSearch -> searchActive = true
            FutachaAiAction.SearchCatalog -> {
                searchActive = true
                command.parameter("query", "q", "word", "keyword", "text")?.let { searchQuery = it }
            }
            FutachaAiAction.OpenBoardExternally -> openUrl(board.originalUrl)
            else -> Unit
        }
        onPlatformAiCommandConsumed(command)
    }
    PlatformBackHandler(enabled = searchActive, iosEdgeGestureEnabled = false) {
        searchActive = false
        searchQuery = ""
    }
    LaunchedEffect(error) {
        if (error != null) {
            delay(3_000)
            error = null
        }
    }
    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            delay(2_000)
            transientMessage = null
        }
    }
    LaunchedEffect(pendingDroppedUndoIds) {
        if (pendingDroppedUndoIds.isNotEmpty()) {
            delay(7_000)
            pendingDroppedUndoIds = emptySet()
        }
    }
    val catalogCommands = listOf(
        CompatToolbarCommand("post", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "post"), "スレ立て", onClick = onOpenBuild),
        CompatToolbarCommand("reload", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "reload"), "リロード") { if (!loading) scope.launch { refresh() } },
        CompatToolbarCommand("search", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "search"), "スレッド検索") { searchActive = true },
        CompatToolbarCommand("sort", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "sort"), "表示順") { sortDialogOpen = true },
        CompatToolbarCommand("board", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "board"), "板一覧", onClick = { boardSelectorOpen = true }),
        CompatToolbarCommand(
            "tab",
            compatToolbarArtwork(
                CompatToolbarSurface.CATALOG,
                "tab",
                selected = hasCompatTabToolbarUpdate(tabs)
            ),
            "タブ",
            showUpdateBadge = hasCompatTabToolbarUpdate(tabs),
            onClick = onToggleSelector
        ),
        CompatToolbarCommand("privacy", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "privacy"), "プライバシー") {
            launchCatalogStoreSafely("catalog privacy persistence") {
                store.savePreference(COMPAT_COMMON_PRIVACY_STORAGE_KEY, if (catalogPrivacyEnabled) "OFF" else "ON")
            }
        },
        CompatToolbarCommand(
            "bypass",
            compatToolbarArtwork(
                CompatToolbarSurface.CATALOG,
                "bypass",
                selected = preferences[COMPAT_CACHE_ENABLED_KEY] != "ON"
            ),
            "通信の軽量化"
        ) {
            val enabled = preferences[COMPAT_CACHE_ENABLED_KEY] == "ON"
            val toggle = nextCompatCacheToggle(enabled)
            launchCatalogStoreSafely("catalog cache preference persistence") {
                store.savePreference(COMPAT_CACHE_ENABLED_KEY, toggle.storedValue)
                transientMessage = toggle.message
            }
        },
        CompatToolbarCommand("check", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "check"), "更新の確認") {
            transientMessage = "開いているスレの更新を確認しています"
            onCheckUpdates()
        },
        CompatToolbarCommand("undo", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "undo"), "リロード前に戻す") {
            scope.launch { showPreviousCatalogGeneration() }
        },
        CompatToolbarCommand("dropped", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "dropped"), "消えたスレ") {
            launchCatalogStoreSafely("dropped catalog load", "消えたスレを読み込めませんでした") {
                droppedDialogItems = store.loadDroppedCatalogItems(board.key)
            }
        },
        CompatToolbarCommand(
            "quickng",
            compatToolbarArtwork(
                CompatToolbarSurface.CATALOG,
                "quickng",
                selected = catalogNgEnabled
            ),
            "NG",
            selected = catalogNgEnabled,
            onClick = {
                launchCatalogStoreSafely("catalog NG preference persistence") {
                    store.savePreference(
                        "compat.catalog.NG機能",
                        if (catalogNgEnabled) "OFF" else "ON"
                    )
                }
            }
        ),
        CompatToolbarCommand("drawer", compatToolbarArtwork(CompatToolbarSurface.CATALOG, "drawer"), "ドロワーを開く", onClick = onOpenDrawer)
    )
    droppedDialogItems?.let { dropped ->
        PlatformBackHandler { droppedDialogItems = null }
        CompatDroppedCatalogScreen(
            boardName = board.name,
            entries = dropped,
            onBack = { droppedDialogItems = null },
            onOpenThread = { item ->
                droppedDialogItems = null
                onOpenThread(item)
            },
            onDeleteDieEntries = {
                store.deleteDroppedCatalogItems(board.key, CompatCatalogDroppedClass.DIE)
                droppedDialogItems = store.loadDroppedCatalogItems(board.key)
                droppedCatalogItems = droppedDialogItems.orEmpty()
            }
        )
        return
    }
    Scaffold(
        containerColor = CompatFutabaBackground,
        topBar = {
            if (searchActive) {
                CompatCatalogSearchTopBar(
                    query = searchQuery,
                    resultCount = displayedCatalogItems.size,
                    onQueryChanged = { searchQuery = it },
                    onClose = { searchActive = false; searchQuery = "" }
                )
            } else {
                CompatTopBar(
                    "${board.name} (${items.size}件)",
                    buildString {
                        append(preference.sort.displayLabel)
                        lastUpdatedAtEpochMillis?.let { append(" "); append(formatCompatCatalogTime(it)) }
                        if (catalogHistoryGeneration > 0) append(" (${catalogHistoryGeneration}つ前)")
                    },
                    onBack,
                    onOpenDrawer = onOpenDrawer,
                    isDrawerOpen = isDrawerOpen,
                    onCloseDrawer = onCloseDrawer,
                    onSearch = { searchActive = true },
                    onDisplayOptions = onOpenDisplayOptions,
                    onToolbarEdit = onToolbarEdit,
                    onSettings = onOpenSettings,
                    onOpenHelp = onOpenHelp
                )
            }
        },
        bottomBar = {
            Column(modifier = if (searchActive) Modifier.imePadding() else Modifier) {
                if (selectorOpen && selectorPresentation == SelectorPresentation.ABOVE) CompatTabSelector(
                    tabs = tabs,
                    currentTabKey = activeTabKey,
                    threadContext = false,
                    onSelect = onSelectTab,
                    onClose = onCloseTab,
                    onCheckUpdates = onCheckUpdates,
                    onReload = { scope.launch { refresh() } },
                    longTapAction = selectorLongTapAction
                )
                CompatToolbar(
                    surface = CompatToolbarSurface.CATALOG,
                    commands = catalogCommands,
                    items = toolbarItems,
                    onOther = { otherMenuRoute = CompatOtherMenuRoute.CATALOG_ROOT },
                    refreshingCommandKeys = if (loading) setOf("reload", "check") else emptySet()
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val catalogColumns = if (maxWidth > maxHeight) landscapeColumns else portraitColumns
            val catalogDensity = LocalDensity.current.density
            val gridThumbnailSize = (
                maxWidth - 5.7.dp - 7.dp - (5.dp * (catalogColumns - 1))
            ).div(catalogColumns).coerceAtLeast(1.dp)
            val gridThumbnailRequestSizePx = compatThumbnailRequestSizePx(
                displaySizeDp = gridThumbnailSize.value,
                density = catalogDensity
            )
            val listLineCount = preferences.compatPreferenceValue(
                "catalog", "catalogListViewLineNum", "長辺の列数"
            )?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(2, 30) ?: 7
            // The reference APK derives the list row from the screen's long
            // edge. A fixed 60dp row made this setting appear ineffective.
            val listRowHeight = (maxHeight / (listLineCount + 4)).coerceIn(40.dp, 180.dp)
            val listThumbnailSize = (listRowHeight - 4.dp).coerceAtLeast(36.dp)
            val listThumbnailRequestSizePx = compatThumbnailRequestSizePx(
                displaySizeDp = listThumbnailSize.value,
                density = catalogDensity
            )
            // A malformed/partially merged catalog can contain the same thread
            // more than once.  CatalogItem.id is the semantic identity used by
            // the reference UI, so remove duplicates before handing the list to
            // either LazyVerticalGrid or LazyColumn.  Letting duplicate ids
            // through produces the same fatal LazyLayout exception as duplicate
            // tabs (#29/#30).
            val displayedItems = displayedCatalogItems
            val displayedItemKeys = remember(board.key, displayedItems) {
                buildCatalogItemLazyKeys(board.key, displayedItems)
            }
            val volumeKeyOwner = remember(board.key) { Any() }
            DisposableEffect(catalogVolumeKeyAction, catalogLayout, board.key) {
                CompatVolumeKeyBus.register(volumeKeyOwner) { key ->
                    if (catalogVolumeKeyAction !in setOf("スクロール", "1画面分スクロール", "screen")) {
                        false
                    } else {
                        scope.launch {
                            val upward = key == CompatVolumeKey.UP
                            if (catalogLayout == CompatCatalogLayout.GRID) {
                                val page = catalogGridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                                val target = (catalogGridState.firstVisibleItemIndex + if (upward) -page else page)
                                    .coerceIn(0, (displayedItems.size - 1).coerceAtLeast(0))
                                catalogGridState.animateScrollToItem(target)
                            } else {
                                val page = catalogListState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                                val target = (catalogListState.firstVisibleItemIndex + if (upward) -page else page)
                                    .coerceIn(0, (displayedItems.size - 1).coerceAtLeast(0))
                                catalogListState.animateScrollToItem(target)
                            }
                        }
                        true
                    }
                }
                onDispose { CompatVolumeKeyBus.unregister(volumeKeyOwner) }
            }
            val openTabsByUrl = tabs.associateBy(CompatTab::canonicalUrl)
            fun replyIndicator(item: CatalogItem): CompatCatalogReplyIndicator? {
                val canonical = canonicalizeThreadUrl(item.threadUrl)?.canonicalUrl
                val tab = canonical?.let(openTabsByUrl::get)
                return resolveCompatCatalogReplyIndicator(
                    currentReplyCount = item.replyCount,
                    checkedReplyCount = tab?.checkedReplyCount,
                    previousCatalogDelta = catalogReplyDeltas[item.compatCatalogReplyDeltaKey()]
                )
            }
            CompatBidirectionalPullRefresh(
                enabled = catalogPullRefreshEnabled,
                refreshing = loading,
                canScrollBackward = {
                    if (catalogLayout == CompatCatalogLayout.GRID) catalogGridState.canScrollBackward
                    else catalogListState.canScrollBackward
                },
                canScrollForward = {
                    if (catalogLayout == CompatCatalogLayout.GRID) catalogGridState.canScrollForward
                    else catalogListState.canScrollForward
                },
                onRefresh = { refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (catalogLayout == CompatCatalogLayout.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(catalogColumns),
                        state = catalogGridState,
                        modifier = Modifier.fillMaxSize().testTag("compat-catalog-grid"),
                        contentPadding = PaddingValues(start = 5.7.dp, end = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        gridItemsIndexed(
                            displayedItems,
                            key = { index, _ -> displayedItemKeys[index] }
                        ) { _, item ->
                            CompatCatalogGridItem(
                                item = item,
                                imageRetryGeneration = catalogRefreshGeneration,
                                thumbnailRequestSizePx = gridThumbnailRequestSizePx,
                                lowQuality = catalogLowQuality,
                                replyIndicator = replyIndicator(item),
                                isOld = catalogItemStates[item.id]?.isOld == true,
                                droppedClass = droppedClassByThreadId[item.id],
                                titleLength = gridTitleLength,
                                fontSize = gridFontSize,
                                cropThumbnail = cropCatalogThumbnails,
                                showReplyCount = showReplyCountOnThumbnail,
                                privacyAlpha = if (catalogPrivacyEnabled) {
                                    compatPrivacyContentAlpha(privacyAlpha)
                                } else 1f,
                                matchedWatchWords = compatCatalogMatchedWords(
                                    item = item,
                                    watchWords = watchWords,
                                    rules = catalogExtractRules
                                ),
                                onClick = { onOpenThread(item) },
                                onLongClick = { handleCatalogLongTap(item) }
                            )
                        }
                    }
                    CompatFastScrollbar(
                        enabled = catalogFastScrollEnabled,
                        totalItems = displayedItems.size,
                        gridState = catalogGridState
                    )
                } else {
                    LazyColumn(
                        state = catalogListState,
                        modifier = Modifier.fillMaxSize().testTag("compat-catalog-list")
                    ) {
                        itemsIndexed(
                            displayedItems,
                            key = { index, _ -> displayedItemKeys[index] }
                        ) { _, item ->
                            CompatCatalogListItem(
                                item = item,
                                imageRetryGeneration = catalogRefreshGeneration,
                                thumbnailRequestSizePx = listThumbnailRequestSizePx,
                                lowQuality = catalogLowQuality,
                                replyIndicator = replyIndicator(item),
                                isOld = catalogItemStates[item.id]?.isOld == true,
                                droppedClass = droppedClassByThreadId[item.id],
                                titleLength = listTitleLength,
                                fontSize = listFontSize,
                                cropThumbnail = cropCatalogThumbnails,
                                rowHeight = listRowHeight,
                                thumbnailSize = listThumbnailSize,
                                privacyAlpha = if (catalogPrivacyEnabled) {
                                    compatPrivacyContentAlpha(privacyAlpha)
                                } else 1f,
                                matchedWatchWords = compatCatalogMatchedWords(
                                    item = item,
                                    watchWords = watchWords,
                                    rules = catalogExtractRules
                                ),
                                onClick = { onOpenThread(item) },
                                onLongClick = { handleCatalogLongTap(item) }
                            )
                        }
                    }
                    CompatFastScrollbar(
                        enabled = catalogFastScrollEnabled,
                        totalItems = displayedItems.size,
                        listState = catalogListState
                    )
                }
            }
            if (selectorOpen && selectorPresentation == SelectorPresentation.OVER) {
                CompatTabSelector(
                    tabs = tabs,
                    currentTabKey = activeTabKey,
                    threadContext = false,
                    onSelect = onSelectTab,
                    onClose = onCloseTab,
                    onCheckUpdates = onCheckUpdates,
                    onReload = { scope.launch { refresh() } },
                    longTapAction = selectorLongTapAction,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            if (showLoadingIndicator || layoutSwitchLoading) CompatLoadingIndicator(
                style = designLoading,
                modifier = Modifier.align(Alignment.Center).testTag("compat-catalog-blocking-loading"),
                size = 50.dp
            )
            catalogImageNgProgress?.takeIf { it.second > 0 }?.let { progress ->
                Text(
                    CompatImagePhash.progressLabel(progress.first, progress.second),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("compat-catalog-image-ng-progress"),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
            error?.let { Text(it, modifier = Modifier.align(Alignment.Center).background(Color(0xFF646464), RoundedCornerShape(22.dp)).padding(12.dp), color = Color.White) }
            transientMessage?.let {
                Text(it, modifier = Modifier.align(Alignment.Center).background(Color(0xFF646464), RoundedCornerShape(22.dp)).padding(12.dp), color = Color.White)
            }
            if (pendingDroppedUndoIds.isNotEmpty()) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    action = {
                        TextButton(
                            onClick = {
                                suppressedDroppedThreadIds += pendingDroppedUndoIds
                                pendingDroppedUndoIds = emptySet()
                            }
                        ) {
                            Text(
                                "元に戻す",
                                color = LocalCompatibilityPalette.current.closedThreadUndoAction,
                                maxLines = 1
                            )
                        }
                    }
                ) {
                    Text("落ちたスレを末尾に${pendingDroppedUndoIds.size}件追加しました")
                }
            }
        }
    }

    if (sortDialogOpen) {
        CompatCatalogSortDialog(
            selected = preference.sort,
            onDismiss = { sortDialogOpen = false },
            onSelected = { selected ->
                sortDialogOpen = false
                persistPreference(preference.copy(sort = selected), reload = preferenceLoaded)
            }
        )
    }
    otherMenuRoute?.let { route ->
        val menuItems = compatCatalogOtherMenu(
            route = route,
            ngEnabled = catalogNgEnabled,
            replyPriorityEnabled = preference.replyPriorityEnabled,
            showNonPriority = preference.showNonPriority,
            canUndoClose = canUndoClose,
            cacheEnabled = preferences[COMPAT_CACHE_ENABLED_KEY] == "ON",
            activeToolbarKeys = toolbarItems.filter(CompatToolbarItem::active).mapTo(mutableSetOf()) { it.key }
        )
        CompatHierarchicalOtherMenuDialog(
            route = route,
            items = menuItems,
            onDismiss = { otherMenuRoute = null },
            onItem = { menuItem ->
                menuItem.childRoute?.let { child ->
                    otherMenuRoute = child
                    return@CompatHierarchicalOtherMenuDialog
                }
                otherMenuRoute = null
                when (menuItem.key) {
                    "watch_words" -> {
                        managedNgKind = CompatNgKind.CATALOG_EXTRACT
                    }
                    "watcher" -> onOpenWatcher()
                    "cache" -> cacheSearchOpen = true
                    "archive" -> archiveSearchOpen = true
                    // sample/1.apk opens the board catalog endpoint, not the
                    // board directory.  In particular, a plain board URL
                    // would leave the user on the site's default page and
                    // loses the reference app's `mode=cat` behavior.
                    "external" -> runCatching {
                        BoardUrlResolver.resolveCatalogUrl(board.originalUrl, CatalogMode.Catalog)
                    }.onSuccess(openUrl).onFailure { openUrl(board.originalUrl) }
                    "display" -> {
                        layoutSwitchLoading = true
                        layoutSwitchGeneration++
                        val nextLayout = if (catalogLayout == CompatCatalogLayout.GRID) {
                            CompatCatalogLayout.LIST
                        } else {
                            CompatCatalogLayout.GRID
                        }
                        val nextPreference = preference.copy(layout = nextLayout)
                        preference = nextPreference
                        launchCatalogStoreSafely("catalog layout persistence") {
                            store.savePreference(
                                COMPAT_CATALOG_VIEW_MODE_STORAGE_KEY,
                                compatCatalogLayoutStorageValue(nextLayout)
                            )
                            // Keep the former board-local value as a downgrade/migration fallback.
                            store.saveCatalogPreference(nextPreference)
                        }
                    }
                    "top" -> scope.launch {
                        if (catalogLayout == CompatCatalogLayout.GRID) catalogGridState.scrollToItem(0)
                        else catalogListState.scrollToItem(0)
                    }
                    "privacy" -> launchCatalogStoreSafely("catalog privacy persistence") {
                        store.savePreference(COMPAT_COMMON_PRIVACY_STORAGE_KEY, if (catalogPrivacyEnabled) "OFF" else "ON")
                    }
                    "bypass" -> {
                        val enabled = preferences[COMPAT_CACHE_ENABLED_KEY] == "ON"
                        val toggle = nextCompatCacheToggle(enabled)
                        launchCatalogStoreSafely("catalog cache preference persistence") {
                            store.savePreference(COMPAT_CACHE_ENABLED_KEY, toggle.storedValue)
                            transientMessage = toggle.message
                        }
                    }
                    "check" -> {
                        transientMessage = "開いているスレの更新を確認しています"
                        onCheckUpdates()
                    }
                    "undo" -> onUndoClose()
                    "ng_ignore" -> managedNgKind = CompatNgKind.CATALOG_IGNORE
                    "ng_refuse" -> managedNgKind = CompatNgKind.CATALOG_REFUSE
                    "ng_image" -> managedNgKind = CompatNgKind.CATALOG_IMAGE
                    "ng_image_phash" -> managedNgKind = CompatNgKind.CATALOG_IMAGE_PHASH
                    "extract" -> managedNgKind = CompatNgKind.CATALOG_EXTRACT
                    "ng_toggle" -> launchCatalogStoreSafely("catalog NG preference persistence") {
                        store.savePreference("compat.catalog.NG機能", if (catalogNgEnabled) "OFF" else "ON")
                    }
                    "reply_priority_toggle" -> persistPreference(
                        preference.copy(replyPriorityEnabled = !preference.replyPriorityEnabled)
                    )
                    "non_priority_toggle" -> persistPreference(
                        preference.copy(showNonPriority = !preference.showNonPriority)
                    )
                }
            }
        )
    }
    managedNgKind?.let { kind ->
        val referenceKind = kind.takeIf {
            it == CompatNgKind.CATALOG_EXTRACT ||
                it == CompatNgKind.CATALOG_IGNORE ||
                it == CompatNgKind.CATALOG_REFUSE
        }
        val imageReferenceSource = CompatImageNgSource.CATALOG.takeIf {
            kind in compatImageNgKinds(CompatImageNgSource.CATALOG)
        }
        val managedRules = when {
            referenceKind != null -> compatCatalogManagementRules(ngRules, board.key, kind)
            imageReferenceSource != null -> compatImageNgManagementRules(
                ngRules,
                board.key,
                imageReferenceSource
            )
            else -> catalogRules.filter { it.kind == kind }
        }
        val managedKinds = compatCatalogManagementKinds(kind)
        CompatNgRuleManagementDialog(
            title = when (kind) {
                CompatNgKind.CATALOG_THREAD -> "NGスレッド"
                CompatNgKind.CATALOG_WORD -> "NGワード"
                CompatNgKind.CATALOG_EXTRACT -> "監視ワード"
                CompatNgKind.CATALOG_IGNORE -> "ＮＧワード"
                CompatNgKind.CATALOG_REFUSE -> "ＮＧスレッド"
                CompatNgKind.CATALOG_IMAGE -> "NG画像"
                CompatNgKind.CATALOG_IMAGE_PHASH -> "NG画像(pHash)"
                else -> "NG管理"
            },
            rules = managedRules,
            imageReferenceBoardName = board.name.takeIf { imageReferenceSource != null },
            phashThreshold = catalogImageNgPhashThreshold.takeIf {
                kind == CompatNgKind.CATALOG_IMAGE || kind == CompatNgKind.CATALOG_IMAGE_PHASH
            },
            onPhashThresholdChange = { value ->
                launchCatalogStoreSafely("catalog image threshold persistence") {
                    store.savePreference(
                        compatPreferenceStorageKey("thread", "threadImageNgPhashThreshold"),
                        value.toString()
                    )
                }
            },
            onDelete = { rule ->
                launchCatalogStoreSafely("catalog NG deletion") { store.deleteNgRule(rule.id) }
            },
            onDeleteAll = { rulesToDelete ->
                launchCatalogStoreSafely("catalog NG bulk deletion") {
                    val deleteTargets = if (referenceKind != null) {
                        ngRules.filter { it.kind in managedKinds }
                    } else {
                        rulesToDelete
                    }
                    store.deleteNgRules(deleteTargets.map(CompatNgRule::id))
                }
            },
            onEdit = { rule, value, allBoards, memo ->
                val normalized = if (imageReferenceSource != null) {
                    rule.normalizedValue
                } else {
                    value.normalizeCompatNgValue()
                }
                if (normalized.isBlank()) {
                    transientMessage = "NGに登録する値を入力してください"
                } else {
                    launchCatalogStoreSafely("catalog NG edit", "NGの更新に失敗しました") {
                        val scopeKey = if (allBoards) "*" else board.key
                        store.deleteNgRule(rule.id)
                        val updated = store.upsertNgRule(
                            rule.copy(
                                id = compatNgRuleId(rule.kind, scopeKey, normalized),
                                scopeKey = scopeKey,
                                normalizedValue = normalized,
                                memo = when (kind) {
                                    CompatNgKind.CATALOG_EXTRACT,
                                    CompatNgKind.CATALOG_IGNORE -> value.trim().take(MAX_COMPAT_NG_MEMO_CHARS)
                                    else -> memo
                                },
                                createdAtEpochMillis = if (imageReferenceSource != null) {
                                    rule.createdAtEpochMillis
                                } else {
                                    Clock.System.now().toEpochMilliseconds()
                                }
                            )
                        )
                        transientMessage = if (updated) "NGを更新しました" else "NGを更新できませんでした"
                    }
                }
            },
            addScopeLabel = "全板に適用",
            referenceKind = referenceKind,
            onAdd = if (
                kind == CompatNgKind.CATALOG_REFUSE ||
                kind == CompatNgKind.CATALOG_IMAGE ||
                kind == CompatNgKind.CATALOG_IMAGE_PHASH
            ) null else { value, allBoards ->
                val normalized = value.normalizeCompatNgValue()
                if (normalized.isBlank()) {
                    transientMessage = "NGに登録する値を入力してください"
                } else {
                    launchCatalogStoreSafely("catalog NG add", "NGの登録に失敗しました") {
                        val scopeKey = if (allBoards) "*" else board.key
                        val added = store.upsertNgRule(
                            CompatNgRule(
                                id = compatNgRuleId(kind, scopeKey, normalized),
                                kind = kind,
                                scopeKey = scopeKey,
                                normalizedValue = normalized,
                                memo = if (
                                    kind == CompatNgKind.CATALOG_EXTRACT ||
                                    kind == CompatNgKind.CATALOG_IGNORE
                                ) value.trim().take(MAX_COMPAT_NG_MEMO_CHARS) else "",
                                createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                            )
                        )
                        transientMessage = if (added) {
                            "NGに登録しました"
                        } else if (referenceKind != null) {
                            "これ以上登録できません！"
                        } else {
                            "NGを登録できませんでした"
                        }
                    }
                }
            },
            onDismiss = { managedNgKind = null }
        )
    }
    if (watchWordsDialogOpen) {
        CompatWatcherManager(
            store = store, repository = repository,
            onDismiss = { watchWordsDialogOpen = false },
            onResultsChanged = { launchCatalogStoreSafely("watch refresh", "巡回後の更新に失敗しました") { refresh(preference.sort) } }
        )
    }
    if (archiveSearchOpen) {
        CompatArchiveSearchDialog(
            httpClient = httpClient,
            archiveScope = extractArchiveSearchScope(board.originalUrl),
            archiveBaseUrl = archiveBaseUrl,
            localHistory = localHistory,
            initialSearchHistory = archiveSearchHistory,
            noticeHidden = archiveSearchNoticeHidden,
            onSearchHistoryChanged = { values ->
                launchCatalogStoreSafely("archive search history persistence") {
                    store.savePreference(
                        COMPAT_ARCHIVE_SEARCH_HISTORY_KEY,
                        serializeCompatArchiveSearchHistory(values)
                    )
                }
            },
            onNoticeHidden = {
                launchCatalogStoreSafely("archive search notice persistence") {
                    store.savePreference(COMPAT_ARCHIVE_SEARCH_NOTICE_HIDDEN_KEY, "ON")
                }
            },
            onDismiss = { archiveSearchOpen = false },
            onSelected = { item ->
                archiveSearchOpen = false
                val sourceUrl = buildCompatArchiveSourceThreadUrl(item)
                if (sourceUrl == null) {
                    transientMessage = "検索結果URLを解釈できませんでした"
                } else {
                    onOpenThread(
                        CatalogItem(
                            id = item.threadId,
                            threadUrl = sourceUrl,
                            title = item.title,
                            thumbnailUrl = item.thumbUrl,
                            fullImageUrl = item.thumbUrl,
                            replyCount = item.replyCount
                        )
                    )
                }
            }
        )
    }
    if (cacheSearchOpen) {
        CompatCatalogCacheSearchDialog(
            httpClient = httpClient,
            store = store,
            boardKey = board.key,
            boardUrl = board.originalUrl,
            localHistory = localHistory,
            initialSearchHistory = normalizeCompatCacheSearchHistory(
                preferences[COMPAT_CACHE_SEARCH_HISTORY_KEY].orEmpty()
            ),
            onSearchHistoryChanged = { values ->
                launchCatalogStoreSafely("cache search history persistence") {
                    store.savePreference(COMPAT_CACHE_SEARCH_HISTORY_KEY, values.joinToString("\n"))
                }
            },
            onDismiss = { cacheSearchOpen = false },
            onOpenThread = { item ->
                cacheSearchOpen = false
                onOpenThread(item)
            }
        )
    }
    if (boardSelectorOpen) {
        CompatBottomPopup(
            alignment = Alignment.BottomCenter,
            onDismiss = { boardSelectorOpen = false }
        ) {
            boards.forEach { candidate ->
                TextButton(
                    onClick = {
                        boardSelectorOpen = false
                        if (candidate.key != board.key) onBoardSelected(candidate)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        candidate.name,
                        modifier = Modifier.fillMaxWidth(),
                        color = compatibilityPopupContent(LocalCompatibilityPalette.current)
                    )
                }
            }
        }
    }
    contextItem?.let { item ->
        val mediaAvailable = item.fullImageUrl != null || item.thumbnailUrl != null
        val choices = compatCatalogContextLabels()
        CompatLegacyChoiceDialog(
            onDismiss = { contextItem = null },
            choices = choices,
            enabled = { choice ->
                when (choice) {
                    "NG画像に登録" -> mediaAvailable
                    "delを送信する", "delとNGスレッドに登録",
                    "delとNGスレッドとNGワードに登録" -> repository != null
                    else -> true
                }
            },
            testTag = "compat-catalog-context-menu",
            onChoice = { choice ->
                when (choice) {
                    "NGスレッドに登録" -> addNgRules(item, thread = true, word = false, image = false)
                    "NGスレッドとNGワードに登録" -> addNgRules(item, thread = true, word = true, image = false)
                    "NG画像に登録" -> catalogImageNgRegistrationItem = item
                    "delを送信する" -> pendingDelete = CompatCatalogDeleteRequest(item)
                    "delとNGスレッドに登録" ->
                        pendingDelete = CompatCatalogDeleteRequest(item, addThreadNg = true)
                    "delとNGスレッドとNGワードに登録" ->
                        pendingDelete = CompatCatalogDeleteRequest(item, addThreadNg = true, addWordNg = true)
                    "タブに追加する" -> {
                        onAddTab(item)
                        transientMessage = "タブに追加しました"
                    }
                }
            }
        )
    }
    catalogImageNgRegistrationItem?.let { item ->
        CompatImageNgRegistrationDialog(
            imageUrl = item.fullImageUrl ?: item.thumbnailUrl.orEmpty(),
            initialMemo = item.title.orEmpty().take(MAX_COMPAT_NG_MEMO_CHARS),
            onDismiss = { catalogImageNgRegistrationItem = null },
            onRegister = { memo, localOnly ->
                catalogImageNgRegistrationItem = null
                addCatalogImagePhashRule(item, memo, localOnly)
            }
        )
    }
    pendingDelete?.let { request ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text("削除依頼 ${request.item.title.orEmpty()}") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        val activeRepository = repository ?: return@TextButton
                        deleting = true
                        scope.launch {
                            runSuspendCatchingPreservingCancellation {
                                activeRepository.requestDeletion(board.originalUrl, request.item.id, request.item.id, "110")
                            }.onSuccess {
                                if (request.addThreadNg || request.addWordNg) {
                                    addNgRules(request.item, request.addThreadNg, request.addWordNg, image = false)
                                }
                                transientMessage = "delを送信しました"
                                pendingDelete = null
                            }.onFailure { throwable ->
                                transientMessage = throwable.toCompatUserMessage("delを送信できませんでした")
                            }
                            deleting = false
                        }
                    }
                ) { Text(if (deleting) "送信中" else "送信する") }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { pendingDelete = null }) { Text("キャンセル") } }
        )
    }
    catalogRuleRequest?.let { request ->
        CompatCatalogRuleScopeDialog(
            kind = request.kind,
            onDismiss = { catalogRuleRequest = null },
            onSelect = { allBoards ->
                catalogRuleRequest = null
                addCatalogRule(request.item, request.kind, allBoards)
            }
        )
    }

}

private data class CompatCatalogDeleteRequest(
    val item: CatalogItem,
    val addThreadNg: Boolean = false,
    val addWordNg: Boolean = false
)

private data class CompatCatalogRuleRequest(
    val item: CatalogItem,
    val kind: CompatNgKind
)

@Composable
private fun CompatThreadScreen(
    tab: CompatTab,
    tabs: List<CompatTab>,
    isDrawerOpen: Boolean = false,
    onDrawerGestureAvailabilityChanged: (Boolean) -> Unit,
    repository: BoardRepository?,
    httpClient: HttpClient?,
    cookieRepository: CookieRepository?,
    toolbarRefreshToken: Long = 0L,
    initialToolbarItems: List<CompatToolbarItem>? = null,
    threadRefreshToken: Long = 0L,
    archiveBaseUrl: String?,
    archiveSearchHistory: List<String>,
    archiveSearchNoticeHidden: Boolean,
    localHistory: List<CompatHistoryEntry>,
    fileSystem: FileSystem?,
    longRunningScope: CoroutineScope,
    store: CompatibilityStore,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    selectorOpen: Boolean,
    selectorPresentation: SelectorPresentation,
    platformAiCommand: FutachaAiCommand? = null,
    onPlatformAiCommandConsumed: (FutachaAiCommand) -> Unit = {},
    scrollToBottomRequest: Long?,
    onToggleSelector: () -> Unit,
    onSelectTab: (CompatTab) -> Unit,
    onPersistScrollAnchor: (String, ScrollAnchor) -> Unit,
    onScrollAnchorObserved: (String, ScrollAnchor) -> Unit,
    onCloseTab: (CompatTab) -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDisplayOptions: () -> Unit,
    onOpenHelp: () -> Unit,
    onToolbarEdit: () -> Unit,
    onOpenPost: () -> Unit,
    onOpenPostWithText: (String, Boolean) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenViewer: (Int, String?) -> Unit,
    onOpenSingleViewer: (String, String, Int) -> Unit,
    onOpenInlineUrl: (String) -> Boolean,
    canUndoClose: Boolean,
    onUndoClose: () -> Unit,
    onArchiveReportEnqueued: (Int) -> Unit,
    onOpenArchiveItem: (ArchiveSearchItem) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val reviewComplianceEnabled = LocalIosReviewCompliance.current.isEnabled
    // Do not seed LazyListState from the persisted anchor while the thread is
    // still empty. Compose clamps that provisional index to zero when the
    // first snapshot arrives, which made restoration intermittent. The
    // restore effect below waits until visiblePosts exists and then applies a
    // stable post-number based anchor.
    val listState = rememberLazyListState()
    // ThreadViewPager follows the finger and commits to the adjacent page only
    // after a horizontal threshold. Keep this gesture on the pager surface so
    // vertical LazyColumn scrolling and post actions remain independent.
    val pagerOffset = remember(tab.key) { Animatable(0f) }
    var pagerDragUpdateJob by remember(tab.key) { mutableStateOf<Job?>(null) }
    var pagerWidthPx by remember(tab.key) { mutableStateOf(0) }
    val tabIndex = tabs.indexOfFirst { it.key == tab.key }
    val previousTab = tabs.getOrNull(tabIndex - 1)
    val nextTab = tabs.getOrNull(tabIndex + 1)
    // Observe only the swipe direction: reading the animated offset here re-ran
    // this whole screen body on every drag and settle frame.
    val pagerShowsNext by remember(pagerOffset) { derivedStateOf { pagerOffset.value < 0f } }
    val pagerNeighbor = if (pagerShowsNext) nextTab else previousTab
    // Keep a deliberate dead zone before the pager captures a gesture. The
    // reference app does not change tabs from a tiny/diagonal reading drag.
    val pagerTouchSlopPx = with(LocalDensity.current) { 16.dp.toPx() }
    var pagerNeighborSnapshot by remember(tab.key, pagerNeighbor?.key) {
        mutableStateOf<CompatThreadSnapshot?>(null)
    }
    LaunchedEffect(pagerNeighbor?.key) {
        try {
            pagerNeighborSnapshot = pagerNeighbor?.let { neighbor ->
                store.loadThreadSnapshot(neighbor.key)?.let { snapshot ->
                    withContext(AppDispatchers.parsing) { normalizeCompatThreadSnapshot(snapshot) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatibilityThread", "Failed to load neighboring tab", failure)
            pagerNeighborSnapshot = null
        }
    }
    val threadFontSize = preferences.compatPreferenceValue("thread", "threadFontSize", "フォントサイズ")
        ?.toIntOrNull()?.coerceIn(10, 30) ?: 14
    val threadThumbnailSize = preferences.compatPreferenceValue("thread", "threadThumbSize", "サムネイルサイズ")
        ?.toIntOrNull()?.coerceIn(150, 1200) ?: 250
    val threadUpsThumbnailSize = preferences.compatPreferenceValue(
        "thread", "threadUpsThumbSize", "あぷ小サムネイルサイズ"
    )?.toIntOrNull()?.coerceIn(150, 1200) ?: threadThumbnailSize
    val threadUpsThumbMethod = preferences.compatPreferenceValue(
        "thread", "threadUpsThumbMethod", "あぷ小のサムネイルの読み込み", "あぷ小の読み込み"
    ) ?: COMPAT_DEFAULT_APU_SMALL_THUMB_METHOD
    val showDeletedPosts = preferences.compatPreferenceValue("thread", "threadAdminDeleteShow", "削除されたレスを表示") == "ON"
    val threadNgEnabled = preferences.compatPreferenceValue("thread", "threadNg", "NG機能") != "OFF"
    val threadPrivacyEnabled = preferences.compatPrivacyEnabled()
    val saidaneExtractThreshold = preferences.compatPreferenceValue("thread", "threadExtractSoudaneNum", "そうだねが多いレス")
        ?.toIntOrNull() ?: 3
    val quoteExtractThreshold = preferences.compatPreferenceValue("thread", "threadExtractQuoteNum", "返信が多いレス")
        ?.toIntOrNull() ?: 3
    val hideDefaultNameAndSubject = preferences.compatPreferenceValue(
        "thread", "threadHideDefaultNameAndSubject", "既定名・題名を非表示"
    ) == "ON"
    val persistedBoardDefaultText = remember(preferences, tab.boardKey) {
        CompatBoardDefaultText(
            defaultSubject = preferences[compatBoardDefaultSubjectPreferenceKey(tab.boardKey)]
                ?.takeIf(String::isNotBlank) ?: "無題",
            defaultName = preferences[compatBoardDefaultNamePreferenceKey(tab.boardKey)]
                ?.takeIf(String::isNotBlank) ?: "名無し"
        )
    }
    var boardDefaultText by remember(tab.boardKey) { mutableStateOf(persistedBoardDefaultText) }
    LaunchedEffect(persistedBoardDefaultText) {
        boardDefaultText = persistedBoardDefaultText
    }
    val simpleQuoteCount = preferences.compatPreferenceValue(
        "thread", "threadHeaderQuoteSimple", "返信数を簡易表示"
    ) == "ON"
    val saidaneDisplayMode = preferences.compatPreferenceValue(
        "thread", "threadHeaderSoudaneDisplay", "そうだねの表示方法"
    )?.let { compatPreferenceDisplayValue("threadHeaderSoudaneDisplay", it) } ?: "通常"
    val threadPullRefreshEnabled = preferences.compatPreferenceValue(
        "thread", "threadPullToRefresh", "スクロール更新"
    ) != "OFF"
    val designLoading = preferences.compatPreferenceValue(
        "design", "designLoading", "ローディング"
    ) ?: "デフォルト"
    val threadVolumeKeyAction = preferences.compatPreferenceValue(
        "control", "controlThreadVolumeKey", "スレッドのボリュームキー"
    )?.let { compatPreferenceDisplayValue("controlThreadVolumeKey", it) } ?: "何もしない"
    val threadPrivacyAlpha = parseCompatPercent(
        preferences.compatPreferenceValue("thread", "commonPrivacyAlpha", "プライバシー透明度")
    )
    val openDrawerOnPostTap = preferences.compatPreferenceValue(
        "control", "controlTouchOpenDrawer", "レスをタッチしてドロワー"
    ) == "ON"
    val touchScrollEnabled = preferences.compatPreferenceValue(
        "control", "controlTouchScroll", "タッチスクロール"
    ) == "ON"
    val closeThreadReturnsToPrevious = preferences.compatPreferenceValue(
        "control", "controlThreadCloseBack", "スレッドを閉じたら前画面に戻る"
    ) == "ON"
    val selectorLongTapAction = preferences.compatPreferenceValue(
        "control", "controlTabSelectorLongTap", "タブ一覧のロングタップ"
    )?.let { compatPreferenceDisplayValue("controlTabSelectorLongTap", it) } ?: "選択メニュー"
    val autoScrollPixel = preferences.compatPreferenceValue("thread", "autoScrollPixel", "自動スクロール量")
        ?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(1, 30) ?: 5
    val autoScrollSpeedMillis = preferences.compatPreferenceValue("thread", "autoScrollSpeed", "自動スクロール速度")
        ?.filter(Char::isDigit)?.toLongOrNull()?.coerceIn(10L, 200L) ?: 50L
    val manualSaveLocation = parseCompatSaveLocation(
        preferences.compatPreferenceValue("storage", "dummyDownloadDir", "保存ファイルの保存先")
    )
    val ownPostNos = remember(preferences, tab.key) {
        val prefix = "compat.ownpost.${tab.key}."
        preferences.asSequence()
            .filter { (key, value) -> key.startsWith(prefix) && value == "1" }
            .map { (key, _) -> key.removePrefix(prefix) }
            .toSet()
    }
    val snapshotState = remember(tab.key) { mutableStateOf<CompatThreadSnapshot?>(null) }
    var snapshot by snapshotState
    val deletionSummary = remember(snapshot) { snapshot?.let(::compatThreadDeletionSummary) }
    val undoRefreshSnapshotState = remember(tab.key) { mutableStateOf<CompatThreadSnapshot?>(null) }
    var undoRefreshSnapshot by undoRefreshSnapshotState
    val newReplyNoticeState = remember(tab.key) { mutableStateOf<CompatNewReplyNotice?>(null) }
    var newReplyNotice by newReplyNoticeState
    val manualRefreshNoticeState = remember(tab.key) {
        mutableStateOf<CompatManualRefreshNotice?>(null)
    }
    var manualRefreshNotice by manualRefreshNoticeState
    var loading by remember(tab.key) { mutableStateOf(true) }
    val loadMutex = remember(tab.key) { Mutex() }
    val errorState = remember(tab.key) { mutableStateOf<String?>(null) }
    var error by errorState
    fun launchThreadStoreSafely(
        operation: String,
        userMessage: String = COMPAT_THREAD_STORE_FAILURE_MESSAGE,
        block: suspend () -> Unit
    ) {
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Logger.e("CompatibilityThread", "$operation failed", failure)
                error = "$userMessage: ${failure.message.orEmpty()}"
            }
        }
    }
    val searchActiveState = rememberSaveable(tab.key) { mutableStateOf(false) }
    var searchActive by searchActiveState
    var searchQuery by rememberSaveable(tab.key) { mutableStateOf("") }
    var searchMatchIndex by rememberSaveable(tab.key) { mutableStateOf(0) }
    val searchBackDismissStagesState = rememberSaveable(tab.key) { mutableStateOf(2) }
    var searchBackDismissStages by searchBackDismissStagesState
    // These are observations of the current IME window, not user state.  Do
    // not restore them with the search query: after state restoration the old
    // `true` value was mistaken for a real keyboard-dismiss transition and
    // consumed one of the APK-compatible Back stages before the user pressed
    // Back at all.
    var searchImeWasVisible by remember(tab.key) { mutableStateOf(false) }
    var searchFocusLostWhileImeVisible by remember(tab.key) { mutableStateOf(false) }
    var searchFieldFocused by remember(tab.key) { mutableStateOf(false) }
    val quoteStackState = remember(tab.key) { mutableStateOf<List<CompatQuoteFrame>>(emptyList()) }
    var quoteStack by quoteStackState
    // The old APK opens a full-width PopupWindow for a tapped >> reference.  Keep
    // this separate from the extraction stack: a quote preview is transient and
    // must not become a new navigation level or alter the tab history.
    val replyPopupPostsState = remember(tab.key) { mutableStateOf<List<CompatPostSnapshot>>(emptyList()) }
    var replyPopupPosts by replyPopupPostsState
    val replyPopupAnchorYState = remember(tab.key) { mutableStateOf(0) }
    var replyPopupAnchorY by replyPopupAnchorYState
    // LazyListInfo offsets are viewport-relative; PopupWindow coordinates are
    // window-relative in the reference APK. Keep the Scaffold content inset.
    val threadContentTopPxState = remember(tab.key) { mutableStateOf(0) }
    var threadContentTopPx by threadContentTopPxState
    val contextPostState = remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var contextPost by contextPostState
    val reportPostState = remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var reportPost by reportPostState
    val delPostState = remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var delPost by delPostState
    val selectionStateHolder = remember(tab.key) { mutableStateOf<CompatPostSelectionState?>(null) }
    var selectionState by selectionStateHolder
    val ngPostState = remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var ngPost by ngPostState
    val deletePostState = remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var deletePost by deletePostState
    val deletePasswordState = remember(tab.key) { mutableStateOf("") }
    var deletePassword by deletePasswordState
    val deleteImageOnlyState = remember(tab.key) { mutableStateOf(false) }
    var deleteImageOnly by deleteImageOnlyState
    val extractionMenuOpenState = remember(tab.key) { mutableStateOf(false) }
    var extractionMenuOpen by extractionMenuOpenState
    val extractionKeywordOpenState = remember(tab.key) { mutableStateOf(false) }
    var extractionKeywordOpen by extractionKeywordOpenState
    val extractionKeywordState = remember(tab.key) { mutableStateOf("") }
    var extractionKeyword by extractionKeywordState
    var headerExtractionPost by remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    val mediaContextPostState = remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var mediaContextPost by mediaContextPostState
    val imageNgRegistrationState = remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var imageNgRegistration by imageNgRegistrationState
    val thumbnailReloadTokensState = remember(tab.key) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var thumbnailReloadTokens by thumbnailReloadTokensState
    val ascii2dRegisterPostState = remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var ascii2dRegisterPost by ascii2dRegisterPostState
    val ascii2dRegistrationUrlState = remember(tab.key) { mutableStateOf("") }
    var ascii2dRegistrationUrl by ascii2dRegistrationUrlState
    var reverseSearchResult by remember(tab.key) { mutableStateOf<CompatImageSearchResult?>(null) }
    val archiveSearchOpenState = remember(tab.key) { mutableStateOf(false) }
    var archiveSearchOpen by archiveSearchOpenState
    val toolbarItems = rememberCompatThreadToolbarItems(
        tab.key, initialToolbarItems, toolbarRefreshToken, store
    )
    val otherMenuRouteState = remember { mutableStateOf<CompatOtherMenuRoute?>(null) }
    var otherMenuRoute by otherMenuRouteState
    val scrollDialogOpenState = remember(tab.key) { mutableStateOf(false) }
    var scrollDialogOpen by scrollDialogOpenState
    val managedNgKindsState = remember { mutableStateOf<Set<CompatNgKind>?>(null) }
    var managedNgKinds by managedNgKindsState
    var savingPage by remember(tab.key) { mutableStateOf(false) }
    var pageSaveJob by remember(tab.key) { mutableStateOf<Job?>(null) }
    var pageSaveCancelRequested by remember(tab.key) { mutableStateOf(false) }
    val auxiliaryPageSaveProgressFlow = remember(tab.key) { kotlinx.coroutines.flow.MutableStateFlow<SaveProgress?>(null) }
    val auxiliaryPageSaveProgress by auxiliaryPageSaveProgressFlow.collectAsState()
    var pageSavePartialSavedCount by remember(tab.key) { mutableIntStateOf(0) }
    val readingAloudState = remember(tab.key) { mutableStateOf(false) }
    var readingAloud by readingAloudState
    var readAloudDialogOpen by remember(tab.key) { mutableStateOf(false) }
    var readAloudDisplayPost by remember(tab.key) { mutableStateOf<CompatPostSnapshot?>(null) }
    var readAloudStatus by remember(tab.key) { mutableStateOf<String?>(null) }
    var readAloudJob by remember(tab.key) { mutableStateOf<Job?>(null) }
    var readAloudCursor by remember(tab.key) { mutableIntStateOf(0) }
    var readAloudCharacterOffset by remember(tab.key) { mutableIntStateOf(0) }
    // A refresh can discover 404/410 while this composable still renders the
    // last local snapshot. Keep an immediate signal for the read-aloud loop;
    // waiting for the parent tab Flow to recompose would allow another poll.
    var readAloudThreadGone by remember(tab.key) { mutableStateOf(tab.isDead) }
    val autoScrollingState = remember(tab.key) { mutableStateOf(false) }
    var autoScrolling by autoScrollingState
    var autoScrollTouchGeneration by remember(tab.key) { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val openExternalUrl = rememberUrlLauncher()
    val openUrl: (String) -> Unit = { url ->
        if (!onOpenInlineUrl(url)) openExternalUrl(url)
    }
    val share = rememberCompatShareLauncher()
    val platformContext = LocalPlatformContext.current
    val compatWifiConnected = isCompatWifiConnected(platformContext)

    fun sendPostReport(post: CompatPostSnapshot) {
        scope.launch {
            if (repository == null) error = "通信機能を初期化できませんでした"
            else runSuspendCatchingPreservingCancellation {
                repository.requestDeletion(tab.originalUrl, tab.threadNo, post.postNo, "110")
            }
                .onSuccess {
                    error = if (reviewComplianceEnabled) "通報を送信しました" else "送信しました"
                }
                .onFailure {
                    error = it.toCompatUserMessage(
                        if (reviewComplianceEnabled) "通報を送信できませんでした"
                        else "削除依頼を送信できませんでした"
                    )
                }
        }
    }
    // Creating Android's TextToSpeech binds to the engine service.  The old
    // eager construction happened every time a thread screen was recreated,
    // even when the user never used read-aloud, and could stall the first
    // frames of a thread transition.  Keep it lazy and create it only when
    // the read-aloud command is actually invoked.
    var textSpeaker by remember(platformContext) { mutableStateOf<TextSpeaker?>(null) }
    val mediaSaver = remember(httpClient, fileSystem) {
        if (httpClient != null && fileSystem != null) SingleMediaSaveService(httpClient, fileSystem) else null
    }
    val pageSaver = remember(httpClient, fileSystem) {
        if (httpClient != null && fileSystem != null) ThreadSaveService(httpClient, fileSystem) else null
    }
    val pageSaveProgress = pageSaver?.saveProgress?.collectAsState()?.value
    val visiblePageSaveProgress = auxiliaryPageSaveProgress ?: pageSaveProgress
    val imageZipSaver = remember(httpClient, fileSystem) {
        if (httpClient != null && fileSystem != null) ImageZipSaveService(httpClient, fileSystem) else null
    }
    fun openSearchResult(url: String, title: String) {
        if (isCompatReverseSearchBrowserUrl(url)) {
            reverseSearchResult = CompatImageSearchResult.RemoteUrl(title, url)
        } else {
            error = "画像検索結果のURLが不正です"
        }
    }
    fun searchAscii2d(post: CompatPostSnapshot) {
        val mediaUrl = resolveCompatViewerMediaUrl(post)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl, allowGif = false)) {
            error = "GIF・WebM・MP4は検索できません"
            return
        }
        val client = httpClient
        if (client == null) {
            error = "二次元画像検索を初期化できませんでした"
            return
        }
        if (!isCompatAscii2dRegistered(preferences)) {
            ascii2dRegistrationUrl = preferences[COMPAT_ASCII2D_ENDPOINT_KEY]
                ?.trim()
                .orEmpty()
            ascii2dRegisterPost = post
            return
        }
        val endpoint = compatAscii2dEndpoint(preferences)
        if (!isValidCompatAscii2dEndpoint(endpoint)) {
            ascii2dRegistrationUrl = preferences[COMPAT_ASCII2D_ENDPOINT_KEY]
                ?.trim()
                .orEmpty()
            ascii2dRegisterPost = post
            return
        }
        error = "二次元画像検索中…"
        scope.launch {
            searchCompatAscii2d(client, endpoint, mediaUrl)
                .onSuccess { resultUrl ->
                    error = null
                    openSearchResult(resultUrl, "二次元画像類似検索")
                }
                .onFailure { failure ->
                    error = failure.toCompatUserMessage("二次元画像検索に失敗しました")
                }
        }
    }
    fun searchGoogle(
        post: CompatPostSnapshot,
        mode: CompatGoogleImageSearchMode = CompatGoogleImageSearchMode.LEGACY
    ) {
        val mediaUrl = resolveCompatViewerMediaUrl(post)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl)) {
            error = "WebM・MP4は検索できません"
            return
        }
        when (mode) {
            CompatGoogleImageSearchMode.LEGACY -> {
                buildCompatGoogleImageSearchUrl(mediaUrl)?.let {
                    openSearchResult(it, mode.label)
                }
                    ?: run { error = "検索する画像がありません" }
            }
            CompatGoogleImageSearchMode.GOOGLE_FILE -> {
                val client = httpClient
                if (client == null) {
                    error = "Google画像検索の通信機能を初期化できませんでした"
                    return
                }
                error = "Google画像検索に画像を送信中…"
                scope.launch {
                    searchCompatGoogleClassicFile(client, mediaUrl)
                        .onSuccess { resultUrl -> error = null; openSearchResult(resultUrl, mode.label) }
                        .onFailure { failure ->
                            error = failure.toCompatUserMessage("Google画像検索に失敗しました")
                        }
                }
            }
            CompatGoogleImageSearchMode.LENS_URL -> {
                buildCompatGoogleLensUrl(mediaUrl)?.let {
                    openSearchResult(it, mode.label)
                }
                    ?: run { error = "検索する画像がありません" }
            }
            CompatGoogleImageSearchMode.LENS_FILE -> {
                val client = httpClient
                if (client == null) {
                    error = "Google Lensの通信機能を初期化できませんでした"
                    return
                }
                error = "Google Lensに画像を送信中…"
                scope.launch {
                    searchCompatGoogleLensFile(client, mediaUrl)
                        .onSuccess { resultUrl ->
                            error = null
                            openSearchResult(resultUrl, mode.label)
                        }
                        .onFailure { failure ->
                            error = failure.toCompatUserMessage("Google Lens検索に失敗しました")
                        }
                }
            }
        }
    }
    fun searchFileTarget(post: CompatPostSnapshot, target: CompatImageSearchTarget) {
        val mediaUrl = resolveCompatViewerMediaUrl(post)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl)) {
            error = "WebM・MP4は検索できません"
            return
        }
        val client = httpClient
        if (client == null) {
            error = "画像検索の通信機能を初期化できませんでした"
            return
        }
        error = "${target.label}に画像を送信中…"
        scope.launch {
            searchCompatImageFileTarget(client, target, mediaUrl)
                .onSuccess { result -> error = null; reverseSearchResult = result }
                .onFailure { failure ->
                    error = failure.toCompatUserMessage("${target.label}に失敗しました")
                }
        }
    }
    DisposableEffect(textSpeaker) {
        onDispose {
            readAloudJob?.cancel()
            textSpeaker?.close()
        }
    }

    fun ensureTextSpeaker(): TextSpeaker = textSpeaker ?: createTextSpeaker(platformContext).also {
        textSpeaker = it
    }

    val withSaveDestination = rememberCompatManualSaveDestinationLauncher(store, preferences) {
        error = it.toCompatUserMessage("保存先の設定を記録できませんでした")
    }
    fun saveCompatPageNow(mode: String, selectedLocation: SaveLocation?) {
        if (savingPage) {
            error = "別の保存を実行中です"
            return
        }
        val currentSnapshot = snapshot
        if (currentSnapshot == null) {
            error = "保存する本文がありません"
            return
        }
        if (httpClient == null || fileSystem == null) {
            error = "保存機能を初期化できませんでした"
            return
        }
        pageSaver?.resetSaveProgress()
        pageSaveCancelRequested = false
        auxiliaryPageSaveProgressFlow.value = null
        pageSavePartialSavedCount = 0
        savingPage = true
        // A page save belongs to the compatibility workspace, not to this
        // one thread composition. Keep it alive when the user switches tabs,
        // closes the thread, or visits settings, matching the reference APK's
        // foreground-save behavior without leaking beyond CompatibilityApp.
        pageSaveJob = longRunningScope.launch {
            try {
                runCompatPageSaveWithCleanup(onFinished = {
                    savingPage = false
                    pageSaveJob = null
                    pageSaveCancelRequested = false
                    auxiliaryPageSaveProgressFlow.value = null
                }) {
                    runProtectedThreadSave(
                        tab.title,
                        if (mode == "save_images_zip" || mode == "save_images_folder") auxiliaryPageSaveProgressFlow
                        else pageSaver?.saveProgress ?: auxiliaryPageSaveProgressFlow
                    ) {
                        if (mode == "save_images_zip") {
                            val urls = compatBatchMediaUrls(currentSnapshot.posts)
                            auxiliaryPageSaveProgressFlow.value = SaveProgress(
                                phase = SavePhase.DOWNLOADING,
                                current = 0,
                                total = urls.size,
                                currentItem = "しばらくお待ち下さい"
                            )
                            error = imageZipSaver?.save(
                                mediaUrls = urls,
                                boardId = tab.boardKey,
                                threadId = tab.threadNo,
                                baseSaveLocation = selectedLocation,
                                baseDirectory = MANUAL_SAVE_DIRECTORY,
                                onProgress = { current, total, item, itemBytes, itemTotalBytes ->
                                    auxiliaryPageSaveProgressFlow.value = SaveProgress(
                                        phase = SavePhase.DOWNLOADING,
                                        current = current,
                                        total = total,
                                        currentItem = item,
                                        currentItemBytes = itemBytes,
                                        currentItemTotalBytes = itemTotalBytes
                                    )
                                }
                            )?.fold(
                                onSuccess = { saved ->
                                    "${saved.fileName} を保存しました 成功${saved.savedItems}件 / 失敗${saved.failedItems}件\n保存先: ${manualSaveDestinationLabel(fileSystem, selectedLocation)}"
                                },
                                onFailure = { it.toCompatUserMessage("ZIPを保存できませんでした") }
                            ) ?: "保存機能を初期化できませんでした"
                        } else if (mode == "save_images_folder") {
                            val urls = compatBatchMediaUrls(currentSnapshot.posts)
                            var success = 0
                            var failure = 0
                            val outputNames = compatBatchOutputFileNames(urls)
                            val imageFolder = buildCompatManualImageFolderName(
                                boardName = tab.boardName,
                                title = tab.title,
                                threadId = tab.threadNo
                            )
                            auxiliaryPageSaveProgressFlow.value = SaveProgress(
                                phase = SavePhase.DOWNLOADING,
                                current = 0,
                                total = urls.size,
                                currentItem = "しばらくお待ち下さい"
                            )
                            urls.forEachIndexed { index, url ->
                                auxiliaryPageSaveProgressFlow.value = SaveProgress(
                                    phase = SavePhase.DOWNLOADING,
                                    current = index,
                                    total = urls.size,
                                    currentItem = url.substringBefore('?').substringAfterLast('/')
                                )
                                mediaSaver?.saveMedia(
                                    url,
                                    tab.boardKey,
                                    tab.threadNo,
                                    baseSaveLocation = selectedLocation,
                                    baseDirectory = MANUAL_SAVE_DIRECTORY,
                                    storageDirectoryOverride = imageFolder,
                                    useTypeSubdirectory = false,
                                    outputFileNameOverride = outputNames[url],
                                    onProgress = { itemBytes, itemTotalBytes ->
                                        auxiliaryPageSaveProgressFlow.value = SaveProgress(
                                            phase = SavePhase.DOWNLOADING,
                                            current = index,
                                            total = urls.size,
                                            currentItem = url.substringBefore('?').substringAfterLast('/'),
                                            currentItemBytes = itemBytes,
                                            currentItemTotalBytes = itemTotalBytes
                                        )
                                    }
                                )
                                    ?.fold({ success += 1 }, { failure += 1 })
                                pageSavePartialSavedCount = success
                            }
                            error = "メディアを保存しました 成功${success}件 / 失敗${failure}件\n保存先: ${manualSaveDestinationLabel(fileSystem, selectedLocation)}/$imageFolder"
                        } else {
                            val includeFull = mode == "save_all"
                            val includeThumb = mode == "save_thumb" || mode == "save_all"
                            val posts = compatPostsForSave(snapshot, includeFull, includeThumb)
                            val result = pageSaver?.let { saver ->
                                    saver.saveThread(
                                        threadId = tab.threadNo,
                                        boardId = tab.boardKey,
                                        boardName = tab.boardName,
                                        boardUrl = BoardUrlResolver.resolveBoardBaseUrl(tab.originalUrl),
                                        title = tab.title,
                                        expiresAtLabel = currentSnapshot.expiresAtLabel,
                                        posts = posts,
                                        baseSaveLocation = selectedLocation,
                                        baseDirectory = MANUAL_SAVE_DIRECTORY,
                                        writeMetadata = true,
                                        rawHtmlOptions = RawHtmlSaveOptions(enable = true, stripExternalResources = true),
                                        limits = ThreadSaveLimits(
                                            maxMediaItems = if (mode == "save_html") {
                                                0
                                            } else {
                                                ThreadSaveService.DEFAULT_MAX_MEDIA_ITEMS
                                            }
                                        ),
                                        storageOptions = buildManualThreadSaveStorageOptions(
                                            tab.boardKey,
                                            tab.threadNo
                                        )
                                    )
                            }
                            error = result?.fold(
                                onSuccess = { saved -> completeCompatThreadSave(saved, fileSystem, selectedLocation) },
                                onFailure = { it.toCompatUserMessage("保存できませんでした") }
                            ) ?: "保存機能を初期化できませんでした"
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                error = compatThreadSaveCancellationMessage(pageSavePartialSavedCount)
                throw cancelled
            } catch (failure: Throwable) {
                error = failure.toCompatUserMessage("保存できませんでした")
            }
        }
    }
    fun saveCompatPage(mode: String) {
        if (savingPage) return
        withSaveDestination { saveCompatPageNow(mode, it) }
    }
    suspend fun load(
        manual: Boolean,
        refreshOnActivation: Boolean = false,
        bypassCache: Boolean = false
    ) = loadMutex.withLock {
        fun isCatalogTitlePlaceholder(value: String, posts: List<CompatPostSnapshot>): Boolean {
            val normalized = value.trim()
            if (normalized.isBlank() || normalized.matches(compatAppDefaultPostLabelRegex)) {
                return true
            }
            val numeric = normalized.toIntOrNull() ?: return false
            // Older catalog snapshots persisted the reply-count badge as the
            // title. Treat it as a placeholder when it matches either the
            // catalog count or the body count, including the OP-inclusive
            // representation used by some boards.
            return numeric == tab.replyCount ||
                numeric == posts.size ||
                numeric == (posts.size - 1).coerceAtLeast(0)
        }

        suspend fun inferThreadTitle(
            existingTitle: String,
            posts: List<CompatPostSnapshot>
        ): String = withContext(AppDispatchers.parsing) {
            val catalogTitle = existingTitle.trim()
                .takeIf { it.isNotEmpty() && !isCatalogTitlePlaceholder(it, posts) }
            val opFirstLine = posts.firstOrNull()
                ?.messageHtml
                ?.toCompatPlainText()
                ?.let(::extractFirstUsableTitleLine)
            catalogTitle ?: opFirstLine ?: existingTitle
        }
        if (loading && snapshot != null && manual) {
            error = "読み込み中です"
            return@withLock
        }
        // Initial, activation and manual loads all own the indicator while
        // they are running. In particular, a restarted LaunchedEffect must be
        // able to recover the initial state without relying on the default
        // value of `loading`.
        loading = true
        try {
            val cached = store.loadThreadSnapshot(tab.key)?.let { cachedSnapshot ->
                withContext(AppDispatchers.parsing) { normalizeCompatThreadSnapshot(cachedSnapshot) }
            }?.takeIf { it.posts.isNotEmpty() }
            val previousSnapshot = snapshot?.takeIf { it.posts.isNotEmpty() }
                ?: cached?.takeIf { it.posts.isNotEmpty() }
            // Keep the currently rendered copy while a refresh is in flight.
            // An empty/stale cache entry must not blank an otherwise valid thread.
            if (cached != null && (snapshot == null || snapshot?.posts.isNullOrEmpty())) {
                snapshot = cached
            }
            // Cache/archive responses do not have a catalog row to seed the
            // OP thumbnail. Persist the first post's media so tabs and history
            // do not stay transparent after opening a cached/dead thread.
            if (tab.thumbnailUrl.isNullOrBlank()) {
                compatSnapshotThumbnail(cached, tab.threadNo)?.let { thumbnail ->
                    val cachedTab = tab.copy(thumbnailUrl = thumbnail)
                    store.updateTab(cachedTab)
                    store.upsertHistory(
                        CompatHistoryEntry(
                            canonicalUrl = tab.canonicalUrl,
                            originalUrl = tab.originalUrl,
                            boardKey = tab.boardKey,
                            boardName = tab.boardName,
                            threadNo = tab.threadNo,
                            title = tab.title,
                            thumbnailUrl = thumbnail,
                            replyCount = tab.replyCount,
                            contentUpdatedAtEpochMillis = tab.contentUpdatedAtEpochMillis,
                            scrollAnchor = tab.scrollAnchor
                        )
                    )
                }
            }
            // A tab restored from a snapshot may still carry the direct-link
            // fallback `No.<id>` even though its cached OP contains the real
            // subject.  Resolve it before deciding that no network refresh is
            // needed, otherwise a force-stop/relaunch keeps the wrong title
            // forever.
            if (cached != null && isCatalogTitlePlaceholder(tab.title, cached.posts)) {
                val inferredTitle = inferThreadTitle(tab.title, cached.posts)
                if (inferredTitle != tab.title) {
                    val titledTab = tab.copy(title = inferredTitle)
                    store.updateTab(titledTab)
                    store.upsertHistory(
                        CompatHistoryEntry(
                            canonicalUrl = tab.canonicalUrl,
                            originalUrl = tab.originalUrl,
                            boardKey = tab.boardKey,
                            boardName = tab.boardName,
                            threadNo = tab.threadNo,
                            title = inferredTitle,
                            thumbnailUrl = tab.thumbnailUrl,
                            replyCount = tab.replyCount,
                            contentUpdatedAtEpochMillis = tab.contentUpdatedAtEpochMillis,
                            scrollAnchor = tab.scrollAnchor
                        )
                    )
                }
            }
            // A cached body is shown immediately, but a tab activation still
            // needs one live request. Otherwise returning from another thread
            // (or from the post screen) leaves this tab at its old reply count.
            if (shouldFetchCompatThread(manual, refreshOnActivation, cached?.posts?.size ?: 0)) {
            val activeRepository = repository
            if (activeRepository == null) {
                error = "通信機能を初期化できませんでした"
            } else {
                val cacheEnabled = canUseCompatCacheServer(
                    settingEnabled = preferences[COMPAT_CACHE_ENABLED_KEY] == "ON" && !bypassCache,
                    serverAvailable = preferences[COMPAT_CACHE_AVAILABLE_KEY] == "ON",
                    sourceUrl = tab.originalUrl,
                    currentReplyCount = tab.replyCount,
                    responseThreshold = preferences[COMPAT_CACHE_RESPONSE_THRESHOLD_KEY]
                        ?.toIntOrNull()
                        ?: DEFAULT_COMPAT_CACHE_RESPONSE_THRESHOLD
                )
                val fetchResult = try {
                    withTimeoutOrNull(COMPAT_THREAD_LOAD_TIMEOUT_MILLIS) {
                        loadCompatThreadWithFallback(
                            sourceUrl = tab.originalUrl,
                            cacheEnabled = cacheEnabled,
                            cacheBaseUrl = archiveBaseUrl,
                            loader = activeRepository::getThreadByUrl,
                            expectedReplyCount = tab.replyCount,
                            archiveLoader = httpClient?.let { client ->
                                { archiveUrl -> fetchCompatArchiveThreadPage(client, archiveUrl) }
                            }
                        )
                    } ?: Result.failure(IllegalStateException("thread load timeout"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                }
                val fetched = fetchResult.getOrNull()
                val page = fetched?.page
                val loadedFromCache = fetched?.source == CompatThreadFetchSource.CACHE
                val loadedFromArchive = fetched?.source == CompatThreadFetchSource.ARCHIVE
                val archiveSupplemented = fetched?.source == CompatThreadFetchSource.MERGED
                val primaryThreadGone = fetched?.let {
                    resolveCompatThreadDeadState(
                        store.tabs.first().firstOrNull { current -> current.key == tab.key }?.isDead ?: tab.isDead,
                        it
                    )
                } ?: tab.isDead
                if (page != null) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    // A live thread can contain thousands of posts.  Mapping
                    // every post (including quote/media metadata) is pure
                    // parsing work and must not run on the Compose/main
                    // dispatcher, otherwise a catalog -> thread transition
                    // can look like an ANR on slower devices.
                    val newSnapshot = withContext(AppDispatchers.parsing) {
                        page.toCompatThreadSnapshot(tab.key, now)
                    }
                    if (!loadedFromArchive) {
                        val learnedDefaultText = withContext(AppDispatchers.parsing) {
                            learnCompatBoardDefaultText(boardDefaultText, newSnapshot.posts)
                        }
                        if (learnedDefaultText != boardDefaultText) {
                            boardDefaultText = learnedDefaultText
                            store.savePreference(
                                compatBoardDefaultSubjectPreferenceKey(tab.boardKey),
                                learnedDefaultText.defaultSubject
                            )
                            store.savePreference(
                                compatBoardDefaultNamePreferenceKey(tab.boardKey),
                                learnedDefaultText.defaultName
                            )
                        }
                    }
                    // A direct thread URL has no catalog item from which to
                    // seed the subject, so it initially uses `No.<id>`.
                    // The legacy APK replaces that fallback with the subject
                    // parsed from the thread header as soon as the body is
                    // loaded.  Keep the tab and history title in sync with
                    // that value so the top bar/back stack match catalog-open
                    // navigation as well.
                    // `ThreadPage.boardTitle` is the board header (e.g.
                    // 二次元裏＠ふたば), not the thread subject.  For a
                    // direct URL the old APK uses the OP's first line as the
                    // title; catalog-open tabs already carry the catalog
                    // subject and must keep it.
                    val resolvedThreadTitle = inferThreadTitle(tab.title, newSnapshot.posts)
                    val visibleLocalSnapshot = snapshot?.takeIf { it.posts.isNotEmpty() }
                        ?: cached?.takeIf { it.posts.isNotEmpty() }
                    val preserveLocalSnapshot = shouldPreferLocalCompatSnapshot(visibleLocalSnapshot, newSnapshot)
                    val committed = !preserveLocalSnapshot && store.saveThreadSnapshot(newSnapshot)
                    val notices = resolveCompatThreadUpdateNotices(
                        previous = previousSnapshot,
                        fetched = newSnapshot,
                        manual = manual,
                        committed = committed,
                        primaryThreadGone = primaryThreadGone
                    )
                    snapshot = if (preserveLocalSnapshot) {
                        // A server-side cache may contain only the prefix it had fetched
                        // before the thread changed. Never replace a fuller device copy
                        // with that shorter response.
                        visibleLocalSnapshot ?: newSnapshot
                    } else if (committed) {
                        newSnapshot
                    } else {
                        // A slower request must not replace a newer committed revision.
                        store.loadThreadSnapshot(tab.key)?.let { committedSnapshot ->
                            withContext(AppDispatchers.parsing) {
                                normalizeCompatThreadSnapshot(committedSnapshot)
                            }
                        } ?: snapshot
                    }
                    if (committed) {
                        if (manual && previousSnapshot != null && previousSnapshot.revision != newSnapshot.revision) {
                            undoRefreshSnapshot = previousSnapshot
                        }
                        newReplyNotice = notices.newReply
                        manualRefreshNotice = notices.manualRefresh
                    }
                    if (committed) {
                        val replyCount = page.compatReplyCount()
                        val statusFlags = parseCompatThreadStatusFlags(page.deletedNotice)
                        val thumbnailUrl = tab.thumbnailUrl ?: compatSnapshotThumbnail(newSnapshot, tab.threadNo)
                        store.updateTab(
                            tab.copy(
                                title = resolvedThreadTitle,
                                thumbnailUrl = thumbnailUrl,
                                replyCount = replyCount,
                                checkedReplyCount = replyCount,
                                contentUpdatedAtEpochMillis = now,
                                snapshotRevision = now,
                                // An archive response can be valid even after
                                // the live board has returned 404/410. Keep
                                // that distinction for the tab and TTS poller.
                                isDead = primaryThreadGone,
                                isDeleted = if (loadedFromArchive) tab.isDeleted else statusFlags.isDeleted,
                                isIsolated = if (loadedFromArchive) tab.isIsolated else statusFlags.isIsolated,
                                isExploded = if (loadedFromArchive) tab.isExploded else statusFlags.isAdminDeleted,
                                isOld = loadedFromArchive || archiveSupplemented
                            )
                        )
                        store.upsertHistory(
                            CompatHistoryEntry(
                                canonicalUrl = tab.canonicalUrl,
                                originalUrl = tab.originalUrl,
                                boardKey = tab.boardKey,
                                boardName = tab.boardName,
                                threadNo = tab.threadNo,
                                title = resolvedThreadTitle,
                                thumbnailUrl = thumbnailUrl,
                                replyCount = replyCount,
                                contentUpdatedAtEpochMillis = now,
                                scrollAnchor = tab.scrollAnchor
                            )
                        )
                    }
                    readAloudThreadGone = primaryThreadGone
                    if (
                        committed && !loadedFromArchive &&
                        preferences[com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY] != "OFF"
                    ) {
                        runSuspendCatchingPreservingCancellation {
                            store.enqueueArchiveReport(tab.originalUrl, now)
                        }
                            .onSuccess { queued ->
                                if (queued.inserted || queued.sendableCount > 0) {
                                    onArchiveReportEnqueued(queued.sendableCount)
                                }
                            }
                    }
                    error = when {
                        primaryThreadGone -> "スレッドは落ちました（過去ログから表示中）"
                        preserveLocalSnapshot -> "端末キャッシュを優先表示（サーバー側の本文が途中まで）"
                        loadedFromCache -> "キャッシュサーバーから表示中"
                        loadedFromArchive -> "過去ログから表示中"
                        archiveSupplemented -> "過去ログから不足レスを補完しました"
                        else -> null
                    }
                } else {
                    val throwable = fetchResult.exceptionOrNull() ?: IllegalStateException("thread load failed")
                    error = throwable.toCompatUserMessage("スレッドを取得できませんでした")
                    val primaryFailure = (throwable as? CompatThreadFetchException)?.primaryFailure
                    if (isCompatThreadGoneFailure(primaryFailure)) {
                        readAloudThreadGone = true
                        store.updateTab(tab.copy(isDead = true))
                    }
                }
            }
        }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // A malformed live page, cache payload, or media reference must
            // not terminate the Compose LaunchedEffect (and therefore the
            // whole activity) while opening a real board thread (#29).
            error = failure.toCompatUserMessage("スレッドを取得できませんでした")
        } finally {
            // Pull-to-refresh uses this state for the indicator and gesture lock.
            // Release it even when the request times out or fails.
            loading = false
        }
    }

    fun stopReadAloud(message: String? = null) {
        readAloudJob?.cancel()
        readAloudJob = null
        textSpeaker?.stop()
        readingAloud = false
        readAloudDialogOpen = false
        readAloudDisplayPost = null
        readAloudStatus = null
        if (message != null) error = message
    }

    fun startReadAloud(startPostIndex: Int = readAloudCursor) {
        if (readingAloud) return
        val resolvedStartIndex = resolveCompatReadAloudStartIndex(
            requestedIndex = startPostIndex,
            postCount = snapshot?.posts?.size ?: 0
        )
        if (resolvedStartIndex != readAloudCursor) readAloudCharacterOffset = 0
        readAloudCursor = resolvedStartIndex
        readAloudDisplayPost = null
        readAloudStatus = "読み上げを準備中"
        readAloudDialogOpen = true
        readAloudJob = scope.launch {
            val hasText = withContext(AppDispatchers.textAnnotation) {
                snapshot?.posts.orEmpty().any { post -> compatReadAloudText(post).isNotBlank() }
            }
            if (!hasText) {
                error = "読み上げ対象がありません"
                readAloudStatus = "読み上げ対象がありません"
                readAloudJob = null
                return@launch
            }
            val speaker = ensureTextSpeaker()
            try {
                // The reference app starts its response cursor only after the
                // platform speech engine reports successful initialization.
                speaker.prepare()
                readingAloud = true
                while (isActive && readingAloud) {
                    val currentPosts = snapshot?.posts.orEmpty()
                    if (currentPosts.size < readAloudCursor) {
                        readAloudCursor = 0
                        readAloudCharacterOffset = 0
                    }
                    readAloudDisplayPost = currentPosts.getOrNull(readAloudCursor)
                    readAloudStatus = null
                    val batch = withContext(AppDispatchers.textAnnotation) {
                        buildCompatReadAloudBatch(
                            posts = currentPosts,
                            startPostIndex = readAloudCursor,
                            startCharacterOffset = readAloudCharacterOffset
                        )
                    }
                    if (batch.text.isNotBlank()) {
                        speaker.speak(batch.text)
                    }
                    readAloudCursor = batch.nextPostIndex
                    readAloudCharacterOffset = batch.nextCharacterOffset
                    if (!isActive || !readingAloud) break

                    // Drain the current snapshot in bounded utterances before
                    // polling the network for newer replies.
                    if (readAloudCursor < currentPosts.size) continue

                    // Keep the old app's hands-free behavior: after the last
                    // spoken reply, poll the live thread for new responses.
                    readAloudDisplayPost = null
                    readAloudStatus = "リロード中"
                    load(manual = true, bypassCache = true)
                    if (readAloudThreadGone) {
                        val stoppedMessage = "スレッドが落ちています"
                        error = stoppedMessage
                        readAloudStatus = stoppedMessage
                        break
                    }
                    if (snapshot?.posts.orEmpty().size <= readAloudCursor) {
                        for (remainingSeconds in 30 downTo 5 step 5) {
                            readAloudStatus = compatReadAloudReloadTimer(remainingSeconds)
                            delay(COMPAT_READ_ALOUD_TIMER_TICK_MILLIS)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                error = failure.toCompatUserMessage("読み上げできませんでした")
                readAloudStatus = error
            } finally {
                readingAloud = false
                readAloudJob = null
            }
        }
    }

    fun moveReadAloudCursor(delta: Int) {
        val postCount = snapshot?.posts?.size ?: 0
        val target = (readAloudCursor + delta).coerceIn(0, (postCount - 1).coerceAtLeast(0))
        stopReadAloud()
        readAloudCursor = target
        readAloudCharacterOffset = 0
        scope.launch { listState.animateScrollToItem(target) }
        startReadAloud(target)
    }
    val viewerHiddenImages = remember(ngRules, tab.key) {
        ngRules.asSequence()
            .filter { it.kind == CompatNgKind.THREAD_IMAGE && it.appliesToThreadImage(tab.boardKey, tab.key) }
            .mapTo(mutableSetOf(), CompatNgRule::normalizedValue)
    }
    val imageNgPhashThreshold = preferences.compatPreferenceValue(
        "thread", "threadImageNgPhashThreshold", "画像NG類似度閾値"
    )?.filter(Char::isDigit)?.toIntOrNull()
        ?.coerceIn(CompatImagePhash.MIN_THRESHOLD, CompatImagePhash.MAX_THRESHOLD)
        ?: CompatImagePhash.DEFAULT_THRESHOLD
    val imageNgPhashRules = remember(ngRules, tab.key) {
        ngRules.filter { it.kind == CompatNgKind.THREAD_IMAGE_PHASH && it.appliesToThreadImage(tab.boardKey, tab.key) }
    }
    var imagePhashes by remember(tab.key) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(snapshot?.revision, imageNgPhashRules, httpClient) {
        val client = httpClient
        if (client == null || imageNgPhashRules.isEmpty()) {
            imagePhashes = emptyMap()
        } else {
            val candidates = withContext(AppDispatchers.parsing) {
                snapshot?.posts.orEmpty()
                    .map(::normalizeCompatPostMedia)
                    .mapNotNull { post ->
                        (post.imageUrl ?: post.thumbnailUrl)?.let { url -> post.postNo to url }
                    }
                    .distinctBy { it.second }
                    .take(256)
            }
            val computed = withTimeoutOrNull(COMPAT_PHASH_BATCH_TIMEOUT_MILLIS) {
                buildMap {
                    candidates.forEach { (postNo, url) ->
                        withTimeoutOrNull(COMPAT_PHASH_REQUEST_TIMEOUT_MILLIS) {
                            fetchCompatImagePhash(client, url).getOrNull()
                        }?.let { put(postNo, it) }
                    }
                }
            }.orEmpty()
            imagePhashes = computed
        }
    }
    val phashHiddenPostNos = remember(snapshot?.revision, imageNgPhashRules, imagePhashes, imageNgPhashThreshold) {
        snapshot?.posts.orEmpty().filter { post ->
            val phash = imagePhashes[post.postNo]
            phash != null && imageNgPhashRules.any {
                CompatImagePhash.isSimilar(phash, it.normalizedValue, imageNgPhashThreshold)
            }
        }.mapTo(mutableSetOf(), CompatPostSnapshot::postNo)
    }
    var viewerMediaPosts by remember(tab.key) {
        mutableStateOf<List<CompatPostSnapshot>>(emptyList())
    }
    LaunchedEffect(
        snapshot?.revision,
        viewerHiddenImages,
        phashHiddenPostNos,
        threadUpsThumbMethod,
        compatWifiConnected,
        showDeletedPosts
    ) {
        val posts = presentCompatPostsForDeletedVisibility(
            posts = snapshot?.posts.orEmpty(),
            showDeletedContent = showDeletedPosts
        )
        val calculateViewerPosts = {
            compatViewerMediaPosts(
                posts = posts,
                hiddenImages = viewerHiddenImages,
                hiddenPostNos = phashHiddenPostNos,
                upsThumbnailMethod = threadUpsThumbMethod,
                wifiConnected = compatWifiConnected
            )
        }
        viewerMediaPosts = if (posts.size <= COMPAT_MAIN_THREAD_ANALYSIS_POST_LIMIT) {
            calculateViewerPosts()
        } else {
            withContext(AppDispatchers.parsing) {
                calculateViewerPosts()
            }
        }
    }

    var visiblePosts by remember(tab.key) {
        mutableStateOf<List<CompatPostSnapshot>>(emptyList())
    }
    var scrollRestoreCompleted by remember(tab.key) { mutableStateOf(false) }
    LaunchedEffect(
        snapshot?.revision,
        showDeletedPosts,
        threadNgEnabled,
        ngRules,
        tab.key,
        imagePhashes,
        imageNgPhashThreshold
    ) {
        val posts = presentCompatPostsForDeletedVisibility(
            posts = snapshot?.posts.orEmpty(),
            showDeletedContent = showDeletedPosts
        )
        val nextVisiblePosts = try {
            if (!threadNgEnabled) {
                // The non-NG path only checks a boolean flag, so keep the normal
                // thread transition immediate and avoid an unnecessary worker hop.
                posts
            } else {
                // matchesCompatThreadNg() converts message HTML to plain text.  It
                // is intentionally kept off the Compose/main dispatcher because a
                // large thread can contain thousands of posts and many NG rules.
                // Keep the last rendered list while the asynchronous filter is
                // calculated. Clearing it makes LazyColumn clamp its state to
                // (0, 0), after which scrollRestoreCompleted prevents a second
                // restoration when the filtered list arrives.
                val calculateVisiblePosts = {
                    val ngRuleIndex = buildCompatThreadNgRuleIndex(
                        rules = ngRules,
                        scopeKey = tab.key,
                        boardKey = tab.boardKey
                    )
                    posts.filter { post ->
                        !post.matchesCompatThreadNg(
                                index = ngRuleIndex,
                                imagePhash = imagePhashes[post.postNo],
                                imagePhashThreshold = imageNgPhashThreshold
                            )
                    }
                }
                if (posts.size <= COMPAT_MAIN_THREAD_ANALYSIS_POST_LIMIT) {
                    calculateVisiblePosts()
                } else {
                    withContext(AppDispatchers.parsing) { calculateVisiblePosts() }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Optional NG processing must not keep a complete WEBP/WEBM thread
            // behind the initial restore cover. Preserve the readable body.
            Logger.e("CompatibilityThread", "Failed to filter initial thread posts", failure)
            posts
        }
        // Capture the latest position after the potentially expensive filter
        // calculation, while the old list is still rendered. Restore the same
        // stable post in the replacement list so filtering or a new snapshot
        // cannot silently move the user to a different row.
        val replacementAnchor = if (scrollRestoreCompleted && visiblePosts.isNotEmpty()) {
            val index = listState.firstVisibleItemIndex.coerceAtLeast(0)
            ScrollAnchor(
                postNo = visiblePosts.getOrNull(index)?.postNo,
                offsetPx = listState.firstVisibleItemScrollOffset.coerceAtLeast(0),
                fallbackIndex = index,
                snapshotRevision = snapshot?.revision ?: tab.snapshotRevision
            )
        } else {
            null
        }
        visiblePosts = nextVisiblePosts
        if (replacementAnchor != null && nextVisiblePosts.isNotEmpty()) {
            yield()
            val targetIndex = replacementAnchor.postNo
                ?.let { postNo -> nextVisiblePosts.indexOfFirst { it.postNo == postNo }.takeIf { it >= 0 } }
                ?: replacementAnchor.fallbackIndex
            listState.scrollToItem(
                targetIndex.coerceIn(0, nextVisiblePosts.lastIndex),
                replacementAnchor.offsetPx.coerceAtLeast(0)
            )
        }
    }

    fun buildCurrentScrollAnchor(
        posts: List<CompatPostSnapshot>,
        currentSnapshot: CompatThreadSnapshot?
    ): ScrollAnchor {
        val index = listState.firstVisibleItemIndex.coerceAtLeast(0)
        return ScrollAnchor(
            postNo = posts.getOrNull(index)?.postNo,
            offsetPx = listState.firstVisibleItemScrollOffset.coerceAtLeast(0),
            fallbackIndex = index,
            snapshotRevision = currentSnapshot?.revision ?: tab.snapshotRevision
        )
    }

    suspend fun persistScrollAnchor(anchor: ScrollAnchor) {
        // The host can dispose the composition before its test/application
        // lifecycle closes the compatibility store.  Scroll persistence is
        // best-effort at that point; it must never turn teardown into an
        // uncaught coroutine exception.
        try {
            store.updateScrollAnchor(tab.key, anchor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatibilityThread", "Failed to persist scroll anchor", failure)
        }
    }

    fun persistCurrentScrollAndSelect(target: CompatTab) {
        val hasCurrentPosition = scrollRestoreCompleted && visiblePosts.isNotEmpty()
        val anchor = if (hasCurrentPosition) {
            buildCurrentScrollAnchor(visiblePosts, snapshot)
        } else {
            target.scrollAnchor
        }
        scope.launch {
            if (hasCurrentPosition) {
                persistScrollAnchor(anchor)
            }
            // The source anchor belongs to the source tab only. Passing it
            // through target.copy() made every adjacent tab inherit the
            // position of the tab being left (often (0, 0)). The target
            // already carries its own persisted anchor.
            onSelectTab(target)
        }
    }

    fun settlePagerSwipe(finalGestureOffset: Float = pagerOffset.value) {
        val width = pagerWidthPx.toFloat().coerceAtLeast(1f)
        val offset = finalGestureOffset
        val target = when {
            offset <= -width * 0.25f -> nextTab
            offset >= width * 0.25f -> previousTab
            else -> null
        }
        scope.launch {
            pagerDragUpdateJob?.cancel()
            pagerDragUpdateJob = null
            pagerOffset.snapTo(offset)
            if (target != null) {
                pagerOffset.animateTo(
                    if (offset < 0f) -width else width,
                    animationSpec = tween(180)
                )
                // Keep the outgoing page off-screen until the selected tab is
                // replaced. Resetting this page to zero first paints the old
                // thread in the centre for a frame and causes the visible jerk
                // at the end of a successful horizontal swipe.
                persistCurrentScrollAndSelect(target)
            } else {
                pagerOffset.animateTo(0f, animationSpec = tween(180))
            }
        }
    }

    // Restore only after the filtered list has been installed. The old code
    // attempted this from the network/cache loader, before LazyColumn had any
    // items, and the initial (0, 0) observation could then overwrite it.
    LaunchedEffect(tab.key, visiblePosts.size) {
        if (scrollRestoreCompleted) return@LaunchedEffect
        if (visiblePosts.isEmpty()) {
            // A non-empty snapshot can legitimately become empty when every
            // row matches NG. Do not leave the full-screen restore cover alive.
            if (snapshot?.posts?.isNotEmpty() == true) scrollRestoreCompleted = true
            return@LaunchedEffect
        }
        val anchor = tab.scrollAnchor
        val targetIndex = anchor.postNo
            ?.let { postNo -> visiblePosts.indexOfFirst { it.postNo == postNo }.takeIf { it >= 0 } }
            ?: anchor.fallbackIndex
        val safeIndex = targetIndex.coerceIn(0, visiblePosts.lastIndex)
        try {
            yield()
            listState.scrollToItem(safeIndex, anchor.offsetPx.coerceAtLeast(0))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Scroll restoration is best-effort. A transient layout/media
            // failure must not hide the fetched body until manual reload.
            Logger.e("CompatibilityThread", "Failed to restore initial thread scroll", failure)
        }
        // Do not put this in finally: a new post-count key cancels the old
        // effect and the replacement must still restore its own anchor.
        scrollRestoreCompleted = true
    }

    val latestVisiblePosts by rememberUpdatedState(visiblePosts)
    val latestSnapshot by rememberUpdatedState(snapshot)
    val latestScrollRestoreCompleted by rememberUpdatedState(scrollRestoreCompleted)
    DisposableEffect(tab.key, listState) {
        onDispose {
            if (latestScrollRestoreCompleted && latestVisiblePosts.isNotEmpty()) {
                // The screen-local rememberCoroutineScope is cancelled as
                // soon as this destination leaves the composition. Delegate
                // the final write to the parent workspace scope instead.
                onPersistScrollAnchor(
                    tab.key,
                    buildCurrentScrollAnchor(latestVisiblePosts, latestSnapshot)
                )
            }
        }
    }

    fun launchThreadViewer(index: Int, mediaIdentity: String) {
        if (index < 0) return
        // Persist the anchor before leaving the thread.  Without this, the
        // thread is recreated with the previous debounced anchor after the
        // viewer is closed, which is especially visible for lower replies.
        scope.launch {
            persistScrollAnchor(
                ScrollAnchor(
                    postNo = visiblePosts.getOrNull(listState.firstVisibleItemIndex)?.postNo,
                    offsetPx = listState.firstVisibleItemScrollOffset,
                    fallbackIndex = listState.firstVisibleItemIndex,
                    snapshotRevision = snapshot?.revision ?: tab.snapshotRevision
                )
            )
            onOpenViewer(index, mediaIdentity)
        }
    }

    fun openViewerFromThread(post: CompatPostSnapshot): Boolean {
        val mediaIdentity = compatMediaIdentity(post)
        val index = viewerMediaPosts.indexOfFirst { compatMediaIdentity(it) == mediaIdentity }
        if (index < 0) return false
        launchThreadViewer(index, mediaIdentity)
        return true
    }

    fun openViewerFromMediaUrl(url: String, fallbackPost: CompatPostSnapshot): Boolean {
        val index = viewerMediaPosts.indexOfFirst { candidate ->
            compatViewerPostMatchesMediaUrl(candidate, url)
        }
        if (index >= 0) {
            val candidate = viewerMediaPosts[index]
            launchThreadViewer(index, compatMediaIdentity(candidate))
            return true
        }
        if (!compatApuSmallGalleryEnabled(threadUpsThumbMethod)) {
            val directMediaUrl = compatApuSmallMediaFileName(url)
                ?.let(::compatApuSmallSourceUrl)
            if (directMediaUrl != null) {
                scope.launch {
                    persistScrollAnchor(
                        ScrollAnchor(
                            postNo = visiblePosts.getOrNull(listState.firstVisibleItemIndex)?.postNo,
                            offsetPx = listState.firstVisibleItemScrollOffset,
                            fallbackIndex = listState.firstVisibleItemIndex,
                            snapshotRevision = snapshot?.revision ?: tab.snapshotRevision
                        )
                    )
                    onOpenSingleViewer(directMediaUrl, fallbackPost.postNo, fallbackPost.position)
                }
                return true
            }
        }
        // A stale popup can outlive the current snapshot.  Preserve the
        // ordinary post-media route as a safe fallback before opening a URL.
        return openViewerFromThread(fallbackPost)
    }
    val posterIdentityProgressState = remember(tab.key) {
        mutableStateOf<Map<String, List<CompatPosterIdentityProgress>>>(emptyMap())
    }
    var posterIdentityProgress by posterIdentityProgressState
    LaunchedEffect(snapshot?.revision) {
        val posts = snapshot?.posts.orEmpty()
        posterIdentityProgress = if (posts.size <= COMPAT_MAIN_THREAD_ANALYSIS_POST_LIMIT) {
            compatPosterIdentityProgressByPost(posts)
        } else {
            withContext(AppDispatchers.parsing) {
                compatPosterIdentityProgressByPost(posts)
            }
        }
    }
    val newReplyMarkerPostNo = remember(visiblePosts, newReplyNotice) {
        newReplyNotice?.let { notice ->
            visiblePosts.firstOrNull { it.position >= notice.firstNewPostPosition }?.postNo
        }
    }
    val threadFooterLabel = remember(snapshot, tab.isDead) {
        compatThreadFooterLabel(snapshot, tab.isDead)
    }
    val threadListLastIndex = (visiblePosts.lastIndex + if (threadFooterLabel != null) 1 else 0)
        .coerceAtLeast(0)
    suspend fun scrollToNewRepliesOrBottom() {
        if (visiblePosts.isNotEmpty() || threadFooterLabel != null) {
            listState.scrollToItem(
                resolveCompatThreadBottomScrollIndex(
                    visiblePosts = visiblePosts,
                    newReplyNotice = newReplyNotice,
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    lastItemIndex = threadListLastIndex
                )
            )
        }
    }
    val volumeKeyOwner = remember(tab.key) { Any() }
    DisposableEffect(threadVolumeKeyAction, tab.key, tabs.size, threadListLastIndex) {
        CompatVolumeKeyBus.register(volumeKeyOwner) { key ->
            if (threadVolumeKeyAction == "何もしない") {
                false
            } else {
                scope.launch {
                    val upward = key == CompatVolumeKey.UP
                    when (threadVolumeKeyAction) {
                        "1レス分スクロール" -> {
                            val target = (listState.firstVisibleItemIndex + if (upward) -1 else 1)
                                .coerceIn(0, threadListLastIndex)
                            listState.animateScrollToItem(target)
                        }
                        "1画面分スクロール" -> {
                            val page = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                            val target = (listState.firstVisibleItemIndex + if (upward) -page else page)
                                .coerceIn(0, threadListLastIndex)
                            listState.animateScrollToItem(target)
                        }
                        "スレッドの切り替え" -> {
                            val target = if (upward) previousTab else nextTab
                            if (target != null) persistCurrentScrollAndSelect(target)
                        }
                    }
                }
                true
            }
        }
        onDispose { CompatVolumeKeyBus.unregister(volumeKeyOwner) }
    }
    var searchHits by remember(tab.key) {
        mutableStateOf<List<CompatThreadSearchHit>>(emptyList())
    }
    LaunchedEffect(visiblePosts, searchQuery) {
        searchHits = emptyList()
        searchHits = if (visiblePosts.size <= COMPAT_MAIN_THREAD_ANALYSIS_POST_LIMIT) {
            findCompatThreadSearchHits(visiblePosts, searchQuery)
        } else {
            withContext(AppDispatchers.parsing) {
                findCompatThreadSearchHits(visiblePosts, searchQuery)
            }
        }
    }
    val searchMatches = remember(searchHits) { searchHits.map(CompatThreadSearchHit::postIndex) }
    val searchHitsByIndex = remember(searchHits) { searchHits.associateBy(CompatThreadSearchHit::postIndex) }
    // A normal `>`/`>>` body link is deliberately not allowed to enter
    // quoteStack.  quoteStack is reserved for the APK's explicit extraction
    // screens (返信/ID/IP/キーワード/NG).  Keeping only the popup route here
    // prevents a future caller from accidentally turning a one-response quote
    // preview into a full-screen navigation page.
    fun openQuotePopup(sourcePosition: Int, query: String) {
        val allPosts = snapshot?.posts.orEmpty()
        fun showReplyPopup(post: CompatPostSnapshot) {
            // Reply text links in the APK select the newest preceding match and
            // show it in a PopupWindow.  Header extraction remains list-based.
            val anchorY = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { info -> visiblePosts.getOrNull(info.index)?.position == sourcePosition }
                ?.offset
                ?.plus(threadContentTopPx)
                ?.plus(24)
                ?: replyPopupAnchorY
            // PopupWindow is a separate Android window. If it is created while
            // the quote link's pointer/semantics click is still being dispatched,
            // a focusable popup can interpret that same click as an outside tap
            // and dismiss itself immediately. Yield one frame before installing
            // the popup state so the triggering click has completed.
            scope.launch {
                yield()
                replyPopupAnchorY = anchorY
                replyPopupPosts = listOf(post)
            }
        }
        // The overwhelmingly common form is a direct numeric reference. Resolve
        // it without launching a worker so the popup remains touch-immediate on
        // ordinary threads; complex ID/IP/header extraction stays off-main below.
        val numericTarget = query.trim()
            .removePrefix(">>")
            .removePrefix("no:")
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            val directMatch = numericTarget?.let { no -> allPosts.firstOrNull { it.postNo == no } }
        if (directMatch != null) {
            showReplyPopup(directMatch)
            return
        }
        scope.launch {
            val matches = withContext(AppDispatchers.parsing) {
                resolveCompatQuotePosts(allPosts, sourcePosition, query).ifEmpty {
                    // Older cached snapshots could carry a one-based position while
                    // the current resolver uses zero-based positions. Numeric >>No
                    // references are unambiguous, so keep them tappable in that case.
                    query.removePrefix("no:").trim().takeIf { it.all(Char::isDigit) }
                        ?.let { no -> allPosts.firstOrNull { it.postNo == no } }
                        ?.let(::listOf)
                        .orEmpty()
                }
            }
            if (matches.isEmpty()) {
                compatMissingQuoteNotice()?.let { error = it }
            } else {
                showReplyPopup(matches.first())
            }
        }
    }
    fun openHeaderExtraction(post: CompatPostSnapshot, kind: CompatHeaderExtractionKind) {
        scope.launch {
            val matches = withContext(AppDispatchers.parsing) {
                extractCompatHeaderPosts(snapshot?.posts.orEmpty(), post, kind)
            }
            if (matches.isEmpty()) {
                error = "抽出するレスが見つかりません"
                return@launch
            }
            val title = when (kind) {
                CompatHeaderExtractionKind.QUOTE -> "返信レス No.${post.postNo}"
                CompatHeaderExtractionKind.ID,
                CompatHeaderExtractionKind.IP -> compatPosterIdentity(post)?.display ?: kind.name
            }
            quoteStack = quoteStack + CompatQuoteFrame(title, "header:${kind.name}:${post.postNo}", matches)
        }
    }
    fun onHeaderClick(post: CompatPostSnapshot) {
        when (val target = compatHeaderTapTarget(compatHeaderText(post))) {
            is CompatHeaderTapTarget.Url -> openUrl(target.value)
            is CompatHeaderTapTarget.Email -> {
                clipboard.setText(AnnotatedString(target.value))
                error = "メールをコピーしました\n${target.value}"
            }
            null -> Unit
        }
    }
    fun onHeaderLongClick(post: CompatPostSnapshot) {
        scope.launch {
            val kinds = withContext(AppDispatchers.parsing) {
                compatHeaderExtractionKinds(post, snapshot?.posts.orEmpty())
            }
            when (kinds.size) {
                0 -> error = "抽出する要素が見つかりません"
                1 -> openHeaderExtraction(post, kinds.single())
                else -> headerExtractionPost = post
            }
        }
    }
    fun closeCurrentThread() {
        if (scrollRestoreCompleted && visiblePosts.isNotEmpty()) {
            onScrollAnchorObserved(tab.key, buildCurrentScrollAnchor(visiblePosts, snapshot))
        }
        onCloseTab(tab)
        if (closeThreadReturnsToPrevious) onBack()
    }
    // Repository creation can finish after the first composition on a cold
    // app start. Include it in the key so that case automatically retries
    // instead of waiting for the toolbar reload button.
    LaunchedEffect(tab.key, repository, threadRefreshToken, tab.refreshOnActivation) {
        load(
            manual = false,
            refreshOnActivation = tab.refreshOnActivation || threadRefreshToken > 0L
        )
    }
    LaunchedEffect(platformAiCommand, tab.key, visiblePosts.size) {
        val command = platformAiCommand ?: return@LaunchedEffect
        val requestedThread = command.threadIdParameter()
        if (requestedThread != null && requestedThread != tab.threadNo) return@LaunchedEffect
        when (command.action) {
            FutachaAiAction.RefreshCurrentThread -> load(manual = true, bypassCache = true)
            FutachaAiAction.ScrollThreadToTop -> listState.animateScrollToItem(0)
            FutachaAiAction.ScrollThreadToBottom -> {
                listState.animateScrollToItem((visiblePosts.lastIndex).coerceAtLeast(0))
            }
            FutachaAiAction.StartThreadSearch,
            FutachaAiAction.SearchThread -> {
                searchActive = true
                command.parameter("query", "q", "word", "text")?.let { searchQuery = it }
            }
            FutachaAiAction.NextSearchResult -> {
                if (searchMatches.isNotEmpty()) {
                    searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size
                    listState.animateScrollToItem(searchMatches[searchMatchIndex])
                }
            }
            FutachaAiAction.PreviousSearchResult -> {
                if (searchMatches.isNotEmpty()) {
                    searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size) % searchMatches.size
                    listState.animateScrollToItem(searchMatches[searchMatchIndex])
                }
            }
            FutachaAiAction.OpenGallery -> onOpenGallery()
            FutachaAiAction.OpenThreadSettings -> onOpenSettings()
            FutachaAiAction.OpenThreadExternally -> openUrl(tab.originalUrl)
            FutachaAiAction.SaveCurrentThread -> saveCompatPage("save_all")
            FutachaAiAction.DraftReply -> onOpenPost()
            FutachaAiAction.StartThreadReadAloud -> startReadAloud()
            FutachaAiAction.PauseThreadReadAloud -> stopReadAloud("読み上げを一時停止しました")
            FutachaAiAction.StopThreadReadAloud -> {
                readAloudCursor = 0
                readAloudCharacterOffset = 0
                stopReadAloud("読み上げを停止しました")
            }
            FutachaAiAction.NextThreadReadAloud -> moveReadAloudCursor(1)
            FutachaAiAction.PreviousThreadReadAloud -> moveReadAloudCursor(-1)
            else -> Unit
        }
        onPlatformAiCommandConsumed(command)
    }
    LaunchedEffect(scrollToBottomRequest) {
        if (scrollToBottomRequest != null && (visiblePosts.isNotEmpty() || threadFooterLabel != null)) {
            // The selector's current-tab tap is a bottom jump. Use the rendered
            // list, not the raw snapshot, because NG/deleted-post filtering may
            // make the raw last index invalid (or visibly not the last row).
            listState.scrollToItem(threadListLastIndex)
        }
    }
    LaunchedEffect(
        autoScrolling,
        autoScrollPixel,
        autoScrollSpeedMillis,
        autoScrollTouchGeneration,
        tab.key
    ) {
        if (autoScrolling && autoScrollTouchGeneration > 0) {
            delay(COMPAT_AUTO_SCROLL_TOUCH_PAUSE_MILLIS)
        }
        while (autoScrolling) {
            when (resolveCompatAutoScrollAction(listState.canScrollForward, tab.isDead)) {
                CompatAutoScrollAction.SCROLL -> {
                    listState.scrollBy(autoScrollPixel.toFloat())
                    delay(autoScrollSpeedMillis)
                }
                CompatAutoScrollAction.WAIT_FOR_RELOAD -> {
                    delay(COMPAT_AUTO_SCROLL_RELOAD_WAIT_MILLIS)
                    if (autoScrolling && !listState.canScrollForward) {
                        load(manual = false, refreshOnActivation = true)
                    }
                }
                CompatAutoScrollAction.STOP_DEAD -> {
                    autoScrolling = false
                    error = "オートスクロールを停止します(スレ落)"
                }
            }
        }
    }
    LaunchedEffect(searchQuery, searchMatches) {
        if (searchQuery.isEmpty()) {
            searchMatchIndex = 0
        } else if (searchMatches.isNotEmpty()) {
            // Query edits reset the index in onQueryChanged. On state restoration,
            // the snapshot can briefly be empty while it is reloaded. Do not erase
            // the saved result index during that gap; restore the same hit afterward.
            searchMatchIndex = searchMatchIndex.coerceIn(0, searchMatches.lastIndex)
            listState.scrollToItem(searchMatches[searchMatchIndex])
        }
    }
    // Android 8/10 IME may consume the first back event without invoking the
    // Compose BackHandler.  Track the visible-to-hidden transition so the
    // staged APK-compatible back behavior remains identical on compact and
    // full-size devices (three physical backs: hide IME, clear focus, close).
    // Only visibility matters; the IME height changes on every animation frame.
    val imeInsets = WindowInsets.ime
    val imeDensity = LocalDensity.current
    val searchImeVisible by remember(imeInsets, imeDensity) {
        derivedStateOf { imeInsets.getBottom(imeDensity) > 0 }
    }
    LaunchedEffect(searchActive, searchImeVisible) {
        if (!searchActive) {
            searchImeWasVisible = false
            searchFocusLostWhileImeVisible = false
        } else if (searchImeVisible) {
            searchImeWasVisible = true
        } else if (searchImeWasVisible && searchBackDismissStages == 2) {
            // All Android IMEs may consume the first physical Back while the
            // Compose handler is still armed.  The inset transition is the
            // reliable cross-API signal that the keyboard was dismissed; keep
            // the field visible for the next physical Back (focus clear), then
            // close search on the third.  The legacy API-specific branch below
            // only handles the inverse case where no inset transition is sent.
            // Samsung's Android 16 IME can briefly lose the restored TextField
            // focus while the restored keyboard is still visible. Its following
            // inset transition is a recreation artifact, not a user Back event;
            // do not consume the first APK-compatible Back stage in that case.
            if (!searchFocusLostWhileImeVisible) searchBackDismissStages = 1
            searchFocusLostWhileImeVisible = false
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            delay(3_000)
            error = null
        }
    }
    LaunchedEffect(manualRefreshNotice) {
        if (manualRefreshNotice != null) {
            delay(3_000)
            manualRefreshNotice = null
        }
    }
    PlatformBackHandler(
        enabled = replyPopupPosts.isNotEmpty(),
        iosEdgeGestureEnabled = false
    ) {
        replyPopupPosts = emptyList()
    }
    PlatformBackHandler(enabled = quoteStack.isNotEmpty(), iosEdgeGestureEnabled = false) {
        quoteStack = quoteStack.dropLast(1)
    }
    PlatformBackHandler(enabled = searchActive, iosEdgeGestureEnabled = false) {
        when (searchBackDismissStages) {
            2 -> {
                // API 26/29 can consume the first BACK in the platform IME without
                // dispatching it to Compose and without reporting an IME inset.
                // When the field is still focused, treat this callback as the
                // second physical BACK so the APK-compatible three-step sequence
                // remains stable on legacy devices.
                if (
                    isLegacyCompatImeBackBehavior() &&
                    !searchImeVisible && !searchImeWasVisible && searchFieldFocused
                ) {
                    // API 26/29's legacy IME consumes the first BACK and delivers
                    // the second to Compose. Hide it explicitly, then leave the
                    // field visible with stage=0 so the third physical BACK is
                    // the close action. Clearing focus here is ineffective on
                    // the old input connection and would collapse too early.
                    keyboardController?.hide()
                    searchBackDismissStages = 0
                } else {
                    keyboardController?.hide()
                    searchBackDismissStages = 1
                }
            }
            1 -> {
                focusManager.clearFocus(force = true)
                searchFieldFocused = false
                searchBackDismissStages = 0
            }
            else -> {
                searchActive = false
                searchQuery = ""
            }
        }
    }
    PlatformBackHandler(
        enabled = !searchActive && quoteStack.isEmpty(),
        iosEdgeGestureEnabled = false
    ) {
        onBack()
    }
    val drawerOpeningAllowed = !searchActive && quoteStack.isEmpty() &&
        replyPopupPosts.isEmpty() && contextPost == null && selectionState == null &&
        !extractionMenuOpen && !extractionKeywordOpen && otherMenuRoute == null
    SideEffect { onDrawerGestureAvailabilityChanged(drawerOpeningAllowed) }
    ThreadDrawerBackGestureHandler(
        enabled = !isDrawerOpen && drawerOpeningAllowed,
        onOpenDrawer = onOpenDrawer
    )
    LaunchedEffect(tab.key, listState) {
        snapshotFlow {
            val anchor = buildCurrentScrollAnchor(visiblePosts, snapshot)
            (scrollRestoreCompleted && visiblePosts.isNotEmpty()) to anchor
        }
            .distinctUntilChanged()
            .collectLatest { (ready, anchor) ->
                if (!ready) return@collectLatest
                // Keep the exact close/Undo anchor in the parent's non-observable
                // holder without invalidating the full workspace on every frame.
                onScrollAnchorObserved(tab.key, anchor)
                delay(500)
                persistScrollAnchor(anchor)
            }
    }
    val threadCommands = compatThreadToolbarCommands(
        tabs = tabs,
        preferences = preferences,
        store = store,
        scope = scope,
        listState = listState,
        threadListLastIndex = threadListLastIndex,
        threadNgEnabled = threadNgEnabled,
        threadPrivacyEnabled = threadPrivacyEnabled,
        onOpenPost = onOpenPost,
        onOpenGallery = onOpenGallery,
        onToggleSelector = onToggleSelector,
        onCheckUpdates = onCheckUpdates,
        onOpenDrawer = onOpenDrawer,
        loadManually = { load(manual = true) },
        scrollToNewRepliesOrBottom = ::scrollToNewRepliesOrBottom,
        closeCurrentThread = ::closeCurrentThread,
        launchStore = ::launchThreadStoreSafely,
        snapshotState = snapshotState,
        undoRefreshSnapshotState = undoRefreshSnapshotState,
        newReplyNoticeState = newReplyNoticeState,
        manualRefreshNoticeState = manualRefreshNoticeState,
        errorState = errorState,
        quoteStackState = quoteStackState,
        replyPopupPostsState = replyPopupPostsState,
        searchBackDismissStagesState = searchBackDismissStagesState,
        searchActiveState = searchActiveState,
        extractionMenuOpenState = extractionMenuOpenState,
        scrollDialogOpenState = scrollDialogOpenState,
        autoScrollingState = autoScrollingState
    )
    Scaffold(
        containerColor = CompatFutabaBackground,
        topBar = {
            Column {
                    if (searchActive) {
                        CompatSearchTopBar(
                            query = searchQuery,
                            matchIndex = searchMatchIndex,
                            matchCount = searchMatches.size,
                            onQueryChanged = {
                                searchQuery = it
                                searchMatchIndex = 0
                            },
                            onFocusChanged = { focused ->
                                if (!focused && searchImeVisible) {
                                    searchFocusLostWhileImeVisible = true
                                }
                                searchFieldFocused = focused
                                if (focused) searchBackDismissStages = 2
                            },
                            onPrevious = {
                                if (searchMatches.isNotEmpty()) {
                                    searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                    scope.launch { listState.scrollToItem(searchMatches[searchMatchIndex]) }
                                }
                            },
                            onNext = {
                                if (searchMatches.isNotEmpty()) {
                                    searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size
                                    scope.launch { listState.scrollToItem(searchMatches[searchMatchIndex]) }
                                }
                            },
                            onClose = {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                                searchFieldFocused = false
                                searchFocusLostWhileImeVisible = false
                                searchBackDismissStages = 0
                                searchActive = false
                                searchQuery = ""
                            }
                        )
                    } else {
                        CompatTopBar(
                            tab.title,
                            buildString {
                                if (tab.isDead) {
                                    append("落ち")
                                } else {
                                    append((snapshot?.posts?.size?.minus(1)?.coerceAtLeast(0)) ?: tab.replyCount)
                                    append("レス")
                                }
                                snapshot?.expiresAtLabel
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?.let { append(" ").append(it) }
                            },
                            onBack,
                            onOpenDrawer = onOpenDrawer,
                            isDrawerOpen = isDrawerOpen,
                            onCloseDrawer = onCloseDrawer,
                            onSearch = {
                                quoteStack = emptyList()
                                searchBackDismissStages = 2
                                searchActive = true
                            },
                            onToolbarEdit = onToolbarEdit,
                            onDisplayOptions = onOpenDisplayOptions,
                            onSettings = onOpenSettings,
                            onOpenHelp = onOpenHelp
                        )
                    }
                    CompatTitleStrip(tabs, tab)
                }
        },
        bottomBar = {
            Column {
                if (selectorOpen && selectorPresentation == SelectorPresentation.ABOVE) CompatTabSelector(
                    tabs = tabs,
                    currentTabKey = tab.key,
                    threadContext = true,
                    onSelect = ::persistCurrentScrollAndSelect,
                    onClose = onCloseTab,
                    onReply = onOpenPost,
                    onCheckUpdates = onCheckUpdates,
                    onReload = { scope.launch { load(manual = true) } },
                    longTapAction = selectorLongTapAction
                )
                CompatToolbar(
                    surface = CompatToolbarSurface.THREAD,
                    commands = threadCommands,
                    items = toolbarItems,
                    onOther = { otherMenuRoute = CompatOtherMenuRoute.THREAD_ROOT }
                )
            }
        }
    ) { padding ->
        val contentTopPx = with(LocalDensity.current) {
            padding.calculateTopPadding().roundToPx()
        }
        SideEffect { threadContentTopPx = contentTopPx }
        val drawerEdgeWidthPx = with(LocalDensity.current) {
            COMPAT_DRAWER_EDGE_GESTURE_WIDTH_DP.dp.toPx()
        }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("compat-thread-pager")
                    .clipToBounds()
                    .onSizeChanged { pagerWidthPx = it.width }
        ) {
            if (pagerNeighbor != null && pagerWidthPx > 0) {
                CompatThreadPagerNeighborPreview(
                    tab = pagerNeighbor,
                    snapshot = pagerNeighborSnapshot,
                    fontSize = threadFontSize,
                    thumbnailSize = threadThumbnailSize,
                    upsThumbnailSize = threadUpsThumbnailSize,
                    upsThumbnailMethod = threadUpsThumbMethod,
                    wifiConnected = compatWifiConnected,
                    privacyAlpha = if (threadPrivacyEnabled) {
                        compatPrivacyContentAlpha(threadPrivacyAlpha)
                    } else 1f,
                    hideDefaultNameAndSubject = hideDefaultNameAndSubject,
                    simpleQuoteCount = simpleQuoteCount,
                    saidaneDisplayMode = saidaneDisplayMode,
                    saidaneThreshold = saidaneExtractThreshold,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                (if (pagerOffset.value < 0f) pagerWidthPx else -pagerWidthPx) + pagerOffset.value.roundToInt(),
                                0
                            )
                        }
                )
            }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = pagerOffset.value }
                    // This must be attached to the visible thread surface.  A
                    // separate fillMaxSize sibling behind this box does not
                    // receive pointer input once the LazyColumn is drawn above
                    // it, which made the APK-compatible left-edge drawer swipe
                    // appear to work only on empty space.
                    .pointerInput(
                        tab.key,
                        tabs,
                        pagerWidthPx,
                        COMPAT_DRAWER_EDGE_GESTURE_WIDTH_DP
                    ) {
                        // Lock the gesture to the first dominant axis instead
                        // of letting a later diagonal move cancel the pager.
                        // This keeps a deliberate horizontal swipe alive while
                        // still returning ordinary vertical drags to the list.
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            val deferToDrawer = compatPagerShouldDeferToDrawer(
                                downX = down.position.x,
                                drawerEdgeWidthPx = drawerEdgeWidthPx
                            )
                            var totalDx = 0f
                            var totalDy = 0f
                            var horizontal = false
                            var rejected = false
                            var finished = false
                            var gesturePagerOffset = pagerOffset.value
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (horizontal && !deferToDrawer) settlePagerSwipe(gesturePagerOffset)
                                    finished = true
                                    break
                                }
                                // ModalNavigationDrawer's edge recognizer lives
                                // above this surface.  Do not consume any part
                                // of an edge-started gesture, or the pager will
                                // win whenever the active tab is not first.
                                if (deferToDrawer) continue
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalDx += dx
                                totalDy += dy
                                if (!horizontal && !rejected) {
                                    when (compatPagerGestureAxis(totalDx, totalDy, pagerTouchSlopPx)) {
                                        CompatPagerGestureAxis.HORIZONTAL -> horizontal = true
                                        CompatPagerGestureAxis.VERTICAL -> {
                                            // Once the drag is clearly vertical,
                                            // return it to LazyColumn/pull-refresh.
                                            finished = true
                                            break
                                        }
                                        CompatPagerGestureAxis.REJECTED -> rejected = true
                                        CompatPagerGestureAxis.UNDECIDED -> Unit
                                    }
                                }
                                if (horizontal || rejected) {
                                    change.consume()
                                }
                                if (horizontal) {
                                    val hasAdjacent = if (totalDx < 0f) nextTab != null else previousTab != null
                                    val resistance = if (hasAdjacent) 1f else 0.22f
                                    val width = pagerWidthPx.toFloat().coerceAtLeast(1f)
                                    gesturePagerOffset = (gesturePagerOffset + dx * resistance)
                                        .coerceIn(-width * 0.72f, width * 0.72f)
                                    pagerDragUpdateJob?.cancel()
                                    pagerDragUpdateJob = scope.launch {
                                        pagerOffset.snapTo(gesturePagerOffset)
                                    }
                                }
                            }
                            if (!finished && horizontal) settlePagerSwipe(gesturePagerOffset)
                        }
                    }
            ) {
                CompatBidirectionalPullRefresh(
                    enabled = threadPullRefreshEnabled,
                    refreshing = loading,
                    canScrollBackward = { listState.canScrollBackward },
                    canScrollForward = { listState.canScrollForward },
                    onRefresh = { load(manual = true) },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(touchScrollEnabled, listState) {
                            if (touchScrollEnabled) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (!change.pressed) {
                                            val viewport = listState.layoutInfo.viewportEndOffset -
                                                listState.layoutInfo.viewportStartOffset
                                            val action = compatTouchScrollAction(
                                                down.position.y,
                                                viewport.toFloat()
                                            )
                                            val page = (viewport * 0.82f).coerceAtLeast(1f)
                                            when (action) {
                                                CompatTouchScrollAction.PAGE_UP -> {
                                                    change.consume()
                                                    scope.launch { listState.scrollBy(-page) }
                                                }
                                                CompatTouchScrollAction.PAGE_DOWN -> {
                                                    change.consume()
                                                    scope.launch { listState.scrollBy(page) }
                                                }
                                                CompatTouchScrollAction.NONE -> Unit
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                        },
                    testTag = "compat-thread-pull-refresh"
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("compat-thread-list")
                            .pointerInput(autoScrolling, tab.key) {
                                if (autoScrolling) {
                                    awaitEachGesture {
                                        awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial
                                        )
                                        // sample/1.apk pauses the automatic
                                        // scroller for five seconds after any
                                        // touch, including links and headers.
                                        autoScrollTouchGeneration++
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                            },
                        // OVER is deliberately drawn above the thread pager.
                        // Leave one selector row after the final list item so
                        // the expiration footer can still be scrolled fully
                        // above it instead of being hidden behind the tabs.
                        contentPadding = PaddingValues(
                            bottom = if (
                                selectorOpen && selectorPresentation == SelectorPresentation.OVER
                            ) 40.dp else 0.dp
                        )
                    ) {
                        compatThreadNoticeForDisplay(snapshot?.deletedNotice)?.let { notice ->
                            item(key = "compat-thread-deleted-notice") {
                                Text(
                                    text = notice,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("compat-thread-deleted-notice")
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = Color.Red,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        itemsIndexed(visiblePosts, key = { _, post -> "${post.postNo}:${post.position}" }) { postIndex, post ->
                            val searchHit = searchHitsByIndex[postIndex]
                            CompatPostRow(
                                post,
                                ownPostNos = ownPostNos,
                                deletionSummary = deletionSummary.takeIf { post.postNo == snapshot?.posts?.firstOrNull()?.postNo },
                                fontSize = threadFontSize,
                                thumbnailSize = threadThumbnailSize,
                                upsThumbnailSize = threadUpsThumbnailSize,
                                upsThumbnailMethod = threadUpsThumbMethod,
                                wifiConnected = compatWifiConnected,
                        privacyAlpha = if (threadPrivacyEnabled) {
                            compatPrivacyContentAlpha(threadPrivacyAlpha)
                        } else 1f,
                                hideDefaultNameAndSubject = hideDefaultNameAndSubject,
                                boardDefaultText = boardDefaultText,
                                simpleQuoteCount = simpleQuoteCount,
                                saidaneDisplayMode = saidaneDisplayMode,
                                saidaneThreshold = saidaneExtractThreshold,
                                posterIdentityProgress = posterIdentityProgress[post.postNo].orEmpty(),
                                searchHit = searchHit != null,
                                searchRanges = searchHit?.textRanges.orEmpty(),
                                newReplyCount = newReplyNotice
                                    ?.takeIf { newReplyMarkerPostNo == post.postNo }
                                ?.count,
                                onClick = { if (openDrawerOnPostTap) onOpenDrawer() },
                                onQuoteClick = { query -> openQuotePopup(post.position, query) },
                                onUrlClick = openUrl,
                                onMediaUrlClick = { url ->
                                    if (!openViewerFromMediaUrl(url, post)) openUrl(url)
                                },
                                onLongClick = { contextPost = post },
                                onHeaderClick = { onHeaderClick(post) },
                                onHeaderLongClick = { onHeaderLongClick(post) },
                                thumbnailReloadToken = thumbnailReloadTokens[post.postNo] ?: 0L,
                                onMediaClick = { openViewerFromThread(post) },
                                onMediaLongClick = { mediaContextPost = post }
                            )
                        }
                        threadFooterLabel?.let { footerLabel ->
                            item(key = "compat-thread-footer") {
                                Text(
                                    text = footerLabel,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("compat-thread-footer")
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (tab.isDead) Color(0xFFB71C1C) else Color(0xFF8A8A8A)
                            )
                        }
                    }
                    }
                    // A tab change installs the new post list before the
                    // post-number/offset anchor can be applied.  Do not let
                    // that intermediate LazyColumn frame expose No.0 and the
                    // following posts; the reference pager keeps the page
                    // visually stable until restoration has completed.
                    if (snapshot != null && !scrollRestoreCompleted) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .zIndex(10f)
                        )
                    }
                    CompatFastScrollbar(
                        enabled = preferences.compatPreferenceValue(
                            "thread", "threadFastScroll", "高速スクロールバー"
                        ) == "ON",
                        totalItems = visiblePosts.size + if (threadFooterLabel != null) 1 else 0,
                        listState = listState
                    )
                }
                if (loading && snapshot == null) CompatLoadingIndicator(
                    style = designLoading,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("compat-thread-initial-loading"),
                    size = 50.dp
                )
                error?.let { Text(it, modifier = Modifier.align(Alignment.Center).background(Color(0xFF646464), RoundedCornerShape(22.dp)).padding(12.dp), color = Color.White) }
            }
            // The OVER selector is owned by the Activity-level workspace in
            // the reference app. Keep it outside the translated pager page so
            // it stays fixed during a horizontal thread swipe (#58).
            if (selectorOpen && selectorPresentation == SelectorPresentation.OVER) {
                CompatTabSelector(
                    tabs = tabs,
                    currentTabKey = tab.key,
                    threadContext = true,
                    onSelect = ::persistCurrentScrollAndSelect,
                    onClose = onCloseTab,
                    onReply = onOpenPost,
                    onCheckUpdates = onCheckUpdates,
                    onReload = { scope.launch { load(manual = true) } },
                    longTapAction = selectorLongTapAction,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            manualRefreshNotice?.let { notice ->
                Text(
                    text = notice.message(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("compat-manual-refresh-notice")
                        .background(
                            if (notice is CompatManualRefreshNotice.NewReplies) Color(0xFF80CBC4)
                            else Color(0xFF646464),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    color = Color.White
                )
            }
        }
    }
    if (scrollDialogOpen) {
        val lastIndex = threadListLastIndex
        val sliderDenominator = lastIndex.coerceAtLeast(1)
        val currentIndex = listState.firstVisibleItemIndex.coerceIn(0, lastIndex)
        Dialog(
            onDismissRequest = { scrollDialogOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f))
                    .clickable { scrollDialogOpen = false }
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .navigationBarsPadding().testTag("compat-thread-scroll-dialog")
                        .clickable(onClick = {}),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Column(Modifier.padding(horizontal = 40.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                modifier = Modifier.width(85.dp).height(50.dp),
                                onClick = {
                                    scrollDialogOpen = false
                                    scope.launch { listState.scrollToItem(0) }
                                }
                            ) { Text("トップ") }
                            TextButton(
                                modifier = Modifier.width(85.dp).height(50.dp),
                                onClick = {
                                    scrollDialogOpen = false
                                    scope.launch { listState.scrollToItem(lastIndex) }
                                }
                            ) { Text("最新レス") }
                        }
                        Slider(
                            value = currentIndex.toFloat() / sliderDenominator.toFloat(),
                            onValueChange = { value ->
                                scope.launch {
                                    listState.scrollToItem(
                                        (value * sliderDenominator).roundToInt().coerceIn(0, lastIndex)
                                    )
                                }
                            },
                            enabled = lastIndex > 0,
                            valueRange = 0f..1f
                        )
                    }
                }
            }
        }
    }
    if (readAloudDialogOpen) {
        CompatThreadSpeechDialog(
            post = readAloudDisplayPost,
            message = readAloudStatus,
            fontSize = threadFontSize,
            onDismiss = { stopReadAloud() }
        )
    }
    if (savingPage && visiblePageSaveProgress != null) {
        CompatThreadSaveProgressDialog(
            progress = visiblePageSaveProgress,
            cancelRequested = pageSaveCancelRequested,
            onCancel = {
                pageSaveCancelRequested = true
                pageSaveJob?.cancel()
            }
        )
    }
    otherMenuRoute?.let { route ->
        CompatThreadOtherMenuDialog(
            route = route,
            tab = tab,
            ngRules = ngRules,
            preferences = preferences,
            store = store,
            httpClient = httpClient,
            canUndoClose = canUndoClose,
            clipboard = clipboard,
            openUrl = openUrl,
            share = share,
            ownPostNos = ownPostNos,
            saidaneExtractThreshold = saidaneExtractThreshold,
            quoteExtractThreshold = quoteExtractThreshold,
            threadNgEnabled = threadNgEnabled,
            toolbarItems = toolbarItems,
            listState = listState,
            threadListLastIndex = threadListLastIndex,
            scope = scope,
            onCheckUpdates = onCheckUpdates,
            onUndoClose = onUndoClose,
            saveCompatPage = ::saveCompatPage,
            scrollToNewRepliesOrBottom = ::scrollToNewRepliesOrBottom,
            startReadAloud = { startReadAloud() },
            stopReadAloud = ::stopReadAloud,
            closeCurrentThread = ::closeCurrentThread,
            launchStore = ::launchThreadStoreSafely,
            otherMenuRouteState = otherMenuRouteState,
            managedNgKindsState = managedNgKindsState,
            errorState = errorState,
            snapshotState = snapshotState,
            quoteStackState = quoteStackState,
            replyPopupPostsState = replyPopupPostsState,
            searchBackDismissStagesState = searchBackDismissStagesState,
            searchActiveState = searchActiveState,
            extractionMenuOpenState = extractionMenuOpenState,
            extractionKeywordState = extractionKeywordState,
            extractionKeywordOpenState = extractionKeywordOpenState,
            readingAloudState = readingAloudState,
            autoScrollingState = autoScrollingState,
            archiveSearchOpenState = archiveSearchOpenState
        )
    }
    managedNgKinds?.let { kinds ->
        CompatThreadNgManagementDialog(
            kinds = kinds,
            tab = tab,
            ngRules = ngRules,
            store = store,
            imageNgPhashThreshold = imageNgPhashThreshold,
            errorState = errorState,
            managedNgKindsState = managedNgKindsState,
            launchStore = ::launchThreadStoreSafely
        )
    }
    if (archiveSearchOpen) {
        CompatArchiveSearchDialog(
            httpClient = httpClient,
            archiveScope = extractArchiveSearchScope(tab.originalUrl),
            archiveBaseUrl = archiveBaseUrl,
            localHistory = localHistory,
            initialSearchHistory = archiveSearchHistory,
            noticeHidden = archiveSearchNoticeHidden,
            onSearchHistoryChanged = { values ->
                launchThreadStoreSafely("archive search history persistence") {
                    store.savePreference(
                        COMPAT_ARCHIVE_SEARCH_HISTORY_KEY,
                        serializeCompatArchiveSearchHistory(values)
                    )
                }
            },
            onNoticeHidden = {
                launchThreadStoreSafely("archive search notice persistence") {
                    store.savePreference(COMPAT_ARCHIVE_SEARCH_NOTICE_HIDDEN_KEY, "ON")
                }
            },
            onDismiss = { archiveSearchOpen = false },
            onSelected = { item ->
                archiveSearchOpen = false
                onOpenArchiveItem(item)
            }
        )
    }
    headerExtractionPost?.let { post ->
        val kinds = compatHeaderExtractionKinds(post, snapshot?.posts.orEmpty())
        AlertDialog(
            onDismissRequest = { headerExtractionPost = null },
            title = { Text("抽出") },
            text = {
                Column {
                    kinds.forEach { kind ->
                        val label = when (kind) {
                            CompatHeaderExtractionKind.QUOTE -> "引用したレスを抽出"
                            CompatHeaderExtractionKind.ID ->
                                "${compatPosterIdentity(post)?.display ?: "ID"}を抽出"
                            CompatHeaderExtractionKind.IP ->
                                "${compatPosterIdentity(post)?.display ?: "IP"}を抽出"
                        }
                        TextButton(
                            onClick = {
                                headerExtractionPost = null
                                openHeaderExtraction(post, kind)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {}
        )
    }

    CompatThreadQuotePopups(
        ownPostNos = ownPostNos,
        threadFontSize = threadFontSize,
        threadThumbnailSize = threadThumbnailSize,
        threadUpsThumbnailSize = threadUpsThumbnailSize,
        threadUpsThumbMethod = threadUpsThumbMethod,
        compatWifiConnected = compatWifiConnected,
        hideDefaultNameAndSubject = hideDefaultNameAndSubject,
        simpleQuoteCount = simpleQuoteCount,
        saidaneDisplayMode = saidaneDisplayMode,
        saidaneExtractThreshold = saidaneExtractThreshold,
        threadPrivacyEnabled = threadPrivacyEnabled,
        threadPrivacyAlpha = threadPrivacyAlpha,
        reviewComplianceEnabled = reviewComplianceEnabled,
        preferences = preferences,
        openUrl = openUrl,
        openQuotePopup = ::openQuotePopup,
        openViewerFromMediaUrl = ::openViewerFromMediaUrl,
        openViewerFromThread = ::openViewerFromThread,
        onHeaderClick = ::onHeaderClick,
        onHeaderLongClick = ::onHeaderLongClick,
        replyPopupPostsState = replyPopupPostsState,
        replyPopupAnchorYState = replyPopupAnchorYState,
        threadContentTopPxState = threadContentTopPxState,
        posterIdentityProgressState = posterIdentityProgressState,
        quoteStackState = quoteStackState,
        snapshotState = snapshotState,
        errorState = errorState,
        contextPostState = contextPostState,
        mediaContextPostState = mediaContextPostState,
        reportPostState = reportPostState,
        delPostState = delPostState,
        deletePostState = deletePostState,
        deletePasswordState = deletePasswordState,
        deleteImageOnlyState = deleteImageOnlyState
    )
    contextPost?.let { post ->
        CompatThreadPostContextDialog(
            post = post,
            tab = tab,
            repository = repository,
            preferences = preferences,
            reviewComplianceEnabled = reviewComplianceEnabled,
            scope = scope,
            onOpenPostWithText = onOpenPostWithText,
            onHeaderLongClick = ::onHeaderLongClick,
            errorState = errorState,
            snapshotState = snapshotState,
            contextPostState = contextPostState,
            selectionStateHolder = selectionStateHolder,
            ngPostState = ngPostState,
            reportPostState = reportPostState,
            delPostState = delPostState,
            deletePostState = deletePostState,
            deletePasswordState = deletePasswordState,
            deleteImageOnlyState = deleteImageOnlyState
        )
    }
    delPost?.let { post ->
        AlertDialog(
            onDismissRequest = { delPost = null },
            title = { Text("削除依頼 No.${post.postNo}") },
            confirmButton = {
                TextButton(onClick = {
                    delPost = null
                    sendPostReport(post)
                }) { Text("送信する") }
            },
            dismissButton = {
                TextButton(onClick = { delPost = null }) { Text("キャンセル") }
            }
        )
    }
    reportPost?.let { post ->
        AlertDialog(
            onDismissRequest = { reportPost = null },
            title = { Text("不適切な投稿を通報") },
            text = {
                Text("No.${post.postNo} を不適切な投稿として、ふたば☆ちゃんねるの掲示板管理者へ通報します。")
            },
            confirmButton = {
                TextButton(onClick = {
                    reportPost = null
                    sendPostReport(post)
                }) { Text("通報する") }
            },
            dismissButton = {
                TextButton(onClick = { reportPost = null }) { Text("キャンセル") }
            }
        )
    }
    selectionState?.let { selection ->
        CompatThreadPostSelectionDialog(
            selection = selection,
            clipboard = clipboard,
            openUrl = openUrl,
            onOpenPostWithText = onOpenPostWithText,
            selectionStateHolder = selectionStateHolder,
            errorState = errorState
        )
    }
    ngPost?.let { post ->
        CompatPostNgDialog(
            post = post,
            onDismiss = { ngPost = null },
            onRegister = { kind, value, onlyThisThread ->
                val scopeKey = if (onlyThisThread) tab.key else "*"
                val displayValue = if (
                    kind == CompatNgKind.THREAD_REFUSE || kind == CompatNgKind.THREAD_IGNORE
                ) cleanCompatThreadReferenceWord(value, maxLength = 0) else value
                val storedValue = if (
                    kind == CompatNgKind.THREAD_REFUSE || kind == CompatNgKind.THREAD_IGNORE
                ) displayValue.normalizeCompatNgValue() else displayValue
                launchThreadStoreSafely("thread post NG registration", "NGの登録に失敗しました") {
                    val added = store.upsertNgRule(
                        CompatNgRule(
                            id = compatNgRuleId(kind, scopeKey, storedValue),
                            kind = kind,
                            scopeKey = scopeKey,
                            normalizedValue = storedValue,
                            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            memo = if (
                                kind == CompatNgKind.THREAD_REFUSE || kind == CompatNgKind.THREAD_IGNORE
                            ) displayValue else ""
                        )
                    )
                    if (added) error = "$displayValue\n登録しました"
                }
                ngPost = null
            }
        )
    }
    deletePost?.let { post ->
        AlertDialog(
            onDismissRequest = { deletePost = null },
            title = { Text("レス削除 No.${post.postNo}") },
            text = {
                Column {
                    TextField(
                        deletePassword,
                        { deletePassword = it.take(COMPAT_POST_DELETE_KEY_MAX_LENGTH) },
                        singleLine = true,
                        label = { Text("削除キー") }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(deleteImageOnly, { deleteImageOnly = it })
                        Text("画像だけ消す")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = deletePassword.isNotBlank(), onClick = {
                    val password = deletePassword
                    val imageOnly = deleteImageOnly
                    deletePost = null
                    launchThreadStoreSafely("post deletion", "削除処理に失敗しました") {
                        store.savePreference(
                            COMPAT_POST_DELETE_KEY_STORAGE_KEY,
                            compatPostDeleteKeyForStorage(password)
                        )
                        if (repository == null) error = "通信機能を初期化できませんでした"
                        else runSuspendCatchingPreservingCancellation {
                            repository.deleteByUser(tab.originalUrl, tab.threadNo, post.postNo, password, imageOnly)
                        }
                            .onSuccess {
                                val revised = snapshot?.let { current ->
                                    applyCompatOwnDeletion(
                                        snapshot = current,
                                        postNo = post.postNo,
                                        imageOnly = imageOnly,
                                        revision = Clock.System.now().toEpochMilliseconds()
                                    )
                                }
                                if (revised != null) {
                                    snapshot = revised
                                    store.saveThreadSnapshot(revised)
                                }
                                error = if (imageOnly) "画像削除を送信しました" else "削除を送信しました"
                                // The reference client follows the deletion request with a
                                // fresh response replacement. Bypass the compatibility cache
                                // so the local optimistic replacement is reconciled with the
                                // board as soon as the server has applied it.
                                scope.launch {
                                    delay(500)
                                    load(manual = true, bypassCache = true)
                                }
                            }
                            .onFailure { error = it.toCompatUserMessage("削除できませんでした") }
                    }
                }) { Text("送信する") }
            },
            dismissButton = { TextButton(onClick = { deletePost = null }) { Text("キャンセル") } }
        )
    }
    CompatThreadExtractionDialogs(
        tab = tab,
        ngRules = ngRules,
        ownPostNos = ownPostNos,
        saidaneExtractThreshold = saidaneExtractThreshold,
        quoteExtractThreshold = quoteExtractThreshold,
        scope = scope,
        extractionMenuOpenState = extractionMenuOpenState,
        extractionKeywordOpenState = extractionKeywordOpenState,
        extractionKeywordState = extractionKeywordState,
        snapshotState = snapshotState,
        quoteStackState = quoteStackState
    )
    mediaContextPost?.let { post ->
        CompatThreadMediaContextDialog(
            post = post,
            tab = tab,
            preferences = preferences,
            fileSystem = fileSystem,
            mediaSaver = mediaSaver,
            manualSaveLocation = manualSaveLocation,
            withSaveDestination = withSaveDestination,
            scope = scope,
            clipboard = clipboard,
            openUrl = openUrl,
            share = share,
            searchGoogle = ::searchGoogle,
            searchAscii2d = ::searchAscii2d,
            searchFileTarget = ::searchFileTarget,
            openSearchResult = ::openSearchResult,
            errorState = errorState,
            mediaContextPostState = mediaContextPostState,
            thumbnailReloadTokensState = thumbnailReloadTokensState,
            imageNgRegistrationState = imageNgRegistrationState,
            ascii2dRegistrationUrlState = ascii2dRegistrationUrlState,
            ascii2dRegisterPostState = ascii2dRegisterPostState
        )
    }
    imageNgRegistration?.let { post ->
        val mediaUrl = resolveCompatViewerMediaUrl(post).orEmpty()
        CompatImageNgRegistrationDialog(
            imageUrl = mediaUrl,
            initialMemo = post.messageHtml.toCompatPlainText().take(MAX_COMPAT_NG_MEMO_CHARS),
            onDismiss = { imageNgRegistration = null },
            onRegister = { memo, localOnly ->
                imageNgRegistration = null
                val scopeKey = compatThreadImageNgScopeKey(tab.boardKey, localOnly)
                val client = httpClient
                if (client == null) {
                    error = "通信機能を初期化できませんでした"
                } else {
                    error = "NG画像登録中"
                    launchThreadStoreSafely("thread image pHash NG registration", "画像NGの保存に失敗しました") {
                        fetchCompatImagePhash(client, mediaUrl)
                            .onSuccess { phash ->
                                store.upsertNgRule(
                                    CompatNgRule(
                                        id = compatNgRuleId(CompatNgKind.THREAD_IMAGE_PHASH, scopeKey, phash),
                                        kind = CompatNgKind.THREAD_IMAGE_PHASH,
                                        scopeKey = scopeKey,
                                        normalizedValue = phash,
                                        imageUrl = mediaUrl,
                                        memo = memo,
                                        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                                    )
                                )
                                error = "画像pHash NGに登録しました"
                            }
                            .onFailure { failure ->
                                error = failure.toCompatUserMessage("画像pHashを作成できませんでした")
                            }
                    }
                }
            }
        )
    }
    ascii2dRegisterPost?.let {
        CompatAscii2dRegistrationDialog(
            initialEndpoint = ascii2dRegistrationUrl,
            onDismiss = { ascii2dRegisterPost = null },
            onRegister = { endpoint ->
                launchThreadStoreSafely("ascii2d preference persistence", "画像検索設定の保存に失敗しました") {
                    store.savePreference(COMPAT_ASCII2D_ENDPOINT_KEY, endpoint)
                    store.savePreference(COMPAT_ASCII2D_ENABLED_KEY, "ON")
                }
                ascii2dRegisterPost = null
                error = "登録しました"
            },
            onInvalid = { error = "アドレスが間違っています" }
        )
    }
    reverseSearchResult?.let { result ->
        CompatReverseImageSearchScreen(
            result = result,
            cookieRepository = cookieRepository,
            onClose = { reverseSearchResult = null },
            onOpenExternal = openUrl
        )
    }
}

private const val COMPAT_THREAD_STORE_FAILURE_MESSAGE = "設定の保存に失敗しました"

// The thread screen's larger dialogs live outside CompatThreadScreen so its
// method stays well below the JVM size limit. They are non-restartable, so they
// recompose exactly as the inline code did, and they read the caller's states
// through the same MutableState objects.
@Composable
@NonRestartableComposable
private fun CompatThreadNgManagementDialog(
    kinds: Set<CompatNgKind>,
    tab: CompatTab,
    ngRules: List<CompatNgRule>,
    store: CompatibilityStore,
    imageNgPhashThreshold: Int,
    errorState: MutableState<String?>,
    managedNgKindsState: MutableState<Set<CompatNgKind>?>,
    launchStore: (String, String, suspend () -> Unit) -> Unit
) {
    var error by errorState
    var managedNgKinds by managedNgKindsState
    fun launchThreadStoreSafely(
        operation: String,
        userMessage: String = COMPAT_THREAD_STORE_FAILURE_MESSAGE,
        block: suspend () -> Unit
    ) = launchStore(operation, userMessage, block)
    val threadReferenceKind = when {
        CompatNgKind.THREAD_REFUSE in kinds -> CompatNgKind.THREAD_REFUSE
        CompatNgKind.THREAD_IGNORE in kinds -> CompatNgKind.THREAD_IGNORE
        else -> null
    }
    val imageReferenceSource = CompatImageNgSource.THREAD.takeIf {
        kinds.any { it in compatImageNgKinds(CompatImageNgSource.THREAD) }
    }
    val managementKinds = threadReferenceKind?.let(::compatThreadReferenceKinds) ?: kinds
    val managementRules = when {
        threadReferenceKind != null -> compatThreadReferenceRules(ngRules, tab.key, threadReferenceKind)
        imageReferenceSource != null -> compatImageNgManagementRules(
            ngRules,
            tab.boardKey,
            imageReferenceSource,
            legacyThreadKey = tab.key
        )
        else -> ngRules.filter { rule ->
            rule.kind in kinds && (rule.scopeKey == tab.key || rule.scopeKey == "*")
        }
    }
    CompatNgRuleManagementDialog(
        title = when {
            CompatNgKind.THREAD_IMAGE_PHASH in kinds -> "NG画像(pHash)"
            CompatNgKind.THREAD_IMAGE in kinds -> "NG画像"
            CompatNgKind.THREAD_IGNORE in kinds -> "ＮＧワード"
            CompatNgKind.THREAD_REFUSE in kinds -> "ＮＧヘッダー"
            CompatNgKind.THREAD_WORD in kinds -> "NGワード"
            else -> "NGヘッダー"
        },
        rules = managementRules,
        imageReferenceBoardName = tab.boardName.takeIf { imageReferenceSource != null },
        phashThreshold = imageNgPhashThreshold.takeIf {
            CompatNgKind.THREAD_IMAGE in kinds || CompatNgKind.THREAD_IMAGE_PHASH in kinds
        },
        onPhashThresholdChange = { value ->
            launchThreadStoreSafely("thread image threshold persistence") {
                store.savePreference(
                    compatPreferenceStorageKey("thread", "threadImageNgPhashThreshold"),
                    value.toString()
                )
            }
        },
        onDelete = { rule ->
            launchThreadStoreSafely("thread NG deletion") { store.deleteNgRule(rule.id) }
        },
        onDeleteAll = { rulesToDelete ->
            launchThreadStoreSafely("thread NG bulk deletion") {
                val ids = if (threadReferenceKind != null) {
                    ngRules.filter { it.kind in managementKinds }.map(CompatNgRule::id)
                } else {
                    rulesToDelete.map(CompatNgRule::id)
                }
                store.deleteNgRules(ids)
            }
        },
        onEdit = { rule, value, allThreads, memo ->
            val displayValue = when {
                threadReferenceKind != null -> cleanCompatThreadReferenceWord(value)
                imageReferenceSource != null -> rule.normalizedValue
                else -> value
            }
            val normalized = if (imageReferenceSource != null) {
                rule.normalizedValue
            } else {
                displayValue.normalizeCompatNgValue()
            }
            if (normalized.isBlank()) {
                error = if (threadReferenceKind != null) "単語を入力して下さい" else "NGに登録する値を入力してください"
            } else if (
                threadReferenceKind == CompatNgKind.THREAD_REFUSE &&
                isCompatThreadRefuseForbidden(displayValue)
            ) {
                error = "登録できない単語です"
            } else {
                launchThreadStoreSafely("thread NG edit", "NGの更新に失敗しました") {
                    val scopeKey = if (allThreads) "*" else if (
                        rule.kind == CompatNgKind.THREAD_IMAGE || rule.kind == CompatNgKind.THREAD_IMAGE_PHASH
                    ) tab.boardKey else tab.key
                    store.deleteNgRule(rule.id)
                    val updatedKind = threadReferenceKind ?: rule.kind
                    val updated = store.upsertNgRule(
                        rule.copy(
                            id = compatNgRuleId(updatedKind, scopeKey, normalized),
                            kind = updatedKind,
                            scopeKey = scopeKey,
                            normalizedValue = normalized,
                            memo = if (threadReferenceKind != null) displayValue else memo,
                            createdAtEpochMillis = if (imageReferenceSource != null) {
                                rule.createdAtEpochMillis
                            } else {
                                Clock.System.now().toEpochMilliseconds()
                            }
                        )
                    )
                    error = if (updated) {
                        if (imageReferenceSource != null) "更新しました" else "$displayValue に更新しました"
                    } else {
                        "NGを更新できませんでした"
                    }
                }
            }
        },
        addScopeLabel = "全スレッドに適用",
        onAdd = if (kinds.any { it == CompatNgKind.THREAD_IMAGE || it == CompatNgKind.THREAD_IMAGE_PHASH }) null else { value, allThreads ->
            val displayValue = if (threadReferenceKind != null) {
                cleanCompatThreadReferenceWord(value)
            } else value
            val normalized = displayValue.normalizeCompatNgValue()
            if (normalized.isBlank()) {
                error = if (threadReferenceKind != null) "単語を入力して下さい" else "NGに登録する値を入力してください"
            } else if (
                threadReferenceKind == CompatNgKind.THREAD_REFUSE &&
                isCompatThreadRefuseForbidden(displayValue)
            ) {
                error = "登録できない単語です"
            } else {
                launchThreadStoreSafely("thread NG add", "NGの登録に失敗しました") {
                    val scopeKey = if (allThreads) "*" else tab.key
                    val now = Clock.System.now().toEpochMilliseconds()
                    var added = false
                    (threadReferenceKind?.let(::setOf) ?: kinds).forEach { kind ->
                        added = store.upsertNgRule(
                            CompatNgRule(
                                id = compatNgRuleId(kind, scopeKey, normalized),
                                kind = kind,
                                scopeKey = scopeKey,
                                normalizedValue = normalized,
                                createdAtEpochMillis = now,
                                memo = if (threadReferenceKind != null) displayValue else ""
                            )
                        ) || added
                    }
                    error = if (added) "$displayValue を追加しました" else "これ以上登録できません！"
                }
            }
        },
        referenceKind = threadReferenceKind,
        onDismiss = { managedNgKinds = null }
    )
}

@Composable
@NonRestartableComposable
private fun CompatThreadQuotePopups(
    ownPostNos: Set<String>,
    threadFontSize: Int,
    threadThumbnailSize: Int,
    threadUpsThumbnailSize: Int,
    threadUpsThumbMethod: String,
    compatWifiConnected: Boolean,
    hideDefaultNameAndSubject: Boolean,
    simpleQuoteCount: Boolean,
    saidaneDisplayMode: String,
    saidaneExtractThreshold: Int,
    threadPrivacyEnabled: Boolean,
    threadPrivacyAlpha: Float,
    reviewComplianceEnabled: Boolean,
    preferences: Map<String, String>,
    openUrl: (String) -> Unit,
    openQuotePopup: (Int, String) -> Unit,
    openViewerFromMediaUrl: (String, CompatPostSnapshot) -> Boolean,
    openViewerFromThread: (CompatPostSnapshot) -> Boolean,
    onHeaderClick: (CompatPostSnapshot) -> Unit,
    onHeaderLongClick: (CompatPostSnapshot) -> Unit,
    replyPopupPostsState: MutableState<List<CompatPostSnapshot>>,
    replyPopupAnchorYState: MutableState<Int>,
    threadContentTopPxState: MutableState<Int>,
    posterIdentityProgressState: MutableState<Map<String, List<CompatPosterIdentityProgress>>>,
    quoteStackState: MutableState<List<CompatQuoteFrame>>,
    snapshotState: MutableState<CompatThreadSnapshot?>,
    errorState: MutableState<String?>,
    contextPostState: MutableState<CompatPostSnapshot?>,
    mediaContextPostState: MutableState<CompatPostSnapshot?>,
    reportPostState: MutableState<CompatPostSnapshot?>,
    delPostState: MutableState<CompatPostSnapshot?>,
    deletePostState: MutableState<CompatPostSnapshot?>,
    deletePasswordState: MutableState<String>,
    deleteImageOnlyState: MutableState<Boolean>
) {
    val quotePopupScope = rememberCoroutineScope()
    val quoteSearchJob = remember { mutableStateOf<Job?>(null) }
    var replyPopupPosts by replyPopupPostsState
    val replyPopupAnchorY by replyPopupAnchorYState
    val threadContentTopPx by threadContentTopPxState
    val posterIdentityProgress by posterIdentityProgressState
    var quoteStack by quoteStackState
    val snapshot by snapshotState
    var error by errorState
    var contextPost by contextPostState
    var mediaContextPost by mediaContextPostState
    var reportPost by reportPostState
    var delPost by delPostState
    var deletePost by deletePostState
    var deletePassword by deletePasswordState
    var deleteImageOnly by deleteImageOnlyState
    // A quote link in the old APK is a full-width, outside-dismissible
    // PopupWindow rather than a navigated extraction page.
    if (replyPopupPosts.isNotEmpty()) {
        CompatReplyPreviewPopup(
            posts = replyPopupPosts,
            ownPostNos = ownPostNos,
            fontSize = threadFontSize,
            thumbnailSize = threadThumbnailSize,
            upsThumbnailSize = threadUpsThumbnailSize,
            upsThumbnailMethod = threadUpsThumbMethod,
            wifiConnected = compatWifiConnected,
            anchorY = replyPopupAnchorY,
            // PopupWindow is constrained below the status/action bar in the
            // APK. Scaffold's content padding can be zero when the edge-to-edge
            // host consumes the inset, so retain an explicit 56dp toolbar floor.
            minimumTopY = maxOf(
                threadContentTopPx,
                with(LocalDensity.current) { 56.dp.roundToPx() }
            ),
            hideDefaultNameAndSubject = hideDefaultNameAndSubject,
            simpleQuoteCount = simpleQuoteCount,
            saidaneDisplayMode = saidaneDisplayMode,
            saidaneThreshold = saidaneExtractThreshold,
            privacyAlpha = if (threadPrivacyEnabled) {
                compatPrivacyContentAlpha(threadPrivacyAlpha)
            } else 1f,
            posterIdentityProgress = posterIdentityProgress,
            onDismiss = { replyPopupPosts = emptyList() },
            onQuoteClick = { sourcePosition, query ->
                // Text quotes convert every earlier post; search off the main
                // thread like the thread screen does, and drop the result if
                // the popup was closed or replaced meanwhile.
                val posts = snapshot?.posts.orEmpty()
                val origin = replyPopupPosts
                quoteSearchJob.value?.cancel()
                quoteSearchJob.value = quotePopupScope.launch {
                    val matches = withContext(AppDispatchers.parsing) {
                        resolveCompatQuotePosts(posts, sourcePosition, query)
                    }
                    if (replyPopupPosts !== origin) return@launch
                    if (matches.isEmpty()) {
                        compatMissingQuoteNotice()?.let { error = it }
                    } else {
                        replyPopupPosts = listOf(matches.first())
                    }
                }
            },
            onUrlClick = openUrl,
            onMediaUrlClick = { url, post ->
                if (!openViewerFromMediaUrl(url, post)) openUrl(url)
            },
            onLongClick = { contextPost = it },
            onHeaderClick = onHeaderClick,
            onHeaderLongClick = onHeaderLongClick,
            onMediaClick = { post -> openViewerFromThread(post) },
            onMediaLongClick = { mediaContextPost = it }
        )
    }
    quoteStack.lastOrNull()?.let { frame ->
        CompatExtractionResultPopup(
            frame = frame,
            ownPostNos = ownPostNos,
            fontSize = threadFontSize,
            thumbnailSize = threadThumbnailSize,
            upsThumbnailSize = threadUpsThumbnailSize,
            upsThumbnailMethod = threadUpsThumbMethod,
            wifiConnected = compatWifiConnected,
            minimumTopY = maxOf(
                threadContentTopPx,
                with(LocalDensity.current) { 56.dp.roundToPx() }
            ),
            hideDefaultNameAndSubject = hideDefaultNameAndSubject,
            simpleQuoteCount = simpleQuoteCount,
            saidaneDisplayMode = saidaneDisplayMode,
            saidaneThreshold = saidaneExtractThreshold,
            privacyAlpha = if (threadPrivacyEnabled) {
                compatPrivacyContentAlpha(threadPrivacyAlpha)
            } else 1f,
            posterIdentityProgress = posterIdentityProgress,
            onDismiss = { quoteStack = quoteStack.dropLast(1) },
            onQuoteClick = { sourcePosition, query -> openQuotePopup(sourcePosition, query) },
            onUrlClick = openUrl,
            onMediaUrlClick = { url, post ->
                if (!openViewerFromMediaUrl(url, post)) openUrl(url)
            },
            onPostClick = { post ->
                if (frame.query == "extract:${CompatExtractionKind.NG.name}") {
                    when (compatNgExtractionAction(isLongClick = false)) {
                        CompatNgExtractionAction.REQUEST_DEL -> {
                            if (reviewComplianceEnabled) reportPost = post else delPost = post
                        }
                        CompatNgExtractionAction.REQUEST_USER_DELETE -> Unit
                    }
                } else {
                    quoteStack = quoteStack.dropLast(1)
                }
            },
            onLongClick = { post ->
                if (frame.query == "extract:${CompatExtractionKind.NG.name}") {
                    when (compatNgExtractionAction(isLongClick = true)) {
                        CompatNgExtractionAction.REQUEST_USER_DELETE -> {
                            deletePassword = preferences.compatStoredPostDeleteKey()
                            deleteImageOnly = false
                            deletePost = post
                        }
                        CompatNgExtractionAction.REQUEST_DEL -> Unit
                    }
                } else {
                    contextPost = post
                }
            },
            onHeaderClick = onHeaderClick,
            onHeaderLongClick = onHeaderLongClick,
            onMediaClick = { post -> openViewerFromThread(post) },
            onMediaLongClick = { mediaContextPost = it }
        )
    }
}

@Composable
@NonRestartableComposable
private fun CompatThreadPostContextDialog(
    post: CompatPostSnapshot,
    tab: CompatTab,
    repository: BoardRepository?,
    preferences: Map<String, String>,
    reviewComplianceEnabled: Boolean,
    scope: CoroutineScope,
    onOpenPostWithText: (String, Boolean) -> Unit,
    onHeaderLongClick: (CompatPostSnapshot) -> Unit,
    errorState: MutableState<String?>,
    snapshotState: MutableState<CompatThreadSnapshot?>,
    contextPostState: MutableState<CompatPostSnapshot?>,
    selectionStateHolder: MutableState<CompatPostSelectionState?>,
    ngPostState: MutableState<CompatPostSnapshot?>,
    reportPostState: MutableState<CompatPostSnapshot?>,
    delPostState: MutableState<CompatPostSnapshot?>,
    deletePostState: MutableState<CompatPostSnapshot?>,
    deletePasswordState: MutableState<String>,
    deleteImageOnlyState: MutableState<Boolean>
) {
    var error by errorState
    var snapshot by snapshotState
    var contextPost by contextPostState
    var selectionState by selectionStateHolder
    var ngPost by ngPostState
    var reportPost by reportPostState
    var delPost by delPostState
    var deletePost by deletePostState
    var deletePassword by deletePasswordState
    var deleteImageOnly by deleteImageOnlyState
    CompatPostContextDialog(
        post = post,
        onDismiss = { contextPost = null },
        onWeb = {
            val terms = compatGoogleSearchTerms(post)
            selectionState = CompatPostSelectionState(
                CompatPostSelectionMode.WEB,
                post,
                terms.map { CompatPostActionCandidate("検索語", it) },
                emptySet()
            )
            contextPost = null
        },
        onExtract = {
            onHeaderLongClick(post)
            contextPost = null
        },
        onNg = { ngPost = post; contextPost = null },
        onDel = {
            contextPost = null
            if (reviewComplianceEnabled) {
                reportPost = post
            } else {
                delPost = post
            }
        },
        onDelete = {
            deletePassword = preferences.compatStoredPostDeleteKey()
            deleteImageOnly = false
            deletePost = post
            contextPost = null
        },
        onSaidane = {
            contextPost = null
            scope.launch {
                if (repository == null) error = "通信機能を初期化できませんでした"
                else runSuspendCatchingPreservingCancellation {
                    repository.voteSaidane(tab.originalUrl, tab.threadNo, post.postNo)
                }
                    .onSuccess {
                        val current = snapshot?.posts.orEmpty().firstOrNull { it.postNo == post.postNo }
                        val oldCount = compatAppTrailingCountRegex
                            .find(current?.saidaneLabel.orEmpty())
                            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        val nextCount = oldCount + 1
                        snapshot = snapshot?.copy(posts = snapshot?.posts.orEmpty().map {
                            if (it.postNo == post.postNo) it.copy(saidaneLabel = "そうだねx$nextCount") else it
                        })
                        error = "そうだねx$nextCount"
                    }
                    .onFailure { error = it.toCompatUserMessage("そうだねを送信できませんでした") }
            }
        },
        onQuick = { contextPost = null; onOpenPostWithText(compatQuickQuoteText(post), false) },
        onReply = {
            val candidates = compatPostActionCandidates(post)
            selectionState = CompatPostSelectionState(CompatPostSelectionMode.REPLY, post, candidates, emptySet())
            contextPost = null
        },
        onCopy = {
            val candidates = compatPostActionCandidates(post)
            selectionState = CompatPostSelectionState(CompatPostSelectionMode.COPY, post, candidates, emptySet())
            contextPost = null
        }
    )
}

@Composable
@NonRestartableComposable
private fun CompatThreadMediaContextDialog(
    post: CompatPostSnapshot,
    tab: CompatTab,
    preferences: Map<String, String>,
    fileSystem: FileSystem?,
    mediaSaver: SingleMediaSaveService?,
    manualSaveLocation: SaveLocation?,
    withSaveDestination: ((SaveLocation?) -> Unit) -> Unit,
    scope: CoroutineScope,
    clipboard: ClipboardManager,
    openUrl: (String) -> Unit,
    share: (String, String, String?) -> Unit,
    searchGoogle: (CompatPostSnapshot, CompatGoogleImageSearchMode) -> Unit,
    searchAscii2d: (CompatPostSnapshot) -> Unit,
    searchFileTarget: (CompatPostSnapshot, CompatImageSearchTarget) -> Unit,
    openSearchResult: (String, String) -> Unit,
    errorState: MutableState<String?>,
    mediaContextPostState: MutableState<CompatPostSnapshot?>,
    thumbnailReloadTokensState: MutableState<Map<String, Long>>,
    imageNgRegistrationState: MutableState<CompatPostSnapshot?>,
    ascii2dRegistrationUrlState: MutableState<String>,
    ascii2dRegisterPostState: MutableState<CompatPostSnapshot?>
) {
    var error by errorState
    var mediaContextPost by mediaContextPostState
    var thumbnailReloadTokens by thumbnailReloadTokensState
    var imageNgRegistration by imageNgRegistrationState
    var ascii2dRegistrationUrl by ascii2dRegistrationUrlState
    var ascii2dRegisterPost by ascii2dRegisterPostState
    val mediaUrl = resolveCompatViewerMediaUrl(post).orEmpty()
    CompatInlineMediaContextDialog(
        onDismiss = { mediaContextPost = null },
        onSave = {
            mediaContextPost = null
            withSaveDestination { selectedLocation ->
            scope.launch {
                val saver = mediaSaver
                if (saver == null) error = "保存機能を初期化できませんでした"
                else saver.saveMedia(
                    mediaUrl,
                    tab.boardKey,
                    tab.threadNo,
                    baseSaveLocation = selectedLocation,
                    storageDirectoryOverride = "",
                    useTypeSubdirectory = false
                )
                    .onSuccess { error = compatMediaSaveCompletionMessage(it, requireNotNull(fileSystem), selectedLocation) }
                    .onFailure { error = it.toCompatUserMessage("画像を保存できませんでした") }
            }
            }
        },
        onReloadThumbnail = {
            thumbnailReloadTokens = thumbnailReloadTokens + (post.postNo to Clock.System.now().toEpochMilliseconds())
            mediaContextPost = null
        },
        onNgImage = {
            imageNgRegistration = post
            mediaContextPost = null
        },
        onCopyUrl = { clipboard.setText(AnnotatedString(mediaUrl)); mediaContextPost = null; error = "コピーしました" },
        onBrowser = { openUrl(mediaUrl); mediaContextPost = null },
        onShareUrl = { share(mediaUrl, "text/plain", null); mediaContextPost = null },
        onShareImage = {
            mediaContextPost = null
            scope.launch {
                val saver = mediaSaver
                val fs = fileSystem
                if (saver == null || fs == null) error = "画像共有を初期化できませんでした"
                else saver.saveMedia(
                    mediaUrl,
                    tab.boardKey,
                    tab.threadNo,
                    baseSaveLocation = manualSaveLocation
                )
                    .onSuccess { saved ->
                        val mime = when (saved.mediaType.name) {
                            "VIDEO" -> "video/*"
                            else -> "image/*"
                        }
                        val localPath = if (manualSaveLocation == null) {
                            fs.resolveAbsolutePath("$MANUAL_SAVE_DIRECTORY/${saved.relativePath}")
                        } else null
                        share(mediaUrl, mime, localPath)
                    }
                    .onFailure { error = it.toCompatUserMessage("画像を共有できませんでした") }
            }
        },
        searchTargets = compatImageSearchActionTargets(
            preferences[COMPAT_CUSTOM_IMAGE_SEARCH_KEY]
        ),
        onSearchTarget = { target ->
            mediaContextPost = null
            when (target) {
                CompatImageSearchTarget.GOOGLE_FILE ->
                    searchGoogle(post, CompatGoogleImageSearchMode.GOOGLE_FILE)
                CompatImageSearchTarget.GOOGLE_URL ->
                    searchGoogle(post, CompatGoogleImageSearchMode.LEGACY)
                CompatImageSearchTarget.LENS_FILE ->
                    searchGoogle(post, CompatGoogleImageSearchMode.LENS_FILE)
                CompatImageSearchTarget.LENS_URL ->
                    searchGoogle(post, CompatGoogleImageSearchMode.LENS_URL)
                CompatImageSearchTarget.ASCII2D_URL -> {
                    if (!isCompatAscii2dRegistered(preferences)) {
                        ascii2dRegistrationUrl = preferences[COMPAT_ASCII2D_ENDPOINT_KEY]
                            ?.trim().orEmpty()
                        ascii2dRegisterPost = post
                    } else {
                        searchAscii2d(post)
                    }
                }
                else -> if (target.method == CompatImageSearchMethod.FILE) {
                    searchFileTarget(post, target)
                } else if (!isCompatImageSearchableMediaUrl(mediaUrl)) {
                    error = "WebM・MP4は検索できません"
                } else {
                    buildCompatImageSearchTargetUrl(target, mediaUrl)?.let {
                        openSearchResult(it, target.label)
                    } ?: run { error = "検索する画像がありません" }
                }
            }
        }
    )
}

@Composable
@NonRestartableComposable
private fun compatThreadToolbarCommands(
    tabs: List<CompatTab>,
    preferences: Map<String, String>,
    store: CompatibilityStore,
    scope: CoroutineScope,
    listState: LazyListState,
    threadListLastIndex: Int,
    threadNgEnabled: Boolean,
    threadPrivacyEnabled: Boolean,
    onOpenPost: () -> Unit,
    onOpenGallery: () -> Unit,
    onToggleSelector: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenDrawer: () -> Unit,
    loadManually: suspend () -> Unit,
    scrollToNewRepliesOrBottom: suspend () -> Unit,
    closeCurrentThread: () -> Unit,
    launchStore: (String, String, suspend () -> Unit) -> Unit,
    snapshotState: MutableState<CompatThreadSnapshot?>,
    undoRefreshSnapshotState: MutableState<CompatThreadSnapshot?>,
    newReplyNoticeState: MutableState<CompatNewReplyNotice?>,
    manualRefreshNoticeState: MutableState<CompatManualRefreshNotice?>,
    errorState: MutableState<String?>,
    quoteStackState: MutableState<List<CompatQuoteFrame>>,
    replyPopupPostsState: MutableState<List<CompatPostSnapshot>>,
    searchBackDismissStagesState: MutableState<Int>,
    searchActiveState: MutableState<Boolean>,
    extractionMenuOpenState: MutableState<Boolean>,
    scrollDialogOpenState: MutableState<Boolean>,
    autoScrollingState: MutableState<Boolean>
): List<CompatToolbarCommand> {
    var snapshot by snapshotState
    var undoRefreshSnapshot by undoRefreshSnapshotState
    var newReplyNotice by newReplyNoticeState
    var manualRefreshNotice by manualRefreshNoticeState
    var error by errorState
    var quoteStack by quoteStackState
    var replyPopupPosts by replyPopupPostsState
    var searchBackDismissStages by searchBackDismissStagesState
    var searchActive by searchActiveState
    var extractionMenuOpen by extractionMenuOpenState
    var scrollDialogOpen by scrollDialogOpenState
    var autoScrolling by autoScrollingState
    fun launchThreadStoreSafely(
        operation: String,
        userMessage: String = COMPAT_THREAD_STORE_FAILURE_MESSAGE,
        block: suspend () -> Unit
    ) = launchStore(operation, userMessage, block)
    return listOf(
        CompatToolbarCommand("post", compatToolbarArtwork(CompatToolbarSurface.THREAD, "post"), "書き込み", onClick = onOpenPost),
        CompatToolbarCommand("reload", compatToolbarArtwork(CompatToolbarSurface.THREAD, "reload"), "リロード") { scope.launch { loadManually() } },
        CompatToolbarCommand(
            "undo",
            compatToolbarArtwork(CompatToolbarSurface.THREAD, "undo"),
            "リロード前に戻す",
            onClick = undoRefreshSnapshot?.let {
                {
                    snapshot = it
                    undoRefreshSnapshot = null
                    newReplyNotice = null
                    manualRefreshNotice = null
                    error = "リロード前の表示に戻しました"
                }
            }
        ),
        CompatToolbarCommand("search", compatToolbarArtwork(CompatToolbarSurface.THREAD, "search"), "レス検索") {
            quoteStack = emptyList()
            replyPopupPosts = emptyList()
            searchBackDismissStages = 2
            searchActive = true
        },
        CompatToolbarCommand("top", compatToolbarArtwork(CompatToolbarSurface.THREAD, "top"), "ページ最上部へ") { scope.launch { listState.scrollToItem(0) } },
        CompatToolbarCommand("page_up", compatToolbarArtwork(CompatToolbarSurface.THREAD, "page_up"), "1ページ上へ") {
            scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex - 6).coerceAtLeast(0)) }
        },
        CompatToolbarCommand("page_down", compatToolbarArtwork(CompatToolbarSurface.THREAD, "page_down"), "1ページ下へ") {
            scope.launch {
                val last = threadListLastIndex
                listState.animateScrollToItem((listState.firstVisibleItemIndex + 6).coerceAtMost(last))
            }
        },
        CompatToolbarCommand("bottom", compatToolbarArtwork(CompatToolbarSurface.THREAD, "bottom"), "ページ最下部へ") {
            scope.launch {
                scrollToNewRepliesOrBottom()
            }
        },
        CompatToolbarCommand("gallery", compatToolbarArtwork(CompatToolbarSurface.THREAD, "gallery"), "画像一覧", onClick = onOpenGallery),
        CompatToolbarCommand(
            "tab",
            compatToolbarArtwork(
                CompatToolbarSurface.THREAD,
                "tab",
                selected = hasCompatTabToolbarUpdate(tabs)
            ),
            "タブ一覧",
            showUpdateBadge = hasCompatTabToolbarUpdate(tabs),
            onClick = onToggleSelector
        ),
        CompatToolbarCommand("privacy", compatToolbarArtwork(CompatToolbarSurface.THREAD, "privacy"), "プライバシー") {
            launchThreadStoreSafely("thread privacy persistence") {
                store.savePreference(COMPAT_COMMON_PRIVACY_STORAGE_KEY, if (threadPrivacyEnabled) "OFF" else "ON")
            }
        },
        CompatToolbarCommand("extract", compatToolbarArtwork(CompatToolbarSurface.THREAD, "extract"), "レス抽出") { extractionMenuOpen = true },
        CompatToolbarCommand(
            "bypass",
            compatToolbarArtwork(
                CompatToolbarSurface.THREAD,
                "bypass",
                selected = preferences[COMPAT_CACHE_ENABLED_KEY] != "ON"
            ),
            "通信の軽量化"
        ) {
            val enabled = preferences[COMPAT_CACHE_ENABLED_KEY] == "ON"
            val toggle = nextCompatCacheToggle(enabled)
            launchThreadStoreSafely("thread cache preference persistence") {
                store.savePreference(COMPAT_CACHE_ENABLED_KEY, toggle.storedValue)
                error = toggle.message
            }
        },
        CompatToolbarCommand("scroll", compatToolbarArtwork(CompatToolbarSurface.THREAD, "scroll"), "スクロールバー") {
            scrollDialogOpen = true
        },
        CompatToolbarCommand("check", compatToolbarArtwork(CompatToolbarSurface.THREAD, "check"), "更新の確認") {
            error = "開いているスレの更新を確認しています"
            onCheckUpdates()
        },
        CompatToolbarCommand("close", compatToolbarArtwork(CompatToolbarSurface.THREAD, "close"), "スレを閉じる") { closeCurrentThread() },
        CompatToolbarCommand(
            "quickng",
            compatToolbarArtwork(CompatToolbarSurface.THREAD, "quickng", selected = threadNgEnabled),
            "NG切り替え",
            onClick = {
                launchThreadStoreSafely("thread NG preference persistence") {
                    store.savePreference(
                        compatPreferenceStorageKey("thread", "threadNg"),
                        if (threadNgEnabled) "OFF" else "ON"
                    )
                }
            }
        ),
        CompatToolbarCommand("drawer", compatToolbarArtwork(CompatToolbarSurface.THREAD, "drawer"), "ドロワーを開く", onClick = onOpenDrawer),
        CompatToolbarCommand(
            "autoscroll",
            compatToolbarArtwork(
                CompatToolbarSurface.THREAD,
                "autoscroll",
                selected = autoScrolling
            ),
            "オートスクロール",
            selected = autoScrolling
        ) {
            autoScrolling = !autoScrolling
        }
    )
}

@Composable
@NonRestartableComposable
private fun CompatThreadOtherMenuDialog(
    route: CompatOtherMenuRoute,
    tab: CompatTab,
    ngRules: List<CompatNgRule>,
    preferences: Map<String, String>,
    store: CompatibilityStore,
    httpClient: HttpClient?,
    canUndoClose: Boolean,
    clipboard: ClipboardManager,
    openUrl: (String) -> Unit,
    share: (String, String, String?) -> Unit,
    ownPostNos: Set<String>,
    saidaneExtractThreshold: Int,
    quoteExtractThreshold: Int,
    threadNgEnabled: Boolean,
    toolbarItems: List<CompatToolbarItem>,
    listState: LazyListState,
    threadListLastIndex: Int,
    scope: CoroutineScope,
    onCheckUpdates: () -> Unit,
    onUndoClose: () -> Unit,
    saveCompatPage: (String) -> Unit,
    scrollToNewRepliesOrBottom: suspend () -> Unit,
    startReadAloud: () -> Unit,
    stopReadAloud: (String?) -> Unit,
    closeCurrentThread: () -> Unit,
    launchStore: (String, String, suspend () -> Unit) -> Unit,
    otherMenuRouteState: MutableState<CompatOtherMenuRoute?>,
    managedNgKindsState: MutableState<Set<CompatNgKind>?>,
    errorState: MutableState<String?>,
    snapshotState: MutableState<CompatThreadSnapshot?>,
    quoteStackState: MutableState<List<CompatQuoteFrame>>,
    replyPopupPostsState: MutableState<List<CompatPostSnapshot>>,
    searchBackDismissStagesState: MutableState<Int>,
    searchActiveState: MutableState<Boolean>,
    extractionMenuOpenState: MutableState<Boolean>,
    extractionKeywordState: MutableState<String>,
    extractionKeywordOpenState: MutableState<Boolean>,
    readingAloudState: MutableState<Boolean>,
    autoScrollingState: MutableState<Boolean>,
    archiveSearchOpenState: MutableState<Boolean>
) {
    var otherMenuRoute by otherMenuRouteState
    var managedNgKinds by managedNgKindsState
    var error by errorState
    val snapshot by snapshotState
    var quoteStack by quoteStackState
    var replyPopupPosts by replyPopupPostsState
    var searchBackDismissStages by searchBackDismissStagesState
    var searchActive by searchActiveState
    var extractionMenuOpen by extractionMenuOpenState
    var extractionKeyword by extractionKeywordState
    var extractionKeywordOpen by extractionKeywordOpenState
    val readingAloud by readingAloudState
    var autoScrolling by autoScrollingState
    var archiveSearchOpen by archiveSearchOpenState
    fun launchThreadStoreSafely(
        operation: String,
        userMessage: String = COMPAT_THREAD_STORE_FAILURE_MESSAGE,
        block: suspend () -> Unit
    ) = launchStore(operation, userMessage, block)
    val menuItems = compatThreadOtherMenu(
        route = route,
        ngEnabled = threadNgEnabled,
        canUndoClose = canUndoClose,
        ngCount = ngRules.count { it.scopeKey == tab.key || it.scopeKey == "*" },
        cacheEnabled = preferences[COMPAT_CACHE_ENABLED_KEY] == "ON",
        activeToolbarKeys = toolbarItems.filter(CompatToolbarItem::active).mapTo(mutableSetOf()) { it.key }
    )
    CompatHierarchicalOtherMenuDialog(
        route = route,
        items = menuItems,
        onDismiss = { otherMenuRoute = null },
        onItem = { menuItem ->
            menuItem.childRoute?.let { child ->
                otherMenuRoute = child
                return@CompatHierarchicalOtherMenuDialog
            }
            otherMenuRoute = null
            when (menuItem.key) {
                "save_html", "save_thumb", "save_all", "save_images_zip", "save_images_folder" ->
                    saveCompatPage(menuItem.key)
                "top" -> scope.launch { listState.scrollToItem(0) }
                "page_up" -> scope.launch {
                    listState.animateScrollToItem((listState.firstVisibleItemIndex - 6).coerceAtLeast(0))
                }
                "page_down" -> scope.launch {
                    listState.animateScrollToItem(
                        (listState.firstVisibleItemIndex + 6).coerceAtMost(threadListLastIndex)
                    )
                }
                "bottom" -> scope.launch {
                    scrollToNewRepliesOrBottom()
                }
                "ng_header" -> managedNgKinds = setOf(CompatNgKind.THREAD_POST_NO, CompatNgKind.THREAD_POSTER_ID)
                "ng_word" -> managedNgKinds = setOf(CompatNgKind.THREAD_WORD)
                "ng_ignore" -> managedNgKinds = setOf(CompatNgKind.THREAD_IGNORE)
                "ng_refuse" -> managedNgKinds = setOf(CompatNgKind.THREAD_REFUSE)
                "ng_image" -> managedNgKinds = setOf(CompatNgKind.THREAD_IMAGE)
                "ng_image_phash" -> managedNgKinds = setOf(CompatNgKind.THREAD_IMAGE_PHASH)
                "ng_toggle" -> scope.launch {
                    store.savePreference(
                        compatPreferenceStorageKey("thread", "threadNg"),
                        if (threadNgEnabled) "OFF" else "ON"
                    )
                }
                "url_browser" -> openUrl(tab.originalUrl)
                "url_copy" -> {
                    clipboard.setText(AnnotatedString(tab.originalUrl))
                    error = "URLをコピーしました"
                }
                "url_share" -> share(tab.originalUrl, "text/plain", null)
                "url_ftbucket" -> openUrl(buildCompatFtbucketUrl(tab.originalUrl))
                "url_forest" -> buildCompatForestUrl(tab.originalUrl)?.let(openUrl)
                    ?: run { error = "ふたばフォレストはmay板のスレだけ対応しています" }
                "url_futapo" -> buildCompatFutapoUrl(tab.originalUrl)?.let(openUrl)
                    ?: run { error = "ふたポのURLを作成できませんでした（may/img板のみ対応）" }
                "url_tsumamne" -> scope.launch {
                    registerCompatTsumanne(httpClient ?: run {
                        error = "通信機能を初期化できませんでした"
                        return@launch
                    }, tab.originalUrl, tab.title)
                        .onSuccess { openUrl(it); error = "つまんね。に登録しました" }
                        .onFailure { error = it.toCompatUserMessage("つまんね。への登録に失敗しました") }
                }
                "search" -> {
                    quoteStack = emptyList()
                    replyPopupPosts = emptyList()
                    searchBackDismissStages = 2
                    searchActive = true
                }
                "extract" -> extractionMenuOpen = true
                "extract_own_direct", "extract_own" -> openCompatExtraction(
                    scope,
                    CompatExtractionKind.OWN,
                    "自分の書き込み",
                    snapshot,
                    tab,
                    ngRules,
                    ownPostNos,
                    saidaneExtractThreshold,
                    quoteExtractThreshold
                ) { frame -> quoteStack = quoteStack + frame }
                "extract_saidane" -> openCompatExtraction(
                    scope,
                    CompatExtractionKind.MANY_SAIDANE,
                    "そうだねが多い",
                    snapshot,
                    tab,
                    ngRules,
                    ownPostNos,
                    saidaneExtractThreshold,
                    quoteExtractThreshold
                ) { frame -> quoteStack = quoteStack + frame }
                "extract_replies" -> openCompatExtraction(
                    scope,
                    CompatExtractionKind.MANY_REPLIES,
                    "返信が多い",
                    snapshot,
                    tab,
                    ngRules,
                    ownPostNos,
                    saidaneExtractThreshold,
                    quoteExtractThreshold
                ) { frame -> quoteStack = quoteStack + frame }
                "extract_deleted" -> openCompatExtraction(
                    scope,
                    CompatExtractionKind.DELETED,
                    "削除されたレス",
                    snapshot,
                    tab,
                    ngRules,
                    ownPostNos,
                    saidaneExtractThreshold,
                    quoteExtractThreshold
                ) { frame -> quoteStack = quoteStack + frame }
                "extract_url" -> openCompatExtraction(
                    scope,
                    CompatExtractionKind.CONTAINS_URL,
                    "URLを含むレス",
                    snapshot,
                    tab,
                    ngRules,
                    ownPostNos,
                    saidaneExtractThreshold,
                    quoteExtractThreshold
                ) { frame -> quoteStack = quoteStack + frame }
                "extract_image" -> openCompatExtraction(
                    scope,
                    CompatExtractionKind.HAS_IMAGE,
                    "画像レス",
                    snapshot,
                    tab,
                    ngRules,
                    ownPostNos,
                    saidaneExtractThreshold,
                    quoteExtractThreshold
                ) { frame -> quoteStack = quoteStack + frame }
                "extract_ng" -> openCompatExtraction(
                    scope,
                    CompatExtractionKind.NG,
                    "NGにマッチしたレス (タップでdel、長押しで削除)",
                    snapshot,
                    tab,
                    ngRules,
                    ownPostNos,
                    saidaneExtractThreshold,
                    quoteExtractThreshold
                ) { frame -> quoteStack = quoteStack + frame }
                "extract_keyword" -> {
                    extractionKeyword = ""
                    extractionKeywordOpen = true
                }
                "read_aloud" -> {
                    if (readingAloud) {
                        stopReadAloud("読み上げを停止しました")
                    } else {
                        startReadAloud()
                    }
                }
                "autoscroll" -> autoScrolling = !autoScrolling
                "cache" -> archiveSearchOpen = true
                "privacy" -> launchThreadStoreSafely("thread privacy persistence") {
                    val enabled = preferences.compatPrivacyEnabled()
                    store.savePreference(COMPAT_COMMON_PRIVACY_STORAGE_KEY, if (enabled) "OFF" else "ON")
                }
                "bypass" -> {
                    val enabled = preferences[COMPAT_CACHE_ENABLED_KEY] == "ON"
                    val toggle = nextCompatCacheToggle(enabled)
                    launchThreadStoreSafely("thread cache preference persistence") {
                        store.savePreference(COMPAT_CACHE_ENABLED_KEY, toggle.storedValue)
                        error = toggle.message
                    }
                }
                "check" -> {
                    error = "開いているスレの更新を確認しています"
                    onCheckUpdates()
                }
                "close" -> closeCurrentThread()
                "undo" -> onUndoClose()
            }
        }
    )
}

@Composable
@NonRestartableComposable
private fun CompatThreadPostSelectionDialog(
    selection: CompatPostSelectionState,
    clipboard: ClipboardManager,
    openUrl: (String) -> Unit,
    onOpenPostWithText: (String, Boolean) -> Unit,
    selectionStateHolder: MutableState<CompatPostSelectionState?>,
    errorState: MutableState<String?>
) {
    var selectionState by selectionStateHolder
    var error by errorState
    CompatPostSelectionDialog(
        state = selection,
        onStateChanged = { selectionState = it },
        onDismiss = { selectionState = null },
        onSearch = {
            val query = selection.candidates.mapIndexedNotNull { i, item -> item.value.takeIf { i in selection.selected } }.joinToString(" ")
            if (query.isNotBlank()) openUrl("https://www.google.com/search?q=${query.encodeURLParameter()}")
            selectionState = null
        },
        onOverwrite = {
            onOpenPostWithText(compatQuoteSelection(selection.candidates, selection.selected), false)
            selectionState = null
        },
        onAppend = {
            onOpenPostWithText(compatQuoteSelection(selection.candidates, selection.selected), true)
            selectionState = null
        },
        onCopy = {
            val text = if (selection.mode == CompatPostSelectionMode.COPY) {
                selection.candidates.mapIndexedNotNull { i, item -> item.value.takeIf { i in selection.selected } }.joinToString("\n")
            } else compatQuoteSelection(selection.candidates, selection.selected)
            clipboard.setText(AnnotatedString(text))
            selectionState = null
            error = "コピーしました"
        }
    )
}

@Composable
@NonRestartableComposable
private fun CompatThreadExtractionDialogs(
    tab: CompatTab,
    ngRules: List<CompatNgRule>,
    ownPostNos: Set<String>,
    saidaneExtractThreshold: Int,
    quoteExtractThreshold: Int,
    scope: CoroutineScope,
    extractionMenuOpenState: MutableState<Boolean>,
    extractionKeywordOpenState: MutableState<Boolean>,
    extractionKeywordState: MutableState<String>,
    snapshotState: MutableState<CompatThreadSnapshot?>,
    quoteStackState: MutableState<List<CompatQuoteFrame>>
) {
    var extractionMenuOpen by extractionMenuOpenState
    var extractionKeywordOpen by extractionKeywordOpenState
    var extractionKeyword by extractionKeywordState
    val snapshot by snapshotState
    var quoteStack by quoteStackState
    if (extractionMenuOpen) {
        CompatExtractionMenuDialog(
            ngCount = ngRules.count { it.scopeKey == tab.key || it.scopeKey == "*" },
            onDismiss = { extractionMenuOpen = false },
            onKeyword = {
                extractionMenuOpen = false
                extractionKeyword = ""
                extractionKeywordOpen = true
            },
            onExtract = { kind, title ->
                extractionMenuOpen = false
                scope.launch {
                    val matches = withContext(AppDispatchers.parsing) {
                        extractCompatPosts(
                            posts = snapshot?.posts.orEmpty(),
                            kind = kind,
                            scopeKey = tab.key,
                            boardKey = tab.boardKey,
                            ngRules = ngRules,
                            ownPostNos = ownPostNos,
                            saidaneThreshold = saidaneExtractThreshold,
                            quoteThreshold = quoteExtractThreshold
                        )
                    }
                    quoteStack = quoteStack + CompatQuoteFrame(title, "extract:${kind.name}", matches)
                }
            }
        )
    }
    if (extractionKeywordOpen) {
        AlertDialog(
            onDismissRequest = { extractionKeywordOpen = false },
            title = { Text("キーワード") },
            text = {
                TextField(
                    extractionKeyword,
                    { extractionKeyword = it },
                    singleLine = true,
                    modifier = Modifier.testTag("compat-thread-extraction-keyword")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val keyword = extractionKeyword
                    extractionKeywordOpen = false
                    scope.launch {
                        val matches = withContext(AppDispatchers.parsing) {
                            extractCompatPosts(
                                posts = snapshot?.posts.orEmpty(),
                                kind = CompatExtractionKind.KEYWORD,
                                scopeKey = tab.key,
                                keyword = keyword
                            )
                        }
                        quoteStack = quoteStack + CompatQuoteFrame("キーワード: $keyword", "extract:keyword", matches)
                    }
                }) { Text("検索する") }
            },
            dismissButton = { TextButton(onClick = { extractionKeywordOpen = false }) { Text("キャンセル") } }
        )
    }
}

// Keep this state/effect outside the large thread composable. D8's release
// register allocation otherwise emitted an invalid continuation reference in
// CompatThreadScreen, causing VerifyError before compatibility mode could open.
@Composable
private fun rememberCompatThreadToolbarItems(
    tabKey: String,
    initialToolbarItems: List<CompatToolbarItem>?,
    toolbarRefreshToken: Long,
    store: CompatibilityStore
): List<CompatToolbarItem> {
    var items by remember(tabKey, initialToolbarItems) {
        mutableStateOf(
            initialToolbarItems
                ?: reconcileCompatToolbar(CompatToolbarSurface.THREAD, emptyList())
        )
    }
    LaunchedEffect(toolbarRefreshToken, initialToolbarItems) {
        try {
            items = initialToolbarItems ?: store.loadToolbar(CompatToolbarSurface.THREAD)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatibilityThread", "Failed to load thread toolbar", failure)
        }
    }
    return items
}

@Composable
private fun CompatCatalogSearchTopBar(
    query: String,
    resultCount: Int,
    onQueryChanged: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequester) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    TopAppBar(
        expandedHeight = 56.dp,
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                placeholder = { Text("検索文字", fontSize = 20.sp) },
                trailingIcon = { if (query.isNotBlank()) Text("${resultCount}件", fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 8.dp)
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedPlaceholderColor = Color(0xFF80CBC4),
                    unfocusedPlaceholderColor = Color(0xFF80CBC4)
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "検索を閉じる") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CompatTeal,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White
        )
    )
}

@Composable
private fun CompatCatalogSortDialog(
    selected: CompatCatalogSort,
    onDismiss: () -> Unit,
    onSelected: (CompatCatalogSort) -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    CompatBottomPopup(
        alignment = Alignment.BottomCenter,
        onDismiss = onDismiss
    ) {
        CompatCatalogSort.entries.forEach { sort ->
            TextButton(
                onClick = { onSelected(sort) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    sort.displayLabel,
                    modifier = Modifier.fillMaxWidth(),
                    color = compatibilityPopupContent(palette)
                )
            }
        }
    }
}

internal val CompatCatalogSort.displayLabel: String
    get() = when (this) {
        CompatCatalogSort.CATALOG -> "カタログ"
        CompatCatalogSort.NEW -> "新しい順"
        CompatCatalogSort.OLD -> "古い順"
        CompatCatalogSort.MANY -> "多い順"
        CompatCatalogSort.FEW -> "少ない順"
        CompatCatalogSort.LIVELY -> "勢い順"
    }

private fun CompatCatalogSort.toCatalogMode(): CatalogMode = when (this) {
    CompatCatalogSort.CATALOG -> CatalogMode.Catalog
    CompatCatalogSort.NEW -> CatalogMode.New
    CompatCatalogSort.OLD -> CatalogMode.Old
    CompatCatalogSort.MANY -> CatalogMode.Many
    CompatCatalogSort.FEW -> CatalogMode.Few
    CompatCatalogSort.LIVELY -> CatalogMode.Momentum
}

private data class CompatToolbarCommand(
    val key: String,
    val icon: CompatToolbarArtwork,
    val label: String,
    val selected: Boolean = false,
    val showUpdateBadge: Boolean = false,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun CompatToolbar(
    surface: CompatToolbarSurface? = null,
    commands: List<CompatToolbarCommand>,
    items: List<CompatToolbarItem>? = null,
    onOther: (() -> Unit)? = null,
    refreshingCommandKeys: Set<String> = emptySet(),
    iconSize: Dp = 24.dp
) {
    val byKey = commands.associateBy(CompatToolbarCommand::key)
    val visibleCommands = items
        ?.filter(CompatToolbarItem::active)
        ?.sortedBy(CompatToolbarItem::position)
        ?.mapNotNull { byKey[it.key] }
        ?: commands
    val renderedCommands = if (onOther != null) {
        visibleCommands + CompatToolbarCommand(
            "other",
            compatToolbarArtwork(surface ?: CompatToolbarSurface.CATALOG, "other"),
            "その他",
            onClick = onOther
        )
    } else {
        visibleCommands
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(CompatTeal).navigationBarsPadding()
            .testTag("compat-main-bottom-bar")
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            renderedCommands.forEach { command ->
                val refreshing = command.key in refreshingCommandKeys
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .testTag("compat-toolbar-command-${command.key}")
                        .clickable(
                        enabled = command.onClick != null && !refreshing,
                        role = Role.Button,
                        onClickLabel = command.label,
                        onClick = command.onClick ?: {}
                    ).semantics {
                        if (command.showUpdateBadge) stateDescription = "更新あり"
                    },
                    contentAlignment = Alignment.Center
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).semantics {
                                contentDescription = "更新中"
                            },
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        CompatToolbarArtworkIcon(
                            artwork = command.icon,
                            contentDescription = command.label,
                            tint = when {
                                command.onClick == null -> Color.White.copy(alpha = 0.38f)
                                command.selected -> Color(0xFFFFEB3B)
                                else -> Color.White
                            },
                            modifier = Modifier.size(iconSize).testTag("compat-toolbar-icon-${command.key}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompatToolbarOverflowDialog(
    commands: List<CompatToolbarCommand>,
    items: List<CompatToolbarItem>,
    onDismiss: () -> Unit
) {
    val commandsByKey = commands.associateBy(CompatToolbarCommand::key)
    val inactive = items.sortedBy(CompatToolbarItem::position)
        .filterNot(CompatToolbarItem::active)
        .mapNotNull { commandsByKey[it.key] }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("その他") },
        text = {
            Column {
                if (inactive.isEmpty()) Text("ツールバー外の操作はありません")
                inactive.forEach { command ->
                    TextButton(
                        enabled = command.onClick != null,
                        onClick = {
                            onDismiss()
                            command.onClick?.invoke()
                        }
                    ) {
                        CompatToolbarArtworkIcon(
                            artwork = command.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(command.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
private fun CompatHierarchicalOtherMenuDialog(
    route: CompatOtherMenuRoute,
    items: List<CompatOtherMenuItem>,
    onDismiss: () -> Unit,
    onItem: (CompatOtherMenuItem) -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    val visibleItems = if (route == CompatOtherMenuRoute.CATALOG_ROOT || route == CompatOtherMenuRoute.THREAD_ROOT) {
        items.filter(CompatOtherMenuItem::enabled)
    } else {
        items
    }
    CompatBottomPopup(onDismiss = onDismiss) {
        if (route == CompatOtherMenuRoute.CATALOG_ROOT || route == CompatOtherMenuRoute.THREAD_ROOT) {
            com.valoser.futacha.shared.ui.media.DeviceImageEditorMenuItem(onDismiss)
            com.valoser.futacha.shared.ui.media.DeviceVideoEditorMenuItem(onDismiss)
        }
        visibleItems.forEach { item ->
            TextButton(
                enabled = item.enabled,
                onClick = { onItem(item) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = buildString {
                        append(item.label)
                        if (item.childRoute != null) append("  ›")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = compatibilityPopupContent(palette)
                )
            }
        }
    }
}
private val compatAppPostNumberRegex = Regex("[0-9]+")
private val compatAppDefaultPostLabelRegex = Regex("No\\.\\d+", RegexOption.IGNORE_CASE)
private val compatAppTrailingCountRegex = Regex("(\\d+)\\s*$")

private data class CompatQuoteFrame(val title: String, val query: String, val posts: List<CompatPostSnapshot>)

/**
 * The legacy viewer keeps extraction results in a full-width PopupWindow below
 * the thread action bar. It does not navigate away from the thread, and an
 * outside tap dismisses the window.
 */
private class CompatExtractionPopupPositionProvider(
    private val minimumTopY: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxTop = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            0,
            minimumTopY.coerceIn(0, maxTop)
        )
    }
}

private fun openCompatExtraction(
    scope: CoroutineScope,
    kind: CompatExtractionKind,
    title: String,
    snapshot: CompatThreadSnapshot?,
    tab: CompatTab,
    ngRules: List<CompatNgRule>,
    ownPostNos: Set<String>,
    saidaneThreshold: Int,
    quoteThreshold: Int,
    onFrame: (CompatQuoteFrame) -> Unit
) {
    scope.launch {
        val matches = withContext(AppDispatchers.parsing) {
            extractCompatPosts(
                posts = snapshot?.posts.orEmpty(),
                kind = kind,
                scopeKey = tab.key,
                boardKey = tab.boardKey,
                ngRules = ngRules,
                ownPostNos = ownPostNos,
                saidaneThreshold = saidaneThreshold,
                quoteThreshold = quoteThreshold
            )
        }
        onFrame(CompatQuoteFrame(title, "extract:${kind.name}", matches))
    }
}

private enum class CompatPostSelectionMode { WEB, REPLY, COPY }

private data class CompatPostSelectionState(
    val mode: CompatPostSelectionMode,
    val post: CompatPostSnapshot,
    val candidates: List<CompatPostActionCandidate>,
    val selected: Set<Int>
)

@Composable
private fun CompatExtractionResultPopup(
    frame: CompatQuoteFrame,
    ownPostNos: Set<String>,
    fontSize: Int,
    thumbnailSize: Int,
    upsThumbnailSize: Int = thumbnailSize,
    upsThumbnailMethod: String? = null,
    wifiConnected: Boolean = false,
    minimumTopY: Int = 0,
    hideDefaultNameAndSubject: Boolean,
    simpleQuoteCount: Boolean,
    saidaneDisplayMode: String,
    saidaneThreshold: Int,
    privacyAlpha: Float = 1f,
    posterIdentityProgress: Map<String, List<CompatPosterIdentityProgress>> = emptyMap(),
    onDismiss: () -> Unit,
    onQuoteClick: (Int, String) -> Unit,
    onUrlClick: (String) -> Unit,
    onMediaUrlClick: (String, CompatPostSnapshot) -> Unit,
    onPostClick: (CompatPostSnapshot) -> Unit,
    onLongClick: (CompatPostSnapshot) -> Unit,
    onHeaderClick: (CompatPostSnapshot) -> Unit,
    onHeaderLongClick: (CompatPostSnapshot) -> Unit,
    onMediaClick: (CompatPostSnapshot) -> Unit,
    onMediaLongClick: (CompatPostSnapshot) -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val extractionHeight = with(density) {
        if (windowHeightPx > 0) {
            val bottomSafety = 40.dp.roundToPx()
            val minimumHeight = 240.dp.roundToPx()
            (windowHeightPx - minimumTopY - bottomSafety)
                .coerceAtLeast(minimumHeight)
                .toDp()
        } else {
            480.dp
        }
    }
    Popup(
        popupPositionProvider = remember(minimumTopY) {
            CompatExtractionPopupPositionProvider(minimumTopY)
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(extractionHeight)
                .testTag("compat-extraction-popup"),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column {
                Text(
                    text = frame.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.background)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("compat-extraction-popup-title"),
                    color = palette.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = CompatDivider)
                if (frame.posts.isEmpty()) {
                    Text(
                        text = "該当するレスはありません",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = palette.text
                    )
                } else {
                    // Lazy: an image-reply or common-word extraction on a 1000-reply
                    // thread can match hundreds of posts; only visible rows are built.
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("compat-extraction-popup-list")) {
                        items(frame.posts, key = { "${it.position}:${it.postNo}" }) { post ->
                            CompatPostRow(
                                post = post,
                                ownPostNos = ownPostNos,
                                fontSize = fontSize,
                                thumbnailSize = thumbnailSize,
                                upsThumbnailSize = upsThumbnailSize,
                                upsThumbnailMethod = upsThumbnailMethod,
                                wifiConnected = wifiConnected,
                                privacyAlpha = privacyAlpha,
                                hideDefaultNameAndSubject = hideDefaultNameAndSubject,
                                simpleQuoteCount = simpleQuoteCount,
                                saidaneDisplayMode = saidaneDisplayMode,
                                saidaneThreshold = saidaneThreshold,
                                onClick = { onPostClick(post) },
                                posterIdentityProgress = posterIdentityProgress[post.postNo].orEmpty(),
                                onQuoteClick = { query -> onQuoteClick(post.position, query) },
                                onUrlClick = onUrlClick,
                                onMediaUrlClick = { url -> onMediaUrlClick(url, post) },
                                onLongClick = { onLongClick(post) },
                                onHeaderClick = { onHeaderClick(post) },
                                onHeaderLongClick = { onHeaderLongClick(post) },
                                onMediaClick = { onMediaClick(post) },
                                onMediaLongClick = { onMediaLongClick(post) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompatPostSelectionDialog(
    state: CompatPostSelectionState,
    onStateChanged: (CompatPostSelectionState) -> Unit,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onOverwrite: () -> Unit,
    onAppend: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.mode == CompatPostSelectionMode.WEB) "Google検索" else "レス欄に…") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = state.selected.size < state.candidates.size,
                        onClick = { onStateChanged(state.copy(selected = state.candidates.indices.toSet())) }
                    ) { Text("全選択") }
                    TextButton(
                        enabled = state.selected.isNotEmpty(),
                        onClick = { onStateChanged(state.copy(selected = emptySet())) }
                    ) { Text("全解除") }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(state.candidates.size) { index ->
                        val item = state.candidates[index]
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val selected = state.selected.toMutableSet()
                                if (!selected.add(index)) selected.remove(index)
                                onStateChanged(state.copy(selected = selected))
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = index in state.selected,
                                onCheckedChange = {
                                    val selected = state.selected.toMutableSet()
                                    if (it) selected.add(index) else selected.remove(index)
                                    onStateChanged(state.copy(selected = selected))
                                }
                            )
                            if (state.mode == CompatPostSelectionMode.WEB) {
                                Text(item.value, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            } else {
                                Column {
                                    Text(item.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(item.value, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.mode == CompatPostSelectionMode.WEB) {
                TextButton(onClick = onSearch) { Text("検索する") }
            } else {
                Row {
                    TextButton(enabled = state.selected.isNotEmpty(), onClick = onOverwrite) { Text("上書き") }
                    TextButton(enabled = state.selected.isNotEmpty(), onClick = onAppend) { Text("追加") }
                    TextButton(enabled = state.selected.isNotEmpty(), onClick = onCopy) { Text("コピー") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun CompatHistoryMetadataRow(
    entry: CompatHistoryEntry,
    openTab: CompatTab?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    val readCount = openTab?.checkedReplyCount ?: entry.replyCount
    val latestCount = maxOf(entry.replyCount, openTab?.replyCount ?: 0)
    val reply = compatDrawerReplyPresentation(readCount, latestCount)
    val noThumb = painterResource(Res.drawable.cmn_no_thumb)
    Row(
        modifier = Modifier.fillMaxWidth().height(COMPAT_REFERENCE_DRAWER_THREAD_ROW_DP.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .testTag("compat-drawer-history-row-${entry.canonicalUrl}")
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.thumbnailUrl,
            contentDescription = null,
            fallback = noThumb,
            error = noThumb,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(COMPAT_REFERENCE_DRAWER_THREAD_THUMBNAIL_DP.dp)
                .testTag("compat-drawer-history-thumb-${entry.canonicalUrl}")
        )
        Spacer(Modifier.width(5.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title.lineSequence().firstOrNull().orEmpty(), maxLines = 1, fontSize = 16.sp, color = palette.text)
            Text(
                compatDrawerThreadSubtitle(entry.lastVisitedEpochMillis, entry.boardName),
                maxLines = 1,
                fontSize = 12.sp,
                color = palette.uiSecondaryText
            )
        }
        Column(
            Modifier.width(50.dp).testTag("compat-drawer-history-replies-${entry.canonicalUrl}"),
            horizontalAlignment = Alignment.End
        ) {
            Text(reply.readCount, maxLines = 1, fontSize = 16.sp, color = Color(0xFF00897B))
            Text(reply.increase, maxLines = 1, fontSize = 12.sp, color = Color.Red)
        }
    }
    HorizontalDivider()
}

@Composable
private fun CompatNavigationDrawer(
    page: CompatDrawerPage,
    tabs: List<CompatTab>,
    histories: List<CompatHistoryEntry>,
    externalWatcherSnapshot: CompatExternalWatcherSnapshot,
    pendingClose: ClosedTabBatch?,
    onPageSelected: (CompatDrawerPage) -> Unit,
    onTabSelected: (CompatTab) -> Unit,
    onHistorySelected: (CompatHistoryEntry) -> Unit,
    onTabFavoriteToggle: (CompatTab) -> Unit,
    onTabsClosed: (Set<String>) -> Unit,
    onCheckDeadTabs: suspend (Boolean) -> Set<String>,
    onHistoryDeleted: (CompatHistoryEntry) -> Unit,
    onHistoryCleared: () -> Unit,
    onExternalWatcherSelected: (CompatExternalWatcherEntry) -> Unit,
    onExternalWatcherDelete: (CompatExternalWatcherEntry) -> Unit,
    onExternalWatcherDeleteAll: () -> Unit,
    onOpenExternalWatcherManager: () -> Unit,
    onRefreshExternalWatcher: () -> Unit,
    onRefreshAllTabs: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onUndoClose: () -> Unit
) {
    var contextTab by remember { mutableStateOf<CompatTab?>(null) }
    var contextHistory by remember { mutableStateOf<CompatHistoryEntry?>(null) }
    var contextExternalWatcher by remember { mutableStateOf<CompatExternalWatcherEntry?>(null) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    var checkingDeadTabs by remember { mutableStateOf(false) }
    val deadTabCheckScope = rememberCoroutineScope()
    val uniqueTabs = remember(tabs) { distinctCompatTabs(tabs) }
    val uniqueHistories = remember(histories) { distinctCompatHistory(histories) }
    // The reference APK keeps the activity action bar visible while the
    // drawer is open. Reserve that 56dp row outside the drawer surface so
    // the history heading and list start at the same vertical position.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .compatReferenceStatusBarPadding()
            .padding(top = 56.dp)
    ) {
        ModalDrawerSheet(
            modifier = Modifier.width(320.dp).fillMaxSize(),
            drawerShape = RoundedCornerShape(0.dp),
            drawerContainerColor = LocalCompatibilityPalette.current.background,
            windowInsets = WindowInsets()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .background(LocalCompatibilityPalette.current.chrome),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            compatDrawerHeaderTitle(page, externalWatcherSnapshot),
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                    HorizontalDivider()
                    if (page == CompatDrawerPage.WATCHER) {
                        TextButton(onClick = onOpenExternalWatcherManager, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("巡回管理")
                        }
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        when (page) {
                            CompatDrawerPage.TABS -> {
                                items(uniqueTabs.take(50), key = { it.key }) { tab ->
                                    CompatDismissibleDrawerRow(
                                        itemKey = tab.key,
                                        onDismissed = { onTabsClosed(setOf(tab.key)) },
                                        allowRightSwipeOnly = true
                                    ) {
                                        CompatThreadMetadataRow(
                                            tab = tab,
                                            onClick = { onTabSelected(tab) },
                                            onLongClick = { contextTab = tab }
                                        )
                                    }
                                }
                            }
                            CompatDrawerPage.HISTORY -> {
                                items(uniqueHistories.take(100), key = { it.canonicalUrl }) { entry ->
                                    CompatDismissibleDrawerRow(
                                        itemKey = entry.canonicalUrl,
                                        onDismissed = { onHistoryDeleted(entry) },
                                        allowRightSwipeOnly = true
                                    ) {
                                        CompatHistoryMetadataRow(
                                            entry = entry,
                                            openTab = uniqueTabs.firstOrNull { it.canonicalUrl == entry.canonicalUrl },
                                            onClick = { onHistorySelected(entry) },
                                            onLongClick = { contextHistory = entry }
                                        )
                                    }
                                }
                            }
                            CompatDrawerPage.WATCHER -> {
                                if (externalWatcherSnapshot.available && externalWatcherSnapshot.message != null) {
                                    item {
                                        Text(
                                            externalWatcherSnapshot.message,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            color = LocalCompatibilityPalette.current.uiSecondaryText,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                val externalEntries = externalWatcherSnapshot.entries.take(500)
                                itemsIndexed(
                                    externalEntries,
                                    key = { index, entry ->
                                        // A stale provider database can contain
                                        // duplicate/empty keys.  Keep the
                                        // displayed row stable enough for the
                                        // drawer, but never let malformed
                                        // watcher data crash LazyColumn.
                                        "external-watcher-${entry.key}-${entry.threadUrl}:$index"
                                    }
                                ) { index, entry ->
                                    CompatDismissibleDrawerRow(
                                        itemKey = "external-watcher-${entry.key}-${entry.threadUrl}:$index",
                                        onDismissed = { onExternalWatcherDelete(entry) },
                                        allowRightSwipeOnly = true
                                    ) {
                                        CompatExternalWatcherMetadataRow(
                                            entry = entry,
                                            onClick = { onExternalWatcherSelected(entry) },
                                            onLongClick = { contextExternalWatcher = entry }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    pendingClose?.let { batch ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF323232),
                            contentColor = Color.White,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    closedThreadUndoMessage(batch),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = onUndoClose) {
                                    Text(
                                        "元に戻す",
                                        color = LocalCompatibilityPalette.current.closedThreadUndoAction,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    CompatToolbar(
                        commands = listOf(
                            CompatToolbarCommand("tabs", compatDrawerToolbarArtwork("tabs"), "開いているタブ") { onPageSelected(CompatDrawerPage.TABS) },
                            CompatToolbarCommand("history", compatDrawerToolbarArtwork("history"), "履歴") { onPageSelected(CompatDrawerPage.HISTORY) },
                            CompatToolbarCommand("watcher", compatDrawerToolbarArtwork("watcher"), "巡回結果") {
                                onPageSelected(CompatDrawerPage.WATCHER)
                                onRefreshExternalWatcher()
                            },
                            CompatToolbarCommand("check_all", compatDrawerToolbarArtwork("check_all"), "全タブ更新確認") {
                                onRefreshAllTabs()
                                transientMessage = "全タブの更新確認を開始しました"
                            },
                            CompatToolbarCommand("settings", compatDrawerToolbarArtwork("settings"), "設定", onClick = onOpenSettings)
                        ),
                        iconSize = COMPAT_REFERENCE_DRAWER_TOOLBAR_ICON_DP.dp
                    )
                }
                transientMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 64.dp)
                            .background(Color(0xFF646464), RoundedCornerShape(18.dp))
                            .padding(8.dp),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    LaunchedEffect(message, checkingDeadTabs) {
                        if (!checkingDeadTabs) {
                            delay(2_000)
                            transientMessage = null
                        }
                    }
                }
            }
        }
    }
    contextTab?.let { selected ->
        var protectFavorites by remember(selected.key) {
            mutableStateOf(COMPAT_REFERENCE_DRAWER_PROTECT_FAVORITES_DEFAULT)
        }
        fun closeCandidates(action: CompatDrawerTabCloseAction) {
            if (action == CompatDrawerTabCloseAction.DEAD) {
                if (checkingDeadTabs) return
                val protect = protectFavorites
                contextTab = null
                checkingDeadTabs = true
                transientMessage = "スレッドの生存確認中…"
                deadTabCheckScope.launch {
                    try {
                        val keys = onCheckDeadTabs(protect)
                        if (keys.isNotEmpty()) onTabsClosed(keys)
                        transientMessage = if (keys.isEmpty()) {
                            "削除できる落ちスレは確認できませんでした"
                        } else {
                            "落ちたスレを${keys.size}件閉じました"
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        transientMessage = failure.toCompatUserMessage("生存確認に失敗しました")
                    } finally {
                        checkingDeadTabs = false
                    }
                }
                return
            }
            val keys = compatDrawerTabCloseKeys(
                tabs = uniqueTabs,
                selectedKey = selected.key,
                action = action,
                protectFavorites = protectFavorites
            )
            contextTab = null
            if (keys.isNotEmpty()) onTabsClosed(keys)
        }
        CompatLegacyChoiceDialog(
            onDismiss = { contextTab = null },
            choices = compatDrawerTabContextLabels(),
            testTag = "compat-drawer-tab-context-menu",
            onChoice = { choice ->
                when (choice) {
                    "お気に入り" -> onTabFavoriteToggle(selected)
                    "削除する" -> closeCandidates(CompatDrawerTabCloseAction.SELECTED)
                    "下のスレを全て削除する" -> closeCandidates(CompatDrawerTabCloseAction.BELOW)
                    "他のスレを全て削除する" -> closeCandidates(CompatDrawerTabCloseAction.OTHERS)
                    "落ちたスレを削除する" -> closeCandidates(CompatDrawerTabCloseAction.DEAD)
                    "全て削除する" -> closeCandidates(CompatDrawerTabCloseAction.ALL)
                }
            },
            footer = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = protectFavorites,
                        onCheckedChange = { protectFavorites = it },
                        modifier = Modifier.testTag("compat-drawer-protect-favorites")
                    )
                    Text("お気に入りを保護する", fontSize = 14.sp)
                }
            }
        )
    }
    contextHistory?.let { selected ->
        CompatLegacyChoiceDialog(
            onDismiss = { contextHistory = null },
            choices = listOf("削除する", "全て削除する"),
            onChoice = { choice ->
                if (choice == "削除する") onHistoryDeleted(selected) else onHistoryCleared()
            }
        )
    }
    contextExternalWatcher?.let { selected ->
        CompatLegacyChoiceDialog(
            onDismiss = { contextExternalWatcher = null },
            choices = listOf("削除する", "全て削除する", "巡回管理"),
            onChoice = { choice ->
                when (choice) {
                    "削除する" -> onExternalWatcherDelete(selected)
                    "全て削除する" -> onExternalWatcherDeleteAll()
                    "巡回管理" -> onOpenExternalWatcherManager()
                }
            }
        )
    }
}

@Composable
private fun CompatExternalWatcherMetadataRow(
    entry: CompatExternalWatcherEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    val primaryColor = if (entry.active) palette.text else Color(0xFFCCCCCC)
    val secondaryColor = if (entry.active) palette.uiSecondaryText else Color(0xFFCCCCCC)
    val replyColor = if (entry.active) Color(0xFF00897B) else Color(0xFFCCCCCC)
    val noThumb = painterResource(Res.drawable.cmn_no_thumb)
    Row(
        modifier = Modifier.fillMaxWidth().height(COMPAT_REFERENCE_DRAWER_WATCHER_ROW_DP.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.thumbnailUrl,
            contentDescription = null,
            fallback = noThumb,
            error = noThumb,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(COMPAT_REFERENCE_DRAWER_WATCHER_THUMBNAIL_DP.dp)
                .testTag("compat-drawer-watcher-thumb-${entry.key}")
                .graphicsLayer { alpha = if (entry.active) 1f else 0.33f }
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                entry.title.ifBlank { "No.${entry.threadUrl.substringAfterLast('/').substringBefore('.')}" },
                maxLines = 1,
                color = primaryColor,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Text(listOfNotNull(entry.boardName, entry.extractedKeyword?.takeIf(String::isNotBlank)).joinToString(" / "), maxLines = 1, color = secondaryColor, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${entry.replyCount}レス${if (entry.active) "" else "・落"}", maxLines = 1, fontSize = 16.sp, color = replyColor, textAlign = TextAlign.Center)
            Text(
                formatCompatDrawerWatcherTimestamp(entry.insertedAtEpochMillis),
                maxLines = 1,
                fontSize = 12.sp,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )
        }
    }
    HorizontalDivider()
}
