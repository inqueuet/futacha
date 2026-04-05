package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.valoser.futacha.shared.model.AppIconVariant
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.clipsToBounds
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
@Suppress("DEPRECATION")
@Composable
internal actual fun AppIconVariantPreview(
    variant: AppIconVariant,
    modifier: Modifier
) {
    val assetName = when (variant) {
        AppIconVariant.Current -> "AppIcon"
        AppIconVariant.Classic -> "AppIconClassic"
        AppIconVariant.Midnight -> "AppIcon"
    }
    val image = UIImage.imageNamed(assetName)
    if (image == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = variant.label,
                style = MaterialTheme.typography.labelMedium
            )
        }
        return
    }
    UIKitView(
        modifier = modifier,
        factory = {
            UIImageView().apply {
                clipsToBounds = true
                this.image = image
            }
        },
        update = { view ->
            view.image = image
        }
    )
}
