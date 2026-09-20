package com.valoser.futacha.shared.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.shared.media.source.isSharedOriginalMediaUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
internal fun PromptSettingsSection() {
    val settings = LocalMediaFeatureSettings.current
    val update = LocalMediaFeatureUpdater.current ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun change(transform: (MediaFeatureSettings) -> MediaFeatureSettings) {
        scope.launch {
            busy = true
            error = false
            try { update(transform) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().testTag("prompt-settings-toggle")
            .toggleable(value = settings.promptDisplayEnabled, enabled = !busy, role = Role.Switch,
                onValueChange = { checked -> change { it.copy(promptDisplayEnabled = checked) } }),
            verticalAlignment = Alignment.CenterVertically) {
            Text("プロンプト・AIラベルを表示", Modifier.weight(1f))
            Switch(settings.promptDisplayEnabled, onCheckedChange = null, enabled = !busy,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                    checkedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
        }
        Text("取得済みの原本にある生成情報を表示します。PNG・JPEG・WebP・MP4・MOV・WebMに対応しています。AIラベルがない画像や動画も、AI生成ではないとは限りません。",
            style = MaterialTheme.typography.bodySmall)
        if (settings.promptDisplayEnabled) {
            PromptPlacement.entries.forEach { placement ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { change { it.copy(promptPlacement = placement) } },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(settings.promptPlacement == placement, onClick = null, enabled = !busy,
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface))
                    Text(when (placement) {
                        PromptPlacement.LABELS_AND_INLINE -> "AIラベルと画像下のプロンプト"
                        PromptPlacement.LABELS_ONLY -> "AIラベルのみ"
                        PromptPlacement.INLINE_ONLY -> "画像下のプロンプトのみ"
                    })
                }
            }
        }
        if (error) Text("設定を保存できませんでした。もう一度お試しください。", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun rememberGenerationMetadata(
    originalUrl: String?,
    painterState: AsyncImagePainter.State? = null,
    visible: Boolean = true
): GenerationMetadata? {
    val source = LocalOriginalMediaSource.current as? PromptMediaSource
    val gate = LocalMediaFeatureGate.current
    val settings = LocalMediaFeatureSettings.current
    val enabled = visible && LocalPromptContentVisible.current && settings.promptDisplayEnabled
    val actualUrl = (painterState as? AsyncImagePainter.State.Success)?.result?.request?.data?.toString()
        ?.takeIf(::isSharedOriginalImageUrl)
    var result by remember(source, originalUrl, enabled, settings.promptPlacement) { mutableStateOf<GenerationMetadata?>(null) }
    LaunchedEffect(source, gate, originalUrl, enabled, settings.promptPlacement, actualUrl) {
        if (source == null || gate == null || originalUrl == null || !enabled ||
            !(isSharedOriginalMediaUrl(originalUrl) || isLocalPromptMediaUrl(originalUrl))) return@LaunchedEffect
        // Settings and their permission can reach the UI in either order. Keep
        // observing permission so an initial OFF or a new generation cannot
        // leave a reopened, memory-cached image permanently without metadata.
        gate.permits(MediaFeature.PROMPT).collectLatest { permit ->
            if (permit == null) {
                result = null
                return@collectLatest
            }
            if (actualUrl != null && isSharedOriginalImageUrl(originalUrl)) source.bindSuccessfulUrl(originalUrl, actualUrl)
            result = source.metadata(originalUrl).takeIf { gate.isCurrent(permit) }
            coroutineScope {
                // A cache probe can wait for disk initialization while the visible
                // media already finishes parsing. Observe those results immediately.
                // Both jobs still stop together when permission or visibility changes.
                launch { source.inspectCached(originalUrl) }
                source.changes.collectLatest {
                    val value = source.metadata(originalUrl)
                    result = value.takeIf { gate.isCurrent(permit) }
                }
            }
        }
    }
    return result.takeIf { enabled }
}

@Composable
internal fun PromptAiBadge(metadata: GenerationMetadata?, modifier: Modifier = Modifier) {
    if (LocalPromptContentVisible.current && LocalMediaFeatureSettings.current.showAiLabels && metadata?.hasAiEvidence == true) {
        Text("AI", modifier.background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 4.dp, vertical = 1.dp)
            .testTag("prompt-ai-label"), color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
internal fun InlinePrompt(metadata: GenerationMetadata?, modifier: Modifier = Modifier) {
    if (!LocalPromptContentVisible.current || !LocalMediaFeatureSettings.current.showInlinePrompt || metadata?.candidates.isNullOrEmpty()) return
    val snapshot = metadata
    var open by remember(snapshot) { mutableStateOf(false) }
    var expanded by remember(snapshot) { mutableStateOf(false) }
    val first = snapshot.candidates.first()
    val positive = first.positive
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TextButton(onClick = { expanded = !expanded }, colors = promptTextButtonColors(),
            modifier = Modifier.testTag("prompt-inline-toggle")) {
            Text(if (expanded) "プロンプトを閉じる" else "プロンプトを開く")
        }
        if (expanded) {
            Column(Modifier.combinedClickable(onClick = { open = true }, onLongClick = { open = true })) {
                if (snapshot.candidates.size > 1) Text("生成情報（${snapshot.candidates.size}件）", style = MaterialTheme.typography.labelSmall)
                Text(promptPreview(generationInlineText(first)), maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("prompt-inline-text"))
                Row {
                    TextButton(onClick = { open = true }, colors = promptTextButtonColors()) { Text(if (first.declaration != null) "詳細" else "全文・選択") }
                    if (positive != null && snapshot.candidates.size == 1) PromptCopyButton(positive, "コピー")
                }
            }
        }
    }
    if (open) PromptInfoDialog(snapshot, onDismiss = { open = false })
}

@Composable
internal fun PromptInfoAction(metadata: GenerationMetadata?, modifier: Modifier = Modifier) {
    val settings = LocalMediaFeatureSettings.current
    if (!LocalPromptContentVisible.current || !settings.promptDisplayEnabled) return
    var opened by remember { mutableStateOf(false) }
    var expanded by remember(metadata, settings.showInlinePrompt) { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<GenerationMetadata?>(null) }
    LaunchedEffect(opened, metadata) {
        // A fast tap can precede the cache result. Accept the first result, then freeze
        // it so a later refresh cannot replace text underneath an active selection.
        if (opened && snapshot == null && metadata != null) snapshot = metadata
    }
    Surface(
        modifier = modifier.widthIn(max = 480.dp).testTag("viewer-prompt-panel"),
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PromptAiBadge(metadata)
                if (settings.showInlinePrompt && !metadata?.candidates.isNullOrEmpty()) {
                    TextButton(onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("viewer-prompt-toggle"),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(if (expanded) "プロンプトを閉じる" else "プロンプトを開く")
                    }
                }
                TextButton(onClick = {
                    snapshot = metadata
                    opened = true
                }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("生成情報") }
            }
            if (settings.showInlinePrompt && expanded) {
                metadata?.candidates?.firstOrNull()?.let { candidate ->
                    Text(
                        text = promptPreview(generationInlineText(candidate)),
                        modifier = Modifier.testTag("viewer-prompt-inline-text"),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }
    }
    if (opened) PromptInfoDialog(snapshot ?: GenerationMetadata(coverage = MetadataCoverage.SOURCE_UNAVAILABLE)) {
        opened = false
        snapshot = null
    }
}

internal class PromptClipboard(
    private val delegate: Clipboard,
    private val gate: MediaFeatureGate,
    private val permit: MediaFeaturePermit,
    private val active: () -> Boolean = { true }
) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = null
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        if (active() && gate.isCurrent(permit)) delegate.setClipEntry(clipEntry)
    }
}

@Composable
private fun PromptCopyButton(value: String, label: String) {
    val gate = LocalMediaFeatureGate.current ?: return
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val permit = remember(gate, LocalMediaFeatureSettings.current) { gate.permit(MediaFeature.PROMPT) } ?: return
    var copied by remember(value) { mutableStateOf(false) }
    var failed by remember(value) { mutableStateOf(false) }
    TextButton(enabled = canCopyPrompt(value), colors = promptTextButtonColors(), onClick = {
        scope.launch {
            if (gate.isCurrent(permit)) {
                try {
                    PromptClipboard(clipboard, gate, permit).setClipEntry(promptClipEntry(value))
                    copied = gate.isCurrent(permit)
                    failed = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            }
        }
    }) { Text(if (failed) "コピーできませんでした" else if (copied) "コピーしました" else label) }
}

@Composable
private fun PromptInfoDialog(metadata: GenerationMetadata, onDismiss: () -> Unit) {
    if (!LocalPromptContentVisible.current || !LocalMediaFeatureSettings.current.promptDisplayEnabled) return
    val gate = LocalMediaFeatureGate.current ?: return
    var candidate by remember(metadata) { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("生成情報") },
        confirmButton = { TextButton(onClick = onDismiss, colors = promptTextButtonColors()) { Text("閉じる") } },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(when (metadata.coverage) {
                    MetadataCoverage.PNG_METADATA -> "PNGのテキスト・EXIF・XMPを確認しました。不可視情報・C2PAには未対応です。"
                    MetadataCoverage.JPEG_METADATA -> "JPEGの画像データより前にあるEXIF・コメント・XMPを確認しました。C2PAには未対応です。"
                    MetadataCoverage.WEBP_METADATA -> "WebPのEXIF・XMPを確認しました。C2PAには未対応です。"
                    MetadataCoverage.MP4_METADATA -> "MP4・MOVのテキストタグとXMPを確認しました。"
                    MetadataCoverage.WEBM_METADATA -> "WebMのテキストタグを確認しました。"
                    MetadataCoverage.UNSUPPORTED -> "この形式の生成情報にはまだ対応していません。"
                    MetadataCoverage.MALFORMED -> "メタデータの一部が壊れているため、確認できた範囲を表示します。"
                    MetadataCoverage.BUDGET_EXCEEDED -> "解析の上限に達したため、確認できた範囲を表示します。"
                    MetadataCoverage.SOURCE_UNAVAILABLE -> "解析済みの原本情報がありません。原本の表示完了後にもう一度開いてください。"
                }, style = MaterialTheme.typography.bodySmall)
                metadata.limitations.forEach { limitation ->
                    Text(when (limitation) {
                        MetadataLimitation.EXTENDED_XMP -> "分割された拡張XMPの読取には未対応です。"
                        MetadataLimitation.EXIF_ENCODING -> "一部のEXIF文字コードには未対応です。"
                        MetadataLimitation.COMPLEX_XMP -> "一部の複雑なXMP構造は本文に変換できません。"
                        MetadataLimitation.C2PA -> "C2PA情報の読取には未対応です。"
                        MetadataLimitation.UNKNOWN_WEBM_CLUSTER -> "長さが未確定の映像区間があり、すべてのタグを確認できていません。"
                    }, style = MaterialTheme.typography.bodySmall)
                }
                if (metadata.candidates.isEmpty()) Text("対応する生成情報は確認できませんでした。AI生成かどうかの判定ではありません。")
                if (metadata.candidates.size > 1) metadata.candidates.forEachIndexed { index, item ->
                    TextButton(onClick = { candidate = index }, colors = promptTextButtonColors()) { Text((if (candidate == index) "✓ " else "") + item.source) }
                }
                metadata.candidates.getOrNull(candidate)?.let { item ->
                    key(metadata, candidate) {
                        val systemClipboard = LocalClipboard.current
                        val permit = remember { gate.permit(MediaFeature.PROMPT) }
                        val alive = remember { mutableStateOf(true) }
                        DisposableEffect(Unit) { onDispose { alive.value = false } }
                        val clipboard = remember(systemClipboard, permit) {
                            permit?.let { PromptClipboard(systemClipboard, gate, it) { alive.value } }
                        }
                        if (clipboard != null) CompositionLocalProvider(LocalClipboard provides clipboard) {
                            Text(item.source, style = MaterialTheme.typography.labelMedium)
                            item.positive?.let { PromptField("プロンプト", it) }
                            item.negative?.let { PromptField("ネガティブ", it) }
                            item.settings?.let { PromptField("生成設定", it) }
                            if (item.positive == null) {
                                Text("本文を確実に分離できなかったため、元の情報を表示します。")
                                PromptField("元の生成情報", item.raw)
                            }
                        }
                    }
                }
            }
        })
}

internal expect fun promptClipEntry(value: String): ClipEntry

/** FutabaClassic uses its surface as 'primary'; default text buttons would be invisible. */
@Composable
private fun promptTextButtonColors() = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

@Composable
private fun PromptField(title: String, value: String) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (canCopyPrompt(value)) {
        if (canSelectWholePrompt(value)) {
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
        } else {
            Text("長い項目のため、表示は先頭部分です。コピーボタンでは全文をコピーできます。")
            Text(promptPreview(value), maxLines = 8, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
        }
        PromptCopyButton(value, "$title をコピー")
    } else {
        Text("この項目はコピー上限（64 KiB）を超えています。以下は先頭部分です。")
        Text(promptPreview(value), maxLines = 8, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}
