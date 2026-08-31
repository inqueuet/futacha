package com.valoser.futacha

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.valoser.futacha.compat.AndroidExperienceProfileStore
import com.valoser.futacha.shared.compat.ExperienceProfileUiController
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import java.io.FileOutputStream

/** Debug-only host proving the real Activity Result registry uses the generation gate. */
class ProfileActivityResultGateTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferencesName = checkNotNull(intent.getStringExtra(EXTRA_PREFERENCES_NAME))
        val launchedMarker = checkNotNull(intent.getStringExtra(EXTRA_LAUNCHED_MARKER))
        val deliveredMarker = checkNotNull(intent.getStringExtra(EXTRA_DELIVERED_MARKER))
        val resultDelayMillis = intent.getLongExtra(EXTRA_RESULT_DELAY_MILLIS, DEFAULT_RESULT_DELAY_MILLIS)
        val store = AndroidExperienceProfileStore(applicationContext, preferencesName)
        val launchProfile = store.readActiveProfile()
        val launchGeneration = store.readGeneration()

        setContent {
            val controller = ExperienceProfileUiController(
                isAvailable = true,
                activeProfile = launchProfile,
                sessionGeneration = launchGeneration,
                isSessionActive = true,
                isSessionAuthoritativelyCurrent = { token ->
                    store.isGenerationCommitAllowed(token.profile, token.generation)
                }
            )
            CompositionLocalProvider(LocalExperienceProfileUiController provides controller) {
                val launcher = rememberExperienceProfileActivityResultLauncher(
                    ActivityResultContracts.StartActivityForResult()
                ) { result, _ ->
                    writeMarker(deliveredMarker, "result=${result.resultCode}")
                    finish()
                }
                LaunchedEffect(Unit) {
                    launcher.launch(
                        Intent(
                            this@ProfileActivityResultGateTestActivity,
                            ProfileResultEchoActivity::class.java
                        ).putExtra(ProfileResultEchoActivity.EXTRA_LAUNCHED_MARKER, launchedMarker)
                            .putExtra(ProfileResultEchoActivity.EXTRA_RESULT_DELAY_MILLIS, resultDelayMillis)
                    )
                }
                Text("Activity Result generation gate test")
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, HOST_TIMEOUT_MILLIS)
    }

    private fun writeMarker(markerName: String, value: String) {
        FileOutputStream(noBackupFilesDir.resolve(markerName)).use { output ->
            output.write(value.encodeToByteArray())
            output.fd.sync()
        }
    }

    companion object {
        const val EXTRA_PREFERENCES_NAME = "preferences_name"
        const val EXTRA_LAUNCHED_MARKER = "launched_marker"
        const val EXTRA_DELIVERED_MARKER = "delivered_marker"
        const val EXTRA_RESULT_DELAY_MILLIS = "result_delay_millis"
        private const val DEFAULT_RESULT_DELAY_MILLIS = 1_000L
        private const val HOST_TIMEOUT_MILLIS = 4_000L
    }
}

/** Debug-only external Activity which returns after a deterministic delay. */
class ProfileResultEchoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val markerName = checkNotNull(intent.getStringExtra(EXTRA_LAUNCHED_MARKER))
        FileOutputStream(noBackupFilesDir.resolve(markerName)).use { output ->
            output.write("launched".encodeToByteArray())
            output.fd.sync()
        }
        val delayMillis = intent.getLongExtra(EXTRA_RESULT_DELAY_MILLIS, 1_000L)
        Handler(Looper.getMainLooper()).postDelayed(
            {
                setResult(RESULT_OK, Intent().putExtra("echo", "ok"))
                finish()
            },
            delayMillis
        )
    }

    companion object {
        const val EXTRA_LAUNCHED_MARKER = "launched_marker"
        const val EXTRA_RESULT_DELAY_MILLIS = "result_delay_millis"
    }
}
