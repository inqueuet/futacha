@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import coil3.disk.DiskCache
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.PromptMediaSource
import com.valoser.futacha.shared.media.source.*
import com.valoser.futacha.shared.network.createHttpClient
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.use
import platform.UIKit.UIViewController
import platform.Foundation.NSURL
import kotlin.random.Random

/** DEBUG-only Swift launch route. WebKit requires a hosted UIApplication, unlike Native tests. */
fun OriginalVideoValidationController(filePath: String, extension: String, fallbackFilePath: String? = null,
    remoteUrl: String? = null, useLocalFile: Boolean = false, muted: Boolean = false): UIViewController = ComposeUIViewController {
    require(extension in setOf("mp4", "webm"))
    val directory = remember { FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("video-ui-${Random.nextLong()}") }
    val requests = remember { MutableStateFlow(0) }
    val gate = remember { MediaFeatureGate() }
    var settings by remember { mutableStateOf(MediaFeatureSettings.Disabled) }
    val client = remember { createHttpClient() }
    val downloader = remember { KtorOriginalMediaDownloader(client, 8 * 1024 * 1024) }
    val originals = remember {
        OriginalMediaStore("video-validation", {
            DiskCache.Builder().directory(directory).maxSizeBytes(16 * 1024 * 1024).build()
        }, { request, sink ->
            requests.update { it + 1 }
            // Keep the real board-source eligibility path; only the DEBUG fixture transport
            // maps its one request to the local server, without changing production hosts.
            if (remoteUrl != null) downloader.download(request.copy(url = remoteUrl), sink)
            else {
                val path = if (request.url.endsWith("fallback.mp4")) requireNotNull(fallbackFilePath) else filePath
                FileSystem.SYSTEM.source(path.toPath()).use { sink.writeAll(it) }
                OriginalMediaInfo(if (path == fallbackFilePath) "video/mp4" else "video/$extension",
                    requireNotNull(FileSystem.SYSTEM.metadata(path.toPath()).size), resolvedUrl = request.url)
            }
        })
    }
    val prompts = remember { PromptMediaSource(originals, gate,
        readLocalMetadata = { com.valoser.futacha.shared.media.prompt.readLocalGenerationMetadata(it, null) }) }
    val fs = remember { createFileSystem() }
    val scope = rememberCoroutineScope()
    val count by requests.collectAsState()
    val url = if (useLocalFile) requireNotNull(NSURL.fileURLWithPath(filePath).absoluteString)
        else "https://may.2chan.net/b/src/validation.$extension"
    var selectedUrl by remember { mutableStateOf(url) }
    var error by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf("loading") }
    var played by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf("unknown") }
    var save by remember { mutableStateOf("") }
    val documents by produceState(0, directory) {
        while (isActive) {
            value = withContext(AppDispatchers.io) {
                if (!FileSystem.SYSTEM.exists(directory)) 0
                else FileSystem.SYSTEM.list(directory).count { it.name.startsWith("web-player-") }
            }
            delay(100)
        }
    }
    SideEffect { gate.update(settings) }
    DisposableEffect(Unit) {
        onDispose {
            prompts.close()
            CoroutineScope(NonCancellable + AppDispatchers.io).launch {
                try { originals.closeAndAwait() }
                finally { downloader.close(); client.close(); FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false) }
            }
        }
    }
    CompositionLocalProvider(LocalOriginalMediaSource provides prompts, LocalMediaFeatureGate provides gate,
        LocalMediaFeatureSettings provides settings) {
        MaterialTheme {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Text("requests:$count size:$size state:$state documents:$documents played:$played")
                Text("source:${selectedUrl.substringAfterLast('/')} error:$error")
                Row {
                    Button(onClick = { shown = !shown; size = "unknown"; state = "loading" }) { Text(if (shown) "Hide video" else "Show video") }
                    Button(onClick = { settings = settings.copy(promptDisplayEnabled = !settings.promptDisplayEnabled) }) {
                        Text(if (settings.promptDisplayEnabled) "Prompt OFF" else "Prompt ON")
                    }
                }
                if (shown) PromptInfoAction(rememberGenerationMetadata(url))
                Button(onClick = { scope.launch {
                    save = SingleMediaSaveService(client, fs, originals).saveMedia(url, "b", "1", baseDirectory = "video_validation")
                        .fold(onSuccess = { "saved:${it.relativePath}" }, onFailure = { "save-error:${it.message}" })
                } }) { Text("Save original") }
                Text(save)
                if (shown) PlatformVideoPlayer(url, Modifier.fillMaxWidth().weight(1f),
                    isMuted = muted,
                    onStateChanged = { state = it.name; if (it == com.valoser.futacha.shared.ui.board.VideoPlayerState.Ready) played = true },
                    onVideoSizeKnown = { w, h -> size = "${w}x$h" },
                    onPlaybackError = { error = it.code.orEmpty() },
                    fallbackVideoUrls = if (fallbackFilePath == null) emptyList() else listOf("https://may.2chan.net/b/src/fallback.mp4"),
                    onPlaybackSourceChanged = { selectedUrl = it; error = "" })
            }
        }
    }
}
