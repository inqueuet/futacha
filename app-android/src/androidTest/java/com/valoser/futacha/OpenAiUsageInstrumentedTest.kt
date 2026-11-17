@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.ui.board.OpenAiLimitNotice
import com.valoser.futacha.shared.ui.board.OpenAiUsageControls
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class OpenAiUsageInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun rateLimitNoticeAndSettingsExplainWaitAndEstimatedUsage() {
        val store = runBlocking {
            AiConnectionStore(object : AiConnectionStorage {
                private var value: String? = null
                override fun read() = value
                override fun write(value: String) { this.value = value }
            }).also {
                it.load()
                it.save(AiProvider.DEVICE, AiProvider.OPENAI, "gpt-4.1-mini", "test-only-key")
                it.usage.attempt(1234)
                it.usage.response(429, headersOf("Retry-After", "17419"), false)
                it.usage.cooldown(17_419_000, "server_retry")
            }
        }
        rule.runOnUiThread {
            rule.activity.setContent {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) { OpenAiUsageControls(store) }
                    OpenAiLimitNotice(true, store)
                }
            }
        }
        rule.onNodeWithText("OpenAIの判定を一時停止（429）").assertIsDisplayed()
        rule.onAllNodesWithText("OpenAIの利用制限（429）：残り4時間", substring = true).onLast().assertIsDisplayed()
        rule.waitForIdle()
        android.os.SystemClock.sleep(500) // Capture the dialog after its entrance animation.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "openai-429-notice.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithText("送信本文の推定トークン量：1234", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("実消費量・課金額ではありません", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("API通知のトークン残量：不明 / 上限不明").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("OpenAIで実際の利用状況を確認").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("OpenAIの判定を一時停止（429）").assertDoesNotExist()
    }
}
