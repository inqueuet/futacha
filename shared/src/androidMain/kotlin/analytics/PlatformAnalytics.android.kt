package com.valoser.futacha.shared.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

actual object PlatformAnalytics {
    private var analytics: FirebaseAnalytics? = null

    actual fun configure(platformContext: Any?) {
        val context = platformContext as? Context ?: return
        ensureFirebaseInitialized(context) ?: return
        analytics = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    actual fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    actual fun logEvent(name: String, params: Map<String, String>) {
        val instance = analytics ?: return
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (key) {
                "firebase_screen" -> bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, value)
                "firebase_screen_class" -> bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, value)
                else -> bundle.putString(key, value)
            }
        }
        instance.logEvent(name, bundle)
    }
}
