package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.runtime.Composable

/** あいもげ is off on the desktop (J3); nothing is opened. */
@Composable
actual fun rememberAimogeApprovalOpener(): AimogeApprovalOpener = AimogeApprovalOpener { _, _ -> false }
