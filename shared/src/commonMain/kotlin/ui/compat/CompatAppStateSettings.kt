package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.state.APP_LOCK_PASSWORD_MAX_LENGTH
import com.valoser.futacha.shared.state.APP_LOCK_PASSWORD_MIN_LENGTH
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.isValidAppLockPassword
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.AppIconVariantPreview
import com.valoser.futacha.shared.ui.board.resolveAppIconSectionDescription
import com.valoser.futacha.shared.ui.board.resolveAppIconVariantDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/*
 * 起動ロック and アプリアイコン in としあき(仮)'s settings (feature-parity batch 3), under "ふたちゃ拡張". Both are the
 * AppState settings the other two modes already offer, so a change made here applies to all of them; the lock is the
 * lock of the whole app and the icon is the one launcher icon. Rules and wording follow ふたちゃ's settings and the
 * ふたばー風 dialog; only the colours are this mode's own (the dialogs read this mode's MaterialTheme). Both rows exist
 * only where an AppStateStore is available, and the schema of the compatibility store is not touched.
 */

internal enum class CompatAppStateSetting(val preferenceKey: String, val title: String) {
    AppLock("appLock", "起動ロック"),
    AppIcon("appIcon", "アプリアイコン");

    companion object {
        fun fromPreferenceKey(key: String): CompatAppStateSetting? = entries.firstOrNull { it.preferenceKey == key }
    }
}

/** The root schema with the two AppState rows after "プライバシー表示" in "ふたちゃ拡張" when [includeAppStateEntries]. */
internal fun compatRootSettingsGroups(
    appVersion: String,
    includeAppStateEntries: Boolean
): List<Pair<String, List<CompatSettingEntry>>> {
    val groups = compatRootSettingsGroups(appVersion)
    if (!includeAppStateEntries) return groups
    return groups.map { (group, entries) ->
        if (group != "ふたちゃ拡張" || entries.any { it.preferenceKey == CompatAppStateSetting.AppLock.preferenceKey }) {
            group to entries
        } else {
            val at = entries.indexOfFirst { it.route == "privacy" }.let { if (it < 0) entries.size else it + 1 }
            group to (entries.take(at) + compatAppStateEntries() + entries.drop(at))
        }
    }
}

internal fun compatAppStateEntries(): List<CompatSettingEntry> = listOf(
    CompatSettingEntry(
        CompatAppStateSetting.AppLock.title, "パスワードで起動時にロック",
        preferenceKey = CompatAppStateSetting.AppLock.preferenceKey
    ),
    CompatSettingEntry(
        CompatAppStateSetting.AppIcon.title, "デフォルト・クラシック",
        preferenceKey = CompatAppStateSetting.AppIcon.preferenceKey
    )
)

/** The icons offered. "ミッドナイト" is not: the store turns it back into the default when saved (as in the other modes). */
internal val COMPAT_OFFERED_APP_ICONS = listOf(AppIconVariant.Current, AppIconVariant.Classic)

internal fun compatAppLockSummary(enabled: Boolean): String =
    if (enabled) "有効・起動時にパスワードを要求" else "無効・パスワードを設定すると起動時にロック"

/** Why a lock password typed here is refused, or null when it is fine to set. Same rule and wording as ふたちゃ and ふたばー風. */
internal fun compatAppLockPasswordError(password: String, confirmation: String): String? = when {
    !isValidAppLockPassword(password) -> "${APP_LOCK_PASSWORD_MIN_LENGTH}文字以上で入力してください。"
    password != confirmation -> "確認用パスワードが一致しません。"
    else -> null
}

/** [limit] chars of the text; a cut between the two halves of a surrogate pair stops one character earlier. */
internal fun String.compatTakeChars(limit: Int): String {
    if (limit <= 0) return ""
    if (length <= limit) return this
    return if (this[limit - 1].isHighSurrogate() && this[limit].isLowSurrogate()) substring(0, limit - 1) else substring(0, limit)
}

internal class CompatAppStateSettingsState(val lockEnabled: Boolean, val icon: AppIconVariant) {
    fun summary(setting: CompatAppStateSetting): String = when (setting) {
        CompatAppStateSetting.AppLock -> compatAppLockSummary(lockEnabled)
        CompatAppStateSetting.AppIcon -> icon.label
    }
}

@Composable
internal fun rememberCompatAppStateSettings(stateStore: AppStateStore?): CompatAppStateSettingsState {
    val hash by (stateStore?.appLockPasswordHash ?: flowOf<String?>(null)).collectAsState(null)
    val icon by (stateStore?.appIconVariant ?: flowOf(AppIconVariant.Current)).collectAsState(AppIconVariant.Current)
    return remember(hash, icon) { CompatAppStateSettingsState(!hash.isNullOrBlank(), icon) }
}

@Composable
internal fun CompatAppStateSettingDialogs(
    stateStore: AppStateStore?,
    open: CompatAppStateSetting?,
    state: CompatAppStateSettingsState,
    onDismiss: () -> Unit
) {
    if (stateStore == null || open == null) return
    when (open) {
        CompatAppStateSetting.AppLock -> CompatAppLockDialog(stateStore, state.lockEnabled, onDismiss)
        CompatAppStateSetting.AppIcon -> CompatAppIconDialog(stateStore, state.icon, onDismiss)
    }
}

/** Sets, changes or removes the start-up lock password. The password stays in memory only (not saved with the screen state). */
@Composable
private fun CompatAppLockDialog(stateStore: AppStateStore, enabled: Boolean, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save(block: suspend () -> Unit) {
        scope.launch {
            try { block(); onDismiss() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "設定を保存できませんでした。" }
        }
    }
    FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(CompatAppStateSetting.AppLock.title) },
            text = {
                Column {
                    Text(
                        com.valoser.futacha.shared.ui.appLockSettingDescription(enabled),
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.compatTakeChars(APP_LOCK_PASSWORD_MAX_LENGTH); error = null },
                        label = { Text(if (enabled) "新しいパスワード" else "パスワード") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("compat-lock-password")
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.compatTakeChars(APP_LOCK_PASSWORD_MAX_LENGTH); error = null },
                        label = { Text("確認") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("compat-lock-confirmation")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val problem = compatAppLockPasswordError(password, confirmation)
                        if (problem != null) error = problem else save { stateStore.setAppLockPassword(password) }
                    },
                    modifier = Modifier.testTag("compat-lock-save")
                ) { Text(if (enabled) "変更" else "有効にする") }
            },
            dismissButton = {
                Column {
                    if (enabled) TextButton(
                        onClick = { save { stateStore.clearAppLockPassword() } },
                        modifier = Modifier.testTag("compat-lock-clear")
                    ) { Text("解除") }
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                }
            }
    ) }
}

/**
 * The icon choice. Selecting one saves it at once through [AppStateStore.setAppIconVariant], the one path every mode
 * uses; the launcher alias is applied from that setting by the host (held until the app is backgrounded on Android 37,
 * which closes the visible task on an alias change), and the mode switch keeps the choice.
 */
@Composable
private fun CompatAppIconDialog(stateStore: AppStateStore, selected: AppIconVariant, onDismiss: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(CompatAppStateSetting.AppIcon.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(resolveAppIconSectionDescription(), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    COMPAT_OFFERED_APP_ICONS.forEach { variant ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(role = Role.RadioButton) {
                                    scope.launch {
                                        try { stateStore.setAppIconVariant(variant); error = null }
                                        catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { error = "設定を保存できませんでした。" }
                                    }
                                }
                                .testTag("compat-app-icon-${variant.name}"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconVariantPreview(variant, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(variant.label, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    resolveAppIconVariantDescription(variant),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                                )
                            }
                            RadioButton(selected = selected == variant, onClick = null)
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    ) }
}
