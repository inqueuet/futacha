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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompatMainScreen(
    boards: List<CompatBoard>,
    isDrawerOpen: Boolean = false,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSavedThreads: () -> Unit,
    onUpdateBoards: () -> Unit,
    onBoardSelected: (CompatBoard) -> Unit,
    onBoardUpsert: (CompatBoard) -> Unit,
    onBoardDelete: (CompatBoard) -> Unit,
    onBoardsReordered: (List<CompatBoard>) -> Unit
) {
    var overflowOpen by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    var deleteMode by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var editingBoard by remember { mutableStateOf<CompatBoard?>(null) }
    var deletingBoard by remember { mutableStateOf<CompatBoard?>(null) }
    var contextBoard by remember { mutableStateOf<CompatBoard?>(null) }
    var boardOperationNotice by remember { mutableStateOf<String?>(null) }
    val uniqueBoards = remember(boards) { distinctCompatBoards(boards) }
    var localBoards by remember(uniqueBoards) { mutableStateOf(uniqueBoards) }
    var draggedBoardKey by remember { mutableStateOf<String?>(null) }
    var draggedBoardOffset by remember { mutableFloatStateOf(0f) }
    var dragStartBoards by remember { mutableStateOf<List<CompatBoard>>(emptyList()) }
    var dragWorkingBoards by remember { mutableStateOf<List<CompatBoard>>(emptyList()) }
    val latestLocalBoards by rememberUpdatedState(localBoards)
    val latestBoardsReordered by rememberUpdatedState(onBoardsReordered)

    PlatformBackHandler(
        enabled = reorderMode || deleteMode,
        iosEdgeGestureEnabled = false
    ) { reorderMode = false; deleteMode = false }
    LaunchedEffect(boardOperationNotice) {
        if (boardOperationNotice != null) {
            delay(2_000)
            boardOperationNotice = null
        }
    }

    Scaffold(
            // TopAppBar owns the status-bar inset. Let the scaffold itself use
            // the full window; its default system-bar union includes the
            // mandatory gesture area on API 37 and leaves a 94px black band
            // below the content instead of the reference 63px navigation bar.
            contentWindowInsets = WindowInsets(),
            containerColor = CompatFutabaBackground,
            snackbarHost = {
                boardOperationNotice?.let { message ->
                    Snackbar(
                        modifier = Modifier
                            .padding(16.dp)
                            .testTag("compat-board-operation-toast")
                    ) { Text(message) }
                }
            },
            topBar = {
                TopAppBar(
                    expandedHeight = 56.dp,
                    title = { Text("ふたば", modifier = Modifier.padding(start = 16.dp)) },
                    navigationIcon = {
                        IconButton(onClick = if (isDrawerOpen) onCloseDrawer else onOpenDrawer) {
                            Icon(
                                if (isDrawerOpen) Icons.Filled.ArrowBack else Icons.Filled.Menu,
                                contentDescription = if (isDrawerOpen) "戻る" else "ドロワー"
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "その他")
                            }
                            if (overflowOpen) {
                                CompatMainOverflowPopup(
                                    onDismiss = { overflowOpen = false },
                                    onItem = { label ->
                                        overflowOpen = false
                                        when (label) {
                                            "板一覧" -> onUpdateBoards()
                                            "新規追加" -> addDialog = true
                                            "並び替え" -> { reorderMode = !reorderMode; deleteMode = false }
                                            "削除" -> { deleteMode = !deleteMode; reorderMode = false }
                                            "設定" -> onOpenSettings()
                                            "ヘルプ" -> onOpenHelp()
                                            "保存済みスレッド" -> onOpenSavedThreads()
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CompatTeal,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
    ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .testTag("compat-board-list")
            ) {
                items(localBoards, key = { it.key }) { board ->
                    val index = localBoards.indexOfFirst { it.key == board.key }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .graphicsLayer {
                                if (draggedBoardKey == board.key) {
                                    translationY = draggedBoardOffset
                                    shadowElevation = 12.dp.toPx()
                                    alpha = 0.1f
                                }
                            }
                            .combinedClickable(
                                onClick = { if (!reorderMode && !deleteMode) onBoardSelected(board) },
                                onLongClick = { if (!reorderMode && !deleteMode) contextBoard = board }
                            )
                            .semantics(mergeDescendants = true) { role = Role.Button },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.board_listview_ico_default),
                                contentDescription = "アイコン",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(board.name, fontSize = 18.sp, maxLines = 1)
                            Text(board.canonicalUrl, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                        if (reorderMode) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .pointerInput(board.key, reorderMode) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragStartBoards = latestLocalBoards
                                                dragWorkingBoards = latestLocalBoards
                                                draggedBoardKey = board.key
                                                draggedBoardOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                if (draggedBoardKey != board.key) return@detectDragGestures
                                                draggedBoardOffset += amount.y
                                                // Snapshot state is updated synchronously, while
                                                // rememberUpdatedState needs a recomposition. Use
                                                // the gesture's working list so two move events in
                                                // the same frame can still cross two rows.
                                                val currentBoards = dragWorkingBoards.ifEmpty { latestLocalBoards }
                                                val currentIndex = currentBoards.indexOfFirst { it.key == board.key }
                                                if (currentIndex < 0) return@detectDragGestures
                                                val rowHeightPx = size.height.toFloat().coerceAtLeast(1f)
                                                val targetIndex = when {
                                                    draggedBoardOffset > rowHeightPx / 2f -> currentIndex + 1
                                                    draggedBoardOffset < -rowHeightPx / 2f -> currentIndex - 1
                                                    else -> currentIndex
                                                }
                                                if (targetIndex !in currentBoards.indices || targetIndex == currentIndex) {
                                                    return@detectDragGestures
                                                }
                                                localBoards = currentBoards.toMutableList().also {
                                                    val moved = it.removeAt(currentIndex)
                                                    it.add(targetIndex, moved)
                                                }.also { dragWorkingBoards = it }
                                                draggedBoardOffset +=
                                                    if (targetIndex > currentIndex) -rowHeightPx else rowHeightPx
                                            },
                                            onDragEnd = {
                                                if (dragWorkingBoards.isNotEmpty() && dragWorkingBoards != dragStartBoards) {
                                                    latestBoardsReordered(dragWorkingBoards)
                                                }
                                                dragStartBoards = emptyList()
                                                dragWorkingBoards = emptyList()
                                                draggedBoardKey = null
                                                draggedBoardOffset = 0f
                                            },
                                            onDragCancel = {
                                                if (dragStartBoards.isNotEmpty()) localBoards = dragStartBoards
                                                dragStartBoards = emptyList()
                                                dragWorkingBoards = emptyList()
                                                draggedBoardKey = null
                                                draggedBoardOffset = 0f
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.cmn_listview_handle),
                                    contentDescription = "ハンドル",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        if (deleteMode) {
                            IconButton(onClick = { deletingBoard = board }) {
                                Image(
                                    painter = painterResource(Res.drawable.cmn_listview_delete),
                                    contentDescription = "${board.name}を削除",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = CompatDivider)
                }
                if (localBoards.isEmpty()) {
                    item { Text("板が登録されていません。右上のメニューから板を追加してください。", modifier = Modifier.padding(16.dp)) }
                }
            }
    }

    if (addDialog) {
        CompatBoardEditDialog(
            title = "新しい板の追加",
            initialName = "",
            initialUrl = "",
            urlEditable = true,
            existingBoards = localBoards,
            onDismiss = { addDialog = false },
            onConfirm = { name, canonical, original ->
                boardOperationNotice = "${name}を追加しました\n"
                onBoardUpsert(
                    CompatBoard(
                        key = compatBoardKey(canonical),
                        name = name,
                        canonicalUrl = canonical,
                        originalUrl = original,
                        sortOrder = localBoards.size
                    )
                )
                addDialog = false
            }
        )
    }
    editingBoard?.let { board ->
        CompatBoardEditDialog(
            title = "名前の変更",
            initialName = board.name,
            initialUrl = board.originalUrl,
            urlEditable = false,
            existingBoards = localBoards,
            onDismiss = { editingBoard = null },
            onConfirm = { name, _, _ ->
                onBoardUpsert(board.copy(name = name))
                editingBoard = null
            }
        )
    }
    contextBoard?.let { board ->
        CompatLegacyChoiceDialog(
            onDismiss = { contextBoard = null },
            choices = listOf("名前を変更", "削除する"),
            onChoice = { choice ->
                contextBoard = null
                if (choice == "名前を変更") editingBoard = board else deletingBoard = board
            }
        )
    }
    deletingBoard?.let { board ->
        AlertDialog(
            onDismissRequest = { deletingBoard = null },
            title = { Text("板の削除") },
            text = { Text("本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    boardOperationNotice = "${board.name}を削除しました\n"
                    onBoardDelete(board)
                    deletingBoard = null
                }) {
                    Text("削除する", color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { deletingBoard = null }) { Text("キャンセル") } }
        )
    }
}

@Composable
private fun CompatMainOverflowPopup(
    onDismiss: () -> Unit,
    onItem: (String) -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    // The legacy PopupWindow starts 10 px below the status-bar edge. At the
    // emulator density this is the equivalent of a zero-dp Compose popup
    // offset; the previous negative offset made the menu touch the status bar.
    val topInset = with(LocalDensity.current) { 0.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, topInset),
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier.width(196.dp),
            shape = RoundedCornerShape(2.dp),
            color = compatibilityPopupSurface(palette),
            contentColor = compatibilityPopupContent(palette),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Column {
            com.valoser.futacha.shared.ui.media.DeviceImageEditorMenuItem(onDismiss)
            com.valoser.futacha.shared.ui.media.DeviceVideoEditorMenuItem(onDismiss)
            // board_menu.xml in sample/1.apk labels the update action simply
            // "板一覧"; the action itself still refreshes the board list.
            listOf("板一覧", "新規追加", "並び替え", "削除", "保存済みスレッド", "設定", "ヘルプ").forEach { label ->
                TextButton(
                    onClick = { onItem(label) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth(),
                        color = compatibilityPopupContent(palette)
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun CompatModeDialog(
    activeProfile: ExperienceProfile,
    onDismiss: () -> Unit,
    onSwitch: (ExperienceProfile) -> Unit
) {
    var selected by remember { mutableStateOf(activeProfile) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("モード") },
        text = {
            Column {
                ExperienceProfile.entries.forEach { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = profile }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (selected == profile) "●" else "○", modifier = Modifier.width(32.dp))
                        Column {
                            Text(profile.displayName)
                            if (profile == ExperienceProfile.TOSHIAKI_COMPAT) {
                                Text("非公式の旧型タブ互換表示です。", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (selected == activeProfile) onDismiss() else onSwitch(selected) }) {
                Text("切り替える")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}
