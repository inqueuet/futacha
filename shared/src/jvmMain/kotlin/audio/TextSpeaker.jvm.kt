package com.valoser.futacha.shared.audio

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import com.valoser.futacha.shared.desktop.DesktopPlatform
import java.util.Base64

actual class TextSpeaker actual constructor(platformContext: Any?) {
    private val active = AtomicReference<Process?>(null)
    actual suspend fun prepare() {
        if (!DesktopPlatform.isWindows) check(java.io.File("/usr/bin/say").canExecute()) { JAPANESE_TTS_UNAVAILABLE_MESSAGE }
    }
    actual suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        prepare(); stop()
        val command = if (DesktopPlatform.isWindows) {
            // Speech text travels through stdin, never through a command or script literal.
            val script = """
                ${'$'}ErrorActionPreference = 'Stop'
                [Console]::InputEncoding = [System.Text.UTF8Encoding]::new()
                Add-Type -AssemblyName System.Speech
                ${'$'}speech = [System.Speech.Synthesis.SpeechSynthesizer]::new()
                try {
                    ${'$'}speech.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet, [System.Speech.Synthesis.VoiceAge]::NotSet, 0, [System.Globalization.CultureInfo]::GetCultureInfo('ja-JP'))
                    ${'$'}speech.Speak([Console]::In.ReadToEnd())
                } finally { ${'$'}speech.Dispose() }
            """.trimIndent()
            listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand",
                Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE)))
        } else listOf("/usr/bin/say", "-v", "Kyoko")
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        active.set(process)
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            while (process.isAlive) { ensureActive(); delay(50) }
            check(process.exitValue() == 0) { JAPANESE_TTS_UNAVAILABLE_MESSAGE }
        } finally { active.compareAndSet(process, null); process.destroyForcibly() }
    }
    actual fun stop() { active.getAndSet(null)?.destroyForcibly() }
    actual fun close() = stop()
}
actual fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)
