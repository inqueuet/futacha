package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.FileManagerPickerDialog
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.ui.compat.rememberCompatCustomFontFamily
import com.valoser.futacha.shared.ui.compat.rememberCompatFontPickerLauncher
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * ふたばー風 機能そろえ B6（保存・設定）。
 * - S02 メディアのみ保存：拡張機能のスイッチ（既定OFF）がONの時だけ、共有の保存ダイアログ（FutachaPageSaveDialog）に
 *   「メディアのみ（ZIP）／（フォルダ）」を出す。保存の処理はふたちゃと同じ（FutachaMediaOnlySave.kt）。
 * - X02 カスタムフォント：拡張機能のスイッチ（既定OFF）がONの時だけ、ふたちゃ・としあき(仮)と共通のフォント
 *   （compat.design.dummyCustomFont、実体は private/compat_font/font.ttf|otf）をこのモードの文字に当てる。
 *   選ぶ・戻すのも共通の値と同じ場所に書くので、3モードで同じフォントになる。
 * - 優先ファイルマネージャー（Android）：ふたちゃと同じ AppStateStore の値を、このモードの設定から選べるようにする。
 */

private const val FUTABER_B6_TAG = "FutaberParityB6"

/** The shared preference that names the custom font; "デフォルト" or nothing means none. */
internal val FUTABER_CUSTOM_FONT_PREFERENCE_KEY = compatPreferenceStorageKey("design", "dummyCustomFont")
private const val COMPAT_FONT_DIRECTORY = "private/compat_font"
private val COMPAT_FONT_CANDIDATES = listOf("$COMPAT_FONT_DIRECTORY/font.ttf", "$COMPAT_FONT_DIRECTORY/font.otf")
internal const val FUTABER_CUSTOM_FONT_RESTART_NOTICE = "フォントを変更しました。反映されない場合はアプリを再起動してください"

/** The name shown for the shared custom font, or null when none is chosen. */
internal fun futaberCustomFontName(preferences: Map<String, String>): String? =
    preferences[FUTABER_CUSTOM_FONT_PREFERENCE_KEY]?.takeIf { it.isNotBlank() && it != "デフォルト" }

/**
 * The shared custom font as a [FontFamily] while [enabled] (the extension switch) and a font is chosen; null otherwise,
 * so the theme keeps its own typography exactly as before.
 */
@Composable
internal fun rememberFutaberCustomFontFamily(
    enabled: Boolean,
    fileSystem: FileSystem?,
    preferences: Map<String, String>
): FontFamily? {
    val selected = if (enabled) futaberCustomFontName(preferences) else null
    var path by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fileSystem, selected) {
        path = if (fileSystem == null || selected == null) null else withContext(AppDispatchers.io) {
            COMPAT_FONT_CANDIDATES.firstOrNull { candidate ->
                try { fileSystem.exists(candidate) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            }?.let(fileSystem::resolveAbsolutePath)
        }
    }
    val font = rememberCompatCustomFontFamily(if (selected == null) null else path)
    return if (selected == null) null else font
}

/** Copies a picked font to the shared place and names it in the shared preference (as the shared settings page does). */
internal suspend fun futaberSaveCustomFont(store: CompatibilityStore, fileSystem: FileSystem, selected: ImageData): Result<Unit> {
    val extension = selected.fileName.substringAfterLast('.', "").lowercase()
    if (extension !in setOf("ttf", "otf")) return Result.failure(IllegalArgumentException("フォントファイルではありません"))
    return try {
        // Only the chosen extension is kept, or a font replaced from OTF to TTF would leave two candidates.
        fileSystem.deleteRecursively(COMPAT_FONT_DIRECTORY).getOrThrow()
        fileSystem.createDirectory(COMPAT_FONT_DIRECTORY).getOrThrow()
        fileSystem.writeBytes("$COMPAT_FONT_DIRECTORY/font.$extension", selected.bytes).getOrThrow()
        store.savePreference(FUTABER_CUSTOM_FONT_PREFERENCE_KEY, selected.fileName)
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}

/** Removes the shared custom font (every mode goes back to its own letters). */
internal suspend fun futaberResetCustomFont(store: CompatibilityStore, fileSystem: FileSystem?): Result<Unit> = try {
    fileSystem?.deleteRecursively(COMPAT_FONT_DIRECTORY)?.getOrThrow()
    store.savePreference(FUTABER_CUSTOM_FONT_PREFERENCE_KEY, "デフォルト")
    Result.success(Unit)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}

/** "フォントを選ぶ": choose a font file, or go back to the default; the font is the one every mode shares. */
@Composable
internal fun FutaberCustomFontDialog(
    services: FutaberMediaServices,
    currentName: String?,
    onNotice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val scope = rememberCoroutineScope()
    val pick = rememberCompatFontPickerLauncher(
        onSelected = { selected ->
            val fileSystem = services.fileSystem
            if (fileSystem == null) {
                onNotice("フォントの保存先を利用できません")
            } else scope.launch {
                futaberSaveCustomFont(services.store, fileSystem, selected)
                    .onSuccess { onNotice(FUTABER_CUSTOM_FONT_RESTART_NOTICE) }
                    .onFailure { error ->
                        Logger.e(FUTABER_B6_TAG, "Failed to copy the font", error)
                        onNotice(if (error is IllegalArgumentException) error.message.orEmpty() else "フォントのコピーに失敗しました")
                    }
            }
        },
        onError = onNotice
    )
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("futaber-custom-font-dialog"),
        containerColor = colors.bar,
        titleContentColor = colors.body,
        textContentColor = colors.body,
        title = { Text("カスタムフォント") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("現在：${currentName ?: "未選択"}")
                Text("ふたちゃ・としあき(仮)と共通のフォントです（TTF・OTF）。ここで選ぶと、他のモードのフォントも変わります。")
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); pick() }, modifier = Modifier.testTag("futaber-custom-font-pick")) {
                Text("選択", color = colors.link)
            }
        },
        dismissButton = {
            Column {
                if (currentName != null) {
                    TextButton(onClick = {
                        onDismiss()
                        scope.launch {
                            futaberResetCustomFont(services.store, services.fileSystem)
                                .onSuccess { onNotice(FUTABER_CUSTOM_FONT_RESTART_NOTICE) }
                                .onFailure { error ->
                                    Logger.e(FUTABER_B6_TAG, "Failed to reset the font", error)
                                    onNotice("既存フォントの削除に失敗しました")
                                }
                        }
                    }, modifier = Modifier.testTag("futaber-custom-font-reset")) { Text("リセット", color = colors.link) }
                }
                TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.link) }
            }
        }
    ) }
}

/** The chosen shared custom font's name, or null. */
@Composable
internal fun rememberFutaberCustomFontName(store: CompatibilityStore): String? {
    val preferences by store.preferences.collectAsState(emptyMap())
    return futaberCustomFontName(preferences)
}

/** The label of the preferred file manager row: the chosen app, or "未設定". */
@Composable
internal fun rememberFutaberPreferredFileManagerLabel(stateStore: AppStateStore): String {
    val current by stateStore.getPreferredFileManager().collectAsState(null)
    return current?.label?.takeIf { it.isNotBlank() } ?: "未設定"
}

/**
 * Android's "優先ファイルマネージャー": the app the attachment and folder pickers open first. The value is the one
 * ふたちゃ's settings write ([AppStateStore.setPreferredFileManager]). With an app chosen the dialog offers to change or
 * clear it; without, the list of file managers opens at once.
 */
@Composable
internal fun FutaberPreferredFileManagerDialog(
    stateStore: AppStateStore,
    onNotice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val scope = rememberCoroutineScope()
    val current by stateStore.getPreferredFileManager().collectAsState(null)
    var picking by remember { mutableStateOf(false) }
    fun write(packageName: String?, label: String?) {
        scope.launch {
            try {
                stateStore.setPreferredFileManager(packageName, label)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Logger.e(FUTABER_B6_TAG, "Failed to save the preferred file manager", failure)
                onNotice(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    val chosen = current?.takeIf { it.packageName.isNotBlank() }
    if (picking || chosen == null) {
        FutachaAppLockAwareWindow {
            FileManagerPickerDialog(
                onDismiss = onDismiss,
                onFileManagerSelected = { packageName, label -> write(packageName, label) }
            )
        }
        return
    }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("futaber-preferred-file-manager-dialog"),
        containerColor = colors.bar,
        titleContentColor = colors.body,
        textContentColor = colors.body,
        title = { Text("優先ファイルマネージャー") },
        text = { Text("現在：${chosen.label}\n添付やフォルダを選ぶ時に、最初にこのアプリを開きます。") },
        confirmButton = {
            TextButton(onClick = { picking = true }, modifier = Modifier.testTag("futaber-preferred-file-manager-change")) {
                Text("変更", color = colors.link)
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { write(null, null); onDismiss() }, modifier = Modifier.testTag("futaber-preferred-file-manager-clear")) {
                    Text("解除", color = colors.link)
                }
                TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.link) }
            }
        }
    ) }
}
