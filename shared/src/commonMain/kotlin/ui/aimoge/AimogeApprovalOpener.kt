package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.runtime.Composable

/**
 * Opens the あいもげ approval page. Returns false when it could not be shown. [onReturn] receives the return
 * address on iOS (ASWebAuthenticationSession), or null when cancelled; on Android the return arrives in
 * AimogeAuthCallbackActivity instead and [onReturn] is not called.
 */
fun interface AimogeApprovalOpener {
    fun open(url: String, onReturn: (String?) -> Unit): Boolean
}

@Composable
expect fun rememberAimogeApprovalOpener(): AimogeApprovalOpener
