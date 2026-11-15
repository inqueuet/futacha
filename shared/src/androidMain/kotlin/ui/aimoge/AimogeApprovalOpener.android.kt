package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.network.aimoge.openAimogeApprovalPage

@Composable
actual fun rememberAimogeApprovalOpener(): AimogeApprovalOpener {
    val context = LocalContext.current
    return remember(context) { AimogeApprovalOpener { url, _ -> openAimogeApprovalPage(context, url) } }
}
