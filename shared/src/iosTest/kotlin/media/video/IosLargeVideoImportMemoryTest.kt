package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.darwin.*
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.*

/**
 * Audit item 41: the device video import copies through NSFileHandle chunks
 * without an explicit autoreleasepool. This measures how far the process
 * footprint rises while a large file is imported. If the temporary NSData
 * stayed alive until the copy finished, the rise would track the file size.
 */
@OptIn(ExperimentalForeignApi::class, NativeRuntimeApi::class)
class IosLargeVideoImportMemoryTest {
    @Test fun importing256MbDoesNotGrowTheFootprintWithTheFileSize() = measure(256)

    @Test fun importing512MbDoesNotGrowTheFootprintWithTheFileSize() = measure(512)

    private fun measure(megabytes: Int) = runBlocking {
        val fs = createFileSystem()
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        val dir = "video_import_memory/$megabytes"
        val selected = "$dir/large.mp4"
        try {
            val chunk = ByteArray(1024 * 1024)
            fs.writeByteStream(selected) { sink ->
                // A valid ISO media header; import only checks the first 12 bytes.
                sink.write(byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(),
                    'p'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte()))
                repeat(megabytes) { sink.write(chunk) }
            }.getOrThrow()
            GC.collect()
            val baseline = physFootprintBytes()
            var peak = baseline
            val sampler = launch(Dispatchers.Default) {
                while (isActive) { peak = maxOf(peak, physFootprintBytes()); delay(10) }
            }
            val source = fs.readByteStream(selected) { reader ->
                VideoEditSource.import(fs, fs.resolveAbsolutePath("$dir/work"), "large", reader, gate,
                    gate.permit(MediaFeature.VIDEO_EDITOR)!!)
            }.getOrThrow()
            sampler.cancel()
            peak = maxOf(peak, physFootprintBytes())
            val rise = (peak - baseline) / (1024 * 1024)
            println("VIDEO_IMPORT_MEMORY size=${megabytes}MB baseline=${baseline / (1024 * 1024)}MB peak=${peak / (1024 * 1024)}MB rise=${rise}MB")
            try {
                assertEquals(megabytes * 1024L * 1024L + 12, source.byteSize)
                // Bounded by the copy buffers, not by the file: well below the smaller file.
                assertTrue(rise < 64, "footprint rose ${rise}MB while importing ${megabytes}MB")
            } finally { source.close() }
        } finally {
            fs.deleteRecursively(dir).getOrThrow()
        }
    }

    private fun physFootprintBytes(): Long = memScoped {
        val info = alloc<task_vm_info_data_t>()
        val count = alloc<mach_msg_type_number_tVar>()
        count.value = (sizeOf<task_vm_info_data_t>() / sizeOf<natural_tVar>()).toUInt()
        val result = task_info(mach_task_self_, TASK_VM_INFO.toUInt(), info.ptr.reinterpret(), count.ptr)
        check(result == KERN_SUCCESS) { "task_info failed: $result" }
        info.phys_footprint.toLong()
    }
}
