package com.valoser.futacha.shared.ui.board

internal fun resolveGlobalSettingsActionTarget(action: GlobalSettingsAction): String? {
    return when (action) {
        GlobalSettingsAction.Cookies -> null
        GlobalSettingsAction.Email -> "mailto:admin@valoser.com?subject=お問い合わせ"
        GlobalSettingsAction.X -> "https://x.com/inqueuet"
        GlobalSettingsAction.Developer -> "https://github.com/inqueuet/futacha"
        GlobalSettingsAction.PrivacyPolicy -> "https://note.com/inqueuet/n/nc6ebcc1d6a67"
    }
}
