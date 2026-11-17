package com.valoser.futacha.shared.ui.aimoge

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit 2026-11-11 P2-L1: a cancelled first read must not mark the icons loaded for the rest of the process. */
class AimogeBoardIconsLoadTest {
    @AfterTest fun reset() = AimogeBoardIcons.resetForTest()

    @Test
    fun aCancelledFirstReadIsReadAgain() = runBlocking {
        AimogeBoardIcons.resetForTest()
        val root = Files.createTempDirectory("aimoge-icons").toFile()
        try {
            val real = DesktopCompatibilityStore(JvmFileSystem(root))
            real.initialize()
            real.savePreference(AimogeBoardIcons.PREFERENCE_KEY, """{"https://nijiurachan.net/b/ai2/":"https://nijiurachan.net/i/ai2.png"}""")
            val reading = CompletableDeferred<Unit>()
            val stuck = object : CompatibilityStore by real {
                override suspend fun loadPreference(key: String): String? { reading.complete(Unit); awaitCancellation() }
            }
            val first = launch(Dispatchers.Default) { AimogeBoardIcons.load(stuck) }
            reading.await()
            first.cancel()
            first.join()
            AimogeBoardIcons.load(real)
            assertEquals("https://nijiurachan.net/i/ai2.png", AimogeBoardIcons.icons.value["https://nijiurachan.net/b/ai2/"])
        } finally {
            root.deleteRecursively()
        }
    }
}
