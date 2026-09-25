package com.valoser.futacha.shared.desktop

import kotlin.test.*

class DesktopHostPolicyTest {
    @Test fun onlyMinimizedOrHiddenWindowsCountAsBackground() {
        assertTrue(desktopWindowInForeground(showing = true, minimized = false, appHidden = false))
        assertFalse(desktopWindowInForeground(showing = true, minimized = true, appHidden = false))
        assertFalse(desktopWindowInForeground(showing = true, minimized = false, appHidden = true))
        assertFalse(desktopWindowInForeground(showing = false, minimized = false, appHidden = false))
    }

    @Test fun wiredWifiAndUnknownConnectionsAreUnmeteredButTetheringIsNot() {
        val route = """
               route to: default
            destination: default
                gateway: 192.168.1.1
              interface: en0
                  flags: <UP,GATEWAY,DONE,STATIC,PRCLONING,GLOBAL>
        """.trimIndent()
        val ports = """

            Hardware Port: Ethernet
            Device: en0
            Ethernet Address: 00:00:00:00:00:01

            Hardware Port: Wi-Fi
            Device: en1
            Ethernet Address: 00:00:00:00:00:02

            Hardware Port: iPhone USB
            Device: en7
            Ethernet Address: 00:00:00:00:00:03

            VLAN Configurations
            ===================
        """.trimIndent()
        assertEquals("en0", desktopDefaultRouteInterface(route))
        assertNull(desktopDefaultRouteInterface("route: writing to routing socket: not in table"))
        assertEquals("Ethernet", desktopHardwarePortName(ports, "en0"))
        assertEquals("Wi-Fi", desktopHardwarePortName(ports, "en1"))
        assertEquals("iPhone USB", desktopHardwarePortName(ports, "en7"))
        assertNull(desktopHardwarePortName(ports, "utun3"))
        assertFalse(isMeteredDesktopHardwarePort("Ethernet"))
        assertFalse(isMeteredDesktopHardwarePort("Wi-Fi"))
        assertFalse(isMeteredDesktopHardwarePort(null))
        assertTrue(isMeteredDesktopHardwarePort("iPhone USB"))
        assertTrue(isMeteredDesktopHardwarePort("Bluetooth PAN"))
        // Before the first probe the state must not block Wi-Fi-only work.
        assertTrue(DesktopNetworkState.unmetered)
    }

    @Test fun trayBalloonOpensItsThreadOnceAndNotOnLaterIconClicks() {
        val link = DesktopTrayBalloonLink(lifetimeMillis = 1_000)
        assertNull(link.consume(0))
        link.show("https://may.2chan.net/b/res/1.htm", 0)
        link.show("https://may.2chan.net/b/res/2.htm", 10)
        assertEquals("https://may.2chan.net/b/res/2.htm", link.consume(20))
        assertNull(link.consume(30), "A later double-click must not reopen the consumed thread")
        link.show("https://may.2chan.net/b/res/3.htm", 100)
        assertNull(link.consume(5_000), "An expired balloon only activates the window")
        assertNull(link.consume(5_010))
    }

    @Test fun videoFilesAreDeletedOnlyAfterThePlayerClosedThem() {
        val files = com.valoser.futacha.shared.ui.board.DesktopVideoFiles
        val directory = java.nio.file.Files.createTempDirectory("futacha-video-files").toFile()
        try {
            val open = directory.resolve("attachment.mp4").apply { writeText("x") }
            files.opened(open.absolutePath)
            files.delete(open)
            assertTrue(open.exists(), "Deleting a file VLC still has open waits for the player")
            files.closed(open.absolutePath)
            assertFalse(open.exists())

            val unused = directory.resolve("unused.mp4").apply { writeText("x") }
            files.delete(unused)
            assertFalse(unused.exists())
        } finally { directory.deleteRecursively() }
    }
}
