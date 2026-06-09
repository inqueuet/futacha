package com.valoser.futacha.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_STALE_AGE_MILLIS
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchThreadSummary
import com.valoser.futacha.wear.WearMainActivity
import com.valoser.futacha.wear.sync.WatchSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class FutachaTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeTileRequests = ConcurrentHashMap<SettableFuture<Tile>, RequestBuilders.TileRequest>()

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<Tile> {
        val future = SettableFuture.create<Tile>()
        activeTileRequests[future] = requestParams
        tileScope.launch {
            runCatching {
                val snapshot = withTimeoutOrNull(TILE_SNAPSHOT_LOAD_TIMEOUT_MILLIS) {
                    WatchSnapshotStore.getSnapshot(applicationContext)
                } ?: WatchSnapshotStore.observe().value
                buildTile(requestParams, snapshot)
            }.onSuccess { tile ->
                completeTileFuture(future, tile)
            }.onFailure {
                completeTileFuture(future, buildTile(requestParams, WatchSnapshotStore.observe().value))
            }.also {
                activeTileRequests.remove(future)
            }
        }
        return future
    }

    private fun buildTile(
        requestParams: RequestBuilders.TileRequest,
        snapshot: WatchSnapshot?
    ): Tile {
        val activeReadAloudThread = snapshot
            ?.threads
            ?.firstOrNull { it.freshReadAloudStatus() != null }
        val mainText = when {
            snapshot == null -> "未同期"
            activeReadAloudThread != null -> activeReadAloudThread.freshReadAloudStatus()
                ?.let { status ->
                    "${readAloudTileStateLabel(status.state.name)}\n${status.postId?.let { "No.$it" } ?: "${status.currentIndex + 1}/${status.totalPosts}"}"
                }
                ?: "読み上げ"
            snapshot.isStale() -> "同期古い\n${formatTileTime(snapshot.generatedAtMillis)}"
            else -> "新着 ${snapshot.unreadTotal}\n監視 ${snapshot.watchMatchTotal}"
        }
        val latestTitle = (activeReadAloudThread ?: snapshot?.threads?.firstOrNull())
            ?.title
            ?.take(TILE_TITLE_MAX_CHARS)
            ?: "スマホと同期"

        val layout = PrimaryLayout.Builder(requestParams.deviceConfiguration)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "futacha")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .build()
            )
            .setContent(buildTileContent(mainText, latestTitle))
            .build()

        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<Resources> {
        return Futures.immediateFuture(
            Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    override fun onDestroy() {
        activeTileRequests.forEach { (future, requestParams) ->
            completeTileFuture(future, buildTile(requestParams, WatchSnapshotStore.observe().value))
        }
        activeTileRequests.clear()
        tileScope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    private fun completeTileFuture(future: SettableFuture<Tile>, tile: Tile) {
        if (!future.isDone) {
            future.set(tile)
        }
    }

    private fun buildTileContent(
        mainText: String,
        latestTitle: String
    ): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Column.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(TILE_CLICK_ID)
                            .setOnClick(buildLaunchAction())
                            .build()
                    )
                    .build()
            )
            .addContent(
                Text.Builder(this, mainText)
                    .setTypography(Typography.TYPOGRAPHY_TITLE2)
                    .setMaxLines(2)
                    .build()
            )
            .addContent(
                Text.Builder(this, latestTitle)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setMaxLines(2)
                    .build()
            )
            .build()
    }

    private fun buildLaunchAction(): ActionBuilders.Action {
        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(WearMainActivity::class.java.name)
                    .build()
            )
            .build()
    }

    private fun readAloudTileStateLabel(stateName: String): String {
        return when (stateName) {
            "Speaking" -> "読上げ中"
            "Paused" -> "一時停止"
            else -> "読み上げ"
        }
    }

    private fun WatchSnapshot.isStale(
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val ageMillis = nowMillis - generatedAtMillis
        return generatedAtMillis <= 0 || ageMillis !in 0..WATCH_SNAPSHOT_STALE_AGE_MILLIS
    }

    private fun formatTileTime(epochMillis: Long): String {
        if (epochMillis <= 0) return "--:--"
        val date = java.util.Date(epochMillis)
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
    }

    private fun WatchThreadSummary.freshReadAloudStatus(
        nowMillis: Long = System.currentTimeMillis()
    ): WatchReadAloudStatus? {
        val status = readAloudStatus ?: return null
        val ageMillis = nowMillis - status.updatedAtMillis
        return status.takeIf {
            status.updatedAtMillis > 0 &&
                ageMillis in 0..WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
        }
    }

    private companion object {
        private const val RESOURCES_VERSION = "1"
        private const val TILE_TITLE_MAX_CHARS = 24
        private const val TILE_CLICK_ID = "open_futacha_wear"
        private const val TILE_SNAPSHOT_LOAD_TIMEOUT_MILLIS = 1_500L
    }
}
