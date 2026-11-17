package com.valoser.futacha.shared.background

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class IosBackgroundTaskIdentifiersTest {
    /** iOS refuses to register (and silently never runs) an identifier missing from Info.plist. */
    @Test fun everyBackgroundTaskIdentifierIsPermittedInTheIosInfoPlist() {
        val plist = File("../iosApp/iosApp/Info.plist").readText()
        val permitted = plist.substringAfter("<key>BGTaskSchedulerPermittedIdentifiers</key>").substringBefore("</array>")
        BackgroundRefreshTaskKind.entries.forEach {
            assertTrue("<string>${it.identifier}</string>" in permitted, it.identifier)
        }
        val modes = plist.substringAfter("<key>UIBackgroundModes</key>").substringBefore("</array>")
        assertTrue("<string>fetch</string>" in modes && "<string>processing</string>" in modes)
    }
}
