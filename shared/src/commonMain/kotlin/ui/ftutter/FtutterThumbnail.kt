package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected
import com.valoser.futacha.shared.ui.image.HighQualityThumbnailMode
import com.valoser.futacha.shared.ui.image.LocalHighQualityThumbnailMode

internal val LocalFtutterPreferences = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** Change only Futaba URLs; URLs outside the service keep their original transport. */
internal fun ftutterNetworkUrl(url: String, https: Boolean): String {
    val parsed = runCatching { io.ktor.http.Url(url) }.getOrNull() ?: return url
    if (parsed.protocol.name !in setOf("http", "https") ||
        !(parsed.host == "2chan.net" || parsed.host.endsWith(".2chan.net"))) return url
    return (if (https) "https://" else "http://") + url.substringAfter("://")
}

internal fun ftutterLoadHighResolution(policy: Int, wifi: Boolean): Boolean = policy == 2 || policy == 1 && wifi

/** Select the requested resolution before creating a request; LOW never silently downloads originals. */
@Composable
internal fun FtutterThumbnail(
    thumbnailUrl: String?, fullImageUrl: String?, targetSizePx: Int, contentDescription: String,
    modifier: Modifier = Modifier, fallbackTint: Color = Color.Gray, quality: Int = 0, catalog: Boolean = false
) {
    val wifi = isCompatWifiConnected(LocalPlatformContext.current)
    val https = LocalFtutterPreferences.current.ftutterBool(FtutterKeys.USE_HTTPS, false)
    val high = ftutterLoadHighResolution(quality, wifi)
    val catalogThumb = thumbnailUrl?.let { if (high) it.replace("/cat/", "/thumb/") else it.replace("/thumb/", "/cat/") }
    val full = (if (catalog) catalogThumb.takeIf { high } else fullImageUrl?.takeIf { it.isNotBlank() && high })
    CompositionLocalProvider(LocalHighQualityThumbnailMode provides HighQualityThumbnailMode.OFF) {
        CatalogPreviewImage((full ?: if (catalog) catalogThumb else thumbnailUrl)?.let { ftutterNetworkUrl(it, https) },
            if (full != null) thumbnailUrl?.let { ftutterNetworkUrl(it, https) } else null,
            targetSizePx, contentDescription, modifier, fallbackTint)
    }
}

@Composable
internal fun rememberFtutterUrlLauncher(preferences: Map<String, String> = LocalFtutterPreferences.current): (String) -> Unit {
    val launch = com.valoser.futacha.shared.util.rememberUrlLauncher(preferences.ftutterBool(FtutterKeys.USE_CUSTOM_TAB, true))
    val https = preferences.ftutterBool(FtutterKeys.USE_HTTPS, false)
    return remember(launch, https) { { url -> launch(ftutterNetworkUrl(url, https)) } }
}
