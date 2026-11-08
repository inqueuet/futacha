package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.compat.applyCompatMailPreset
import com.valoser.futacha.shared.ui.compat.compatPostMailPresets
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

/** The sections of futaber's 書き込み設定 (`P/out-post/90-12`), then this app's own. */
internal enum class FutaberPostSettingsSection { Writing, ImageQuality, Other, NameAndMail }

/**
 * The sections shown: futaber's 書き込み・画像の品質, 「その他」 (端末情報出力) only when opened from the writing screen's gear
 * ([fromWritingScreen]), then this app's 名前・メール (this mode's own values until D-B6 shares them). futaber's 画像アップローダー is left out
 * until it is decided (plan §4.2: 塩粒 stays out).
 */
internal fun futaberPostSettingsSections(fromWritingScreen: Boolean): List<FutaberPostSettingsSection> = buildList {
    add(FutaberPostSettingsSection.Writing)
    add(FutaberPostSettingsSection.ImageQuality)
    if (fromWritingScreen) add(FutaberPostSettingsSection.Other)
    add(FutaberPostSettingsSection.NameAndMail)
}

internal const val FUTABER_POST_SETTINGS_TITLE = "書き込み設定"
internal const val FUTABER_DEVICE_INFO_ROW = "端末情報出力"

/**
 * futaber's full-screen 書き込み設定 (S16): 「完了」 at the top left, the grouped table. Every change is passed on at once
 * (the stored settings come back late, and typing into that round trip loses the composing text), as the dialog did.
 * [onDeviceInfo] (only from the writing screen, C44) puts the device line into the text and closes the page.
 * Back closes it like 「完了」.
 */
@Composable
internal fun FutaberPostSettingsScreen(
    settings: FutaberPostSettings,
    deleteKey: String,
    onSettingsChange: (name: String, email: String, confirmBeforeSend: Boolean) -> Unit,
    onDeleteKeyChange: (String) -> Unit,
    onImageQualityChange: (FutaberImageQuality) -> Unit,
    onDone: () -> Unit,
    onDeviceInfo: (() -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    var name by remember { mutableStateOf(settings.name) }
    var email by remember { mutableStateOf(settings.email) }
    var key by remember { mutableStateOf(deleteKey) }
    var confirmBeforeSend by remember { mutableStateOf(settings.confirmBeforeSend) }
    var quality by remember { mutableStateOf(settings.imageQuality) }
    val nameIme = com.valoser.futacha.shared.ui.board.rememberStableTextInputState(name, {
        name = it.futaberTakeChars(60)
        onSettingsChange(name, email, confirmBeforeSend)
    })
    val emailIme = com.valoser.futacha.shared.ui.board.rememberStableTextInputState(email, {
        email = it.futaberTakeChars(60)
        onSettingsChange(name, email, confirmBeforeSend)
    })
    PlatformBackHandler(enabled = true) { onDone() }
    // The writing's text under this page keeps no focus while it is up: what is typed goes to the field tapped here.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    FutaberTableScreen(
        title = FUTABER_POST_SETTINGS_TITLE,
        onDone = onDone,
        doneTag = "futaber-post-settings-done",
        modifier = Modifier.testTag("futaber-post-settings-page")
    ) {
        futaberPostSettingsSections(fromWritingScreen = onDeviceInfo != null).forEach { section ->
            when (section) {
                FutaberPostSettingsSection.Writing -> FutaberTableSection("書き込み", tag = "futaber-post-settings-section-writing") {
                    FutaberTableSwitchRow("書き込み前に確認", "futaber-post-confirm-switch", confirmBeforeSend) {
                        confirmBeforeSend = it
                        onSettingsChange(name, email, confirmBeforeSend)
                    }
                    FutaberTableFieldRow(
                        label = "削除キー", tag = "futaber-post-deletekey", value = key, secret = true, last = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        onValueChange = {
                            key = it.futaberTakeChars(20)
                            onDeleteKeyChange(key)
                        }
                    )
                }
                FutaberPostSettingsSection.ImageQuality -> FutaberTableSection("画像の品質", tag = "futaber-post-settings-section-quality") {
                    FutaberImageQuality.entries.forEachIndexed { index, choice ->
                        FutaberTableCheckRow(
                            choice.label, "futaber-post-quality-${choice.persisted.lowercase()}", quality == choice,
                            last = index == FutaberImageQuality.entries.lastIndex
                        ) {
                            quality = choice
                            onImageQualityChange(choice)
                        }
                    }
                }
                FutaberPostSettingsSection.Other -> FutaberTableSection("その他", tag = "futaber-post-settings-section-other") {
                    FutaberTableActionRow(FUTABER_DEVICE_INFO_ROW, "futaber-post-device-info", last = true) { onDeviceInfo?.invoke() }
                }
                FutaberPostSettingsSection.NameAndMail -> {
                    FutaberTableSection("名前・メール", tag = "futaber-post-settings-section-name") {
                        FutaberTableFieldRow(
                            "名前", "futaber-post-name", nameIme.value, nameIme.onValueChange,
                            visualTransformation = com.valoser.futacha.shared.ui.board.imeCompositionHighlight(nameIme.value.composition)
                        )
                        FutaberTableFieldRow(
                            "メール", "futaber-post-email", emailIme.value, emailIme.onValueChange, last = true,
                            visualTransformation = com.valoser.futacha.shared.ui.board.imeCompositionHighlight(emailIme.value.composition)
                        )
                    }
                    if (LocalFutaberDisplaySettings.current.extMailPresets) {
                        // The same buttons and values ふたちゃ and としあき(仮) offer for the mail field.
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            compatPostMailPresets(true).forEach { preset ->
                                val applied = applyCompatMailPreset(email, preset, true)
                                TextButton(
                                    onClick = {
                                        email = applied.futaberTakeChars(60)
                                        onSettingsChange(name, email, confirmBeforeSend)
                                    },
                                    modifier = Modifier.testTag("futaber-post-mail-preset-$preset")
                                ) { Text(preset, color = if (email == applied) colors.accent else colors.link) }
                            }
                        }
                    }
                    FutaberTableNote("名前・メールはこのアプリの項目です（futaberの書き込み設定にはありません）。削除キーは自分のレスを後から削除するときに使います")
                }
            }
        }
    }
}
