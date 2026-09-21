package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.ui.compat.CompatImageSearchMethod
import com.valoser.futacha.shared.ui.compat.CompatImageSearchResult
import com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget
import com.valoser.futacha.shared.ui.compat.CompatReverseImageSearchScreen
import com.valoser.futacha.shared.ui.compat.DEFAULT_COMPAT_ASCII2D_ENDPOINT
import com.valoser.futacha.shared.ui.compat.buildCompatImageSearchTargetUrl
import com.valoser.futacha.shared.ui.compat.isCompatImageSearchableMediaUrl
import com.valoser.futacha.shared.ui.compat.searchCompatAscii2d
import com.valoser.futacha.shared.ui.compat.searchCompatImageFileTarget
import com.valoser.futacha.shared.ui.compat.COMPAT_CUSTOM_IMAGE_SEARCH_KEY
import com.valoser.futacha.shared.ui.compat.parseCompatImageSearchTargets
import com.valoser.futacha.shared.ui.compat.compatAscii2dEndpoint
import com.valoser.futacha.shared.util.rememberUrlLauncher
import io.ktor.client.HttpClient
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal fun threadImageSearchTargets(imageUrl: String): List<CompatImageSearchTarget> =
    CompatImageSearchTarget.entries.filter { target ->
        isCompatImageSearchableMediaUrl(
            imageUrl,
            allowGif = target != CompatImageSearchTarget.ASCII2D_URL
        )
    }

internal suspend fun searchThreadImage(
    httpClient: HttpClient?,
    imageUrl: String,
    target: CompatImageSearchTarget,
    ascii2dEndpoint: String = DEFAULT_COMPAT_ASCII2D_ENDPOINT
): Result<CompatImageSearchResult> {
    if (target !in threadImageSearchTargets(imageUrl)) {
        return Result.failure(IllegalArgumentException("この画像は検索できません"))
    }
    buildCompatImageSearchTargetUrl(target, imageUrl)?.let { url ->
        return Result.success(CompatImageSearchResult.RemoteUrl(target.label, url))
    }
    val client = httpClient
        ?: return Result.failure(IllegalStateException("画像検索の通信機能を利用できません"))
    return try {
        if (target.method == CompatImageSearchMethod.FILE) {
            searchCompatImageFileTarget(client, target, imageUrl)
        } else {
            searchCompatAscii2d(client, ascii2dEndpoint, imageUrl).map {
                CompatImageSearchResult.RemoteUrl(target.label, it)
            }
        }
    } catch (timeout: TimeoutCancellationException) {
        coroutineContext.ensureActive()
        Result.failure(IllegalStateException("画像検索がタイムアウトしました", timeout))
    }
}

@Composable
internal fun ThreadImageSearchDialog(
    imageUrl: String,
    httpClient: HttpClient?,
    cookieRepository: CookieRepository?,
    onDismiss: () -> Unit
) {
    val openUrl = rememberUrlLauncher()
    val features = LocalFutachaSharedFeatures.current
    val selectedTargets = features?.preferences?.get(COMPAT_CUSTOM_IMAGE_SEARCH_KEY)
    val targets = remember(imageUrl, selectedTargets) {
        val supported = threadImageSearchTargets(imageUrl)
        if (selectedTargets == null) supported else parseCompatImageSearchTargets(selectedTargets).filter { it in supported }
    }
    var pendingTarget by remember(imageUrl) { mutableStateOf<CompatImageSearchTarget?>(null) }
    var result by remember(imageUrl) { mutableStateOf<CompatImageSearchResult?>(null) }
    var error by remember(imageUrl) { mutableStateOf<String?>(null) }

    // Leaving this dialog cancels uploads and pending provider requests.
    LaunchedEffect(imageUrl, pendingTarget) {
        val target = pendingTarget ?: return@LaunchedEffect
        searchThreadImage(httpClient, imageUrl, target,
            compatAscii2dEndpoint(features?.preferences.orEmpty()))
            .onSuccess { result = it }
            .onFailure { error = it.message ?: "画像検索に失敗しました" }
        pendingTarget = null
    }

    val searchResult = result
    if (searchResult != null) {
        CompatReverseImageSearchScreen(
            result = searchResult,
            cookieRepository = cookieRepository,
            onClose = { result = null },
            onOpenExternal = openUrl
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("画像検索") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("検索先を選択してください。URL方式は画像URL、File方式は画像を検索先へ送信します。")
                    if (features != null) {
                        TextButton(onClick = { features.openSettings("image_search") }, enabled = pendingTarget == null) {
                            Text("検索先を設定")
                        }
                    }
                    if (targets.isEmpty()) Text("検索先を設定してください。")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (pendingTarget != null) {
                        Text("${pendingTarget?.label}で検索中…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                            targets.forEach { target ->
                                TextButton(
                                    onClick = { error = null; pendingTarget = target },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(target.label) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Text(if (pendingTarget != null) "キャンセル" else "閉じる")
                }
            }
        )
    }
}
