@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.util

/**
 * Provides access to platform-specific text encoding utilities.
 * We specifically need Shift_JIS for Futaba form submissions.
 */
expect object TextEncoding {
    fun encodeToShiftJis(text: String): ByteArray
    fun decodeToString(bytes: ByteArray, contentType: String? = null): String
}
