@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.ai.IosAiConnectionStorage
import com.valoser.futacha.shared.util.findIosRootViewController
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlinx.coroutines.launch

/** The Keychain item for あいもげ (this device only, while unlocked; a separate service from the OpenAI keys). */
private class IosAimogeSecretStorage(service: String = "com.valoser.futacha.aimoge") : AimogeSecretStorage {
    private val item = IosAiConnectionStorage(serviceName = service, maxBytes = if (service.endsWith(".requests")) AIMOGE_LEDGER_MAX_BYTES else AIMOGE_SECRETS_MAX_BYTES)
    override fun read(): String? = item.read()
    override fun write(value: String) = item.write(value)
}

/** Installs the app's [AimogeAuth]. iOS keeps the waiting approval in memory only (the session receives the return). */
fun installIosAimogeAuth() {
    AimogeAuthRegistry.auth?.let { auth ->
        aimogeStoreScope.launch { runCatching { auth.refresh() } }
        return
    }
    AimogeWriteRegistry.ledgerStorage = IosAimogeSecretStorage("com.valoser.futacha.aimoge.requests")
    AimogeAuthRegistry.auth = AimogeAuth(
        storage = IosAimogeSecretStorage(),
        api = AimogeAuthApi(createAimogeHttpClientForThisApp()),
        persistPending = false
    )
}

private class AimogePresentationProvider(private val window: UIWindow) : NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor = window
}

/** Held strongly until the session finishes (a released session closes at once). */
private var liveSession: ASWebAuthenticationSession? = null
private var liveProvider: AimogePresentationProvider? = null

/**
 * Shows the approval page in ASWebAuthenticationSession (shared cookies, not ephemeral). [onFinished] receives the
 * return address, or null when the person cancelled or the session could not start.
 */
fun openIosAimogeApprovalPage(url: String, onFinished: (String?) -> Unit) {
    val nsUrl = NSURL.URLWithString(url) ?: return onFinished(null)
    val window = findIosRootViewController()?.view?.window ?: return onFinished(null)
    liveSession?.cancel()
    val provider = AimogePresentationProvider(window)
    val session = ASWebAuthenticationSession(nsUrl, AIMOGE_CALLBACK_SCHEME) { callback, _ ->
        liveSession = null
        liveProvider = null
        onFinished(callback?.absoluteString)
    }
    session.presentationContextProvider = provider
    session.prefersEphemeralWebBrowserSession = false
    liveSession = session
    liveProvider = provider
    if (!session.start()) {
        liveSession = null
        liveProvider = null
        onFinished(null)
    }
}
