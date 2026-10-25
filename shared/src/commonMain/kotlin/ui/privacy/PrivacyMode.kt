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
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue
import com.valoser.futacha.shared.ui.compat.parseCompatPercent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val PRIVACY_FILTER_KEY = "compat.privacy.screenFilter"
internal const val PRIVACY_STRENGTH_KEY = "compat.privacy.screenStrength"
internal const val PRIVACY_TITLE_KEY = "compat.privacy.futachaTitle"
internal const val PRIVACY_COMPAT_TITLE_KEY = "compat.privacy.toshiakiTitle"
internal const val PRIVACY_SCOPE_KEY = "compat.privacy.scope"
internal const val PRIVACY_IMAGE_STRENGTH_KEY = "compat.privacy.imageStrength"

internal enum class PrivacyFilter(val label: String) { DIM("暗くする"), MESH("暗くする＋網目") }
internal enum class PrivacyScope(val label: String) { IMAGES("画像のみ"), SCREEN("画面全体") }
internal enum class PrivateTitle(val label: String) {
    NORMAL("通常"), SMALL("小さくする"), FAINT("薄くする"), SMALL_FAINT("小さく・薄くする"), HIDDEN("タイトルを隠す")
}

internal data class PrivacyOptions(
    val filter: PrivacyFilter = PrivacyFilter.DIM,
    val strength: Int = 50,
    val title: PrivateTitle = PrivateTitle.NORMAL,
    val scope: PrivacyScope = PrivacyScope.IMAGES,
    val compatTitle: PrivateTitle = PrivateTitle.NORMAL,
    val imageStrength: Int? = null
)

internal fun privacyOptions(preferences: Map<String, String>): PrivacyOptions = PrivacyOptions(
    filter = PrivacyFilter.entries.firstOrNull { it.name == preferences[PRIVACY_FILTER_KEY] } ?: PrivacyFilter.DIM,
    strength = (preferences[PRIVACY_STRENGTH_KEY]?.toIntOrNull() ?: 50).coerceIn(20, 80),
    title = PrivateTitle.entries.firstOrNull { it.name == preferences[PRIVACY_TITLE_KEY] } ?: PrivateTitle.NORMAL,
    scope = PrivacyScope.entries.firstOrNull { it.name == preferences[PRIVACY_SCOPE_KEY] }
        ?: if (PRIVACY_FILTER_KEY in preferences || PRIVACY_STRENGTH_KEY in preferences) PrivacyScope.SCREEN else PrivacyScope.IMAGES,
    compatTitle = PrivateTitle.entries.firstOrNull { it.name == preferences[PRIVACY_COMPAT_TITLE_KEY] } ?: PrivateTitle.NORMAL,
    imageStrength = preferences[PRIVACY_IMAGE_STRENGTH_KEY]?.toIntOrNull()?.coerceIn(0, 100)
)

internal class PrivacyModeState(
    val enabled: Boolean,
    val options: PrivacyOptions,
    val compatibilityMode: Boolean,
    val setEnabled: (Boolean) -> Unit,
    val save: (String, String) -> Unit,
    val error: String?,
    val settingsWindows: MutableIntState,
    val inheritedImageStrength: Int = if (compatibilityMode) 80 else 50
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
    // Read through a derived state like the options: reading the preferences here recomposed this host on every
    // saved preference, which handed every LocalPrivacyMode reader (each image cell, the title bars) a new state (L66).
    val inheritedImageStrength by remember(preferences, compatibilityMode) {
        derivedStateOf(structuralEqualityPolicy()) {
            if (compatibilityMode) ((1f - parseCompatPercent(preferences.value.compatPreferenceValue("thread", "commonPrivacyAlpha"))) * 100f).toInt() else 50
        }
    }
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
    val setEnabled: (Boolean) -> Unit = remember(store, stateStore, compatibilityMode, scope) {
        { next -> persist {
            if (compatibilityMode) store.savePreference(COMPAT_COMMON_PRIVACY_STORAGE_KEY, if (next) "ON" else "OFF")
            else stateStore?.setPrivacyFilterEnabled(next)
        } }
    }
    val save: (String, String) -> Unit = remember(store, scope) { { key, value -> persist { store.savePreference(key, value) } } }
    // A new state only when something it holds changes, so a preference unrelated to privacy recomposes no reader (L66).
    val state = remember(enabled, options, compatibilityMode, error, inheritedImageStrength, setEnabled, save, settingsWindows) {
        PrivacyModeState(enabled, options, compatibilityMode,
            setEnabled = setEnabled,
            save = save, error = error,
            settingsWindows = settingsWindows,
            inheritedImageStrength = inheritedImageStrength)
    }
    CompositionLocalProvider(LocalPrivacyMode provides state) {
        val draw = enabled && options.scope == PrivacyScope.SCREEN && settingsWindows.intValue == 0
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
    return if (state?.enabled == true && state.options.scope == PrivacyScope.SCREEN) privacyFilter(state.options) else this
}

/** Legacy media keeps its existing opacity until an image strength is explicitly selected. */
@Composable
internal fun privacyImageContentAlpha(legacyAlpha: Float): Float {
    val state = LocalPrivacyMode.current ?: return legacyAlpha
    if (!state.enabled || state.options.scope == PrivacyScope.SCREEN) return 1f
    return state.options.imageStrength?.let { 1f - it / 100f }
        ?: if (!state.compatibilityMode && legacyAlpha >= .999f) .5f else legacyAlpha
}

/** Apply only to the media surface, never its title, caption or controls. */
@Composable
internal fun Modifier.privacyMediaFilter(): Modifier {
    val state = LocalPrivacyMode.current ?: return this
    if (!state.enabled || state.options.scope != PrivacyScope.IMAGES) return this
    val strength = state.options.imageStrength ?: 50
    return drawWithCache { onDrawWithContent {
        drawContent()
        drawRect(Color.Black.copy(alpha = strength / 100f))
    } }
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
    var imageStrength by remember(state.options.imageStrength, state.inheritedImageStrength) { mutableFloatStateOf((state.options.imageStrength ?: state.inheritedImageStrength).toFloat()) }
    val title = if (state.compatibilityMode) state.options.compatTitle else state.options.title
    val titleKey = if (state.compatibilityMode) PRIVACY_COMPAT_TITLE_KEY else PRIVACY_TITLE_KEY
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(state.enabled, role = Role.Switch, onValueChange = state.setEnabled)
            .testTag("privacy-mode-toggle"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("プライバシーモード", modifier = Modifier.weight(1f))
            Switch(state.enabled, onCheckedChange = null)
        }
        Text("画像のみ、または文字を含む画面全体を暗くします。設定画面は通常の明るさで操作できます。", style = MaterialTheme.typography.bodyMedium)
        Text("周囲から読みにくくする表示です。画面の視野角を変える機能ではありません。", style = MaterialTheme.typography.bodyMedium)
        PrivacyScope.entries.forEach { scope ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(state.options.scope == scope, role = Role.RadioButton,
                onClick = { state.save(PRIVACY_SCOPE_KEY, scope.name) }).testTag("privacy-scope-${scope.name}"), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.options.scope == scope, onClick = null)
                Text(scope.label, Modifier.padding(start = 8.dp))
            }
        }
        if (state.options.scope == PrivacyScope.IMAGES) {
            Text("画像の暗さ ${imageStrength.toInt()}%")
            Slider(imageStrength, { imageStrength = it }, valueRange = 0f..100f, steps = 9,
                onValueChangeFinished = { state.save(PRIVACY_IMAGE_STRENGTH_KEY, imageStrength.toInt().toString()) },
                modifier = Modifier.testTag("privacy-image-strength"))
            Text("文字と操作ボタンの明るさは変えません。未調整の場合は既存の画像透明度を使います。")
        } else {
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
        }
        HorizontalDivider()
        Text(if (state.compatibilityMode) "としあき(仮)のスレッドタイトル" else "ふたちゃのスレッドタイトル", style = MaterialTheme.typography.titleMedium)
        Text("このモードの上部タイトルに適用します。プライバシーモードがOFFでも有効です。", style = MaterialTheme.typography.bodyMedium)
        PrivateTitle.entries.forEach { choice ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(title == choice, role = Role.RadioButton,
                onClick = { state.save(titleKey, choice.name) }).testTag("privacy-title-${choice.name}").padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(title == choice, onClick = null)
                Text(choice.label, Modifier.padding(start = 8.dp))
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
