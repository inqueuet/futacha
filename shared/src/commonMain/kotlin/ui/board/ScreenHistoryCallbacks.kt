package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.FutachaHistoryArchivePreview

data class ScreenHistoryCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = {},
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = {},
    val onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = {},
    val onHistoryRefresh: suspend () -> Unit = {},
    val onHistoryExport: suspend () -> String = { "" },
    val onHistoryExportThenClear: suspend () -> String = { "" },
    val onHistoryExportSelected: suspend (List<ThreadHistoryEntry>) -> String = { "" },
    val onHistoryLoadImportPreview: suspend () -> FutachaHistoryArchivePreview? = { null },
    val onHistoryImport: suspend () -> String = { "" },
    val onHistoryImportSelected: suspend (Set<String>) -> String = { "" },
    val onHistoryCleared: () -> Unit = {}
)
