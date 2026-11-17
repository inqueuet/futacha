package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.ui.aimoge.AIMOGE_BOARD_URL_DISABLED_MESSAGE
import com.valoser.futacha.shared.ui.aimoge.AimogeBoardUrlInput
import com.valoser.futacha.shared.ui.aimoge.classifyAimogeBoardUrlInput
import com.valoser.futacha.shared.ui.ftutter.FtutterBoardInput
import com.valoser.futacha.shared.ui.ftutter.classifyFtutterBoardInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import com.valoser.futacha.shared.ui.board.buildAddBoardValidationState
import com.valoser.futacha.shared.ui.board.withAimoge
import com.valoser.futacha.shared.ui.compat.CompatAimogeBoardInput
import com.valoser.futacha.shared.ui.compat.compatAimogeBoardInput
import com.valoser.futacha.shared.ui.futaber.futaberAddBoardAlertReason

/** Top/overall/mix URLs open the AM-B5 individual-board picker in all four modes. */
class AimogeAggregateBoardUrlTest {
    @BeforeTest fun on() { AimogeAvailability.sitesEnabled = true }
    @AfterTest fun off() { AimogeAvailability.sitesEnabled = false }

    private val aggregateUrls = listOf("https://nijiurachan.net/b/", "https://nijiurachan.net/mix/", "http://nijiurachan.net/mix", "https://nijiurachan.net/b", "https://nijiurachan.net/", "https://nijiurachan.net", "https://www.nijiurachan.net/?ref=x", " HTTPS://NIJIURACHAN.NET/MIX/ ")

    @Test fun theOverallAndMixPagesOpenTheSelectionInEveryMode() {
        aggregateUrls.forEach {
            val classified = classifyAimogeBoardUrlInput(it)
            assertEquals(AimogeBoardUrlInput.SelectBoards, classified, it)
            assertTrue(buildAddBoardValidationState("", it, emptyList()).withAimoge(classified, emptyList()).canSubmit, it)
            assertEquals(CompatAimogeBoardInput.SelectBoards, compatAimogeBoardInput(it, emptyList()), it)
            assertNull(futaberAddBoardAlertReason(it, emptyList()), it)
            assertEquals(FtutterBoardInput.AimogeBoards, classifyFtutterBoardInput(it, emptyList()), it)
        }
        assertEquals(AimogeBoardUrlInput.Board("https://nijiurachan.net/b/aimg/", "aimg"), classifyAimogeBoardUrlInput("https://nijiurachan.net/b/aimg/"))
    }

    @Test fun disabledBuildsNeverOfferThePicker() {
        AimogeAvailability.sitesEnabled = false
        aggregateUrls.forEach {
            assertEquals(AimogeBoardUrlInput.Refused(AIMOGE_BOARD_URL_DISABLED_MESSAGE), classifyAimogeBoardUrlInput(it))
            assertEquals(FtutterBoardInput.Invalid(AIMOGE_BOARD_URL_DISABLED_MESSAGE), classifyFtutterBoardInput(it, emptyList()))
        }
    }

    @Test fun unrelatedHostsAndPathsNeverOpenThePicker() {
        listOf("https://nijiurachan.net.evil.example/mix/", "https://evil.example/mix/", "https://api.nijiurachan.net/mix/",
            "https://nijiurachan.net/b/aimg/app-auth", "https://nijiurachan.net:443/mix/", "https://nijiurachan.net/mix/aimg/thread/no").forEach {
            assertEquals(AimogeBoardUrlInput.NotAimoge, classifyAimogeBoardUrlInput(it), it)
        }
    }
}
