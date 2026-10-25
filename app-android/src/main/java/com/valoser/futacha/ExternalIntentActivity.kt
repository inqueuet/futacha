package com.valoser.futacha

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.valoser.futacha.shared.util.Logger

/**
 * Entry for shares and links from other apps (M66/M79). It forwards them to the app's own task, where the
 * single existing MainActivity receives them, and finishes without drawing. MainActivity itself stays
 * singleTop, so returning through the launcher icon brings the task back as it was (pickers stay open).
 */
class ExternalIntentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) forwardToMainTask(intent)
        finish()
    }

    private fun forwardToMainTask(incoming: Intent?) {
        if (incoming == null) return
        val grants = incoming.flags and URI_GRANT_FLAGS
        val forward = Intent(incoming).setClass(this, MainActivity::class.java).apply {
            // CLEAR_TOP|SINGLE_TOP: delivered to the existing root through onNewIntent, never a second root.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or grants
        }
        try {
            startActivity(forward)
        } catch (denied: SecurityException) {
            // Only the grants received with this intent are passed on; without them Main explains the unreadable share.
            Logger.w(TAG, "Forwarding the external intent with its URI grant failed: ${denied.message}")
            runCatching { startActivity(forward.apply { flags = flags and URI_GRANT_FLAGS.inv() }) }
        } catch (error: RuntimeException) {
            Logger.w(TAG, "Failed to forward the external intent: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "ExternalIntentActivity"
        const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
