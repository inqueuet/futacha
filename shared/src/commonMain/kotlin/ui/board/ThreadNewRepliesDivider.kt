package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.ui.compat.CompatNewRepliesDivider

@Composable
internal fun ThreadNewRepliesDivider(count: Int) {
    CompatNewRepliesDivider(count, Modifier.testTag("thread-new-replies-divider"))
}
