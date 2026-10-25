package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected
import com.valoser.futacha.shared.ui.compat.isCompatVideoMediaUrl
import com.valoser.futacha.shared.ui.image.HighQualityThumbnailMode
import com.valoser.futacha.shared.ui.image.LocalHighQualityThumbnailMode

// Only URL launchers consume this preference. Track those reads without invalidating every reply on any setting write.
internal val LocalFtutterUseCustomTab = compositionLocalOf { true }

/** Futaba always uses HTTPS, including legacy/imported useHttps=false settings. */
internal fun ftutterNetworkUrl(url: String): String {
    val parsed = runCatching { io.ktor.http.Url(url) }.getOrNull() ?: return url
    if (parsed.protocol.name !in setOf("http", "https") ||
        !(parsed.host == "2chan.net" || parsed.host.endsWith(".2chan.net"))) return url
    return "https://" + url.substringAfter("://")
}

internal fun ftutterLoadHighResolution(policy: Int, wifi: Boolean): Boolean = policy == 2 || policy == 1 && wifi

internal data class FtutterThumbnailSources(val primary: String?, val fallback: String?)

internal fun ftutterThumbnailSources(
    thumbnailUrl: String?, fullImageUrl: String?, quality: Int, wifi: Boolean, catalog: Boolean = false
): FtutterThumbnailSources {
    val high = ftutterLoadHighResolution(quality, wifi)
    val thumbnail = thumbnailUrl?.takeUnless(::isCompatVideoMediaUrl)
    val catalogThumb = thumbnail?.let { if (high) it.replace("/cat/", "/thumb/") else it.replace("/thumb/", "/cat/") }
    val full = if (catalog) catalogThumb.takeIf { high }
        else fullImageUrl?.takeIf { it.isNotBlank() && high && !isCompatVideoMediaUrl(it) }
    return FtutterThumbnailSources(
        (full ?: if (catalog) catalogThumb else thumbnail)?.let(::ftutterNetworkUrl),
        thumbnail?.takeIf { full != null && it != full }?.let(::ftutterNetworkUrl)
    )
}

/** Select the requested resolution before creating a request; LOW never silently downloads originals. */
@Composable
internal fun FtutterThumbnail(
    thumbnailUrl: String?, fullImageUrl: String?, targetSizePx: Int, contentDescription: String,
    modifier: Modifier = Modifier, fallbackTint: Color = Color.Gray, quality: Int = 0, catalog: Boolean = false
) {
    val wifi = isCompatWifiConnected(LocalPlatformContext.current)
    val sources = ftutterThumbnailSources(thumbnailUrl, fullImageUrl, quality, wifi, catalog)
    CompositionLocalProvider(LocalHighQualityThumbnailMode provides HighQualityThumbnailMode.OFF) {
        CatalogPreviewImage(sources.primary, sources.fallback,
            targetSizePx, contentDescription, modifier, fallbackTint,
            allowThumbnailFallbackOnError = sources.fallback != null)
    }
}

@Composable
internal fun rememberFtutterUrlLauncher(preferences: Map<String, String>? = null): (String) -> Unit {
    val useCustomTab = preferences?.ftutterBool(FtutterKeys.USE_CUSTOM_TAB, true) ?: LocalFtutterUseCustomTab.current
    val launch = com.valoser.futacha.shared.util.rememberUrlLauncher(useCustomTab)
    return remember(launch) { { url -> launch(ftutterNetworkUrl(url)) } }
}
