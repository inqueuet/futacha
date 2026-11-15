package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createAimogeHttpClient(appVersion: String): HttpClient = HttpClient(Darwin) {
    configureAimogeCommon(appVersion)
    engine {
        configureSession {
            // No cookies are kept or sent for あいもげ.
            HTTPShouldSetCookies = false
            HTTPCookieStorage = null
        }
    }
}

/** [createAimogeHttpClient] with this app's version from the bundle. */
fun createAimogeHttpClientForThisApp(): HttpClient = createAimogeHttpClient(
    platform.Foundation.NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
)
