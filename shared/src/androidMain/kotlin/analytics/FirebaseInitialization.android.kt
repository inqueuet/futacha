package com.valoser.futacha.shared.analytics

import android.content.Context
import com.google.firebase.FirebaseApp

/**
 * Firebase must not be initialized by a startup ContentProvider.  Keeping the
 * initialization here lets the application call it from its IO-scoped
 * telemetry job after the first frame is available.
 */
internal fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
    val appContext = context.applicationContext
    return runCatching {
        FirebaseApp.getApps(appContext).firstOrNull()
            ?: FirebaseApp.initializeApp(appContext)
    }.getOrNull()
}
