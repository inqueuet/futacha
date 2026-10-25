package com.valoser.futacha.shared.ui.board

import kotlin.test.*
import io.ktor.http.parseQueryString
import com.valoser.futacha.shared.util.resolveUrlLaunchRequest

class BugReportTest {
    @Test fun theReportContainsOnlyReviewedFields() {
        val report = buildBugReport("13.0", "としあき(仮)", "device/os", "mono", "写真から戻る")
        listOf("13.0", "としあき(仮)", "device/os", "mono", "写真から戻る").forEach { assertTrue(it in report) }
        listOf("Cookie", "削除キー", "下書き").forEach { assertFalse(it in report) }
    }

    @Test fun aShortMailContainsEveryReviewedCharacterWithSafeQueryEncoding() {
        val report = buildBugReport("13.1", "mode", "device", "theme", "改行\n&body=置換しない + % 🧪")
        val plan = bugReportMailPlan(report)
        assertNull(plan.reportToCopy)
        assertNotNull(resolveUrlLaunchRequest(plan.url))
        assertEquals(report, parseQueryString(plan.url.substringAfter('?'))["body"])
        assertEquals("ふたちゃ 不具合報告", parseQueryString(plan.url.substringAfter('?'))["subject"])
    }

    @Test fun theExactLocal8192CharacterLimitIsAcceptedAndTheNextCharacterUsesFullCopy() {
        val prefix = bugReportMailto("").length
        val exactReport = "a".repeat(8192 - prefix)
        val exact = bugReportMailPlan(exactReport)
        assertEquals(8192, exact.url.length)
        assertNull(exact.reportToCopy)
        assertNotNull(resolveUrlLaunchRequest(exact.url))
        val over = bugReportMailPlan(exactReport + "b")
        assertEquals(exactReport + "b", over.reportToCopy)
        assertNotNull(resolveUrlLaunchRequest(over.url))
        assertFalse("body=" in over.url)
    }

    @Test fun japaneseUtf8ExpansionUsesEncodedUrlLengthInsteadOfTheNumberOfInputCharacters() {
        val prefix = bugReportMailto("").length
        val count = (8192 - prefix) / 9
        val fitting = "あ".repeat(count)
        assertEquals(count * 3, fitting.encodeToByteArray().size)
        assertEquals(count * 9, bugReportMailto(fitting).length - prefix)
        assertNull(bugReportMailPlan(fitting).reportToCopy)
        val overflow = fitting + "あ"
        assertTrue(bugReportMailto(overflow).length > 8192)
        assertEquals(overflow, bugReportMailPlan(overflow).reportToCopy)
    }

    @Test fun oversizedSupplementaryUnicodeCopiesAllBytesWithoutSplittingCharacters() {
        val report = "🧪".repeat(1000) + "終端"
        val plan = bugReportMailPlan(report)
        assertTrue(bugReportMailto(report).length > 8192)
        assertContentEquals(report.encodeToByteArray(), assertNotNull(plan.reportToCopy).encodeToByteArray())
        assertEquals(bugReportMailto(), plan.url)
    }

    @Test fun reportConstructionDoesNotTruncateALongPastedBody() {
        val steps = "詳細".repeat(3000) + "最後の一文字まで"
        val report = buildBugReport("v", "m", "d", "t", steps)
        assertTrue(report.endsWith(steps))
        assertEquals(report, bugReportMailPlan(report).reportToCopy)
    }

    @Test fun onlyAnEmptyEditorCanBeClosedWithoutAConfirmation() {
        assertFalse(shouldConfirmBugReportDiscard(""))
        assertTrue(shouldConfirmBugReportDiscard(" "))
        assertTrue(shouldConfirmBugReportDiscard("再現手順"))
    }

    /** L35: each mode names its scheme as its settings do. */
    @Test fun theSchemeNamesFollowEachModesSettings() {
        assertEquals("ふたばクラシック・ダーク", futachaBugReportThemeName(
            com.valoser.futacha.shared.model.ThemePalette.FutabaClassic, com.valoser.futacha.shared.model.ThemeMode.Dark))
        assertEquals("メインカラー #00A0E9・ダークモード（背景真っ黒）", com.valoser.futacha.shared.ui.ftutter.ftutterBugReportThemeName(
            0xFF00A0E9, com.valoser.futacha.shared.ui.ftutter.FtutterBrightness.BLACK))
        assertEquals("メインカラー 標準・ライトモード", com.valoser.futacha.shared.ui.ftutter.ftutterBugReportThemeName(
            null, com.valoser.futacha.shared.ui.ftutter.FtutterBrightness.LIGHT))
    }
}
