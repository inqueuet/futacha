package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable

@Composable
actual fun FileManagerPickerDialog(
    onDismiss: () -> Unit,
    onFileManagerSelected: (packageName: String, label: String) -> Unit
) {
    // iOS does not support file manager selection
    // This is a no-op on iOS
}
