package com.valoser.futacha

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Observe the test-APK sender across processes; teardown never launches another Activity. */
internal class M66SenderFixture(private val instrumentation: Instrumentation) {
    private class Session(val token: String) {
        val created = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        @Volatile var taskId = -1
        @Volatile var root = false
        val events = CopyOnWriteArrayList<String>()
    }
    private val sessions = CopyOnWriteArrayList<Session>()
    private val context get() = instrumentation.context
    private fun intent(uri: Uri?, picker: Boolean, view: Boolean): Pair<Intent, Session> {
        val session = Session(UUID.randomUUID().toString()).also(sessions::add)
        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                val task = data?.getInt("task", -1) ?: -1
                session.events += "$code task=$task"
                when (code) {
                    1 -> { session.taskId = task; session.root = data?.getBoolean("root") == true; session.created.countDown() }
                    2 -> session.resumed.countDown()
                    3 -> session.destroyed.countDown()
                }
                Log.i("AuditM66", "fixture ack token=${session.token} code=$code task=$task root=${session.root}")
            }
        }
        return Intent().setComponent(ComponentName(context.packageName, M66TaskSenderActivity::class.java.name))
            .putExtra("targetPackage", instrumentation.targetContext.packageName)
            .putExtra("attachmentUri", uri?.toString()).putExtra("pickerFixture", picker).putExtra("viewFixture", view)
            .putExtra("fixtureToken", session.token).putExtra("fixtureObserver", receiver) to session
    }
    /** A fresh foreign root every time, even if an earlier failing test left a task behind. */
    fun start(uri: Uri?, view: Boolean = false): Int {
        val (incoming, session) = intent(uri, picker = false, view = view)
        context.startActivity(incoming.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        if (!session.resumed.await(15, TimeUnit.SECONDS)) throw AssertionError("Sender never resumed: ${session.events}")
        if (!session.root || session.taskId < 0) throw AssertionError("Sender must own a fresh foreign task: ${session.events}")
        return session.taskId
    }
    /** No task flags: a real picker must return a result into the caller's task. */
    fun picker(uri: Uri): Intent = intent(uri, picker = true, view = false).first

    fun close() {
        sessions.forEach { session ->
            if (session.destroyed.count == 0L) return@forEach
            // onCreate registers the receiver before acknowledging creation.
            if (!session.created.await(5, TimeUnit.SECONDS)) throw AssertionError("Sender never acknowledged creation: ${session.events}")
            context.sendBroadcast(Intent(context.packageName + ".audit.M66_FINISH")
                .setPackage(context.packageName).putExtra("fixtureToken", session.token))
            if (!session.destroyed.await(5, TimeUnit.SECONDS)) throw AssertionError("Sender did not finish before the next test: ${session.events}")
        }
        sessions.clear()
    }
}
