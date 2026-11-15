package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.compat.compatInlineLinksCached
import com.valoser.futacha.shared.ui.compat.isCompatApuSmallMediaUrl
import com.valoser.futacha.shared.ui.compat.isCompatImageMediaUrl
import com.valoser.futacha.shared.ui.compat.isCompatVideoMediaUrl
import com.valoser.futacha.shared.ui.compat.compatApuSmallThumbnailUrl
import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected

internal fun ftutterApuImages(html: String, skipQuotes: Boolean = false): List<String> =
    compatInlineLinksCached(if (skipQuotes) ftutterWithoutQuoteLines(html) else html).map { it.url }
        .filter { isCompatApuSmallMediaUrl(it) && isCompatImageMediaUrl(it) }.distinct()

/** レスの中に並べる本文のあぷ・あぷ小：画像に加え動画も（動画はあぷ小のサムネイルを出す。2026-11-12のユーザー指示）。 */
internal fun ftutterApuMedia(html: String, skipQuotes: Boolean = false): List<String> =
    compatInlineLinksCached(if (skipQuotes) ftutterWithoutQuoteLines(html) else html).map { it.url }
        .filter { isCompatApuSmallMediaUrl(it) && (isCompatImageMediaUrl(it) || isCompatVideoMediaUrl(it)) }.distinct()

/** 動画は、あぷ小の作るサムネイル（…s.jpg）を出す。画像はそのまま。 */
internal fun ftutterApuPreviewUrl(url: String): String =
    if (isCompatVideoMediaUrl(url)) compatApuSmallThumbnailUrl(url) ?: url else url

/** M9：原作は引用行（`>fu1234567.jpg`）のあぷ小にはサムネイルを出さない。引用の行を除いた本文。 */
internal fun ftutterWithoutQuoteLines(html: String): String =
    html.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).filterNot { line ->
        val plain = line.replace(Regex("<[^>]*>"), "").replace("&gt;", ">").trimStart()
        plain.startsWith(">") || plain.startsWith("＞")
    }.joinToString("<br>")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FtutterApuPreviews(html: String, settings: FtutterThreadSettings, alpha: Float,
    loadedUrls: MutableSet<String>, legacy: Boolean = false, onOpen: (String) -> Unit) {
    val urls = remember(html, legacy) { ftutterApuMedia(html, skipQuotes = !legacy) }
    if (urls.isEmpty()) return
    val wifi = isCompatWifiConnected(LocalPlatformContext.current)
    val automatic = ftutterLoadHighResolution(settings.apuAutoLoad, wifi)
    // The rows are recreated when they scroll away, so what was loaded is kept by the thread screen (L24).
    val loaded = urls.all { it in loadedUrls }
    if (!automatic && !loaded) {
        Text("あぷ小の画像を読み込む（${urls.size}件）", color = LocalFtutterColors.current.link,
            modifier = Modifier.clickable { loadedUrls.addAll(urls) }.padding(vertical = 8.dp).testTag("ftutter-apu-load"))
    } else if (legacy) urls.forEach { url ->
        Box(Modifier.size(settings.apuThumbSize.dp).clickable { onOpen(url) }.testTag("ftutter-apu-preview")) {
            FtutterThumbnail(ftutterApuPreviewUrl(url), null, settings.apuThumbSize * 3, "あぷ小の画像", Modifier.fillMaxSize().alpha(alpha), quality = 0)
        }
    } else {
        // M9：原作は細い枠の正方形に、画像を切り抜かずに収めて横に並べる。
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp)) {
            urls.forEach { url ->
                Box(
                    Modifier.size(settings.apuThumbSize.dp).border(1.dp, LocalFtutterColors.current.outline)
                        .clickable { onOpen(url) }.padding(1.dp).testTag("ftutter-apu-preview")
                ) {
                    FtutterThumbnail(ftutterApuPreviewUrl(url), null, settings.apuThumbSize * 3,
                        if (isCompatVideoMediaUrl(url)) "あぷ小の動画" else "あぷ小の画像", Modifier.fillMaxSize().alpha(alpha),
                        quality = 0, contentScale = ContentScale.Fit)
                    if (isCompatVideoMediaUrl(url)) FtutterApuVideoMark(Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }
        }
    }
}

/** 動画の印（再生の三角）。 */
@Composable
private fun FtutterApuVideoMark(modifier: Modifier) {
    androidx.compose.material3.Icon(Icons.Rounded.PlayCircleOutline, contentDescription = null,
        tint = androidx.compose.ui.graphics.Color.White, modifier = modifier.size(32.dp).testTag("ftutter-apu-video"))
}
