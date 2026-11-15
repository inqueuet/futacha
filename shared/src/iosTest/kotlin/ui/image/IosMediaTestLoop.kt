package com.valoser.futacha.shared.ui.image
import kotlinx.coroutines.*
import platform.Foundation.*
internal fun runIosMediaMainTest(block: suspend () -> Unit) = runBlocking {
    val work = async(Dispatchers.Default) { withTimeout(15_000) { block() } }
    while (!work.isCompleted) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
    work.await()
}
