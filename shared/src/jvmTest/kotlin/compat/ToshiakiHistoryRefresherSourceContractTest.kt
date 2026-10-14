package compat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** B8: ふたちゃ's history refresher reaches としあき(仮)'s history tools (the host passes it; the app hands it on). */
class ToshiakiHistoryRefresherSourceContractTest {
    @Test
    fun theHostPassesTheSharedRefresherAndTheAppHandsItToTheTools() {
        val host = File("src/commonMain/kotlin/ui/FutachaApp.kt").readText()
        val compatCall = host.substring(host.indexOf("CompatibilityApp("))
        assertTrue(compatCall.substring(0, compatCall.indexOf("onExitApplication")).contains("historyRefresher = sharedHistoryRefresher"))

        val app = File("src/commonMain/kotlin/ui/compat/CompatibilityApp.kt").readText()
        assertTrue(app.contains("historyRefresher: com.valoser.futacha.shared.service.HistoryRefresher? = null"))
        assertTrue(Regex("""CompatibilityAppContent\([\s\S]*?historyRefresher = historyRefresher""").containsMatchIn(app))
        assertTrue(Regex("""rememberCompatHistoryTools\([\s\S]*?historyRefresher = historyRefresher""").containsMatchIn(app))

        val tools = File("src/commonMain/kotlin/ui/compat/CompatHistoryTools.kt").readText()
        // Same bounds as ふたちゃ's drawer refresh: no auto-saved copies, a few threads, a timeout.
        assertTrue(tools.contains("autoSaveBudgetMillis = 0L"))
        assertTrue(tools.contains("maxAutoSavesPerRun = 0"))
        assertTrue(tools.contains("withTimeoutOrNull(COMPAT_HISTORY_REFRESH_TIMEOUT_MILLIS)"))
        assertTrue(tools.contains("HistoryRefresher.RefreshAlreadyRunningException"))
    }
}
