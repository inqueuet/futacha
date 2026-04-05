package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun AppIconVariantPreview(
    variant: AppIconVariant,
    modifier: Modifier
) {
    val context = LocalContext.current
    val resourceId = remember(context, variant) {
        context.resources.getIdentifier(
            when (variant) {
                AppIconVariant.Current -> "ic_launcher_current"
                AppIconVariant.Classic -> "ic_launcher_classic"
                AppIconVariant.Midnight -> "ic_launcher_midnight"
            },
            "mipmap",
            context.packageName
        )
    }
    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = resourceId,
        key2 = context.theme
    ) {
        value = withContext(Dispatchers.Default) {
            if (resourceId == 0) {
                null
            } else {
                val drawable = ResourcesCompat.getDrawable(context.resources, resourceId, context.theme)
                if (drawable == null) {
                    Logger.w(
                        "AppIconPreview",
                        "Failed to resolve drawable for app icon variant: $variant (resId=$resourceId)"
                    )
                    null
                } else {
                    drawable
                        .toBitmap(192, 192)
                        .asImageBitmap()
                }
            }
        }
    }
    imageBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = variant.label,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } ?: Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = variant.label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
