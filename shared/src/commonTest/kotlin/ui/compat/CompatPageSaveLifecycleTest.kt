package com.valoser.futacha.shared.ui.compat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompatPageSaveLifecycleTest {
    @Test
    fun notificationCancellationReopensThePageSaveGateWithoutConvertingTheCause() = runBlocking {
        var finished = false

        assertFailsWith<CancellationException> {
            runCompatPageSaveWithCleanup(onFinished = { finished = true }) {
                throw CancellationException("notification cancel")
            }
        }

        assertTrue(finished)
    }
}
