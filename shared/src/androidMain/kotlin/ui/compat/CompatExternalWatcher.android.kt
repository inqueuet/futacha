package com.valoser.futacha.shared.ui.compat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WATCHER_PACKAGE = "jp.andosan.futabawatcher"
private const val WATCHER_PROVIDER = "content://jp.andosan.futabawatcher.provider/crawl"
private const val WATCHER_PLAY_URL = "https://play.google.com/store/apps/details?id=$WATCHER_PACKAGE"
private const val MAX_WATCHER_ROWS = 100

private class AndroidCompatExternalWatcher(
    private val context: Context
) : CompatExternalWatcher {
    private val providerUri = Uri.parse(WATCHER_PROVIDER)

    private fun isInstalled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(WATCHER_PACKAGE, PackageManager.GET_META_DATA)
    }.isSuccess

    override suspend fun load(): Result<CompatExternalWatcherSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isInstalled()) {
                return@runCatching CompatExternalWatcherSnapshot(
                    message = "にじろぐ(仮) 未インストール"
                )
            }
            val cursor = try {
                context.contentResolver.query(
                    providerUri,
                    null,
                    null,
                    null,
                    "datInsert desc"
                )
            } catch (_: SQLiteException) {
                return@runCatching CompatExternalWatcherSnapshot(
                    installed = true,
                    message = "にじろぐ(仮) 初期化されていない"
                )
            } ?: return@runCatching CompatExternalWatcherSnapshot(
                installed = true,
                message = "にじろぐ(仮) バージョンが古い？"
            )
            try {
                cursor.use { rows ->
                    val entries = buildList {
                        // The drawer renders at most 100 rows. Do not materialize an
                        // unbounded third-party provider table into memory when a
                        // stale watcher database has accumulated years of crawl
                        // results (ANR/OOM guard).
                        while (size < MAX_WATCHER_ROWS && rows.moveToNext()) {
                            add(readEntry(rows))
                        }
                    }
                    CompatExternalWatcherSnapshot(
                        installed = true,
                        available = true,
                        entries = entries
                    )
                }
            } catch (_: SQLiteException) {
                CompatExternalWatcherSnapshot(
                    installed = true,
                    message = "にじろぐ(仮) 初期化されていない"
                )
            }
        }
    }

    override suspend fun delete(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(isInstalled()) { "にじろぐ(仮) がインストールされていません" }
            context.contentResolver.delete(providerUri, " strKey = ?", arrayOf(key))
            Unit
        }
    }

    override suspend fun deleteAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(isInstalled()) { "にじろぐ(仮) がインストールされていません" }
            context.contentResolver.delete(providerUri, " strKey != ?", arrayOf(""))
            Unit
        }
    }

    override fun openManager(): Result<Unit> = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(WATCHER_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(WATCHER_PLAY_URL))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(WATCHER_PLAY_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    private fun readEntry(cursor: Cursor): CompatExternalWatcherEntry {
        fun string(name: String): String? = cursor.getColumnIndex(name)
            .takeIf { it >= 0 && !cursor.isNull(it) }
            ?.let(cursor::getString)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        fun int(name: String): Int = string(name)?.toIntOrNull() ?: 0
        fun epochMillis(name: String): Long = (string(name)?.toLongOrNull() ?: 0L) * 1_000L

        return CompatExternalWatcherEntry(
            key = string("strKey").orEmpty().ifBlank { string("strThreadUrl").orEmpty() },
            active = int("intThreadActive") == 1,
            // The watcher provider follows the legacy APK's intent-extra
            // names. Older watcher builds exposed the shorter aliases, so
            // keep them only as a compatibility fallback. Reading strTitle /
            // strRes first was the reason every external entry appeared as
            // No.<thread> with 0レス (#37).
            title = string("strThreadTitle") ?: string("strTitle").orEmpty(),
            replyCount = string("strThreadRes")?.toIntOrNull()
                ?: string("strRes")?.toIntOrNull()
                ?: 0,
            threadUrl = string("strThreadUrl").orEmpty(),
            categoryUrl = string("strThreadImageCat"),
            thumbnailUrl = string("strThreadImageThumb"),
            extractedKeyword = string("strKeywordExtract"),
            boardKey = string("strBoardKey"),
            boardName = string("strBoardName"),
            boardUrl = string("strBoardUrl"),
            updatedAtEpochMillis = epochMillis("datUpdate"),
            insertedAtEpochMillis = epochMillis("datInsert")
        )
    }
}

@Composable
internal actual fun rememberCompatExternalWatcher(
    store: com.valoser.futacha.shared.compat.CompatibilityStore
): CompatExternalWatcher {
    val context = LocalContext.current
    return remember(context) { AndroidCompatExternalWatcher(context.applicationContext) }
}
