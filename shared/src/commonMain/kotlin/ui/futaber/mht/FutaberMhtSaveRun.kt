package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.service.runProtectedThreadSave
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Saves one MHT file the way the other thread saves run: one user save at a time across all screens,
 * kept alive while the app is in the background, with its progress shown by the platform's
 * notification. A second save started meanwhile fails with "別の保存を実行中です".
 */
internal suspend fun FutaberMhtLibrary.saveProtected(
    request: FutaberMhtSaveRequest,
    fullImages: Boolean,
    nowMillis: Long,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
): Result<FutaberMhtSaveResult> {
    val progress = MutableStateFlow<SaveProgress?>(null)
    return try {
        runProtectedThreadSave(request.title.ifBlank { "MHT" }, progress) {
            save(request, fullImages, nowMillis) { done, total ->
                progress.value = SaveProgress(SavePhase.DOWNLOADING, done, total, "")
                onProgress(done, total)
            }
        }
    } catch (error: com.valoser.futacha.shared.service.ThreadSaveAlreadyRunningException) {
        Result.failure(error)
    }
}
