package com.valoser.futacha.shared.background

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class IosAppTransportSecurityTest {
    /**
     * L136 (rest of M9, decision 1A): Futaba is always HTTPS. The ATS exception added for the former
     * ftutter useHttps=false let any mode send Cookies and deletion keys to 2chan.net in plaintext on iOS.
     */
    @Test fun iosDoesNotAllowPlaintextHttpTo2chan() {
        val plist = File("../iosApp/iosApp/Info.plist").readText()
        assertFalse("NSExceptionAllowsInsecureHTTPLoads" in plist, "no insecure HTTP exception may remain")
        assertFalse("<key>2chan.net</key>" in plist, "no ATS exception domain for 2chan.net")
    }
}
