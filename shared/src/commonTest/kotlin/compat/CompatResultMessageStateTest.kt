package compat

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.SavedMediaFile
import com.valoser.futacha.shared.service.SavedMediaType
import com.valoser.futacha.shared.ui.compat.CompatGalleryBatchSaveFormat
import com.valoser.futacha.shared.ui.compat.CompatResultMessageState
import com.valoser.futacha.shared.ui.compat.CompatSaveResultAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatResultMessageStateTest {
    private val saved = SavedMediaFile("a.jpg", "a.jpg", SavedMediaType.IMAGE, 1L, 0L)

    @Test
    fun aLaterMessageDropsTheSaveResultsShareAction() {
        val state = CompatResultMessageState()
        var message by state
        message = "保存しました"
        state.attach(CompatSaveResultAction.Share(saved, SaveLocation.Path("dir")))
        assertEquals(CompatSaveResultAction.Share(saved, SaveLocation.Path("dir")), state.action)

        message = "URLをコピーしました"
        assertEquals("URLをコピーしました", message)
        assertNull(state.action)
    }

    @Test
    fun dismissingTheMessageDropsTheRetryActionForTheNextMessage() {
        val state = CompatResultMessageState()
        var message by state
        message = "2件失敗しました"
        state.attach(CompatSaveResultAction.RetryFailed(CompatGalleryBatchSaveFormat.ZIP, setOf("a")))
        message = null
        assertNull(state.action)

        message = "画像検索結果のURLが不正です"
        assertNull(state.action)
    }

    @Test
    fun anActionIsNeverAttachedWithoutItsMessage() {
        val state = CompatResultMessageState()
        state.attach(CompatSaveResultAction.Share(saved, null))
        assertNull(state.action)
        state.show(null, CompatSaveResultAction.Share(saved, null))
        assertNull(state.action)
        state.show("保存しました", CompatSaveResultAction.Share(saved, null))
        assertEquals(CompatSaveResultAction.Share(saved, null), state.action)
    }
}
