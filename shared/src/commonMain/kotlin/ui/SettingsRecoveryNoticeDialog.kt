package com.valoser.futacha.shared.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.valoser.futacha.shared.state.SettingsRecoveryNotices

/**
 * Tells the user once that a damaged settings file was replaced by the
 * settings that could be recovered (and whether the app lock was lost), and
 * that the toshiaki saved data could not be read (P4-1).
 * Host it inside the app tree; it waits behind the app lock like other windows.
 */
@Composable
internal fun SettingsRecoveryNoticeDialog() {
    val notice by SettingsRecoveryNotices.notice.collectAsState()
    val current = notice ?: return
    FutachaAppLockAwareWindow {
        AlertDialog(
            // Requires an explicit OK so the notice is not lost to a stray tap.
            onDismissRequest = {},
            title = { Text(current.title) },
            text = { Text(current.message) },
            confirmButton = {
                TextButton(onClick = SettingsRecoveryNotices::acknowledge) { Text("OK") }
            }
        )
    }
}
