package com.valoser.futacha.shared.ui.board

import kotlin.test.*

class BugReportTest {
    @Test fun theReportContainsOnlyReviewedFields() {
        val report = buildBugReport("13.0", "としあき(仮)", "device/os", "mono", "写真から戻る")
        listOf("13.0", "としあき(仮)", "device/os", "mono", "写真から戻る").forEach { assertTrue(it in report) }
        listOf("Cookie", "削除キー", "下書き").forEach { assertFalse(it in report) }
    }
}
