package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable

@Composable
actual fun FileManagerPickerDialog(
    onDismiss: () -> Unit,
    onFileManagerSelected: (packageName: String, label: String) -> Unit
) = Unit
