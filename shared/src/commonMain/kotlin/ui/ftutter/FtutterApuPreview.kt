package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.compat.compatInlineLinksCached
import com.valoser.futacha.shared.ui.compat.isCompatApuSmallMediaUrl
import com.valoser.futacha.shared.ui.compat.isCompatImageMediaUrl
import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected

internal fun ftutterApuImages(html: String, skipQuotes: Boolean = false): List<String> =
    compatInlineLinksCached(if (skipQuotes) ftutterWithoutQuoteLines(html) else html).map { it.url }
        .filter { isCompatApuSmallMediaUrl(it) && isCompatImageMediaUrl(it) }.distinct()

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
    val urls = remember(html, legacy) { ftutterApuImages(html, skipQuotes = !legacy) }
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
            FtutterThumbnail(url, null, settings.apuThumbSize * 3, "あぷ小の画像", Modifier.fillMaxSize().alpha(alpha), quality = 0)
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
                    FtutterThumbnail(url, null, settings.apuThumbSize * 3, "あぷ小の画像", Modifier.fillMaxSize().alpha(alpha),
                        quality = 0, contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
