package com.valoser.futacha.shared.media.analysis

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.media.MediaFeaturePermit

internal data class ModelImportRequest(val model: AnalysisModel, val store: ModelStore, val gate: MediaFeatureGate, val permit: MediaFeaturePermit)
internal class ModelImportPicker(val launch: (ModelImportRequest) -> Unit, val cancel: () -> Unit)

/** The platform owns a single selected stream until ModelStore finishes verification or cancellation. */
@Composable
internal expect fun rememberModelImportPicker(
    onBusy: (Boolean) -> Unit,
    onImported: (AnalysisModel) -> Unit,
    onError: (String) -> Unit
): ModelImportPicker
