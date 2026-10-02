package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.service.SavedMediaFile
import com.valoser.futacha.shared.model.SaveLocation
import kotlin.reflect.KProperty

/** An action offered next to the save result it was produced with (E-7). */
internal sealed interface CompatSaveResultAction {
    data class Share(val file: SavedMediaFile, val location: SaveLocation?) : CompatSaveResultAction
    data class RetryFailed(
        val format: CompatGalleryBatchSaveFormat,
        val mediaKeys: Set<String>
    ) : CompatSaveResultAction
}

/**
 * The gallery/viewer message dialog text plus the action that belongs to it.
 * Used as a delegate (`var message by state`): assigning any new text or
 * clearing it drops the action, so a later message ("URLをコピーしました",
 * a search error...) can never show an old save result's 「共有」 or
 * 「失敗分を再試行」 (E-7). Attach an action only after setting its text.
 */
@Stable
internal class CompatResultMessageState {
    var text: String? by mutableStateOf(null)
        private set
    var action: CompatSaveResultAction? by mutableStateOf(null)
        private set

    fun show(text: String?, action: CompatSaveResultAction? = null) {
        this.text = text
        this.action = action?.takeIf { text != null }
    }

    fun attach(action: CompatSaveResultAction?) {
        if (text != null) this.action = action
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String? = text

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) = show(value)
}
