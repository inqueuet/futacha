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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.readImageDataFromUri
import com.valoser.futacha.shared.util.Logger
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberAttachmentPickerLauncher(
    preference: AttachmentPickerPreference,
    mimeType: String,
    onImageSelected: (ImageData) -> Unit,
    preferredFileManagerPackage: String?
): () -> Unit {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    fun handleImageUri(uri: Uri) {
        coroutineScope.launch {
            val imageData = withContext(Dispatchers.IO) {
                readImageDataFromUri(context, uri)
            }
            imageData?.let(onImageSelected)
        }
    }
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            handleImageUri(it)
        }
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            handleImageUri(it)
        }
    }
    val chooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        handleImageUri(uri)
    }
    val packageAwareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        handleImageUri(uri)
    }

    return {
        when (preference) {
            AttachmentPickerPreference.MEDIA -> {
                val preferredPackage = preferredFileManagerPackage
                if (preferredPackage != null) {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = mimeType
                        addCategory(Intent.CATEGORY_OPENABLE)
                        setPackage(preferredPackage)
                    }
                    try {
                        packageAwareLauncher.launch(intent)
                    } catch (e: Exception) {
                        Logger.e("ImagePicker", "Failed to launch preferred file manager for GET_CONTENT", e)
                        getContentLauncher.launch(mimeType)
                    }
                } else {
                    getContentLauncher.launch(mimeType)
                }
            }
            AttachmentPickerPreference.DOCUMENT -> {
                val preferredPackage = preferredFileManagerPackage
                if (preferredPackage != null) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mimeType
                        setPackage(preferredPackage)
                    }
                    try {
                        packageAwareLauncher.launch(intent)
                    } catch (e: Exception) {
                        Logger.e("ImagePicker", "Failed to launch preferred file manager for OPEN_DOCUMENT", e)
                        openDocumentLauncher.launch(arrayOf(mimeType))
                    }
                } else {
                    openDocumentLauncher.launch(arrayOf(mimeType))
                }
            }
            AttachmentPickerPreference.ALWAYS_ASK -> {
                val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = mimeType
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                val openDocumentIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                }
                val preferredIntent = preferredFileManagerPackage?.let { pkg ->
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mimeType
                        setPackage(pkg)
                    }
                }
                val initialIntents = buildList {
                    preferredIntent?.let { add(it) }
                    add(openDocumentIntent)
                }.toTypedArray()
                val chooser = Intent.createChooser(getContentIntent, null).apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents)
                }
                try {
                    chooserLauncher.launch(chooser)
                } catch (e: Exception) {
                    Logger.e("ImagePicker", "Failed to launch chooser with preferred file manager", e)
                    getContentLauncher.launch(mimeType)
                }
            }
        }
    }
}

@Composable
actual fun ImagePickerButton(
    onImageSelected: (ImageData) -> Unit,
    preference: AttachmentPickerPreference,
    preferredFileManagerPackage: String?
) {
    val launchPicker = rememberAttachmentPickerLauncher(
        preference = preference,
        onImageSelected = onImageSelected,
        preferredFileManagerPackage = preferredFileManagerPackage
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
    val coroutineScope = rememberCoroutineScope()

    val customLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                permissionFlags
            )
        } catch (e: Exception) {
            Logger.e("DirectoryPicker", "Failed to persist URI permission for $uri", e)
            // FIX: ユーザーにエラーフィードバックを表示
            android.widget.Toast.makeText(
                context,
                "フォルダへのアクセス権限を保存できませんでした",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val canWrite = withContext(Dispatchers.IO) {
                canWriteToDocumentTree(context, uri)
            }
            if (!canWrite) {
                Logger.w("DirectoryPicker", "Cannot write to selected URI: $uri")
                withContext(Dispatchers.IO) {
                    releasePersistedUriPermission(context, uri, permissionFlags)
                }
                android.widget.Toast.makeText(
                    context,
                    "選択したフォルダに書き込み権限がありません",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                releaseStalePersistedUriPermissions(context, keepUri = uri, permissionFlags = permissionFlags)
            }
            val treeUri = SaveLocation.TreeUri(uri.toString())
            onDirectorySelected(treeUri)
        }
    }

    val defaultLauncher = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
        if (uri != null) {
            val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    permissionFlags
                )
            } catch (e: Exception) {
                Logger.e("DirectoryPicker", "Failed to persist URI permission for $uri", e)
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val canWrite = withContext(Dispatchers.IO) {
                    canWriteToDocumentTree(context, uri)
                }
                if (!canWrite) {
                    Logger.w("DirectoryPicker", "Cannot write to selected URI: $uri")
                    withContext(Dispatchers.IO) {
                        releasePersistedUriPermission(context, uri, permissionFlags)
                    }
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    releaseStalePersistedUriPermissions(context, keepUri = uri, permissionFlags = permissionFlags)
                }
                val treeUri = SaveLocation.TreeUri(uri.toString())
                onDirectorySelected(treeUri)
            }
        }
    }

    return {
        if (preferredFileManagerPackage != null) {
            // 端末によっては OPEN_DOCUMENT_TREE を持たず、OPEN_DOCUMENT でディレクトリを返すファイラーもあるため二段階で試す
            val intents = listOf(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setPackage(preferredFileManagerPackage)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                },
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    setPackage(preferredFileManagerPackage)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            )
            var launched = false
            intents.forEach { intent ->
                if (launched) return@forEach
                try {
                    customLauncher.launch(intent)
                    launched = true
                } catch (e: Exception) {
                    Logger.e("DirectoryPicker", "Failed to launch preferred file manager: $preferredFileManagerPackage with ${intent.action}", e)
                }
            }
            if (!launched) {
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
        val output = context.contentResolver.openOutputStream(probe.uri)
        if (output == null) {
            probe.delete()
            Logger.w("DirectoryPicker", "Failed to open output stream for DocumentTree probe: $treeUri")
            return false
        }
        output.use {
            it.write("ok".toByteArray())
            it.flush()
        }
        // FIX: テストファイル削除の結果を確認してログに記録
        val deleted = probe.delete()
        if (!deleted) {
            Logger.w("DirectoryPicker", "Failed to delete test file from DocumentTree $treeUri")
        }
        true
    } catch (e: Exception) {
        Logger.e("DirectoryPicker", "Failed to write test file to DocumentTree $treeUri", e)
        false
    }
}

private fun releasePersistedUriPermission(
    context: android.content.Context,
    uri: android.net.Uri,
    permissionFlags: Int
) {
    runCatching {
        context.contentResolver.releasePersistableUriPermission(uri, permissionFlags)
    }.onFailure { e ->
        Logger.w("DirectoryPicker", "Failed to release persisted URI permission for $uri: ${e.message}")
    }
}

private fun releaseStalePersistedUriPermissions(
    context: android.content.Context,
    keepUri: android.net.Uri,
    permissionFlags: Int
) {
    val keep = keepUri.toString()
    context.contentResolver.persistedUriPermissions
        .mapNotNull { it.uri }
        .filter { it.toString() != keep }
        .forEach { staleUri ->
            releasePersistedUriPermission(context, staleUri, permissionFlags)
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
        // FIX: テストファイル削除の結果を確認してログに記録
        val deleted = probe.delete()
        if (!deleted && probe.exists()) {
            Logger.w("DirectoryPicker", "Failed to delete test file from $directoryPath")
        }
        deleted || !probe.exists()
    } catch (e: Exception) {
        Logger.e("DirectoryPicker", "Failed to write test file to $directoryPath", e)
        false
    }
}
