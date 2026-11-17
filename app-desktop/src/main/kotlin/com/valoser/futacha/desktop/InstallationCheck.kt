package com.valoser.futacha.desktop

import com.valoser.futacha.shared.desktop.*
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.*
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skia.Image
import java.awt.Component
import java.awt.Container
import java.io.File

private fun findSkia(component: Component): SkiaLayer? = if (component is SkiaLayer) component
    else (component as? Container)?.components?.firstNotNullOfOrNull(::findSkia)

/** Explicit diagnostic only; normal launches stay open and never populate test data. */
internal suspend fun checkInstallation(window: java.awt.Window, graph: DesktopAppGraph, output: File) {
    File(output, "PASSED.txt").delete()
    File(output, "FAILED.txt").delete()
    withTimeout(30_000) { while (!graph.initialized) delay(50) }
    withContext(Dispatchers.IO) { verifyDesktopNativeLibraries() }
    DesktopOsIntegration.checkInstallation(output)
    for (profile in ExperienceProfile.selectableEntries) {
        graph.switchTo(profile)
        delay(1000)
        val bitmap = checkNotNull(findSkia(window)?.screenshot()) { "Window did not render" }
        bitmap.use { source -> Image.makeFromBitmap(source).use { image ->
            image.encodeToData()?.use { data -> File(output, "${profile.persistedValue}.png").writeBytes(data.bytes) }
        } }
    }
    File(output, "PASSED.txt").writeText("Both modes rendered. Shift_JIS, SQLite/DataStore, FFmpeg, ONNX, OpenCV and VLC loaded. ${DesktopPlatform.displayName} integration checks completed; see the platform report. No microphone capture or external sharing performed.\n")
}
