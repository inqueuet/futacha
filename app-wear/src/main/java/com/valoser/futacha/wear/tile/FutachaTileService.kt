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
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchSnapshotFreshness
import com.valoser.futacha.shared.watch.WatchThreadSummary
import com.valoser.futacha.shared.watch.classifyWatchSnapshotFreshness
import com.valoser.futacha.shared.watch.isWatchReadAloudStatusFreshOnWatch
import com.valoser.futacha.wear.WearMainActivity
import com.valoser.futacha.wear.sync.WatchClockOffsetStore
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
                completeActiveTileRequest(future, tile)
            }.onFailure {
                completeActiveTileRequest(future, buildTile(requestParams, WatchSnapshotStore.observe().value))
            }
        }
        return future
    }

    private fun buildTile(
        requestParams: RequestBuilders.TileRequest,
        snapshot: WatchSnapshot?
    ): Tile {
        // The phone's time as far as the watch can tell; the watch clock may be off.
        val nowMillis = WatchClockOffsetStore.phoneNowMillis(this)
        val activeReadAloudThread = snapshot
            ?.threads
            ?.firstOrNull { it.freshReadAloudStatus(nowMillis) != null }
        val mainText = when {
            snapshot == null -> "未同期"
            activeReadAloudThread != null -> activeReadAloudThread.freshReadAloudStatus(nowMillis)
                ?.let { status ->
                    "${readAloudTileStateLabel(status.state.name)}\n${status.postId?.let { "No.$it" } ?: "${status.currentIndex + 1}/${status.totalPosts}"}"
                }
                ?: "読み上げ"
            snapshot.freshness(nowMillis) == WatchSnapshotFreshness.Stale ->
                "同期古い\n${formatTileTime(snapshot.generatedAtMillis)}"
            snapshot.freshness(nowMillis) == WatchSnapshotFreshness.ClockSkew ->
                "時計ずれ\n${formatTileTime(snapshot.generatedAtMillis)}"
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
            .setFreshnessIntervalMillis(tileFreshnessIntervalMillis(snapshot, nowMillis))
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
        activeTileRequests.entries.toList().forEach { (future, requestParams) ->
            if (activeTileRequests.remove(future) != null) {
                completeTileFuture(future, buildTile(requestParams, WatchSnapshotStore.observe().value))
            }
        }
        tileScope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    private fun completeActiveTileRequest(future: SettableFuture<Tile>, tile: Tile) {
        if (activeTileRequests.remove(future) != null) {
            completeTileFuture(future, tile)
        }
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

    private fun WatchSnapshot.freshness(nowMillis: Long): WatchSnapshotFreshness =
        classifyWatchSnapshotFreshness(generatedAtMillis, nowMillis)

    private fun formatTileTime(epochMillis: Long): String {
        if (epochMillis <= 0) return "--:--"
        val date = java.util.Date(epochMillis)
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
    }

    private fun WatchThreadSummary.freshReadAloudStatus(
        nowMillis: Long
    ): WatchReadAloudStatus? {
        val status = readAloudStatus ?: return null
        return status.takeIf { isWatchReadAloudStatusFreshOnWatch(it.updatedAtMillis, nowMillis) }
    }

    private companion object {
        private const val RESOURCES_VERSION = "1"
        private const val TILE_TITLE_MAX_CHARS = 24
        private const val TILE_CLICK_ID = "open_futacha_wear"
        private const val TILE_SNAPSHOT_LOAD_TIMEOUT_MILLIS = 1_500L
    }
}
