package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.reconcileCompatToolbar
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException

internal data class CompatToolbarLoadResult(
    val items: Map<CompatToolbarSurface, List<CompatToolbarItem>> = emptyMap(),
    val failedSurfaces: Set<CompatToolbarSurface> = emptySet()
)

/** A failed read must not make another surface, or the host itself, unusable. Never persists a fallback. */
internal suspend fun loadCompatToolbarsWithRecovery(
    previous: Map<CompatToolbarSurface, List<CompatToolbarItem>>,
    load: suspend (CompatToolbarSurface) -> List<CompatToolbarItem>
): CompatToolbarLoadResult {
    val items = LinkedHashMap<CompatToolbarSurface, List<CompatToolbarItem>>()
    val failures = LinkedHashSet<CompatToolbarSurface>()
    for (surface in CompatToolbarSurface.entries) {
        items[surface] = try {
            load(surface)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Logger.e("CompatibilityToolbar", "Failed to load ${surface.name} toolbar", failure)
            failures += surface
            previous[surface] ?: reconcileCompatToolbar(surface, emptyList())
        }
    }
    return CompatToolbarLoadResult(items, failures)
}

/** Reserve space for recovery actions so they cannot cover the usable toolbar or its content. */
@Composable
internal fun CompatToolbarRecoveryFrame(
    showError: Boolean,
    retrying: Boolean,
    onRetry: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        if (showError) Surface(
            modifier = Modifier.fillMaxWidth().testTag("compat-toolbar-recovery"),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
            Row(Modifier.navigationBarsPadding().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("ツールバーを読み込めませんでした", Modifier.weight(1f))
                TextButton(onClick = onRetry, enabled = !retrying, modifier = Modifier.testTag("compat-toolbar-retry")) {
                    Text(if (retrying) "再試行中" else "再試行")
                }
            }
        }
    }
}
