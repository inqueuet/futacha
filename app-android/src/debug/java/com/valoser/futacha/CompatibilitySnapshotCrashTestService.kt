package com.valoser.futacha

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.IBinder
import android.os.Process
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Debug-only fault injector. It deliberately dies with a compatibility snapshot transaction
 * open so instrumentation can prove SQLite rollback after a real Linux process death.
 */
class CompatibilitySnapshotCrashTestService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val databaseName = intent?.getStringExtra(EXTRA_DATABASE_NAME)
        val tabKey = intent?.getStringExtra(EXTRA_TAB_KEY)
        val markerName = intent?.getStringExtra(EXTRA_MARKER_NAME)
        if (databaseName.isNullOrBlank() || tabKey.isNullOrBlank() || markerName.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        thread(name = "compat-snapshot-crash-injector") {
            runCrashTransaction(databaseName, tabKey, markerName, startId)
        }
        return START_NOT_STICKY
    }

    private fun runCrashTransaction(
        databaseName: String,
        tabKey: String,
        markerName: String,
        startId: Int
    ) {
        val marker = File(noBackupFilesDir, markerName)
        val db = SQLiteDatabase.openDatabase(
            getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            db.beginTransaction()
            db.update(
                "compat_thread_snapshot",
                ContentValues().apply {
                    put("revision", CRASH_REVISION)
                    put("fetched_at", CRASH_FETCHED_AT)
                },
                "tab_key=?",
                arrayOf(tabKey)
            )
            db.delete("compat_post", "tab_key=?", arrayOf(tabKey))
            db.insertOrThrow(
                "compat_post",
                null,
                ContentValues().apply {
                    put("tab_key", tabKey)
                    put("revision", CRASH_REVISION)
                    put("position", 0)
                    put("post_json", "{\"position\":0,\"postNo\":\"crash\",\"timestamp\":\"now\",\"messageHtml\":\"partial\"}")
                }
            )
            db.update(
                "compat_tab",
                ContentValues().apply {
                    put("reply_count", 1)
                    put("content_updated_at", CRASH_FETCHED_AT)
                    put("snapshot_revision", CRASH_REVISION)
                },
                "tab_key=?",
                arrayOf(tabKey)
            )
            FileOutputStream(marker).use { output ->
                output.write("transaction-open pid=${Process.myPid()}".encodeToByteArray())
                output.fd.sync()
            }
            Thread.sleep(KILL_DELAY_MILLIS)
            Process.killProcess(Process.myPid())
        } catch (error: Throwable) {
            if (db.inTransaction()) db.endTransaction()
            FileOutputStream(marker).use { output ->
                output.write("error=${error.javaClass.name}:${error.message}".encodeToByteArray())
                output.fd.sync()
            }
            stopSelf(startId)
        }
    }

    companion object {
        const val EXTRA_DATABASE_NAME = "database_name"
        const val EXTRA_TAB_KEY = "tab_key"
        const val EXTRA_MARKER_NAME = "marker_name"
        private const val CRASH_REVISION = 999_999L
        private const val CRASH_FETCHED_AT = 999_999L
        private const val KILL_DELAY_MILLIS = 750L
    }
}
