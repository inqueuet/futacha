@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class OpenAiApiManagementInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private class Storage : AiConnectionStorage {
        var data: String? = null
        var usage: String? = null
        override fun read() = data
        override fun write(value: String) { data = value }
        override fun readUsage() = usage
        override fun writeUsage(value: String) { usage = value }
    }
    private fun open(store: AiConnectionStore) = rule.setContent {
        MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { OpenAiConnectionControls(connectionStore = store) }
            OpenAiLimitNotice(true, store)
        }
    }
    private fun add(name: String, key: String) {
        rule.onNodeWithTag("api-add").performClick()
        rule.onNodeWithTag("api-name").performTextInput(name)
        rule.onNodeWithTag("openai-api-key").performTextInput(key)
        rule.onNodeWithTag("openai-api-key").performImeAction()
        rule.onNodeWithTag("api-editor-save").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("api-editor-save").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun manageMultipleApisReorderDisableEditDeleteAndKeepCloudConsent() {
        val storage = Storage()
        val store = runBlocking { AiConnectionStore(storage).also { it.load() } }
        open(store)
        rule.onNodeWithTag("openai-api-management-open").performScrollTo().performClick()
        add("個人用", "test-ui-key-1111")
        add("予備用", "test-ui-key-2222")
        val first = store.state.value.apiKeys[0].id
        val second = store.state.value.apiKeys[1].id
        rule.onNodeWithTag("api-up-$second").performScrollTo().performClick()
        rule.waitUntil(5_000) { store.state.value.apiKeys.first().id == second }
        rule.onNodeWithTag("api-edit-$second").performScrollTo().performClick()
        rule.onNodeWithTag("api-name").performTextReplacement("予備を優先")
        rule.onNodeWithTag("api-editor-save").performClick()
        rule.waitUntil(5_000) { store.state.value.apiKeys.first().name == "予備を優先" }
        rule.onNodeWithTag("api-enabled-$first").performScrollTo().performClick()
        rule.waitUntil(5_000) { !store.state.value.apiKeys.last().enabled }
        rule.onNodeWithTag("api-management-close").performClick()
        rule.onNodeWithTag("ai-moderation-BOTH").performScrollTo().performClick()
        rule.onNodeWithText("AI設定を保存").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("ai-cloud-consent").performScrollTo().performClick()
        rule.onNodeWithText("AI設定を保存").performScrollTo().performClick()
        rule.waitUntil(5_000) { store.state.value.moderationProvider == AiProvider.BOTH }
        assertEquals("test-ui-key-2222", runBlocking { store.apiKey(store.state.value.revision) })
        rule.onNodeWithTag("openai-api-management-open").performScrollTo().performClick()
        rule.onNodeWithTag("api-delete-$second").performScrollTo().performClick()
        rule.onNodeWithTag("api-delete-confirm").performClick()
        rule.waitUntil(5_000) { store.state.value.apiKeys.size == 1 }
        assertEquals(AiProvider.DEVICE, store.state.value.moderationProvider)
        assertFalse(store.state.value.hasApiKey)
        assertFalse(storage.data!!.contains("test-ui-key-2222"))
        rule.onNodeWithTag("api-enabled-$first").performScrollTo().assertIsOff()
    }
    @Test fun automatic429SwitchShowsPerKeyWaitAndStopsOnlyWhenAllKeysAreUnavailable() {
        val store = runBlocking { AiConnectionStore(Storage()).also {
            it.load(); it.saveApiKey(null, "先頭API", "test-ui-first"); it.saveApiKey(null, "次のAPI", "test-ui-next")
            it.save(AiProvider.DEVICE, AiProvider.OPENAI, DEFAULT_OPENAI_SUMMARY_MODEL)
        } }
        var allLimited = false
        val calls = mutableListOf<String>()
        val service = OpenAiService(store, store.state.value, HttpClient(MockEngine { request ->
            val key = request.headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")
            calls += key
            if (key == "test-ui-first" || allLimited) respond("{}", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "600"))
            else respond(buildJsonObject { putJsonArray("results") { add(buildJsonObject {
                putJsonObject("category_scores") { OPENAI_MODERATION_CATEGORIES.keys.forEach { put(it, 0.01) } }
            }) } }.toString())
        }))
        fun input(body: String) = PostModerationInput("t", listOf(Post("1", author = null, subject = null, timestamp = "",
            messageHtml = body, imageUrl = null, thumbnailUrl = null)))
        try {
            runBlocking { service.classifyPosts(input("普通の投稿")).getOrThrow() }
            assertEquals(listOf("test-ui-first", "test-ui-next"), calls)
            open(store)
            rule.onNodeWithText("OpenAIの判定を一時停止（429）").assertDoesNotExist()
            rule.onNodeWithTag("openai-api-management-open").performScrollTo().performClick()
            val first = store.state.value.apiKeys.first().id
            rule.onNodeWithTag("api-entry-$first").performScrollTo()
            rule.onNodeWithText("待機中：残り", substring = true).assertIsDisplayed()
            screenshot("multi-api-management.png")
            rule.onNodeWithTag("api-management-close").performClick()
            allLimited = true
            assertTrue(runBlocking { service.classifyPosts(input("別の普通の投稿")).isFailure })
            rule.onNodeWithText("OpenAIの判定を一時停止（429）").assertIsDisplayed()
            rule.onAllNodesWithText("全APIが待機中です。", substring = true).onLast().assertIsDisplayed()
            assertEquals(3, calls.size)
        } finally { service.close() }
    }
    @Test fun androidEncryptedStorageReloadsSixteenKeysLargerThanOldLimit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "api-pool-${System.nanoTime()}").apply { mkdirs() }
        val isolatedContext = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = folder }
        try {
            val store = AiConnectionStore(AndroidAiConnectionStorage(isolatedContext)).also { it.load() }
            repeat(MAX_OPENAI_KEYS) { store.saveApiKey(null, "接続$it", "test-large-$it-" + "x".repeat(1000)) }
            val bytes = File(folder, "openai-connection.enc").readBytes()
            assertTrue(bytes.size > 8192)
            assertFalse(bytes.decodeToString().contains("test-large"))
            val reopened = AiConnectionStore(AndroidAiConnectionStorage(isolatedContext)).also { it.load() }
            assertNull(reopened.state.value.storageError)
            assertEquals(MAX_OPENAI_KEYS, reopened.state.value.apiKeys.size)
        } finally { folder.deleteRecursively() }
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
