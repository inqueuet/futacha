package com.valoser.futacha.shared.util

/**
 * Platform helper to gate behavior in common UI logic.
 */
expect fun isAndroid(): Boolean

/** Android 8/10's legacy IME consumes an extra physical BACK before Compose sees it. */
expect fun isLegacyCompatImeBackBehavior(): Boolean
