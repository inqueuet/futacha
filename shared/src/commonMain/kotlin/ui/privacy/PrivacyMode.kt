package com.valoser.futacha.shared.ui.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.compat.COMPAT_COMMON_PRIVACY_STORAGE_KEY
import com.valoser.futacha.shared.ui.compat.compatPrivacyEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val PRIVACY_FILTER_KEY = "compat.privacy.screenFilter"
internal const val PRIVACY_STRENGTH_KEY = "compat.privacy.screenStrength"
internal const val PRIVACY_TITLE_KEY = "compat.privacy.futachaTitle"

internal enum class PrivacyFilter(val label: String) { DIM("暗くする"), MESH("暗くする＋網目") }
internal enum class PrivateTitle(val label: String) {
    NORMAL("通常"), SMALL("小さくする"), FAINT("薄くする"), SMALL_FAINT("小さく・薄くする"), HIDDEN("タイトルを隠す")
}

internal data class PrivacyOptions(
    val filter: PrivacyFilter = PrivacyFilter.DIM,
    val strength: Int = 50,
    val title: PrivateTitle = PrivateTitle.NORMAL
)

internal fun privacyOptions(preferences: Map<String, String>): PrivacyOptions = PrivacyOptions(
    filter = PrivacyFilter.entries.firstOrNull { it.name == preferences[PRIVACY_FILTER_KEY] } ?: PrivacyFilter.DIM,
    strength = (preferences[PRIVACY_STRENGTH_KEY]?.toIntOrNull() ?: 50).coerceIn(20, 80),
    title = PrivateTitle.entries.firstOrNull { it.name == preferences[PRIVACY_TITLE_KEY] } ?: PrivateTitle.NORMAL
)

internal class PrivacyModeState(
    val enabled: Boolean,
    val options: PrivacyOptions,
    val compatibilityMode: Boolean,
    val setEnabled: (Boolean) -> Unit,
    val save: (String, String) -> Unit,
    val error: String?,
    val settingsWindows: MutableIntState
)

internal val LocalPrivacyMode = compositionLocalOf<PrivacyModeState?> { null }

/** Both platforms share drawing and settings; no display-angle protection is claimed. */
@Composable
internal fun PrivacyModeHost(
    store: CompatibilityStore,
    stateStore: AppStateStore?,
    compatibilityMode: Boolean,
    content: @Composable () -> Unit
) {
    val preferences = store.preferences.collectAsState(emptyMap())
    val modernEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = true)
        ?: remember { mutableStateOf(false) }
    val options by remember(preferences) { derivedStateOf { privacyOptions(preferences.value) } }
    val compatEnabled by remember(preferences) { derivedStateOf { preferences.value.compatPrivacyEnabled() } }
    val enabled = if (compatibilityMode) compatEnabled else modernEnabled
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val settingsWindows = remember { mutableIntStateOf(0) }
    fun persist(block: suspend () -> Unit) {
        scope.launch {
            try { block(); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "プライバシー設定を保存できませんでした。もう一度お試しください。" }
        }
    }
    val state = PrivacyModeState(enabled, options, compatibilityMode,
        setEnabled = { next -> persist {
            if (compatibilityMode) store.savePreference(COMPAT_COMMON_PRIVACY_STORAGE_KEY, if (next) "ON" else "OFF")
            else stateStore?.setPrivacyFilterEnabled(next)
        } },
        save = { key, value -> persist { store.savePreference(key, value) } }, error = error,
        settingsWindows = settingsWindows)
    CompositionLocalProvider(LocalPrivacyMode provides state) {
        val draw = enabled && settingsWindows.intValue == 0
        Box(Modifier.fillMaxSize().then(if (draw) Modifier.privacyFilter(options) else Modifier)) {
            content()
        }
    }
}

@Composable
internal fun PrivacySettingsExemption() {
    val state = LocalPrivacyMode.current ?: return
    if (!state.compatibilityMode) return
    DisposableEffect(state.settingsWindows) {
        state.settingsWindows.intValue++
        onDispose { state.settingsWindows.intValue-- }
    }
}

@Composable
internal fun Modifier.privacyWindowFilter(): Modifier {
    val state = LocalPrivacyMode.current
    return if (state?.enabled == true) privacyFilter(state.options) else this
}

internal fun Modifier.privacyFilter(options: PrivacyOptions): Modifier =
    testTag("privacy-screen-filter").semantics { stateDescription = "${options.filter.label} ${options.strength}%" }
        .drawWithCache {
            val mesh = Path()
            if (options.filter == PrivacyFilter.MESH) {
                val spacing = 5.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    mesh.moveTo(x, 0f); mesh.lineTo(x + size.height, size.height)
                    x += spacing
                }
            }
            val stroke = Stroke(width = 1.dp.toPx())
            onDrawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = options.strength.coerceIn(20, 80) / 100f))
                if (options.filter == PrivacyFilter.MESH) drawPath(mesh, Color.Black.copy(alpha = 0.45f), style = stroke)
            }
        }

internal fun privateTitleStyle(style: TextStyle, appearance: PrivateTitle): TextStyle =
    if (appearance == PrivateTitle.SMALL || appearance == PrivateTitle.SMALL_FAINT)
        style.copy(fontSize = style.fontSize * 0.7f) else style

internal fun privateTitleAlpha(appearance: PrivateTitle): Float =
    if (appearance == PrivateTitle.FAINT || appearance == PrivateTitle.SMALL_FAINT) 0.35f else 1f

@Composable
internal fun PrivacySettingsControls() {
    val state = LocalPrivacyMode.current ?: return
    var strength by remember(state.options.strength) { mutableFloatStateOf(state.options.strength.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(state.enabled, role = Role.Switch, onValueChange = state.setEnabled)
            .testTag("privacy-mode-toggle"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("プライバシーモード", modifier = Modifier.weight(1f))
            Switch(state.enabled, onCheckedChange = null)
        }
        Text("画像・タイトル・本文を含む閲覧画面を暗くします。設定画面は通常の明るさで操作できます。", style = MaterialTheme.typography.bodyMedium)
        Text("周囲から読みにくくする表示です。画面の視野角を変える機能ではありません。", style = MaterialTheme.typography.bodyMedium)
        PrivacyFilter.entries.forEach { filter ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(state.options.filter == filter, role = Role.RadioButton,
                onClick = { state.save(PRIVACY_FILTER_KEY, filter.name) }).testTag("privacy-filter-${filter.name}").padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.options.filter == filter, onClick = null)
                Text(filter.label, Modifier.padding(start = 8.dp))
            }
        }
        Text("フィルターの強さ ${strength.toInt()}%")
        Slider(strength, { strength = it }, valueRange = 20f..80f, steps = 5,
            onValueChangeFinished = { state.save(PRIVACY_STRENGTH_KEY, strength.toInt().toString()) },
            modifier = Modifier.testTag("privacy-filter-strength"))
        Text("強くすると自分からも読みにくくなります。カタログ・スレッドの「プライバシー」からも切り替えられます。", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("ふたちゃのスレッドタイトル", style = MaterialTheme.typography.titleMedium)
        Text("ふたちゃモードの上部タイトルに適用します。プライバシーモードがOFFでも有効です。", style = MaterialTheme.typography.bodyMedium)
        PrivateTitle.entries.forEach { title ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(state.options.title == title, role = Role.RadioButton,
                onClick = { state.save(PRIVACY_TITLE_KEY, title.name) }).testTag("privacy-title-${title.name}").padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.options.title == title, onClick = null)
                Text(title.label, Modifier.padding(start = 8.dp))
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
