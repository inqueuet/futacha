package com.valoser.futacha

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.valoser.futacha.shared.network.aimoge.AimogeAuthRegistry
import com.valoser.futacha.shared.network.aimoge.AimogeReturnResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives `com.valoser.futacha://auth/callback?state=…&code=…` (AM-B8). The code and state are handed straight to
 * [AimogeAuthRegistry] and never reach MainActivity's intent, saved state or the profile store. A return opened
 * from the recents/history, a return that does not match the waiting approval, and a second delivery of the same
 * return are dropped without a word. No UI; MainActivity is brought forward with a plain intent.
 */
class AimogeAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val address = if (fromHistory || savedInstanceState != null) null else intent?.dataString
        // Forget the address at once (it holds the one-time code).
        intent = Intent()
        if (address != null) {
            val app = application as FutachaApplication
            val context = applicationContext
            app.applicationScope.launch {
                val result = runCatching { AimogeAuthRegistry.auth?.handleReturn(address) }.getOrElse { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    AimogeReturnResult.Failed(com.valoser.futacha.shared.network.aimoge.AIMOGE_SAVE_FAILED_MESSAGE)
                }
                val message = when (result) {
                    AimogeReturnResult.Approved -> "あいもげの承認が済みました"
                    is AimogeReturnResult.Failed -> result.message
                    else -> null
                }
                if (message != null) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
                }
            }
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
