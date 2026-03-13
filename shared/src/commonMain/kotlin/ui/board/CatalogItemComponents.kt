package com.valoser.futacha.shared.ui.board

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogGrid(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    gridColumns: Int,
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }

    var overscrollTarget by remember { mutableFloatStateOf(0f) }
    val overscrollOffset by animateFloatAsState(
        targetValue = overscrollTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "catalogGridOverscroll"
    )

    val isAtTop by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem != null &&
                firstVisibleItem.index == 0 &&
                firstVisibleItem.offset.y.toFloat() <= edgeOffsetTolerancePx
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisibleItem == null || totalItems == 0) return@derivedStateOf false
            if (lastVisibleItem.index < totalItems - 1) return@derivedStateOf false
            val viewportEnd = layoutInfo.viewportEndOffset
            val lastItemEnd = lastVisibleItem.offset.y + lastVisibleItem.size.height
            val remainingSpace = viewportEnd - lastItemEnd
            remainingSpace.toFloat() <= edgeOffsetTolerancePx ||
                layoutInfo.visibleItemsInfo.size >= totalItems
        }
    }
    val latestIsAtTop by rememberUpdatedState(isAtTop)
    val latestIsAtBottom by rememberUpdatedState(isAtBottom)

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollTarget = 0f
        }
    }

    LazyVerticalGrid(
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, overscrollOffset.toInt()) }
            .pointerInput(isRefreshing, refreshTriggerPx, maxOverscrollPx) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        val updatedState = updateCatalogOverscrollDragState(
                            totalDrag = totalDrag,
                            dragAmount = dragAmount,
                            isRefreshing = isRefreshing,
                            isAtTop = latestIsAtTop,
                            isAtBottom = latestIsAtBottom,
                            maxOverscrollPx = maxOverscrollPx
                        )
                        totalDrag = updatedState.totalDrag
                        overscrollTarget = updatedState.overscrollTarget
                        if (updatedState.shouldConsume) {
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (shouldTriggerCatalogOverscrollRefresh(totalDrag, refreshTriggerPx)) {
                            onRefresh()
                        }
                        totalDrag = 0f
                        overscrollTarget = 0f
                    },
                    onDragCancel = {
                        totalDrag = 0f
                        overscrollTarget = 0f
                    }
                )
            },
        columns = GridCells.Fixed(gridColumns.coerceIn(MIN_CATALOG_GRID_COLUMNS, MAX_CATALOG_GRID_COLUMNS)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        items(items = items, key = { it.id }) { catalogItem ->
            CatalogCard(
                item = catalogItem,
                boardUrl = board?.url,
                repository = repository,
                onClick = { onThreadSelected(catalogItem) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogList(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }

    var overscrollTarget by remember { mutableFloatStateOf(0f) }
    val overscrollOffset by animateFloatAsState(
        targetValue = overscrollTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "catalogListOverscroll"
    )

    val isAtTop by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem != null &&
                firstVisibleItem.index == 0 &&
                firstVisibleItem.offset.toFloat() <= edgeOffsetTolerancePx
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisibleItem == null || totalItems == 0) return@derivedStateOf false
            if (lastVisibleItem.index < totalItems - 1) return@derivedStateOf false
            val viewportEnd = layoutInfo.viewportEndOffset
            val lastItemEnd = lastVisibleItem.offset + lastVisibleItem.size
            val remainingSpace = viewportEnd - lastItemEnd
            remainingSpace.toFloat() <= edgeOffsetTolerancePx ||
                layoutInfo.visibleItemsInfo.size >= totalItems
        }
    }
    val latestIsAtTop by rememberUpdatedState(isAtTop)
    val latestIsAtBottom by rememberUpdatedState(isAtBottom)

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollTarget = 0f
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, overscrollOffset.toInt()) }
            .pointerInput(isRefreshing, refreshTriggerPx, maxOverscrollPx) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        val updatedState = updateCatalogOverscrollDragState(
                            totalDrag = totalDrag,
                            dragAmount = dragAmount,
                            isRefreshing = isRefreshing,
                            isAtTop = latestIsAtTop,
                            isAtBottom = latestIsAtBottom,
                            maxOverscrollPx = maxOverscrollPx
                        )
                        totalDrag = updatedState.totalDrag
                        overscrollTarget = updatedState.overscrollTarget
                        if (updatedState.shouldConsume) {
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (shouldTriggerCatalogOverscrollRefresh(totalDrag, refreshTriggerPx)) {
                            onRefresh()
                        }
                        totalDrag = 0f
                        overscrollTarget = 0f
                    },
                    onDragCancel = {
                        totalDrag = 0f
                        overscrollTarget = 0f
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        items(items = items, key = { it.id }) { catalogItem ->
            CatalogListItem(
                item = catalogItem,
                boardUrl = board?.url,
                repository = repository,
                onClick = { onThreadSelected(catalogItem) }
            )
        }
    }
}

@Composable
private fun CatalogCard(
    item: CatalogItem,
    boardUrl: String?,
    repository: BoardRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val targetSizePx = with(density) { 50.dp.toPx().toInt() }
    val hasPreviewImage = !item.thumbnailUrl.isNullOrBlank() || !item.fullImageUrl.isNullOrBlank()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                shape = MaterialTheme.shapes.small
            ),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPreviewImage) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CatalogPreviewImage(
                        thumbnailUrl = item.thumbnailUrl,
                        fullImageUrl = item.fullImageUrl,
                        targetSizePx = targetSizePx,
                        contentDescription = item.title ?: "サムネイル",
                        modifier = Modifier.fillMaxSize(),
                        fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.replyCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = Color.White,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "${item.replyCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.title ?: "無題",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.bodySmall.fontSize
                )
            }
        }
    }
}

@Composable
private fun CatalogListItem(
    item: CatalogItem,
    boardUrl: String?,
    repository: BoardRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val targetSizePx = with(density) { 72.dp.toPx().toInt() }
    val hasPreviewImage = !item.thumbnailUrl.isNullOrBlank() || !item.fullImageUrl.isNullOrBlank()

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPreviewImage) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CatalogPreviewImage(
                        thumbnailUrl = item.thumbnailUrl,
                        fullImageUrl = item.fullImageUrl,
                        targetSizePx = targetSizePx,
                        contentDescription = item.title ?: "サムネイル",
                        modifier = Modifier.fillMaxSize(),
                        fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title?.takeIf { it.isNotBlank() } ?: "無題",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No.${item.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${item.replyCount}レス",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CatalogPreviewImage(
    thumbnailUrl: String?,
    fullImageUrl: String?,
    targetSizePx: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fallbackTint: Color = Color.Gray
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = LocalFutachaImageLoader.current
    val candidates = remember(thumbnailUrl, fullImageUrl) {
        buildList {
            thumbnailUrl?.takeIf { it.isNotBlank() }?.let(::add)
            fullImageUrl
                ?.takeIf { it.isNotBlank() && it != thumbnailUrl }
                ?.let(::add)
        }
    }
    var candidateIndex by remember(candidates) { mutableIntStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex)
    val imageRequest = remember(activeUrl, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(activeUrl)
            .crossfade(true)
            .size(targetSizePx, targetSizePx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val imagePainter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader
    )
    val painterState by imagePainter.state.collectAsState()

    LaunchedEffect(painterState, candidateIndex, candidates.size) {
        if (painterState is AsyncImagePainter.State.Error && candidateIndex < candidates.lastIndex) {
            candidateIndex += 1
        }
    }

    val shouldShowFallback = activeUrl.isNullOrBlank() ||
        ((painterState is AsyncImagePainter.State.Error || painterState is AsyncImagePainter.State.Empty) &&
            candidateIndex >= candidates.lastIndex)

    if (shouldShowFallback) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = fallbackTint
        )
    } else {
        Image(
            painter = imagePainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
