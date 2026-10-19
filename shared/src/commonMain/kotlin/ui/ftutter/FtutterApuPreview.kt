package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.clickable
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

internal fun ftutterApuImages(html: String): List<String> = compatInlineLinksCached(html).map { it.url }
    .filter { isCompatApuSmallMediaUrl(it) && isCompatImageMediaUrl(it) }.distinct()

@Composable
internal fun FtutterApuPreviews(html: String, settings: FtutterThreadSettings, alpha: Float, onOpen: (String) -> Unit) {
    val urls = remember(html) { ftutterApuImages(html) }
    if (urls.isEmpty()) return
    val wifi = isCompatWifiConnected(LocalPlatformContext.current)
    val automatic = ftutterLoadHighResolution(settings.apuAutoLoad, wifi)
    var loaded by remember(html) { mutableStateOf(false) }
    if (!automatic && !loaded) {
        Text("あぷ小の画像を読み込む（${urls.size}件）", color = LocalFtutterColors.current.link,
            modifier = Modifier.clickable { loaded = true }.padding(vertical = 8.dp).testTag("ftutter-apu-load"))
    } else urls.forEach { url ->
        Box(Modifier.size(settings.apuThumbSize.dp).clickable { onOpen(url) }.testTag("ftutter-apu-preview")) {
            FtutterThumbnail(url, null, settings.apuThumbSize * 3, "あぷ小の画像", Modifier.fillMaxSize().alpha(alpha), quality = 0)
        }
    }
}
