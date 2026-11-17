package com.valoser.futacha.shared.media.source

import com.valoser.futacha.shared.desktop.DesktopPlatform
import okio.FileHandle
import okio.FileSystem
import okio.Path
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

internal actual fun FileSystem.openOriginalMediaReadHandle(path: Path): FileHandle {
    if (!DesktopPlatform.isWindows || this !== FileSystem.SYSTEM) return openReadOnly(path)
    // RandomAccessFile denies FILE_SHARE_DELETE on Windows. NIO permits renaming
    // a completed download while existing playback readers keep the same file open.
    val channel = FileChannel.open(java.nio.file.Path.of(path.toString()), StandardOpenOption.READ)
    return object : FileHandle(readWrite = false) {
        override fun protectedRead(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Int =
            channel.read(ByteBuffer.wrap(array, arrayOffset, byteCount), fileOffset)
        override fun protectedSize(): Long = channel.size()
        override fun protectedClose() = channel.close()
        override fun protectedWrite(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Unit =
            throw UnsupportedOperationException("Read-only media handle")
        override fun protectedResize(size: Long): Unit = throw UnsupportedOperationException("Read-only media handle")
        override fun protectedFlush(): Unit = throw UnsupportedOperationException("Read-only media handle")
    }
}
