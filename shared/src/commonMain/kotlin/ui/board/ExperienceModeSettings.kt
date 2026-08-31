package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController

@Composable
internal fun GlobalSettingsModeSection() {
    val controller = LocalExperienceProfileUiController.current
    if (!controller.isAvailable) return
    var requestedProfile by remember { mutableStateOf<ExperienceProfile?>(null) }

    SettingsSection(
        title = "モード",
        icon = Icons.Rounded.SwapHoriz,
        description = "現在: ${controller.activeProfile.displayName}",
        initiallyExpanded = false
    ) {
        ExperienceProfile.entries.forEach { profile ->
            ListItem(
                headlineContent = { Text(profile.displayName) },
                supportingContent = {
                    Text(
                        if (profile == ExperienceProfile.FUTACHA) {
                            "現在のふたちゃ画面と操作を使用します。"
                        } else {
                            "旧型タブ操作を再現した、ふたちゃ内の非公式表示モードです。元アプリや開発者との公式な関係はありません。"
                        }
                    )
                },
                leadingContent = {
                    RadioButton(
                        selected = controller.activeProfile == profile,
                        onClick = null
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = controller.activeProfile == profile,
                        enabled = !controller.switchInProgress,
                        role = Role.RadioButton,
                        onClick = {
                            if (profile != controller.activeProfile) requestedProfile = profile
                        }
                    )
            )
        }
        controller.lastError?.let { error ->
            Text("切替に失敗しました: $error")
        }
    }

    val target = requestedProfile
    if (target != null) {
        AlertDialog(
            onDismissRequest = { requestedProfile = null },
            title = { Text("${target.displayName}へ切り替えますか？") },
            text = {
                Text(
                    "画面構成とホーム画面のアイコンが切り替わり、現在の画面を閉じて板一覧へ戻ります。" +
                        "板一覧・閲覧中のスレッド・履歴・スレッドキャッシュはモード間で共有されます。" +
                        "モード固有の下書き・表示設定はそれぞれ保持されます。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        requestedProfile = null
                        controller.requestSwitch(target)
                    }
                ) { Text("切り替える") }
            },
            dismissButton = {
                TextButton(onClick = { requestedProfile = null }) { Text("キャンセル") }
            }
        )
    }
}
