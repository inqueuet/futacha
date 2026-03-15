package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.rememberUrlLauncher

@Composable
internal fun ImagePreviewDialog(
    entry: MediaPreviewEntry,
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onSave: (() -> Unit)? = null,
    isSaveEnabled: Boolean = true,
    isSaveInProgress: Boolean = false
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = LocalFutachaImageLoader.current
    val urlLauncher = rememberUrlLauncher()
    var scale by remember { mutableStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(entry.url) {
        scale = 1f
        translation = Offset.Zero
    }
    val previewRequest = remember(entry.url) {
        ImageRequest.Builder(platformContext)
            .data(entry.url)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = previewRequest,
        imageLoader = imageLoader
    )
    val painterState by painter.state.collectAsState()
    val isLoadingState = painterState is AsyncImagePainter.State.Loading
    val isErrorState = painterState is AsyncImagePainter.State.Error

    ThreadMediaPreviewDialogFrame(
        navigationKey = entry.url,
        onDismiss = onDismiss,
        onNavigateNext = onNavigateNext,
        onNavigatePrevious = onNavigatePrevious,
        containerModifier = Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan: Offset, zoom: Float, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                translation += pan
            }
        }
    ) { previewSize ->
        val targetContentScale by remember(previewSize, painterState) {
            derivedStateOf {
                val imageSize = painter.intrinsicSize
                val containerWidth = previewSize.width.toFloat()
                val containerHeight = previewSize.height.toFloat()
                if (
                    imageSize.width > 0f &&
                    imageSize.height > 0f &&
                    containerWidth > 0f &&
                    containerHeight > 0f
                ) {
                    val imageAspect = imageSize.width / imageSize.height
                    val containerAspect = containerWidth / containerHeight
                    if (imageAspect < containerAspect) {
                        ContentScale.FillHeight
                    } else {
                        ContentScale.FillWidth
                    }
                } else {
                    ContentScale.Fit
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painter,
                contentDescription = "プレビュー画像",
                contentScale = targetContentScale,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translation.x
                        translationY = translation.y
                        alpha = if (isErrorState) 0f else 1f
                    }
            )
            if (isLoadingState) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isErrorState) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "画像を読み込めませんでした",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { urlLauncher(entry.url) }) {
                        Text("ブラウザで開く")
                    }
                }
            }
            ThreadMediaPreviewHeader(
                title = entry.title,
                currentIndex = currentIndex,
                totalCount = totalCount,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSave != null) {
                    FilledTonalButton(
                        onClick = onSave,
                        enabled = isSaveEnabled && !isSaveInProgress,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.Black.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.55f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (isSaveInProgress) "保存中..." else "保存")
                    }
                }
                ThreadMediaPreviewCloseButton(onDismiss = onDismiss)
            }
        }
    }
}
