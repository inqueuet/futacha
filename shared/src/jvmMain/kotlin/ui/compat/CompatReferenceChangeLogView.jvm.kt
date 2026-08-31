package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun CompatReferenceChangeLogView(
    html: String,
    modifier: Modifier,
    onLinkClicked: (String) -> Unit
) {
    Box(modifier) { Text("更新履歴") }
}
