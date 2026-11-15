package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.network.aimoge.openIosAimogeApprovalPage

@Composable
actual fun rememberAimogeApprovalOpener(): AimogeApprovalOpener = remember {
    AimogeApprovalOpener { url, onReturn -> openIosAimogeApprovalPage(url, onReturn); true }
}
