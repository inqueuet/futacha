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

private fun formatCompatNgCreatedAt(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(local.year.toString().padStart(4, '0'))
        append('/')
        append((local.month.ordinal + 1).toString().padStart(2, '0'))
        append('/')
        append(local.day.toString().padStart(2, '0'))
    }
}

private fun formatCompatImageNgCreatedAt(epochMillis: Long): String {
    if (epochMillis <= 0L) return "-"
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(local.year.toString().padStart(4, '0'))
        append('/')
        append((local.month.ordinal + 1).toString().padStart(2, '0'))
        append('/')
        append(local.day.toString().padStart(2, '0'))
        append(' ')
        append(local.hour.toString().padStart(2, '0'))
        append(':')
        append(local.minute.toString().padStart(2, '0'))
    }
}

@Composable
internal fun CompatNgRuleManagementDialog(
    title: String,
    rules: List<CompatNgRule>,
    imageReferenceBoardName: String? = null,
    phashThreshold: Int? = null,
    onPhashThresholdChange: ((Int) -> Unit)? = null,
    onDelete: (CompatNgRule) -> Unit,
    onDeleteAll: (List<CompatNgRule>) -> Unit,
    addScopeLabel: String? = null,
    referenceKind: CompatNgKind? = null,
    onAdd: ((value: String, globalScope: Boolean) -> Unit)? = null,
    onEdit: ((rule: CompatNgRule, value: String, globalScope: Boolean, memo: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isImageReference = imageReferenceBoardName != null
    val isReference = referenceKind != null || isImageReference
    val isCatalogWordReference = referenceKind == CompatNgKind.CATALOG_EXTRACT ||
        referenceKind == CompatNgKind.CATALOG_IGNORE
    val isCatalogRefuseReference = referenceKind == CompatNgKind.CATALOG_REFUSE
    val isThreadWordReference = referenceKind == CompatNgKind.THREAD_REFUSE ||
        referenceKind == CompatNgKind.THREAD_IGNORE
    val isWordReference = isCatalogWordReference || isThreadWordReference
    val referenceWordMaxLength = if (isThreadWordReference) 20 else 10
    val referenceTag = when (referenceKind) {
        CompatNgKind.CATALOG_EXTRACT -> "compat-catalog-extract"
        CompatNgKind.CATALOG_IGNORE -> "compat-catalog-ignore"
        CompatNgKind.CATALOG_REFUSE -> "compat-catalog-refuse"
        CompatNgKind.THREAD_REFUSE -> "compat-thread-refuse"
        CompatNgKind.THREAD_IGNORE -> "compat-thread-ignore"
        else -> if (isImageReference) "compat-image-ng" else null
    }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var newValue by remember { mutableStateOf("") }
    var globalScope by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var thresholdOpen by remember { mutableStateOf(false) }
    var thresholdDraft by remember(phashThreshold) {
        mutableIntStateOf(phashThreshold ?: CompatImagePhash.DEFAULT_THRESHOLD)
    }
    var thresholdSavedMessage by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var addValidationMessage by remember { mutableStateOf<String?>(null) }
    var editingRule by remember { mutableStateOf<CompatNgRule?>(null) }
    var pendingReferenceDeleteRule by remember { mutableStateOf<CompatNgRule?>(null) }
    var editValue by remember { mutableStateOf("") }
    var editMemo by remember { mutableStateOf("") }
    var editGlobalScope by remember { mutableStateOf(false) }
    var editValidationMessage by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val openUrl = rememberUrlLauncher()
    val filteredRules = remember(rules, searchQuery, isImageReference) {
        val query = normalizeCompatSearchText(searchQuery)
        if (isImageReference) {
            rules.filter { rule -> compatImageNgMatchesSearch(rule, searchQuery) }
        } else if (query.isBlank()) rules else rules.filter { rule ->
            normalizeCompatSearchText(rule.normalizedValue).contains(query) ||
                normalizeCompatSearchText(rule.memo).contains(query) ||
                normalizeCompatSearchText(
                    if (isThreadWordReference) compatThreadReferenceDisplayValue(rule)
                    else rule.normalizedValue
                ).contains(query) ||
                normalizeCompatSearchText(rule.imageUrl.orEmpty()).contains(query)
        }
    }
    fun hasReferenceDuplicate(value: String, global: Boolean, editingId: String? = null): Boolean = when {
        isCatalogWordReference -> hasCompatCatalogManagementDuplicate(rules, value, editingId)
        isThreadWordReference -> hasCompatThreadReferenceDuplicate(
            rules = rules,
            kind = referenceKind,
            value = value,
            globalScope = global,
            excludingRuleId = editingId,
            editing = editingId != null
        )
        else -> false
    }
    fun submitReferenceAdd() {
        val cleaned = if (isThreadWordReference) cleanCompatThreadReferenceWord(newValue) else newValue
        when {
            cleaned.isBlank() -> addValidationMessage = "単語を入力して下さい"
            referenceKind == CompatNgKind.THREAD_REFUSE && isCompatThreadRefuseForbidden(cleaned) ->
                addValidationMessage = "登録できない単語です"
            hasReferenceDuplicate(cleaned, globalScope) -> {
                addOpen = false
                searchOpen = true
                searchQuery = cleaned
            }
            else -> {
                onAdd?.invoke(cleaned, globalScope)
                newValue = ""
                addValidationMessage = null
                addOpen = false
            }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            if (searchOpen) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it.take(200) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (referenceTag != null) {
                                                Modifier.testTag("$referenceTag-search")
                                            } else Modifier
                                        ),
                                    singleLine = true,
                                    placeholder = { Text("検索") },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedPlaceholderColor = Color.White,
                                        unfocusedPlaceholderColor = Color.White,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            } else {
                                Text(
                                    when (referenceKind) {
                                        CompatNgKind.CATALOG_EXTRACT -> "スレッド監視 ${rules.size}個"
                                        CompatNgKind.CATALOG_IGNORE -> "ＮＧワード ${rules.size}個"
                                        CompatNgKind.CATALOG_REFUSE -> "ＮＧスレッド ${rules.size}個"
                                        CompatNgKind.THREAD_REFUSE -> "ＮＧヘッダー ${rules.size}個"
                                        CompatNgKind.THREAD_IGNORE -> "ＮＧワード ${rules.size}個"
                                        else -> if (isImageReference) "ＮＧ画像 ${rules.size}個" else title
                                    }
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    searchOpen = !searchOpen
                                    if (!searchOpen) searchQuery = ""
                                },
                                modifier = Modifier.testTag("compat-rule-management-search")
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "検索")
                            }
                            if (onAdd != null) {
                                IconButton(onClick = {
                                    newValue = ""
                                    globalScope = false
                                    addValidationMessage = null
                                    addOpen = true
                                }) {
                                    Icon(Icons.Filled.Add, contentDescription = "新規追加")
                                }
                            }
                            Box {
                                IconButton(
                                    onClick = { moreOpen = true },
                                    modifier = Modifier.testTag("compat-rule-management-overflow")
                                ) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "その他")
                                }
                                DropdownMenu(
                                    expanded = moreOpen,
                                    onDismissRequest = { moreOpen = false },
                                    shape = RoundedCornerShape(2.dp),
                                    containerColor = compatibilityPopupSurface(LocalCompatibilityPalette.current),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 8.dp
                                ) {
                                    if (phashThreshold != null && onPhashThresholdChange != null) {
                                        DropdownMenuItem(
                                            text = { Text("類似判定のしきい値") },
                                            colors = compatibilityMenuItemColors(),
                                            onClick = {
                                                moreOpen = false
                                                thresholdDraft = phashThreshold
                                                thresholdOpen = true
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        enabled = isReference || rules.isNotEmpty(),
                                        text = { Text("全て削除") },
                                        colors = compatibilityMenuItemColors(),
                                        onClick = { moreOpen = false; confirmDeleteAll = true }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = LocalCompatibilityPalette.current.chrome,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                }
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    LaunchedEffect(thresholdSavedMessage) {
                        if (thresholdSavedMessage) {
                            delay(3_000)
                            thresholdSavedMessage = false
                        }
                    }
                    if (thresholdSavedMessage) {
                        Text(
                            "保存しました。次回リロードまたはNG on/off後に反映されます",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                    if (filteredRules.isEmpty() && !isReference) {
                        Text(
                            if (rules.isEmpty()) "登録はありません" else "一致するNGはありません",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredRules, key = CompatNgRule::id) { rule ->
                                val isImageRule = isImageReference || rule.imageUrl != null
                                val openEditor = {
                                    if (onEdit != null) {
                                        editValue = when {
                                            isCatalogWordReference -> compatCatalogManagementDisplayValue(rule)
                                            isThreadWordReference -> compatThreadReferenceDisplayValue(rule)
                                            else -> rule.normalizedValue
                                        }
                                        editMemo = rule.memo
                                        editGlobalScope = rule.scopeKey == "*"
                                        editValidationMessage = null
                                        editingRule = rule
                                    }
                                }
                                if (isCatalogRefuseReference) {
                                    Text(
                                        text = compatCatalogRefuseDisplayText(rule),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 60.dp)
                                            .clickable { pendingReferenceDeleteRule = rule }
                                            .then(
                                                referenceTag?.let {
                                                    Modifier.testTag("$it-row-${rule.id}")
                                                } ?: Modifier
                                            )
                                            .padding(20.dp),
                                        color = LocalCompatibilityPalette.current.text,
                                        fontSize = 16.sp
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(if (isImageRule) 68.dp else 60.dp)
                                            .then(
                                                if (isWordReference) {
                                                    Modifier.clickable(onClick = openEditor)
                                                } else if (isImageReference) {
                                                    Modifier.clickable(onClick = openEditor)
                                                } else {
                                                    Modifier.combinedClickable(
                                                        onClick = openEditor,
                                                        onLongClick = { onDelete(rule) }
                                                    )
                                                }
                                            )
                                            .then(
                                                if (isWordReference && referenceTag != null) {
                                                    Modifier.testTag("$referenceTag-row-${rule.id}")
                                                } else {
                                                    Modifier.padding(horizontal = if (isImageReference) 8.dp else 10.dp)
                                                }
                                            )
                                            .then(
                                                if (isImageReference && referenceTag != null) {
                                                    Modifier.testTag("$referenceTag-row-${rule.id}")
                                                } else Modifier
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                    if (isWordReference) {
                                        Box(
                                            modifier = Modifier.size(60.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (rule.scopeKey == "*") {
                                                CompatAllBoardsReferenceIcon(
                                                    tint = LocalCompatibilityPalette.current.text,
                                                    contentDescription = if (isThreadWordReference) {
                                                        "全てのスレッド"
                                                    } else {
                                                        "全ての板"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (isImageRule) {
                                        AsyncImage(
                                            model = compatImageNgFirstUrl(rule),
                                            imageLoader = LocalFutachaImageLoader.current,
                                            contentDescription = "NG画像",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(
                                                if (isWordReference) Modifier.padding(start = 10.dp)
                                                else Modifier
                                            )
                                    ) {
                                        Text(
                                            text = if (isImageRule) {
                                                if (isImageReference) {
                                                    compatImageNgDisplayTitle(rule)
                                                } else {
                                                    rule.memo.takeIf(String::isNotBlank)
                                                        ?: compatImageNgFirstUrl(rule)
                                                            .substringAfterLast('/')
                                                            .substringBefore('?')
                                                            .takeIf(String::isNotBlank)
                                                        ?: rule.normalizedValue
                                                }
                                            } else if (isCatalogWordReference) {
                                                compatCatalogManagementDisplayValue(rule)
                                            } else if (isThreadWordReference) {
                                                compatThreadReferenceDisplayValue(rule)
                                            } else {
                                                rule.normalizedValue
                                            },
                                            fontSize = if (isWordReference) 22.sp else if (isImageRule) 15.sp else 16.sp,
                                            maxLines = if (isWordReference || isImageRule) 1 else 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isWordReference || isImageReference) {
                                                LocalCompatibilityPalette.current.text
                                            } else {
                                                Color.Unspecified
                                            },
                                            modifier = if (isWordReference && referenceTag != null) {
                                                Modifier.testTag("$referenceTag-word-${rule.id}")
                                            } else Modifier
                                        )
                                        if (!isWordReference) {
                                            if (isImageReference) {
                                                Text(
                                                    text = compatImageNgBoardLabel(
                                                        rule,
                                                        imageReferenceBoardName.orEmpty()
                                                    ),
                                                    fontSize = 12.sp,
                                                    color = LocalCompatibilityPalette.current.text,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = formatCompatImageNgCreatedAt(rule.createdAtEpochMillis),
                                                    fontSize = 12.sp,
                                                    color = LocalCompatibilityPalette.current.text,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            } else {
                                                Text(
                                                    text = buildString {
                                                        append(if (rule.scopeKey == "*") "全ての板" else "この板のみ")
                                                        append(" ・ ")
                                                        append(formatCompatNgCreatedAt(rule.createdAtEpochMillis))
                                                    },
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    }
                                    if (!isWordReference && !isImageReference) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (addOpen) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = {
                Text(
                    when (referenceKind) {
                        CompatNgKind.CATALOG_EXTRACT -> "監視ワード"
                        CompatNgKind.CATALOG_IGNORE -> "ＮＧワード"
                        CompatNgKind.THREAD_REFUSE -> "ＮＧヘッダー"
                        CompatNgKind.THREAD_IGNORE -> "ＮＧワード"
                        else -> "新規追加"
                    }
                )
            },
            text = {
                Column {
                    if (isWordReference) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("単語", fontSize = 18.sp, modifier = Modifier.padding(10.dp))
                            TextField(
                                value = newValue,
                                onValueChange = {
                                    newValue = it.take(referenceWordMaxLength)
                                    addValidationMessage = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        referenceTag?.let {
                                            Modifier.testTag("$it-add-word")
                                        } ?: Modifier
                                    ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    submitReferenceAdd()
                                })
                            )
                        }
                    } else {
                        TextField(
                            value = newValue,
                            onValueChange = { newValue = it.take(200) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("登録値") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onAdd?.invoke(newValue, globalScope)
                                newValue = ""
                                addOpen = false
                            })
                        )
                    }
                    if (addScopeLabel != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = if (isThreadWordReference) !globalScope else globalScope,
                                onCheckedChange = {
                                    globalScope = if (isThreadWordReference) !it else it
                                }
                            )
                            Text(
                                when {
                                    isCatalogWordReference -> "全ての板"
                                    isThreadWordReference -> "このスレッドのみ"
                                    else -> addScopeLabel
                                }.orEmpty()
                            )
                        }
                    }
                    if (isWordReference) {
                        addValidationMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 10.dp))
                        }
                        Text(
                            if (isThreadWordReference) {
                                "・リロード後に反映されます\n" +
                                    "・読み込みが長くなります\n" +
                                    "・多いほど時間が掛かります\n" +
                                    "・登録数に注意して下さい"
                            } else {
                                "・大文字と小文字を区別しません\n" +
                                    "・全角と半角を区別しません\n" +
                                    "・リロード後に反映されます\n" +
                                    "・多いほど時間が掛かります\n" +
                                    "・登録数に注意して下さい"
                            },
                            fontSize = 14.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isWordReference || newValue.isNotBlank(),
                    onClick = {
                        if (isWordReference) {
                            submitReferenceAdd()
                        } else {
                            onAdd?.invoke(newValue, globalScope)
                            newValue = ""
                            addValidationMessage = null
                            addOpen = false
                        }
                    }
                ) { Text(if (isWordReference) "追加する" else "追加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newValue = ""
                    globalScope = false
                    addValidationMessage = null
                    addOpen = false
                }) { Text("キャンセル") }
            }
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(if (isReference) "全て削除" else "NGを全削除") },
            text = {
                Text(
                    if (isImageReference) "登録済みのNG画像を全て削除します。よろしいですか？"
                    else if (isReference) "本当によろしいですか？"
                    else "${rules.size}件のNGルールを削除します。元に戻せません。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    onDeleteAll(rules)
                }) {
                    Text(
                        if (isReference) "削除する" else "削除",
                        color = if (isReference) Color.Unspecified else Color.Red
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("キャンセル") } }
        )
    }
    pendingReferenceDeleteRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingReferenceDeleteRule = null },
            title = { Text("登録の削除") },
            text = { Text("本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingReferenceDeleteRule = null
                    onDelete(rule)
                }) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReferenceDeleteRule = null }) { Text("キャンセル") }
            }
        )
    }
    if (thresholdOpen && phashThreshold != null && onPhashThresholdChange != null) {
        AlertDialog(
            onDismissRequest = { thresholdOpen = false },
            title = { Text("類似判定のしきい値") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("しきい値", fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            thresholdDraft.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 24.dp)
                        )
                    }
                    Slider(
                        value = thresholdDraft.toFloat(),
                        onValueChange = {
                            thresholdDraft = it.roundToInt().coerceIn(
                                CompatImagePhash.MIN_THRESHOLD,
                                CompatImagePhash.MAX_THRESHOLD
                            )
                        },
                        valueRange = 0f..16f,
                        steps = 15,
                        modifier = Modifier.testTag("compat-image-ng-threshold-slider")
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("ⓘ 64bit pHashのハミング距離です。", fontSize = 13.sp)
                            Text("小さいほど厳しく、大きいほど緩く判定します。", fontSize = 13.sp)
                            CompatImagePhash.thresholdGuideRows.forEach { (range, description) ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(range, modifier = Modifier.width(58.dp), fontSize = 13.sp)
                                    Text(description, fontSize = 13.sp)
                                }
                            }
                            Text("※ 画像の種類によって目安は変わります。", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onPhashThresholdChange(thresholdDraft)
                    thresholdOpen = false
                    thresholdSavedMessage = true
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        thresholdDraft = CompatImagePhash.DEFAULT_THRESHOLD
                    }) { Text("初期値に戻す") }
                    TextButton(onClick = { thresholdOpen = false }) { Text("キャンセル") }
                }
            }
        )
    }
    editingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = {
                Text(
                    when (referenceKind) {
                        CompatNgKind.CATALOG_EXTRACT -> "監視ワード"
                        CompatNgKind.CATALOG_IGNORE -> "ＮＧワード"
                        CompatNgKind.THREAD_REFUSE -> "ＮＧヘッダー"
                        CompatNgKind.THREAD_IGNORE -> "ＮＧワード"
                        else -> if (isImageReference) "NG画像" else "NGを編集"
                    }
                )
            },
            text = {
                Column {
                    if (isImageReference) {
                        AsyncImage(
                            model = compatImageNgFirstUrl(rule),
                            imageLoader = LocalFutachaImageLoader.current,
                            contentDescription = "編集するNG画像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .align(Alignment.CenterHorizontally)
                                .testTag("compat-image-ng-edit-thumb")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("メモ", fontSize = 16.sp, modifier = Modifier.width(48.dp))
                            TextField(
                                value = editMemo,
                                onValueChange = { editMemo = it.take(MAX_COMPAT_NG_MEMO_CHARS) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("compat-image-ng-edit-memo"),
                                minLines = 1,
                                maxLines = 4,
                                singleLine = false
                            )
                        }
                    } else if (isWordReference) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("単語", fontSize = 18.sp, modifier = Modifier.padding(10.dp))
                            TextField(
                                value = editValue,
                                onValueChange = {
                                    editValue = it.take(referenceWordMaxLength)
                                    editValidationMessage = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        referenceTag?.let {
                                            Modifier.testTag("$it-edit-word")
                                        } ?: Modifier
                                    ),
                                singleLine = true
                            )
                        }
                    } else {
                        TextField(
                            value = editValue,
                            onValueChange = { editValue = it.take(200) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("登録値") }
                        )
                    }
                    if (isWordReference) {
                        editValidationMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 10.dp))
                        }
                    }
                    if (rule.imageUrl != null && !isImageReference) {
                        TextField(
                            value = editMemo,
                            onValueChange = { editMemo = it.take(MAX_COMPAT_NG_MEMO_CHARS) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("メモ") }
                        )
                    }
                    Row(
                        modifier = if (isWordReference) {
                            Modifier.fillMaxWidth().padding(10.dp)
                        } else if (isImageReference) {
                            Modifier.fillMaxWidth()
                        } else Modifier,
                        horizontalArrangement = if (isWordReference) {
                            Arrangement.End
                        } else if (isImageReference) {
                            Arrangement.End
                        } else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = if (isThreadWordReference || isImageReference) {
                                !editGlobalScope
                            } else {
                                editGlobalScope
                            },
                            onCheckedChange = {
                                editGlobalScope = if (isThreadWordReference || isImageReference) !it else it
                            },
                            modifier = if (isImageReference) {
                                Modifier.testTag("compat-image-ng-edit-local-only")
                            } else Modifier
                        )
                        Text(
                            when {
                                isCatalogWordReference -> "全ての板"
                                isThreadWordReference -> "このスレッドのみ"
                                isImageReference -> "この板のみ"
                                else -> addScopeLabel ?: "全体に適用"
                            }
                        )
                    }
                    rule.imageUrl?.takeUnless { isImageReference }?.let { imageUrl ->
                        Row {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(imageUrl)) }) {
                                Text("URLをコピー")
                            }
                            TextButton(onClick = { openUrl(imageUrl) }) {
                                Text("ブラウザで開く")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalizedEditValue = normalizeCompatSearchText(
                        if (isThreadWordReference) cleanCompatThreadReferenceWord(editValue) else editValue
                    )
                    val originalDisplayValue = if (isThreadWordReference) {
                        compatThreadReferenceDisplayValue(rule)
                    } else {
                        rule.normalizedValue
                    }
                    val unchanged = normalizedEditValue == normalizeCompatSearchText(originalDisplayValue) &&
                        editGlobalScope == (rule.scopeKey == "*")
                    if (isWordReference && editValue.isBlank()) {
                        editValidationMessage = "単語を入力して下さい"
                    } else if (
                        referenceKind == CompatNgKind.THREAD_REFUSE &&
                        isCompatThreadRefuseForbidden(editValue)
                    ) {
                        editValidationMessage = "登録できない単語です"
                    } else if (
                        isWordReference &&
                        (unchanged || hasReferenceDuplicate(editValue, editGlobalScope, rule.id))
                    ) {
                        editValidationMessage = "既に登録されているか、または変更がありません"
                    } else {
                        onEdit?.invoke(rule, editValue, editGlobalScope, editMemo)
                        editValidationMessage = null
                        editingRule = null
                    }
                }) { Text(if (isWordReference || isImageReference) "更新する" else "保存") }
            },
            dismissButton = {
                Row {
                    if (isWordReference || isImageReference) {
                        TextButton(onClick = {
                            onDelete(rule)
                            editValidationMessage = null
                            editingRule = null
                        }) { Text("削除") }
                    }
                    TextButton(onClick = {
                        editValidationMessage = null
                        editingRule = null
                    }) { Text("キャンセル") }
                }
            }
        )
    }
}

@Composable
private fun CompatAllBoardsReferenceIcon(
    tint: Color,
    contentDescription: String = "全ての板"
) {
    Canvas(
        modifier = Modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val strokeWidth = size.minDimension * 0.055f
        val corner = size.minDimension * 0.07f
        drawRoundRect(
            color = tint.copy(alpha = 0.45f),
            topLeft = Offset(size.width * 0.05f, size.height * 0.03f),
            size = Size(size.width * 0.72f, size.height * 0.76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
            style = Stroke(width = strokeWidth)
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.72f),
            topLeft = Offset(size.width * 0.12f, size.height * 0.10f),
            size = Size(size.width * 0.72f, size.height * 0.76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
            style = Stroke(width = strokeWidth)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.20f, size.height * 0.18f),
            size = Size(size.width * 0.72f, size.height * 0.76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner)
        )
        val bookmark = Path().apply {
            moveTo(size.width * 0.58f, size.height * 0.18f)
            lineTo(size.width * 0.78f, size.height * 0.18f)
            lineTo(size.width * 0.78f, size.height * 0.57f)
            lineTo(size.width * 0.68f, size.height * 0.49f)
            lineTo(size.width * 0.58f, size.height * 0.57f)
            close()
        }
        drawPath(bookmark, color = Color.White)
    }
}

@Composable
internal fun CompatCatalogRuleScopeDialog(
    kind: CompatNgKind,
    onDismiss: () -> Unit,
    onSelect: (allBoards: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.compatCatalogRuleLabel()) },
        text = { Text("このルールを適用する範囲を選択してください。") },
        confirmButton = {
            Row {
                TextButton(onClick = { onSelect(false) }) { Text("この板のみ") }
                TextButton(onClick = { onSelect(true) }) { Text("全板") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}
