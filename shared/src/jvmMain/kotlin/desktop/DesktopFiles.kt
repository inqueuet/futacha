package com.valoser.futacha.shared.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private fun desktopDialogOwner() = java.awt.Window.getWindows().firstOrNull { it.isFocused }
    ?: java.awt.Window.getWindows().firstOrNull { it.isVisible }

internal suspend fun chooseWindowsFile(title: String, extensions: Set<String> = emptySet(), directory: Boolean = false): File? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = if (directory) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                if (extensions.isNotEmpty()) fileFilter = FileNameExtensionFilter(extensions.joinToString(" / "), *extensions.toTypedArray())
            }
            continuation.invokeOnCancellation { java.awt.EventQueue.invokeLater { chooser.cancelSelection() } }
            val result = if (chooser.showOpenDialog(desktopDialogOwner()) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            if (continuation.isActive) continuation.resume(result)
        }
    }

internal suspend fun chooseDesktopFile(title: String = "ファイルを選択", extensions: Set<String> = emptySet()): File? =
    if (DesktopPlatform.isWindows) chooseWindowsFile(title, extensions) else withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            if (extensions.isNotEmpty()) dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in extensions }
            continuation.invokeOnCancellation { java.awt.EventQueue.invokeLater { dialog.dispose() } }
            try {
                dialog.isVisible = true
                val result = dialog.file?.let { File(dialog.directory, it) }
                if (continuation.isActive) continuation.resume(result)
            } finally { dialog.dispose() }
        }
    }

internal suspend fun chooseDesktopDirectory(): File? = if (DesktopPlatform.isWindows)
    chooseWindowsFile("保存先のフォルダーを選択", directory = true) else MacOsIntegration.chooseDirectory()

internal suspend fun readDesktopAttachment(file: File, maxBytes: Long): com.valoser.futacha.shared.util.ImageData = withContext(Dispatchers.IO) {
    require(file.isFile && file.length() in 1..maxBytes) { "ファイルのサイズ上限を超えています" }
    val bytes = file.inputStream().use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer); if (n < 0) break
            require(output.size().toLong() + n <= maxBytes) { "ファイルのサイズ上限を超えています" }
            output.write(buffer, 0, n)
        }
        output.toByteArray()
    }
    com.valoser.futacha.shared.util.ImageData(bytes, file.name)
}

internal fun openDesktopUrl(value: String) {
    val uri = URI(value)
    require(uri.scheme?.lowercase() in setOf("http", "https", "mailto")) { "このリンクは開けません" }
    if (uri.scheme == "mailto") java.awt.Desktop.getDesktop().mail(uri)
    else java.awt.Desktop.getDesktop().browse(uri)
}

internal fun desktopLocalFile(value: String): File =
    if (value.startsWith("file:/", true)) File(com.valoser.futacha.shared.util.localMediaSavePath(value)) else File(value)
