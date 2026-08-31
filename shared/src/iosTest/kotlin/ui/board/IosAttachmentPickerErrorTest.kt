package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosAttachmentPickerErrorTest {
    @Test
    fun everyAsynchronousPickerFailureUsesTheReferenceMessage() = runBlocking {
        var message: String? = null

        runIosAttachmentPickerCatching(onSelectionError = { message = it }) {
            error("document provider failed")
        }

        assertEquals("添付ファイルが読み込めません", message)
    }

    @Test
    fun pickerCancellationRemainsCancellationInsteadOfAnErrorMessage() = runBlocking {
        var message: String? = null

        assertFailsWith<CancellationException> {
            runIosAttachmentPickerCatching(onSelectionError = { message = it }) {
                throw CancellationException("picker closed")
            }
        }

        assertEquals(null, message)
    }
}
