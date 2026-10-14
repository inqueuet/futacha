package com.valoser.futacha.shared.audio

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import com.valoser.futacha.shared.desktop.DesktopPlatform
import java.util.Base64

actual class TextSpeaker actual constructor(platformContext: Any?) {
    actual val supportsPitch: Boolean = false
    @Volatile private var settings = SpeechSettings()
    private var voiceCache: List<SpeechVoice>? = null
    actual fun configure(settings: SpeechSettings) { this.settings = settings }
    actual suspend fun availableVoices(): List<SpeechVoice> = withContext(Dispatchers.IO) {
        voiceCache?.let { return@withContext it }
        val command = if (DesktopPlatform.isWindows) windowsSpeechCommand("""
            Add-Type -AssemblyName System.Speech
            ${'$'}speech = [System.Speech.Synthesis.SpeechSynthesizer]::new()
            try {
                [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
                ${'$'}speech.GetInstalledVoices() | Where-Object { ${'$'}_.Enabled -and ${'$'}_.VoiceInfo.Culture.Name -like 'ja*' } | ForEach-Object { [Console]::WriteLine(${'$'}_.VoiceInfo.Name) }
            } finally { ${'$'}speech.Dispose() }
        """.trimIndent()) else listOf("/usr/bin/say", "-v", "?")
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            val lines = process.inputStream.bufferedReader(Charsets.UTF_8).lineSequence().take(2000).toList()
            check(process.waitFor() == 0) { JAPANESE_TTS_UNAVAILABLE_MESSAGE }
            val voices = if (DesktopPlatform.isWindows) lines.filter { it.isNotBlank() }.map { SpeechVoice(it, it) }
                else lines.mapNotNull { line -> Regex("^(.+?)\\s+ja[_-][A-Za-z]+\\s").find(line)?.groupValues?.get(1)?.trim()?.let { SpeechVoice(it, it) } }
            voices.distinctBy { it.id }.also { voiceCache = it }
        } finally { process.destroyForcibly() }
    }
    private val active = AtomicReference<Process?>(null)
    actual suspend fun prepare() {
        if (!DesktopPlatform.isWindows) check(java.io.File("/usr/bin/say").canExecute()) { JAPANESE_TTS_UNAVAILABLE_MESSAGE }
    }
    actual suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        prepare(); stop()
        val current = settings
        val voice = availableVoices().firstOrNull { it.id == current.voiceId }?.id
        val command = if (DesktopPlatform.isWindows) {
            // Speech text travels through stdin, never through a command or script literal.
            val script = """
                ${'$'}ErrorActionPreference = 'Stop'
                [Console]::InputEncoding = [System.Text.UTF8Encoding]::new()
                Add-Type -AssemblyName System.Speech
                ${'$'}speech = [System.Speech.Synthesis.SpeechSynthesizer]::new()
                try {
                    ${'$'}speech.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet, [System.Speech.Synthesis.VoiceAge]::NotSet, 0, [System.Globalization.CultureInfo]::GetCultureInfo('ja-JP'))
                    ${'$'}payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
                    if (${'$'}payload.voice) { ${'$'}speech.SelectVoice(${'$'}payload.voice) }
                    ${'$'}speech.Rate = [int]${'$'}payload.rate
                    ${'$'}speech.Speak([string]${'$'}payload.text)
                } finally { ${'$'}speech.Dispose() }
            """.trimIndent()
            windowsSpeechCommand(script)
        } else listOf("/usr/bin/say", "-v", voice ?: availableVoices().firstOrNull()?.id ?: "Kyoko", "-r", (175 * current.rate.coerceIn(0.5f, 2f)).toInt().toString())
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        active.set(process)
        try {
            val input = if (DesktopPlatform.isWindows) kotlinx.serialization.json.buildJsonObject {
                put("text", kotlinx.serialization.json.JsonPrimitive(text))
                put("voice", kotlinx.serialization.json.JsonPrimitive(voice.orEmpty()))
                put("rate", kotlinx.serialization.json.JsonPrimitive((kotlin.math.ln(current.rate.toDouble()) / kotlin.math.ln(2.0) * 5).toInt().coerceIn(-10, 10)))
            }.toString() else text
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(input) }
            while (process.isAlive) { ensureActive(); delay(50) }
            check(process.exitValue() == 0) { JAPANESE_TTS_UNAVAILABLE_MESSAGE }
        } finally { active.compareAndSet(process, null); process.destroyForcibly() }
    }
    actual fun stop() { active.getAndSet(null)?.destroyForcibly() }
    actual fun close() = stop()
}
actual fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)

private fun windowsSpeechCommand(script: String) = listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand",
    Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE)))
