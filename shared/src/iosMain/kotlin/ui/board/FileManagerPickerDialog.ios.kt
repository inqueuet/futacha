package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.FileProvider.NSFileProviderDomain
import platform.FileProvider.NSFileProviderManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
actual fun FileManagerPickerDialog(
    onDismiss: () -> Unit,
    onFileManagerSelected: (packageName: String, label: String) -> Unit
) {
    val options by produceState<List<IosFileManagerOption>>(
        initialValue = listOf(defaultFilesOption())
    ) {
        value = withContext(Dispatchers.Default) {
            fetchFileProviderOptions().ifEmpty { listOf(defaultFilesOption()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ファイラーを選択") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "iOS では Files アプリのプロバイダを経由して保存先を選択します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                options.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onFileManagerSelected(option.packageName, option.label)
                                onDismiss()
                            },
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

private suspend fun fetchFileProviderOptions(): List<IosFileManagerOption> = suspendCoroutine { continuation ->
    NSFileProviderManager.getDomainsWithCompletionHandler { domains, error ->
        if (error != null) {
            Logger.w("FileManagerPicker", "Failed to load file providers: ${error.localizedDescription}")
            continuation.resume(emptyList())
            return@getDomainsWithCompletionHandler
        }
        val resolved = domains
            ?.filterIsInstance<NSFileProviderDomain>()
            ?.map { domain ->
                IosFileManagerOption(
                    packageName = domain.identifier,
                    label = domain.displayName ?: domain.identifier
                )
            }
            ?.sortedBy { it.label.lowercase() }
            .orEmpty()
        continuation.resume(resolved)
    }
}

private fun defaultFilesOption(): IosFileManagerOption {
    return IosFileManagerOption(
        packageName = "com.apple.DocumentsApp",
        label = "Files"
    )
}

private data class IosFileManagerOption(
    val packageName: String,
    val label: String
)
