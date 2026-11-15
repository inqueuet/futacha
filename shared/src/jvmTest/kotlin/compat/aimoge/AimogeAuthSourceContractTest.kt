package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.network.aimoge.aimogeBrowserPackage
import com.valoser.futacha.shared.ui.aimoge.aimogeApprovalSlugOf
import com.valoser.futacha.shared.ui.aimoge.aimogeSettingsVisible
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AM-B8: the approval return, the browser choice, the settings rows, and that no secret reaches a log. */
class AimogeAuthSourceContractTest {
    private val manifest = File("../app-android/src/main/AndroidManifest.xml").readText()
    private val callback = File("../app-android/src/main/java/com/valoser/futacha/AimogeAuthCallbackActivity.kt").readText()

    @Test fun theReturnActivityIsInTheMainManifestWithOnlyTheCallbackFilter() {
        val block = Regex("<activity\\s+android:name=\"\\.AimogeAuthCallbackActivity\"[\\s\\S]*?</activity>").find(manifest)!!.value
        for (attr in listOf(
            "android:exported=\"true\"", "android:excludeFromRecents=\"true\"", "android:noHistory=\"true\"",
            "android:taskAffinity=\"\"", "android:scheme=\"com.valoser.futacha\"", "android:host=\"auth\"",
            "android:path=\"/callback\"", "android.intent.category.BROWSABLE"
        )) assertTrue(attr in block, attr)
        assertFalse("https" in block)
        // Browsers can be listed (the page opens in one of them, never in this app).
        assertTrue(Regex("<queries>[\\s\\S]*android.intent.category.BROWSABLE[\\s\\S]*android:scheme=\"https\"[\\s\\S]*</queries>").containsMatchIn(manifest))
    }

    @Test fun theReturnNeverReachesMainActivityAndHistoryReturnsAreDropped() {
        assertTrue("FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY" in callback)
        assertTrue("intent = Intent()" in callback)
        assertTrue("Intent(this, MainActivity::class.java)" in callback)
        assertFalse("putExtra" in callback, "the code or state must not ride on MainActivity's intent")
        assertFalse("setData" in callback || ".data =" in callback)
        assertFalse("experienceProfileStore" in callback)
        assertFalse("Log." in callback || "Logger." in callback)
    }

    @Test fun noAuthSourceLogsOrPrintsAnything() {
        val files = listOf(
            "src/commonMain/kotlin/network/aimoge/AimogeAuth.kt",
            "src/commonMain/kotlin/network/aimoge/AimogeAuthApi.kt",
            "src/androidMain/kotlin/network/aimoge/AimogeAuth.android.kt",
            "src/iosMain/kotlin/network/aimoge/AimogeAuth.ios.kt",
            "src/commonMain/kotlin/ui/aimoge/AimogeSettingsDialog.kt",
        )
        for (path in files) {
            val text = File(path).readText()
            for (bad in listOf("Logger.", "println(", "NSLog(", "Log.d(", "Log.e(", "Log.w(", "Log.i(", "printStackTrace")) {
                assertFalse(bad in text, "$path uses $bad")
            }
        }
    }

    @Test fun theBrowserIsNeverThisApp() {
        val own = "com.valoser.futacha"
        assertEquals("org.mozilla.firefox", aimogeBrowserPackage(listOf(own, "org.mozilla.firefox"), own, own))
        assertEquals("com.android.chrome", aimogeBrowserPackage(listOf("org.mozilla.firefox", "com.android.chrome"), "com.android.chrome", own))
        assertEquals("org.mozilla.firefox", aimogeBrowserPackage(listOf("org.mozilla.firefox"), "android", own))
        assertNull(aimogeBrowserPackage(listOf(own), own, own))
    }

    @Test fun theApprovalUsesARegisteredAimogeBoardAndTheRowsHideWhileOff() {
        assertEquals("aimg", aimogeApprovalSlugOf(listOf("https://may.2chan.net/b/futaba.php", "https://nijiurachan.net/b/aimg/")))
        assertNull(aimogeApprovalSlugOf(listOf("https://may.2chan.net/b/")))
        assertFalse(aimogeSettingsVisible(), "tests start with あいもげ off")
        for (path in listOf(
            "src/commonMain/kotlin/ui/board/GlobalSettingsComponents.kt",
            "src/commonMain/kotlin/ui/compat/CompatSettingsModel.kt",
            "src/commonMain/kotlin/ui/futaber/FutaberSettingsOrder.kt",
            "src/commonMain/kotlin/ui/ftutter/FtutterSettingsHost.kt",
        )) assertTrue("aimogeSettingsVisible()" in File(path).readText(), path)
    }

    @Test fun iosAddsNoNewUrlScheme() {
        // ASWebAuthenticationSession receives the return itself; the plist keeps only its old `futacha` scheme.
        val plist = File("../iosApp/iosApp/Info.plist").readText()
        assertFalse("com.valoser.futacha</string>" in plist)
        assertTrue("TEAM_ID" in File("../iosApp/iosApp.xcodeproj/project.pbxproj").readText())
        val ios = File("src/iosMain/kotlin/network/aimoge/AimogeAuth.ios.kt").readText()
        assertTrue("prefersEphemeralWebBrowserSession = false" in ios)
        assertTrue("persistPending = false" in ios)
    }
}
