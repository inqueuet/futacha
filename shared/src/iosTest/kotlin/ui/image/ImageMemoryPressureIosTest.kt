package com.valoser.futacha.shared.ui.image

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageMemoryPressureIosTest {
    @Test
    fun memoryWarningIsDeliveredUntilTheMonitorCloses() {
        val events = mutableListOf<ImageMemoryPressureLevel>()
        val monitor = createImageMemoryPressureMonitor(platformContext = null, events::add)

        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = UIApplicationDidReceiveMemoryWarningNotification,
            `object` = null
        )
        assertEquals(listOf(ImageMemoryPressureLevel.CRITICAL), events)

        monitor.close()
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = UIApplicationDidReceiveMemoryWarningNotification,
            `object` = null
        )
        assertEquals(listOf(ImageMemoryPressureLevel.CRITICAL), events)
    }
}
