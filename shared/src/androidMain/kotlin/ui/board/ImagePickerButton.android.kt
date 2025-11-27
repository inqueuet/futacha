package com.valoser.futacha.shared.ui.board

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.readImageDataFromUri
import com.valoser.futacha.shared.util.Logger
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberAttachmentPickerLauncher(
    preference: AttachmentPickerPreference,
    mimeType: String,
    onImageSelected: (ImageData) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val imageData = readImageDataFromUri(context, it)
            imageData?.let(onImageSelected)
        }
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val imageData = readImageDataFromUri(context, it)
            imageData?.let(onImageSelected)
        }
    }
    val chooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val imageData = readImageDataFromUri(context, uri)
        imageData?.let(onImageSelected)
    }

    return {
        when (preference) {
            AttachmentPickerPreference.MEDIA -> getContentLauncher.launch(mimeType)
            AttachmentPickerPreference.DOCUMENT -> openDocumentLauncher.launch(arrayOf(mimeType))
            AttachmentPickerPreference.ALWAYS_ASK -> {
                val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = mimeType
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                val openDocumentIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                }
                val chooser = Intent.createChooser(getContentIntent, null).apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(openDocumentIntent))
                }
                chooserLauncher.launch(chooser)
            }
        }
    }
}

@Composable
actual fun ImagePickerButton(
    onImageSelected: (ImageData) -> Unit,
    preference: AttachmentPickerPreference
) {
    val launchPicker = rememberAttachmentPickerLauncher(
        preference = preference,
        onImageSelected = onImageSelected
    )

    Button(
        onClick = { launchPicker() }
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("画像を選択")
    }
}

@Composable
actual fun rememberDirectoryPickerLauncher(
    onDirectorySelected: (SaveLocation) -> Unit,
    preferredFileManagerPackage: String?
): () -> Unit {
    val context = LocalContext.current

    val customLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            Logger.e("DirectoryPicker", "Failed to persist URI permission for $uri", e)
            return@rememberLauncherForActivityResult
        }

        // 書き込みテスト
        if (!canWriteToDocumentTree(context, uri)) {
            Logger.w("DirectoryPicker", "Cannot write to selected URI: $uri")
            return@rememberLauncherForActivityResult
        }

        // TreeUri として SaveLocation に変換
        val treeUri = SaveLocation.TreeUri(uri.toString())
        onDirectorySelected(treeUri)
    }

    val defaultLauncher = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Logger.e("DirectoryPicker", "Failed to persist URI permission for $uri", e)
                return@rememberLauncherForActivityResult
            }

            // 書き込みテスト
            if (!canWriteToDocumentTree(context, uri)) {
                Logger.w("DirectoryPicker", "Cannot write to selected URI: $uri")
                return@rememberLauncherForActivityResult
            }

            // TreeUri として SaveLocation に変換
            val treeUri = SaveLocation.TreeUri(uri.toString())
            onDirectorySelected(treeUri)
        }
    }

    return {
        if (preferredFileManagerPackage != null) {
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    setPackage(preferredFileManagerPackage)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                customLauncher.launch(intent)
            } catch (e: Exception) {
                Logger.e("DirectoryPicker", "Failed to launch preferred file manager: $preferredFileManagerPackage", e)
                // Fallback to default launcher
                defaultLauncher.launch(null)
            }
        } else {
            defaultLauncher.launch(null)
        }
    }
}

/**
 * DocumentTree URI に書き込み可能かテスト
 */
private fun canWriteToDocumentTree(context: android.content.Context, treeUri: android.net.Uri): Boolean {
    return try {
        val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val probe = docFile.createFile("text/plain", ".futacha_write_probe") ?: return false
        context.contentResolver.openOutputStream(probe.uri)?.use { it.write("ok".toByteArray()) }
        probe.delete()
        true
    } catch (e: Exception) {
        Logger.e("DirectoryPicker", "Failed to write test file to DocumentTree $treeUri", e)
        false
    }
}

private fun resolveDocumentTreeToPath(uri: android.net.Uri): String? {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val parts = docId.split(":")
    if (parts.isEmpty()) return null
    val volume = parts[0]
    val relativePath = parts.getOrNull(1).orEmpty()
    return if (volume.equals("primary", ignoreCase = true)) {
        val base = Environment.getExternalStorageDirectory()?.absolutePath ?: return null
        File(base, relativePath).absolutePath
    } else {
        null
    }
}

private fun canWriteTestFile(directoryPath: String): Boolean {
    return try {
        val dir = File(directoryPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val probe = File(dir, ".futacha_write_probe")
        FileOutputStream(probe).use { it.write("ok".toByteArray()) }
        val deleted = probe.delete()
        deleted || !probe.exists()
    } catch (e: Exception) {
        Logger.e("DirectoryPicker", "Failed to write test file to $directoryPath", e)
        false
    }
}
