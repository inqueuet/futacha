package com.valoser.futacha.shared.network.aimoge

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Audit 2026-11-11 P2-L3 / L3: a question left open under the app lock is answered "no"; the host count holds. */
class AimogePromptsLockTest {
    @Test
    fun anOpenQuestionIsAnsweredNoWhenTheAppLocks() = runTest {
        AimogePrompts.attachHost()
        try {
            val asked = async { AimogePrompts.ask(AimogePromptRequest.Confirm("送りますか")) }
            while (AimogePrompts.current.value == null) yield()
            AimogePrompts.answerOpenQuestionsNo()
            assertFalse(asked.await())
            // The next question is not held by the first one.
            val next = async { AimogePrompts.ask(AimogePromptRequest.Confirm("もう一度")) }
            while (AimogePrompts.current.value == null) yield()
            AimogePrompts.current.value!!.second.complete(true)
            assertEquals(true, next.await())
        } finally {
            AimogePrompts.detachHost()
        }
    }
}
