@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.ui.compat

import kotlinx.coroutines.*
import kotlinx.cinterop.useContents
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import kotlin.test.*

class CompatShareAnchorTest {
    @Test fun shareHasAnAnchorEvenWhenPresentedFullScreen() = com.valoser.futacha.shared.ui.image.runIosMediaMainTest {
        withContext(Dispatchers.Main) {
            val presenter = UIViewController()
            val activity = UIActivityViewController(listOf("local test"), null)
            activity.modalPresentationStyle = platform.UIKit.UIModalPresentationFullScreen
            anchorCompatShare(activity, presenter)
            val popover = assertNotNull(activity.popoverPresentationController)
            assertEquals(presenter.view, popover.sourceView)
            popover.sourceRect.useContents {
                assertTrue(size.width > 0)
                assertTrue(size.height > 0)
                assertTrue(origin.x.isFinite() && origin.y.isFinite())
            }
        }
    }
}
