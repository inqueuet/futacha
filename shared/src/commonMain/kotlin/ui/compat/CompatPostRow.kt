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

private val compatHeaderIdentityTokenRegex = Regex("(?:ID|IP):[^\\s<]+", RegexOption.IGNORE_CASE)
private val compatAppWhitespaceRegex = Regex("\\s+")

internal fun compatPostQuotesOwnPost(
    post: CompatPostSnapshot,
    ownPostNos: Set<String>
): Boolean {
    if (ownPostNos.isEmpty() || post.postNo in ownPostNos) return false
    if (post.quoteReferences.any { reference ->
            reference.targetPostIds.any(ownPostNos::contains)
        }
    ) return true
    return post.messageHtml.toCompatPlainText().lineSequence().any { line ->
        val query = compatQuoteQueryForLine(line.trimStart()) ?: return@any false
        query.startsWith("no:", ignoreCase = true) &&
            query.substringAfter(':').trim() in ownPostNos
    }
}

@Composable
internal fun CompatPostRow(
    post: CompatPostSnapshot,
    ownPostNos: Set<String> = emptySet(),
    deletionSummary: String? = null,
    fontSize: Int,
    thumbnailSize: Int,
    upsThumbnailSize: Int = thumbnailSize,
    upsThumbnailMethod: String? = null,
    wifiConnected: Boolean = false,
    privacyAlpha: Float = 1f,
    hideDefaultNameAndSubject: Boolean = false,
    boardDefaultText: CompatBoardDefaultText = CompatBoardDefaultText(),
    simpleQuoteCount: Boolean = false,
    saidaneDisplayMode: String = "通常",
    saidaneThreshold: Int = Int.MAX_VALUE,
    posterIdentityProgress: List<CompatPosterIdentityProgress> = emptyList(),
    searchHit: Boolean = false,
    searchRanges: List<CompatSearchTextRange> = emptyList(),
    newReplyCount: Int? = null,
    onClick: () -> Unit = {},
    onQuoteClick: (String) -> Unit = {},
    onUrlClick: (String) -> Unit = {},
    onMediaUrlClick: (String) -> Unit = onUrlClick,
    onLongClick: () -> Unit = {},
    onHeaderClick: () -> Unit = {},
    onHeaderLongClick: () -> Unit = {},
    thumbnailReloadToken: Long = 0L,
    onMediaClick: () -> Unit = {},
    onMediaLongClick: () -> Unit = {}
) {
    val palette = LocalCompatibilityPalette.current
    val isOwnPost = post.postNo in ownPostNos
    val quotesOwnPost = remember(post, ownPostNos) {
        compatPostQuotesOwnPost(post, ownPostNos)
    }
    val inlineApuSmallMediaUrls = remember(
        post.messageHtml,
        post.imageUrl,
        post.thumbnailUrl,
        upsThumbnailMethod,
        wifiConnected
    ) {
        compatVisibleInlineApuSmallMediaUrls(
            messageHtml = post.messageHtml,
            upsThumbnailMethod = upsThumbnailMethod,
            wifiConnected = wifiConnected
        )
            // If the fu… file is already the post's main media, its normal
            // media row is sufficient; otherwise it needs its own preview.
            .filterNot { inlineUrl ->
                val identity = compatMediaFileIdentity(inlineUrl)
                identity == compatMediaFileIdentity(post.imageUrl) ||
                    identity == compatMediaFileIdentity(post.thumbnailUrl)
            }
    }
    val firstQuoteQuery = remember(post.messageHtml) {
        post.messageHtml.toCompatPlainText()
            .lineSequence()
            .mapNotNull(::compatQuoteQueryForLine)
            .firstOrNull()
    }
    val mediaAwareUrlClick: (String) -> Unit = remember(onUrlClick, onMediaUrlClick) {
        { url ->
            if (isCompatImageMediaUrl(url) || isCompatVideoMediaUrl(url)) {
                onMediaUrlClick(url)
            } else {
                onUrlClick(url)
            }
        }
    }
    val subject = post.subject?.takeIf { value ->
        value.isNotBlank() && !(hideDefaultNameAndSubject &&
            shouldHideCompatDefaultSubject(value, boardDefaultText))
    }
    val author = post.author?.takeIf { value ->
        value.isNotBlank() && !(hideDefaultNameAndSubject &&
            shouldHideCompatDefaultName(value, boardDefaultText))
    }
    // The HTML uses a bare `+` anchor for a zero-count そうだね action.  The
    // legacy APK keeps that action in the header hit target but does not draw
    // the bare plus; only an actual count (e.g. `そうだねx1`) is visible.
    val rawSaidane = post.saidaneLabel?.takeIf {
        it.isNotBlank() && it.trim() != "+" && saidaneDisplayMode != "非表示"
    }
    val displayedSaidane = if (saidaneDisplayMode.startsWith("シンプル")) {
        rawSaidane?.removePrefix("そうだね")
    } else rawSaidane
    val rightAlignedSaidane = saidaneDisplayMode.endsWith("(右寄せ)")
    val saidaneColor = compatibilitySaidaneColor(palette, rawSaidane, saidaneThreshold)
    // Legacy ThreadListItemHeaderText appends the uploaded file name on a
    // second line for media posts.  Keeping it in the same annotated block
    // preserves both the hit target and the row height used by the old APK.
    val primaryMediaUrl = post.imageUrl ?: post.thumbnailUrl
    val mediaFileName = primaryMediaUrl
        ?.takeUnless { isCompatApuSmallMediaUrl(it) }
        ?.substringAfterLast('/')
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() }
    val headerText = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = when {
                    isOwnPost -> palette.headerSelfPost
                    quotesOwnPost -> palette.headerSelfQuote
                    else -> palette.text
                },
                fontWeight = if (isOwnPost || quotesOwnPost) FontWeight.Bold else null
            )
        ) { append(post.position.toString()) }
        append(" ")
        subject?.let {
            withStyle(SpanStyle(color = palette.headerSubject, fontWeight = FontWeight.Bold)) {
                append(it); append(" ")
            }
        }
        author?.let {
            withStyle(SpanStyle(color = palette.headerAuthor, fontWeight = FontWeight.Bold)) {
                append(it); append(" ")
            }
        }
        post.mail?.trim()?.takeIf(String::isNotEmpty)?.let {
            withStyle(SpanStyle(color = palette.headerEmail)) { append("["); append(it); append("] ") }
        }
        val timestampText = post.timestamp.replace(compatHeaderIdentityTokenRegex, " ")
            .replace(compatAppWhitespaceRegex, " ")
            .trim()
        if (timestampText.isNotBlank()) {
            withStyle(SpanStyle(color = palette.headerSubtext)) { append(timestampText) }
        }
        if (post.referencedCount > 0) {
            withStyle(SpanStyle(color = palette.headerSubject)) {
                if (simpleQuoteCount) {
                    append(" ")
                    appendInlineContent("compat-quote-count", "返信")
                    append(post.referencedCount.toString())
                } else {
                    append(" ${post.referencedCount}レス")
                }
            }
        }
        if (!rightAlignedSaidane) {
            displayedSaidane?.let {
                withStyle(SpanStyle(color = saidaneColor)) { append(" "); append(it) }
            }
        }
        posterIdentityProgress.forEach { progress ->
            val color = if (progress.total > 4) palette.identityTotal else palette.text
            withStyle(SpanStyle(color = color)) {
                append(" ")
                append(progress.identity.display)
                append("(")
                append(progress.label)
                append(")")
            }
        }
        if (!rightAlignedSaidane) {
            withStyle(SpanStyle(color = palette.headerSubtext)) {
                append(" No.")
                append(post.postNo)
            }
        }
        mediaFileName?.let {
            append("\n")
            withStyle(SpanStyle(color = palette.fileName)) { append(it) }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .testTag("compat-thread-post-${post.postNo}")
            .combinedClickable(
                onClick = {
                    firstQuoteQuery?.let(onQuoteClick) ?: onClick()
                },
                onLongClick = onLongClick
            )
            .background(if (searchHit) palette.searchResultBackground else Color.Transparent)
    ) {
        newReplyCount?.takeIf { it > 0 }?.let { count ->
            CompatNewRepliesDivider(count, Modifier.testTag("compat-new-replies-divider"))
        }
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onHeaderClick,
                onLongClick = onHeaderLongClick
            ),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                headerText,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                fontSize = 11.2f.sp,
                lineHeight = 14.sp,
                inlineContent = mapOf(
                    "compat-quote-count" to InlineTextContent(
                        Placeholder(
                            width = 1.2.em,
                            height = 0.96.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.thread_header_quote),
                            contentDescription = "返信数",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                ),
                color = when {
                    post.isContentRedacted -> Color.Red
                    post.isDeleted -> Color.Red
                    else -> palette.text
                }
            )
            if (rightAlignedSaidane) {
                Text(
                    buildAnnotatedString {
                        displayedSaidane?.let {
                            withStyle(SpanStyle(color = saidaneColor)) { append(it) }
                            append("\u00A0")
                        }
                        withStyle(SpanStyle(color = palette.headerSubtext)) {
                            append("No.")
                            append(post.postNo)
                        }
                    },
                    modifier = Modifier
                        .testTag("compat-thread-header-trailing-${post.postNo}")
                        .padding(start = 2.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                    fontSize = 11.2f.sp,
                    lineHeight = 14.sp,
                    maxLines = 1
                )
            }
        }
        val requestedPreviewUrl = resolveCompatPostPreviewUrl(post, upsThumbnailMethod, wifiConnected)
        val originalMediaUrl = resolveCompatViewerMediaUrl(post)
        val isUpsMedia = isCompatApuSmallMediaUrl(post.imageUrl ?: post.thumbnailUrl ?: "")
        val usesDirectApuSource = isUpsMedia && requestedPreviewUrl == originalMediaUrl
        val effectiveThumbnailSize = if (isUpsMedia) upsThumbnailSize else thumbnailSize
        val thumbnailRequestSizePx = compatThumbnailRequestSizePx(
            displaySizeDp = effectiveThumbnailSize.toFloat(),
            density = LocalDensity.current.density
        )
        var useOriginalAfterPreviewFailure by remember(post.postNo, requestedPreviewUrl) {
            mutableStateOf(false)
        }
        var completedPreviewRetries by remember(
            post.postNo,
            requestedPreviewUrl,
            thumbnailReloadToken
        ) {
            mutableIntStateOf(0)
        }
        val previewUrl = if (
            useOriginalAfterPreviewFailure &&
            requestedPreviewUrl != originalMediaUrl
        ) originalMediaUrl else requestedPreviewUrl
        if (previewUrl != null) {
            val platformContext = LocalPlatformContext.current
            val imageLoader = LocalFutachaImageLoader.current
            val imageModel: Any = remember(
                platformContext,
                previewUrl,
                completedPreviewRetries,
                thumbnailReloadToken,
                thumbnailRequestSizePx
            ) {
                ImageRequest.Builder(platformContext)
                    .data(previewUrl)
                    .compatImageFallbackPolicy()
                    .size(thumbnailRequestSizePx, thumbnailRequestSizePx)
                    // Changing the memory key makes Coil create a fresh
                    // request after a transient failure while retaining a
                    // successful disk entry. Manual reload remains the only
                    // path that deliberately bypasses both caches.
                    .apply {
                        // Direct あぷ小 sources use Coil's normal URL key so
                        // the thread, gallery and viewer share one memory/disk
                        // entry. Other thumbnails retain a distinct retry key.
                        compatThumbnailMemoryCacheKey(
                            previewUrl = previewUrl,
                            usesDirectApuSource = usesDirectApuSource,
                            completedRetries = completedPreviewRetries,
                            reloadToken = thumbnailReloadToken
                        )?.let(::memoryCacheKey)
                        refreshImageOnce(thumbnailReloadToken)
                    }
                    .build()
            }
            val painter = rememberAsyncImagePainter(model = imageModel, imageLoader = imageLoader)
            val painterState by painter.state.collectAsState()
            val promptMetadata = rememberGenerationMetadata(originalMediaUrl, painterState, visible = privacyAlpha >= 1f)
            val requestStartedAtEpochMillis = remember(
                previewUrl,
                completedPreviewRetries,
                thumbnailReloadToken
            ) { Clock.System.now().toEpochMilliseconds() }
            var delayedLoadingVisible by remember(
                post.postNo,
                previewUrl,
                completedPreviewRetries,
                thumbnailReloadToken
            ) { mutableStateOf(false) }
            LaunchedEffect(
                painterState,
                previewUrl,
                completedPreviewRetries,
                thumbnailReloadToken
            ) {
                when (painterState) {
                    is coil3.compose.AsyncImagePainter.State.Loading -> {
                        delay(COMPAT_THUMBNAIL_LOADING_INDICATOR_DELAY_MILLIS)
                        delayedLoadingVisible =
                            painter.state.value is coil3.compose.AsyncImagePainter.State.Loading
                    }
                    is coil3.compose.AsyncImagePainter.State.Success -> {
                        delayedLoadingVisible = false
                        val elapsedMillis =
                            Clock.System.now().toEpochMilliseconds() - requestStartedAtEpochMillis
                        if (elapsedMillis >= COMPAT_THUMBNAIL_LOADING_INDICATOR_DELAY_MILLIS) {
                            Logger.d(
                                "CompatThumbnail",
                                "Slow load post=${post.postNo} elapsedMs=$elapsedMillis url=$previewUrl"
                            )
                        }
                    }
                    is coil3.compose.AsyncImagePainter.State.Error -> {
                        delayedLoadingVisible = false
                        Logger.w(
                            "CompatThumbnail",
                            "Failed post=${post.postNo} attempt=$completedPreviewRetries elapsedMs=${Clock.System.now().toEpochMilliseconds() - requestStartedAtEpochMillis} url=$previewUrl"
                        )
                    }
                    else -> delayedLoadingVisible = false
                }
            }
            LaunchedEffect(
                painterState,
                previewUrl,
                requestedPreviewUrl,
                originalMediaUrl,
                completedPreviewRetries
            ) {
                if (painterState is coil3.compose.AsyncImagePainter.State.Error) {
                    val hasOriginalFallback =
                        !useOriginalAfterPreviewFailure &&
                            requestedPreviewUrl != null &&
                            originalMediaUrl != null &&
                            requestedPreviewUrl != originalMediaUrl
                    when (
                        if (usesDirectApuSource) {
                            CompatThumbnailFailureAction.SHOW_TERMINAL_ERROR
                        } else resolveCompatThumbnailFailureAction(
                            completedRetries = completedPreviewRetries,
                            hasOriginalFallback = hasOriginalFallback,
                            failure = (painterState as? coil3.compose.AsyncImagePainter.State.Error)?.result?.throwable
                        )
                    ) {
                        CompatThumbnailFailureAction.RETRY_CURRENT -> {
                            delay(compatThumbnailRetryDelayMillis(completedPreviewRetries))
                            completedPreviewRetries += 1
                        }
                        CompatThumbnailFailureAction.FALLBACK_TO_ORIGINAL -> {
                            // up/up2 thumbnails are derived files and can
                            // disappear independently of the source upload.
                            useOriginalAfterPreviewFailure = true
                            completedPreviewRetries = 0
                        }
                        CompatThumbnailFailureAction.SHOW_TERMINAL_ERROR -> Unit
                    }
                }
            }
            val intrinsicSize = painter.intrinsicSize
            val bounds = remember(
                effectiveThumbnailSize,
                post.thumbnailWidth,
                post.thumbnailHeight,
                painterState
            ) {
                compatThreadThumbnailBounds(
                    maxSize = effectiveThumbnailSize,
                    sourceWidth = post.thumbnailWidth
                        ?: intrinsicSize.width.toInt().takeIf { it > 0 },
                    sourceHeight = post.thumbnailHeight
                        ?: intrinsicSize.height.toInt().takeIf { it > 0 }
                )
            }
            val hasOriginalFallback =
                !useOriginalAfterPreviewFailure &&
                    requestedPreviewUrl != null &&
                    originalMediaUrl != null &&
                    requestedPreviewUrl != originalMediaUrl
            val terminalImageError =
                painterState is coil3.compose.AsyncImagePainter.State.Error &&
                    (
                        usesDirectApuSource ||
                            resolveCompatThumbnailFailureAction(
                                completedRetries = completedPreviewRetries,
                                hasOriginalFallback = hasOriginalFallback,
                            failure = (painterState as? coil3.compose.AsyncImagePainter.State.Error)?.result?.throwable
                            ) == CompatThumbnailFailureAction.SHOW_TERMINAL_ERROR
                        )
            Box(
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp, bottom = 5.dp)
                    .width(bounds.first.dp)
                    .height(bounds.second.dp)
                    .background(
                        if (delayedLoadingVisible || terminalImageError) {
                            palette.divider.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .compatPrivacyImageEffect(privacyAlpha)
                    .combinedClickable(
                        onClick = onMediaClick,
                        onLongClick = onMediaLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painter,
                    contentDescription = "No.${post.postNo}の画像",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(
                            "compat-thread-thumbnail-${post.postNo}-${when (painterState) {
                                is coil3.compose.AsyncImagePainter.State.Success -> "ready"
                                is coil3.compose.AsyncImagePainter.State.Error -> "error"
                                else -> "loading"
                            }}"
                        )
                )
                PromptAiBadge(promptMetadata, Modifier.align(Alignment.BottomEnd))
                if (delayedLoadingVisible) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("compat-thread-thumbnail-${post.postNo}-placeholder"),
                        color = palette.loadingProgress,
                        strokeWidth = 2.dp
                    )
                } else if (terminalImageError) {
                    Text(
                        text = "画像読込エラー",
                        modifier = Modifier.testTag("compat-thread-thumbnail-${post.postNo}-terminal-error"),
                        color = palette.uiSecondaryText,
                        fontSize = 10.sp
                    )
                }
            }
            InlinePrompt(promptMetadata)
        }
        // The reference client places generated あぷ小 previews above the
        // body.  The body itself must stay byte-for-byte represented as text;
        // no uploader filename is injected into it.
        CompatInlineApuSmallPreviews(
            urls = inlineApuSmallMediaUrls,
            thumbnailSize = upsThumbnailSize,
            privacyAlpha = privacyAlpha,
            onUrlClick = mediaAwareUrlClick
        )
        CompatMessageText(
            post = post,
            fontSize = fontSize,
            searchRanges = searchRanges,
            onClick = onClick,
            onLongClick = onLongClick,
            onUrlClick = mediaAwareUrlClick,
            onQuoteClick = onQuoteClick
        )
        deletionSummary?.let { summary ->
            Text(
                text = summary,
                color = Color.Red,
                fontSize = fontSize.sp,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                    .testTag("compat-thread-deletion-summary")
            )
        }
    }
    HorizontalDivider(color = CompatDivider)
}

/**
 * A post may have a normal board attachment and also mention an あぷ小 file
 * in its body (for example `fu7099123.jpg`). The post model has one primary
 * media slot, so this separate preview keeps the inline uploader reference
 * visible without replacing the board attachment.
 */
@Composable
internal fun CompatInlineApuSmallPreviews(
    urls: List<String>,
    thumbnailSize: Int,
    privacyAlpha: Float,
    onUrlClick: (String) -> Unit
) {
    if (urls.isEmpty()) return
    val imageLoader = LocalFutachaImageLoader.current
    val platformContext = LocalPlatformContext.current
    val thumbnailRequestSizePx = compatThumbnailRequestSizePx(
        displaySizeDp = thumbnailSize.toFloat(),
        density = LocalDensity.current.density
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        urls.forEach { sourceUrl ->
            val previewUrl = if (classifyFutabaMedia(sourceUrl) == FutabaMediaKind.VIDEO) {
                compatApuSmallThumbnailUrl(sourceUrl) ?: sourceUrl
            } else {
                sourceUrl
            }
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(platformContext)
                    .data(previewUrl)
                    .compatImageFallbackPolicy()
                    .size(thumbnailRequestSizePx, thumbnailRequestSizePx)
                    .build(),
                imageLoader = imageLoader
            )
            val painterState by painter.state.collectAsState()
            val intrinsicSize = painter.intrinsicSize
            val bounds = remember(thumbnailSize, painterState) {
                compatThreadThumbnailBounds(
                    maxSize = thumbnailSize,
                    sourceWidth = intrinsicSize.width.toInt().takeIf { it > 0 },
                    sourceHeight = intrinsicSize.height.toInt().takeIf { it > 0 }
                )
            }
            Image(
                painter = painter,
                contentDescription = "あぷ小画像を開く",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp, bottom = 5.dp)
                    .width(bounds.first.dp)
                    .height(bounds.second.dp)
                    .compatPrivacyImageEffect(privacyAlpha)
                    .combinedClickable(
                        onClick = { onUrlClick(sourceUrl) },
                        onLongClick = { onUrlClick(sourceUrl) }
                    )
            )
        }
    }
}

@Composable
private fun CompatMessageText(
    post: CompatPostSnapshot,
    fontSize: Int,
    searchRanges: List<CompatSearchTextRange>,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onUrlClick: (String) -> Unit = {},
    onQuoteClick: (String) -> Unit
) {
    val message = remember(post.messageHtml) { post.messageHtml.toCompatPlainText() }
    val inlineLinks = remember(post.messageHtml) { compatInlineLinks(post.messageHtml) }
    val palette = LocalCompatibilityPalette.current
    val searchTextHighlight = palette.searchTextHighlight
    val deletedNoticeRanges = remember(post, message) {
        compatDeletedNoticeRanges(post, message)
    }
    val annotated = remember(
        message,
        inlineLinks,
        searchRanges,
        deletedNoticeRanges,
        searchTextHighlight,
        palette.bodyLink,
        palette.bodyQuote
    ) {
        buildAnnotatedString {
            append(message)
            deletedNoticeRanges.forEach { range ->
                addStyle(
                    SpanStyle(color = Color.Red),
                    range.start.coerceIn(0, length),
                    range.endExclusive.coerceIn(0, length)
                )
            }
            message.lineSequence().fold(0) { offset, line ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith(">") || trimmed.startsWith("＞")) {
                    val markerIndex = line.indexOfFirst { it == '>' || it == '＞' }
                    val start = offset + markerIndex
                    val end = offset + line.length
                    val quoteQuery = compatQuoteQueryForLine(trimmed)
                    if (quoteQuery == null) return@fold offset + line.length + 1
                    addStringAnnotation(
                        tag = "compat_quote",
                        annotation = quoteQuery,
                        start = start,
                        end = end.coerceAtLeast(start + 1)
                    )
                    addStyle(
                        // Reply references in the APK use the legacy green quote
                        // color (#789922) and explicitly disable underlining.
                        SpanStyle(color = palette.bodyQuote),
                        start,
                        end.coerceAtLeast(start + 1)
                    )
                }
                offset + line.length + 1
            }
            inlineLinks.forEach { link ->
                val start = link.start.coerceIn(0, length)
                val end = link.endExclusive.coerceIn(start, length)
                val lineStart = message.lastIndexOf('\n', (start - 1).coerceAtLeast(0)) + 1
                val isQuotedLine = message.substring(lineStart, start.coerceAtMost(message.length))
                    .trimStart()
                    .let { it.startsWith(">") || it.startsWith("＞") }
                // A filename/URL inside a Futaba quote is the quote source,
                // not a browser link.  The reference APK colors the complete
                // line as a quote and opens the referenced response popup.
                if (isQuotedLine) return@forEach
                if (end > start) {
                    addStringAnnotation("compat_url", link.url, start, end)
                    addStyle(
                        SpanStyle(
                            color = palette.bodyLink,
                            textDecoration = TextDecoration.Underline
                        ),
                        start,
                        end
                    )
                }
            }
            searchRanges.forEach { range ->
                val start = range.start.coerceIn(0, length)
                val end = range.endExclusive.coerceIn(start, length)
                if (end > start) {
                    addStyle(SpanStyle(background = searchTextHighlight), start, end)
                }
            }
        }
    }
    var textLayoutResult by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = annotated,
        style = TextStyle(
            fontSize = fontSize.sp,
            // BasicText does not consume MaterialTheme.typography by itself.
            // Supplying a complete TextStyle here used to replace the custom
            // font selected in compatibility settings with the platform font
            // for every thread body.
            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
            color = if (compatPostBodyUsesAlertColor(post)) {
                Color.Red
            } else {
                LocalCompatibilityPalette.current.text
            }
        ),
        onTextLayout = { textLayoutResult = it },
        modifier = Modifier
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            .clickable(
                onClickLabel = "引用を表示",
                onClick = {
                    message.lineSequence()
                        .mapNotNull(::compatQuoteQueryForLine)
                        .firstOrNull()
                        ?.let(onQuoteClick)
                        ?: inlineLinks.firstOrNull()?.url?.let(onUrlClick)
                        ?: onClick()
                }
            )
            // `clickable` supplies an explicit accessibility/test action. The
            // pointer detector below remains responsible for choosing the
            // exact annotated URL/quote under a real finger tap.
            .pointerInput(annotated) {
            detectTapGestures(
                onLongPress = { onLongClick() },
                onTap = { position ->
                    val offset = textLayoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                    annotated.getStringAnnotations("compat_quote", offset, offset)
                        .firstOrNull()
                        ?.let { onQuoteClick(it.item) }
                        ?: annotated.getStringAnnotations("compat_url", offset, offset)
                            .firstOrNull()
                            ?.let { onUrlClick(it.item) }
                        ?: run {
                            // BasicText can report the caret at the end of a
                            // glyph on some Android text engines. Recover the
                            // complete line so a >>No link remains tappable
                            // even when its annotation range misses that edge.
                            val lineStart = message.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)) + 1
                            val lineEnd = message.indexOf('\n', offset).takeIf { it >= 0 } ?: message.length
                            compatQuoteQueryForLine(message.substring(lineStart, lineEnd))
                                ?.let(onQuoteClick)
                                ?: onClick()
                        }
                }
            )
        }
    )
}

@Composable
internal fun CompatThreadMetadataRow(tab: CompatTab, onClick: () -> Unit, onLongClick: () -> Unit) {
    val palette = LocalCompatibilityPalette.current
    val live = !tab.isDead
    val titleColor = when {
        tab.favorite -> Color(0xFF00897B)
        !live -> Color(0xFFCCCCCC)
        else -> palette.text
    }
    val secondaryColor = if (live) palette.uiSecondaryText else Color(0xFFCCCCCC)
    val reply = compatDrawerReplyPresentation(tab.checkedReplyCount, tab.replyCount)
    val noThumb = painterResource(Res.drawable.cmn_no_thumb)
    Row(
        modifier = Modifier.fillMaxWidth().height(COMPAT_REFERENCE_DRAWER_THREAD_ROW_DP.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .testTag("compat-drawer-tab-row-${tab.key}")
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = tab.thumbnailUrl,
            contentDescription = null,
            fallback = noThumb,
            error = noThumb,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(COMPAT_REFERENCE_DRAWER_THREAD_THUMBNAIL_DP.dp)
                .testTag("compat-drawer-tab-thumb-${tab.key}")
                .graphicsLayer { alpha = if (live) 1f else 0.33f }
        )
        Spacer(Modifier.width(5.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tab.title.lineSequence().firstOrNull().orEmpty(),
                maxLines = 1,
                fontSize = 16.sp,
                color = titleColor,
                modifier = Modifier.testTag("compat-drawer-tab-title-${tab.key}")
            )
            Text(
                compatDrawerThreadSubtitle(tab.contentUpdatedAtEpochMillis, tab.boardName),
                maxLines = 1,
                fontSize = 12.sp,
                color = secondaryColor,
                modifier = Modifier.testTag("compat-drawer-tab-subtitle-${tab.key}")
            )
        }
        if (tab.isDeleted) Text("消", color = Color(0xFFB71C1C), fontSize = 13.sp)
        else if (tab.isIsolated) Text("隔", color = Color(0xFFE65100), fontSize = 13.sp)
        else if (tab.isExploded) Text("爆", color = Color.Red, fontSize = 13.sp)
        else if (tab.isDead) Text("落", color = Color.Red, fontSize = 13.sp)
        else if (tab.isOld) Text("古", color = Color(0xFFE65100), fontSize = 13.sp)
        Column(Modifier.width(50.dp).testTag("compat-drawer-tab-replies-${tab.key}"), horizontalAlignment = Alignment.End) {
            Text(reply.readCount, maxLines = 1, fontSize = 16.sp, color = if (live) Color(0xFF00897B) else Color(0xFFCCCCCC))
            Text(reply.increase, maxLines = 1, fontSize = 12.sp, color = if (live) Color.Red else Color(0xFFCCCCCC))
        }
    }
    HorizontalDivider()
}
