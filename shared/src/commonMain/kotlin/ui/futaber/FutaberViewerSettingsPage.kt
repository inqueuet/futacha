package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatibilityStore
import kotlinx.coroutines.launch

/**
 * L-B11 (M34・C04): futaber's 「画像ビューア」 page, the one the settings' row and the gear of the picture list and of the
 * viewer open: the storage-provider button (four choices and what it says), the upright orientation and the video's autoplay.
 * The columns and the preloading, which futaber does not have, follow in a row of this app's own that opens the shared page.
 */
@Composable
internal fun FutaberViewerSettingsPage(
    settings: FutaberDisplaySettings,
    onSettingChange: (key: String, value: String) -> Unit,
    onDone: () -> Unit,
    /** Opens the shared page (columns, preloading); null leaves the row out. */
    onOpenShared: (() -> Unit)? = null
) {
    FutaberTableScreen(
        title = "画像ビューア", onDone = onDone, doneTag = "futaber-viewer-settings-done",
        modifier = Modifier.testTag("futaber-viewer-settings-page")
    ) {
        FutaberTableSection("ストレージプロバイダを表示するボタン", tag = "futaber-viewer-storage-title") {
            FutaberStorageButton.entries.forEachIndexed { index, choice ->
                FutaberTableCheckRow(
                    choice.label, "futaber-viewer-storage-${choice.persisted}", selected = settings.viewerStorageButton == choice,
                    last = index == FutaberStorageButton.entries.lastIndex
                ) { onSettingChange(FutaberSettingKeys.VIEWER_STORAGE_BUTTON, choice.persisted) }
            }
        }
        FutaberTableNote(FUTABER_STORAGE_PROVIDER_NOTE, tag = "futaber-viewer-storage-note")
        FutaberTableSection(null) {
            FutaberTableSwitchRow(
                FUTABER_VIEWER_PORTRAIT_LOCK_LABEL, "futaber-viewer-portrait-lock", settings.viewerPortraitLock, last = true
            ) { onSettingChange(FutaberSettingKeys.VIEWER_PORTRAIT_LOCK, if (it) "ON" else "OFF") }
        }
        FutaberTableSection(null) {
            FutaberTableSwitchRow(
                FUTABER_VIEWER_AUTOPLAY_LABEL, "futaber-viewer-autoplay", settings.viewerAutoplay, last = true
            ) { onSettingChange(FutaberSettingKeys.VIEWER_AUTOPLAY, if (it) "ON" else "OFF") }
        }
        if (onOpenShared != null) {
            FutaberTableSection("このアプリの設定", gap = 48.dp) {
                FutaberTableNavRow("一覧の列数・先読み", "futaber-viewer-shared-row", last = true, onClick = onOpenShared)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * The page with the stored values and a writer, for the two places that open it inside a window of their own (the settings'
 * shared-page host and the picture list / viewer): it reads the preferences from the store and writes through it.
 */
@Composable
internal fun FutaberViewerSettingsStorePage(
    store: CompatibilityStore,
    onDone: () -> Unit,
    onOpenShared: (() -> Unit)? = null
) {
    val preferences by store.preferences.collectAsState(emptyMap<String, String>())
    val scope = rememberCoroutineScope()
    FutaberViewerSettingsPage(
        settings = FutaberDisplaySettings.from(preferences),
        onSettingChange = { key, value -> scope.launch { store.savePreference(key, value) } },
        onDone = onDone, onOpenShared = onOpenShared
    )
}

/** The path of the shared page behind the row: the columns and the preloading of the picture list and the viewer. */
internal const val FUTABER_VIEWER_SHARED_PATH = "viewer-shared"

/** The shared settings page a path of the media windows names ("viewer-shared" is the shared "viewer" page). */
internal fun futaberSharedSettingsPath(path: String): String = if (path == FUTABER_VIEWER_SHARED_PATH) "viewer" else path
