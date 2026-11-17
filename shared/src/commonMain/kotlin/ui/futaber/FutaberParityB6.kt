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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.FileManagerPickerDialog
import com.valoser.futacha.shared.ui.compat.COMPAT_CUSTOM_FONT_CANDIDATES
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.ui.compat.rememberCompatCustomFontFamily
import com.valoser.futacha.shared.ui.compat.rememberCompatFontPickerLauncher
import com.valoser.futacha.shared.ui.compat.removeCompatCustomFontFiles
import com.valoser.futacha.shared.ui.compat.replaceCompatCustomFontFile
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
            COMPAT_CUSTOM_FONT_CANDIDATES.firstOrNull { candidate ->
                try { fileSystem.exists(candidate) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            }?.let(fileSystem::resolveAbsolutePath)
        }
    }
    // The loader also follows a replacement of the file at the same path (a font replaced by one of the same extension).
    val font = rememberCompatCustomFontFamily(if (selected == null) null else path)
    return if (selected == null) null else font
}

/** Copies a picked font to the shared place and names it in the shared preference (as the shared settings page does). */
internal suspend fun futaberSaveCustomFont(store: CompatibilityStore, fileSystem: FileSystem, selected: ImageData): Result<Unit> {
    val extension = selected.fileName.substringAfterLast('.', "").lowercase()
    if (extension !in setOf("ttf", "otf")) return Result.failure(IllegalArgumentException("フォントファイルではありません"))
    return try {
        // The new file is written before anything is removed, so a failed copy keeps the font in use; only the chosen
        // extension is kept, or a font replaced from OTF to TTF would leave two candidates.
        replaceCompatCustomFontFile(fileSystem, extension, selected.bytes)
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
    fileSystem?.let { removeCompatCustomFontFiles(it) }
    store.savePreference(FUTABER_CUSTOM_FONT_PREFERENCE_KEY, "デフォルト")
    Result.success(Unit)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}

/** "選択" and "リセット" of the custom font. */
internal class FutaberCustomFontActions(val pick: () -> Unit, val reset: () -> Unit)

/**
 * The custom font's actions, held by the screen that opens the dialog and not by the dialog: the dialog closes as soon
 * as one is picked. A picker registered by the dialog lost the chosen file with it (Android drops the result of a
 * launcher that is gone; iOS waited for the file in the dialog's own scope), and a save or reset run in the dialog's
 * scope was cancelled half way. [scope] must outlive the dialog.
 */
@Composable
internal fun rememberFutaberCustomFontActions(
    services: FutaberMediaServices?,
    scope: CoroutineScope,
    onNotice: (String) -> Unit
): FutaberCustomFontActions {
    val currentServices by rememberUpdatedState(services)
    val notice by rememberUpdatedState(onNotice)
    val pick = rememberCompatFontPickerLauncher(
        onSelected = { selected ->
            val target = currentServices
            val fileSystem = target?.fileSystem
            if (target == null || fileSystem == null) {
                notice("フォントの保存先を利用できません")
            } else scope.launch {
                futaberSaveCustomFont(target.store, fileSystem, selected)
                    .onSuccess { notice(FUTABER_CUSTOM_FONT_RESTART_NOTICE) }
                    .onFailure { error ->
                        Logger.e(FUTABER_B6_TAG, "Failed to copy the font", error)
                        notice(if (error is IllegalArgumentException) error.message.orEmpty() else "フォントのコピーに失敗しました")
                    }
            }
        },
        onError = { notice(it) }
    )
    return FutaberCustomFontActions(
        pick = pick,
        reset = {
            currentServices?.let { target ->
                scope.launch {
                    futaberResetCustomFont(target.store, target.fileSystem)
                        .onSuccess { notice(FUTABER_CUSTOM_FONT_RESTART_NOTICE) }
                        .onFailure { error ->
                            Logger.e(FUTABER_B6_TAG, "Failed to reset the font", error)
                            notice("既存フォントの削除に失敗しました")
                        }
                }
            }
        }
    )
}

/**
 * "フォントを選ぶ": choose a font file, or go back to the default; the font is the one every mode shares. The work is
 * done by [actions] (see [rememberFutaberCustomFontActions]), which outlive this dialog.
 */
@Composable
internal fun FutaberCustomFontDialog(
    currentName: String?,
    actions: FutaberCustomFontActions,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
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
            TextButton(onClick = { onDismiss(); actions.pick() }, modifier = Modifier.testTag("futaber-custom-font-pick")) {
                Text("選択", color = colors.link)
            }
        },
        dismissButton = {
            Column {
                if (currentName != null) {
                    TextButton(onClick = {
                        onDismiss()
                        actions.reset()
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
 *
 * Choosing or clearing closes the dialog at once, so the value is written in [scope], which must outlive the dialog
 * (a scope of the dialog was cancelled with it, before the value was stored).
 */
@Composable
internal fun FutaberPreferredFileManagerDialog(
    stateStore: AppStateStore,
    scope: CoroutineScope,
    onNotice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
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
