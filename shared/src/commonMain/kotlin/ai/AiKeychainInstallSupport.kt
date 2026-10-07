package com.valoser.futacha.shared.ai

/**
 * iOS keeps Keychain items when an app is deleted, so the OpenAI keys and the OpenAI/BOTH
 * moderation destination saved by a previous install came back after a reinstall, and AI
 * judgement could be switched on again without the user having chosen it. NSUserDefaults and
 * the app container are removed with the app. An item created before this install's container
 * therefore belongs to an earlier install.
 *
 * Only "the item is older than the container" discards it. An existing user who simply updates
 * has no marker yet, but the key was saved after the container was created, so it is kept; with
 * either date unknown nothing is discarded.
 */
internal fun isAiKeychainItemFromEarlierInstall(
    installMarkerPresent: Boolean,
    itemCreatedAtSeconds: Double?,
    installedAtSeconds: Double?
): Boolean {
    if (installMarkerPresent) return false
    if (itemCreatedAtSeconds == null || installedAtSeconds == null) return false
    return itemCreatedAtSeconds < installedAtSeconds - AI_KEYCHAIN_INSTALL_MARGIN_SECONDS
}

private const val AI_KEYCHAIN_INSTALL_MARGIN_SECONDS = 1.0
