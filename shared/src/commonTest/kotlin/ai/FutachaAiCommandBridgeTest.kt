package com.valoser.futacha.shared.ai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutachaAiCommandBridgeTest {
    @BeforeTest
    fun setUp() {
        FutachaAiCommandBridge.drainBufferedCommandsForTest()
    }

    @AfterTest
    fun tearDown() {
        FutachaAiCommandBridge.drainBufferedCommandsForTest()
    }

    @Test
    fun commandEnqueuedBeforeCollectorStartsIsDelivered() {
        runBlocking {
            val command = FutachaAiCommand(
                action = FutachaAiAction.OpenThread,
                parameters = mapOf("board" to "b", "thread" to "123")
            )

            assertTrue(FutachaAiCommandBridge.enqueue(command))

            val delivered = withTimeout(1_000) {
                FutachaAiCommandBridge.commands.first()
            }
            assertEquals(command, delivered)
        }
    }

    @Test
    fun deepLinkEnqueuedBeforeCollectorStartsIsParsedAndDelivered() {
        runBlocking {
            assertTrue(
                FutachaAiCommandBridge.enqueueDeepLink(
                    raw = "futacha://ai?action=search_catalog&board=b&query=%E3%83%86%E3%82%B9%E3%83%88",
                    source = "test"
                )
            )

            val delivered = withTimeout(1_000) {
                FutachaAiCommandBridge.commands.first()
            }
            assertEquals(FutachaAiAction.SearchCatalog, delivered.action)
            assertEquals("b", delivered.parameter("board"))
            assertEquals("テスト", delivered.parameter("query"))
            assertEquals("test", delivered.source)
        }
    }

    @Test
    fun intentCommandIsEnqueuedWithoutDeepLinkSerialization() {
        runBlocking {
            assertTrue(
                FutachaAiCommandBridge.enqueueIntentCommand(
                    actionId = "draft_reply",
                    board = "b",
                    thread = "123",
                    query = "",
                    url = "",
                    value = "",
                    name = "name",
                    email = "sage",
                    subject = "",
                    comment = "本文です",
                    password = "delete-key",
                    source = "ios-app-intents"
                )
            )

            val delivered = withTimeout(1_000) {
                FutachaAiCommandBridge.commands.first()
            }
            assertEquals(FutachaAiAction.DraftReply, delivered.action)
            assertEquals("本文です", delivered.parameter("comment"))
            assertEquals("delete-key", delivered.parameter("password"))
            assertEquals(null, delivered.parameter("query"))
            assertEquals("ios-app-intents", delivered.source)
        }
    }

    @Test
    fun intentCommandAcceptsActionAliases() {
        runBlocking {
            assertTrue(
                FutachaAiCommandBridge.enqueueIntentCommand(
                    actionId = "save",
                    board = "b",
                    thread = "123",
                    query = "",
                    url = "",
                    value = "",
                    name = "",
                    email = "",
                    subject = "",
                    comment = "",
                    password = "",
                    source = "test"
                )
            )

            val delivered = withTimeout(1_000) {
                FutachaAiCommandBridge.commands.first()
            }
            assertEquals(FutachaAiAction.SaveCurrentThread, delivered.action)
            assertEquals("123", delivered.threadIdParameter())
        }
    }

    @Test
    fun unsupportedDeepLinkIsRejectedWithoutBuffering() {
        runBlocking {
            assertFalse(
                FutachaAiCommandBridge.enqueueDeepLink(
                    raw = "futacha://ai?action=unknown_action",
                    source = "test"
                )
            )

            assertTrue(FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.OpenBoardList)))
            val delivered = withTimeout(1_000) {
                FutachaAiCommandBridge.commands.first()
            }
            assertEquals(FutachaAiAction.OpenBoardList, delivered.action)
        }
    }

    @Test
    fun unsupportedIntentCommandIsRejectedWithoutBuffering() {
        runBlocking {
            assertFalse(
                FutachaAiCommandBridge.enqueueIntentCommand(
                    actionId = "unknown_action",
                    board = "",
                    thread = "",
                    query = "",
                    url = "",
                    value = "",
                    name = "",
                    email = "",
                    subject = "",
                    comment = "",
                    password = "",
                    source = "test"
                )
            )

            assertTrue(FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.OpenBoardList)))
            val delivered = withTimeout(1_000) {
                FutachaAiCommandBridge.commands.first()
            }
            assertEquals(FutachaAiAction.OpenBoardList, delivered.action)
        }
    }
}
