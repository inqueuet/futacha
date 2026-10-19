package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
internal fun CompatSpeechSettingsScreen(store: CompatibilityStore, preferences: Map<String, String>, onBack: () -> Unit) {
    val context = LocalPlatformContext.current
    val runtime = LocalSpeechRuntime.current
    val speaker = remember(context, runtime) { createTextSpeaker(context, runtime) }
    val settings = speechSettingsFromPreferences(preferences)
    val scope = rememberCoroutineScope()
    var voices by remember { mutableStateOf<List<SpeechVoice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var samplePlaying by remember { mutableStateOf(false) }
    var sampleCompleted by remember { mutableStateOf(false) }
    var sampleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val unlocked = LocalFutachaAppUnlocked.current
    DisposableEffect(speaker) { onDispose { sampleJob?.cancel(); speaker.close() } }
    LaunchedEffect(unlocked) {
        if (!unlocked) { sampleJob?.cancel(); speaker.stop(); samplePlaying = false }
    }
    LaunchedEffect(speaker) {
        // Listing voices must not acquire playback focus or interrupt other audio.
        // Each platform initializes voice discovery independently of speak().
        try { voices = speaker.availableVoices() }
        catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive(); error = "音声の準備がタイムアウトしました" }
        catch (failure: Exception) { error = failure.message ?: "音声を取得できませんでした" }
        finally { loading = false }
    }
    fun save(key: String, value: String) { scope.launch {
        try { store.savePreference(key, value) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "読み上げ設定を保存できませんでした" }
    } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row { TextButton(onClick = onBack) { Text("‹ 設定") }; Text("読み上げ", modifier = Modifier.padding(12.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("モード間で共有する読み上げ設定です。利用できる日本語音声を選べます。")
            VoiceAssetSettings(runtime, settings, onEngine = { save(SPEECH_ENGINE_KEY, it) },
                onStop = { sampleJob?.cancel(); speaker.stop(); samplePlaying = false })
            if (settings.engine == SpeechEngineKind.SYSTEM) {
            Text("端末の音声", style = MaterialTheme.typography.titleMedium)
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            val choices = listOf(SpeechVoice("", "端末の既定音声")) + voices
            val selectedId = settings.voiceId.takeIf { id -> voices.any { it.id == id } }.orEmpty()
            choices.forEach { voice ->
                Row {
                    RadioButton(selected = selectedId == voice.id, onClick = { save(SPEECH_VOICE_KEY, voice.id) })
                    TextButton(onClick = { save(SPEECH_VOICE_KEY, voice.id) }) { Text(voice.label) }
                }
            }
            if (settings.voiceId.isNotBlank() && voices.none { it.id == settings.voiceId } && !loading) {
                Text("保存した音声が見つからないため、既定音声で読み上げます。")
            }
            }
            SpeechSettingSlider("速さ", "speech-rate", settings.rate) { save(SPEECH_RATE_KEY, it.toString()) }
            if (settings.engine == SpeechEngineKind.VOICEVOX_ZUNDAMON) VoicePitchSlider(settings.voicevoxPitch) { save(SPEECH_VOICEVOX_PITCH_KEY, it.toString()) }
            else if (speaker.supportsPitch) SpeechSettingSlider("声の高さ", "speech-pitch", settings.pitch) { save(SPEECH_PITCH_KEY, it.toString()) }
            if (samplePlaying) TextButton(onClick = { sampleJob?.cancel(); speaker.stop(); samplePlaying = false }) { Text("試聴を停止") }
            else TextButton(enabled = !loading || settings.engine == SpeechEngineKind.VOICEVOX_ZUNDAMON, onClick = {
                samplePlaying = true; sampleCompleted = false; error = null
                sampleJob = scope.launch {
                    try { speaker.configure(settings); speaker.speak("ふたちゃの読み上げ音声を確認しています。"); sampleCompleted = true }
                    catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive(); error = "試聴がタイムアウトしました" }
                    catch (failure: Exception) { error = failure.message ?: "試聴できませんでした" }
                    finally { samplePlaying = false }
                }
            }) { Text("試聴") }
            if (sampleCompleted) Text("試聴が終了しました", Modifier.testTag("speech-sample-finished"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("speech-error")) }
        }
    }
}

@Composable
private fun SpeechSettingSlider(title: String, tag: String, saved: Float, onSave: (Float) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved) }
    Text("$title：${(value * 100).toInt()}%")
    Slider(value = value, onValueChange = { value = it }, valueRange = 0.5f..2f, steps = 14,
        onValueChangeFinished = { onSave(value) }, modifier = Modifier.testTag(tag))
    TextButton(onClick = { value = 1f; onSave(1f) }) { Text("$title を標準に戻す") }
}
