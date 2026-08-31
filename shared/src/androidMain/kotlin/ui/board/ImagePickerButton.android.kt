package com.valoser.futacha.shared.ui.board

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.app.Activity
import android.app.AlertDialog
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.compat.ExperienceProfileSessionToken
import com.valoser.futacha.shared.compat.ExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.isExperienceProfileSessionCurrent
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.readImageDataFromUri
import com.valoser.futacha.shared.util.Logger
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COMPAT_PICKER_PREFERENCES = "compat_reference_file_picker"
private const val COMPAT_PICKER_COMPONENT_KEY = "component"

private fun rememberedCompatPickerComponent(context: Context): ComponentName? {
    val flattened = context.getSharedPreferences(COMPAT_PICKER_PREFERENCES, Context.MODE_PRIVATE)
        .getString(COMPAT_PICKER_COMPONENT_KEY, null)
        ?: return null
    val component = ComponentName.unflattenFromString(flattened) ?: return null
    return runCatching {
        context.packageManager.getActivityInfo(component, 0)
        component
    }.getOrElse {
        context.getSharedPreferences(COMPAT_PICKER_PREFERENCES, Context.MODE_PRIVATE)
            .edit().remove(COMPAT_PICKER_COMPONENT_KEY).apply()
        null
    }
}

private fun rememberCompatPickerComponent(context: Context, component: ComponentName) {
    context.getSharedPreferences(COMPAT_PICKER_PREFERENCES, Context.MODE_PRIVATE)
        .edit().putString(COMPAT_PICKER_COMPONENT_KEY, component.flattenToString()).apply()
}

private fun <I> launchPickerOrNotify(
    context: Context,
    launcher: ExperienceProfileActivityResultLauncher<I>,
    input: I,
    logLabel: String,
    userMessage: String
): Boolean = try {
    launcher.launch(input)
    true
} catch (error: Exception) {
    Logger.e("ImagePicker", "Failed to launch $logLabel", error)
    android.widget.Toast.makeText(context, userMessage, android.widget.Toast.LENGTH_LONG).show()
    false
}

@Composable
actual fun rememberAttachmentPickerLauncher(
    preference: AttachmentPickerPreference,
    mimeType: String,
    maxBytes: Long,
    onImageSelected: (ImageData) -> Unit,
    preferredFileManagerPackage: String?,
    onSelectionError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val profileController by rememberUpdatedState(LocalExperienceProfileUiController.current)
    val currentOnSelectionError by rememberUpdatedState(onSelectionError)
    if (context !is Activity) {
        return {
            Logger.w("ImagePicker", "ActivityResultRegistryOwner is unavailable; attachment picker is disabled")
            currentOnSelectionError(ATTACHMENT_LOAD_FAILURE_MESSAGE)
            android.widget.Toast.makeText(
                context,
                "この画面では添付ピッカーを起動できません",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    fun handleImageUri(uri: Uri, session: ExperienceProfileSessionToken) {
        if (isActivityUnavailable(context)) {
            Logger.w("ImagePicker", "Ignoring picker result because Activity is unavailable")
            return
        }
        coroutineScope.launch {
            if (isActivityUnavailable(context)) {
                Logger.w("ImagePicker", "Skipping image read because Activity is unavailable")
                return@launch
            }
            val imageData = withContext(Dispatchers.IO) {
                readImageDataFromUri(context, uri, maxBytes)
            }
            if (!isExperienceProfileSessionCurrent(session, profileController)) {
                Logger.w("ImagePicker", "Dropping decoded image because the experience profile session changed")
                return@launch
            }
            if (imageData != null) {
                onImageSelected(imageData)
            } else {
                currentOnSelectionError(ATTACHMENT_LOAD_FAILURE_MESSAGE)
            }
        }
    }
    val getContentLauncher = rememberExperienceProfileActivityResultLauncher(
        contract = ActivityResultContracts.GetContent()
    ) { uri, session ->
        uri?.let {
            handleImageUri(it, session)
        }
    }
    val openDocumentLauncher = rememberExperienceProfileActivityResultLauncher(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri, session ->
        uri?.let {
            handleImageUri(it, session)
        }
    }
    val packageAwareLauncher = rememberExperienceProfileActivityResultLauncher(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result, session ->
        val uri = result.data?.data ?: return@rememberExperienceProfileActivityResultLauncher
        handleImageUri(uri, session)
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
                        launchPickerOrNotify(
                            context,
                            getContentLauncher,
                            mimeType,
                            "fallback GET_CONTENT picker",
                            "ファイル選択に対応したアプリがありません"
                        )
                    }
                } else {
                    launchPickerOrNotify(
                        context,
                        getContentLauncher,
                        mimeType,
                        "GET_CONTENT picker",
                        "ファイル選択に対応したアプリがありません"
                    )
                }
            }
            AttachmentPickerPreference.COMPAT_REFERENCE_GET_CONTENT -> {
                val rememberedComponent = rememberedCompatPickerComponent(context)
                if (rememberedComponent == null) {
                    launchPickerOrNotify(
                        context,
                        getContentLauncher,
                        mimeType,
                        "compatibility GET_CONTENT picker",
                        "ファイル選択に対応したアプリがありません"
                    )
                } else {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = mimeType
                        addCategory(Intent.CATEGORY_OPENABLE)
                        component = rememberedComponent
                    }
                    try {
                        packageAwareLauncher.launch(intent)
                    } catch (e: Exception) {
                        Logger.e("ImagePicker", "Failed to launch remembered compatibility picker", e)
                        launchPickerOrNotify(
                            context,
                            getContentLauncher,
                            mimeType,
                            "fallback compatibility GET_CONTENT picker",
                            "ファイル選択に対応したアプリがありません"
                        )
                    }
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
                        launchPickerOrNotify(
                            context,
                            openDocumentLauncher,
                            arrayOf(mimeType),
                            "fallback OPEN_DOCUMENT picker",
                            "ファイル選択に対応したアプリがありません"
                        )
                    }
                } else {
                    launchPickerOrNotify(
                        context,
                        openDocumentLauncher,
                        arrayOf(mimeType),
                        "OPEN_DOCUMENT picker",
                        "ファイル選択に対応したアプリがありません"
                    )
                }
            }
            AttachmentPickerPreference.ALWAYS_ASK -> {
                val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = mimeType
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                @Suppress("DEPRECATION")
                val candidates = context.packageManager.queryIntentActivities(
                    getContentIntent,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY or
                        android.content.pm.PackageManager.GET_RESOLVED_FILTER
                ).filterNot { candidate ->
                    candidate.activityInfo.name.contains("Trampoline", ignoreCase = true)
                }.distinctBy { candidate ->
                    candidate.activityInfo.packageName to candidate.activityInfo.name
                }
                if (candidates.isEmpty()) {
                    AlertDialog.Builder(context)
                        .setTitle("ファイル選択アプリを選択")
                        .setMessage("ファイル選択に対応したアプリがありません")
                        .setPositiveButton("閉じる", null)
                        .show()
                } else {
                    val labels = candidates.map { candidate ->
                        candidate.loadLabel(context.packageManager).toString()
                    }.toTypedArray()
                    AlertDialog.Builder(context)
                        .setTitle("ファイル選択アプリを選択")
                        .setItems(labels) { _, selectedIndex ->
                            val selected = candidates[selectedIndex]
                            val component = ComponentName(
                                selected.activityInfo.packageName,
                                selected.activityInfo.name
                            )
                            rememberCompatPickerComponent(context, component)
                            try {
                                packageAwareLauncher.launch(
                                    Intent(getContentIntent).setComponent(component)
                                )
                            } catch (e: Exception) {
                                Logger.e("ImagePicker", "Failed to launch selected GET_CONTENT picker", e)
                                launchPickerOrNotify(
                                    context,
                                    getContentLauncher,
                                    mimeType,
                                    "fallback selected GET_CONTENT picker",
                                    "ファイル選択に対応したアプリがありません"
                                )
                            }
                        }
                        .show()
                }
            }
            AttachmentPickerPreference.LEGACY_GET_CONTENT -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = mimeType }
                try {
                    packageAwareLauncher.launch(intent)
                } catch (e: Exception) {
                    Logger.e("ImagePicker", "Failed to launch legacy GET_CONTENT picker", e)
                    launchPickerOrNotify(
                        context,
                        getContentLauncher,
                        mimeType,
                        "fallback legacy GET_CONTENT picker",
                        "ファイル選択に対応したアプリがありません"
                    )
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
    val profileController by rememberUpdatedState(LocalExperienceProfileUiController.current)
    if (context !is Activity) {
        return {
            Logger.w("DirectoryPicker", "ActivityResultRegistryOwner is unavailable; directory picker is disabled")
            android.widget.Toast.makeText(
                context,
                "この画面ではフォルダ選択を起動できません",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    val customLauncher = rememberExperienceProfileActivityResultLauncher(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result, session ->
        if (isActivityUnavailable(context)) {
            Logger.w("DirectoryPicker", "Ignoring picker result because Activity is unavailable")
            return@rememberExperienceProfileActivityResultLauncher
        }
        val uri = result.data?.data ?: return@rememberExperienceProfileActivityResultLauncher
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
            return@rememberExperienceProfileActivityResultLauncher
        }
        coroutineScope.launch {
            if (isActivityUnavailable(context)) {
                Logger.w("DirectoryPicker", "Skipping URI permission check because Activity is unavailable")
                withContext(Dispatchers.IO) {
                    releasePersistedUriPermission(context, uri, permissionFlags)
                }
                return@launch
            }
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
            if (isActivityUnavailable(context)) {
                Logger.w("DirectoryPicker", "Skipping directory selection callback because Activity is unavailable")
                withContext(Dispatchers.IO) {
                    releasePersistedUriPermission(context, uri, permissionFlags)
                }
                return@launch
            }
            if (!isExperienceProfileSessionCurrent(session, profileController)) {
                Logger.w("DirectoryPicker", "Dropping directory result because the experience profile session changed")
                withContext(Dispatchers.IO) {
                    releasePersistedUriPermission(context, uri, permissionFlags)
                }
                return@launch
            }
            withContext(Dispatchers.IO) {
                releaseStalePersistedUriPermissions(context, keepUri = uri, permissionFlags = permissionFlags)
            }
            if (!isExperienceProfileSessionCurrent(session, profileController)) {
                Logger.w("DirectoryPicker", "Dropping directory result after permission cleanup because the experience profile session changed")
                withContext(Dispatchers.IO) {
                    releasePersistedUriPermission(context, uri, permissionFlags)
                }
                return@launch
            }
            val treeUri = SaveLocation.TreeUri(uri.toString())
            onDirectorySelected(treeUri)
        }
    }

    val defaultLauncher = rememberExperienceProfileActivityResultLauncher(OpenDocumentTree()) { uri, session ->
        if (isActivityUnavailable(context)) {
            Logger.w("DirectoryPicker", "Ignoring default picker result because Activity is unavailable")
            return@rememberExperienceProfileActivityResultLauncher
        }
        if (uri != null) {
            val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    permissionFlags
                )
            } catch (e: Exception) {
                Logger.e("DirectoryPicker", "Failed to persist URI permission for $uri", e)
                return@rememberExperienceProfileActivityResultLauncher
            }
            coroutineScope.launch {
                if (isActivityUnavailable(context)) {
                    Logger.w("DirectoryPicker", "Skipping URI permission check because Activity is unavailable")
                    withContext(Dispatchers.IO) {
                        releasePersistedUriPermission(context, uri, permissionFlags)
                    }
                    return@launch
                }
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
                if (isActivityUnavailable(context)) {
                    Logger.w("DirectoryPicker", "Skipping directory selection callback because Activity is unavailable")
                    withContext(Dispatchers.IO) {
                        releasePersistedUriPermission(context, uri, permissionFlags)
                    }
                    return@launch
                }
                if (!isExperienceProfileSessionCurrent(session, profileController)) {
                    Logger.w("DirectoryPicker", "Dropping default directory result because the experience profile session changed")
                    withContext(Dispatchers.IO) {
                        releasePersistedUriPermission(context, uri, permissionFlags)
                    }
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    releaseStalePersistedUriPermissions(context, keepUri = uri, permissionFlags = permissionFlags)
                }
                if (!isExperienceProfileSessionCurrent(session, profileController)) {
                    Logger.w("DirectoryPicker", "Dropping default directory result after permission cleanup because the experience profile session changed")
                    withContext(Dispatchers.IO) {
                        releasePersistedUriPermission(context, uri, permissionFlags)
                    }
                    return@launch
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
                launchPickerOrNotify(
                    context,
                    defaultLauncher,
                    null,
                    "fallback directory picker",
                    "フォルダ選択に対応したアプリがありません"
                )
            }
        } else {
            launchPickerOrNotify(
                context,
                defaultLauncher,
                null,
                "directory picker",
                "フォルダ選択に対応したアプリがありません"
            )
        }
    }
}

private fun isActivityUnavailable(activity: Activity): Boolean {
    return activity.isFinishing || activity.isDestroyed
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
