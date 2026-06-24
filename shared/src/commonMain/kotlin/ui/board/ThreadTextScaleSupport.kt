package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.ThreadBodyTextSize

private val LocalAppliedThreadTextSize = staticCompositionLocalOf { ThreadBodyTextSize.Standard }

@Composable
internal fun ProvideThreadTextSizeTypography(
    bodyTextSize: ThreadBodyTextSize,
    content: @Composable () -> Unit
) {
    if (LocalAppliedThreadTextSize.current == bodyTextSize) {
        content()
        return
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography.withThreadTextSize(bodyTextSize),
        shapes = MaterialTheme.shapes
    ) {
        CompositionLocalProvider(LocalAppliedThreadTextSize provides bodyTextSize) {
            content()
        }
    }
}

private fun Typography.withThreadTextSize(bodyTextSize: ThreadBodyTextSize): Typography {
    return copy(
        displayLarge = displayLarge.withThreadTextSize(bodyTextSize, 57.sp, 64.sp),
        displayMedium = displayMedium.withThreadTextSize(bodyTextSize, 45.sp, 52.sp),
        displaySmall = displaySmall.withThreadTextSize(bodyTextSize, 36.sp, 44.sp),
        headlineLarge = headlineLarge.withThreadTextSize(bodyTextSize, 32.sp, 40.sp),
        headlineMedium = headlineMedium.withThreadTextSize(bodyTextSize, 28.sp, 36.sp),
        headlineSmall = headlineSmall.withThreadTextSize(bodyTextSize, 24.sp, 32.sp),
        titleLarge = titleLarge.withThreadTextSize(bodyTextSize, 22.sp, 28.sp),
        titleMedium = titleMedium.withThreadTextSize(bodyTextSize, 16.sp, 24.sp),
        titleSmall = titleSmall.withThreadTextSize(bodyTextSize, 14.sp, 20.sp),
        bodyLarge = bodyLarge.withThreadTextSize(bodyTextSize, 16.sp, 24.sp),
        bodyMedium = bodyMedium.withThreadTextSize(bodyTextSize, 14.sp, 20.sp),
        bodySmall = bodySmall.withThreadTextSize(bodyTextSize, 12.sp, 16.sp),
        labelLarge = labelLarge.withThreadTextSize(bodyTextSize, 14.sp, 20.sp),
        labelMedium = labelMedium.withThreadTextSize(bodyTextSize, 12.sp, 16.sp),
        labelSmall = labelSmall.withThreadTextSize(bodyTextSize, 11.sp, 16.sp)
    )
}
