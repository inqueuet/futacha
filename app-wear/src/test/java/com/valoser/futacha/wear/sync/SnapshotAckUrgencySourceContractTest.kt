package com.valoser.futacha.wear.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C-10: the snapshot receipt goes back to the phone as an urgent DataItem. A
 * non-urgent item may wait up to 30 minutes for the next sync, so the phone
 * kept the delivered snapshot pending and the "+N" new-reply badges did not
 * reset. (PutDataRequest needs the Android runtime, so the source is checked.)
 */
class SnapshotAckUrgencySourceContractTest {
    @Test
    fun snapshotAckDataItemIsUrgent() {
        val file = File("src/main/java/com/valoser/futacha/wear/sync/WatchDataLayerListenerService.kt")
        assertTrue("source not found from ${File(".").absolutePath}", file.isFile)
        val source = file.readText()
        val start = source.indexOf("private fun sendSnapshotAck(")
        assertTrue("sendSnapshotAck not found", start >= 0)
        val body = source.substring(start, source.indexOf(".putDataItem(request)", start))
        assertTrue("snapshot ack is not built", body.contains("PutDataMapRequest.create(WATCH_SNAPSHOT_ACK_PATH)"))
        assertTrue("snapshot ack DataItem is not urgent", body.contains(".asPutDataRequest().setUrgent()"))
    }
}
