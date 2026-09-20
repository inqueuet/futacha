package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.valoser.futacha.shared.desktop.desktopResource
import com.valoser.futacha.shared.model.AppIconVariant
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal actual fun AppIconVariantPreview(variant: AppIconVariant, modifier: Modifier) {
    val bitmap = remember(variant) {
        runCatching { SkiaImage.makeFromEncoded(desktopResource("icons/${variant.name}.png").readBytes()).toComposeImageBitmap() }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, variant.label, modifier, contentScale = ContentScale.Fit)
    else Text(variant.label, modifier)
}
