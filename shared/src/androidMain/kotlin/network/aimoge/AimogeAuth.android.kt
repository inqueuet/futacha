package com.valoser.futacha.shared.network.aimoge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The あいもげ secrets in an AES-GCM file under noBackupFilesDir, keyed by the Android Keystore (never backed up). */
internal class AndroidAimogeSecretStorage(context: Context, fileName: String = "aimoge-secrets.enc") : AimogeSecretStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
            )
        }.generateKey()
    }

    @Synchronized
    override fun read(): String? {
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully()
        check(bytes.size in 29..(MAX_BYTES + 28))
        return run {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
            }
        }
    }

    @Synchronized
    override fun write(value: String) {
        val plain = value.encodeToByteArray()
        check(plain.size <= MAX_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain)
        val stream = file.startWrite()
        try { stream.write(out); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }

    private companion object {
        const val ALIAS = "futacha.aimoge.secrets.v1"
        const val MAX_BYTES = 262_144
    }
}

/** Installs the app's [AimogeAuth] (Android keeps the waiting approval so a return after a stopped process works). */
fun installAndroidAimogeAuth(context: Context, appVersion: String) {
    if (AimogeAuthRegistry.auth != null) return
    val app = context.applicationContext
    AimogeWriteRegistry.ledgerStorage = AndroidAimogeSecretStorage(app, "aimoge-requests.enc")
    AimogeAuthRegistry.auth = AimogeAuth(
        storage = AndroidAimogeSecretStorage(app),
        api = AimogeAuthApi(createAimogeHttpClient(appVersion)),
        persistPending = true
    )
}

/**
 * Opens the approval page in a browser app, never in futacha itself (our https filter only takes thread
 * addresses, but the page must not depend on that). Returns false when no browser is installed.
 */
fun openAimogeApprovalPage(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    val pm = context.packageManager
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
    @Suppress("DEPRECATION")
    val browsers = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
        .mapNotNull { it.activityInfo?.packageName }
        .filter { it != context.packageName }
        .distinct()
    @Suppress("DEPRECATION")
    val preferred = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val target = aimogeBrowserPackage(browsers, preferred, context.packageName) ?: return false
    val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(target)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}
