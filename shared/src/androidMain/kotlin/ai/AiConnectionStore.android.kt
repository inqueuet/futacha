package com.valoser.futacha.shared.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private var connectionStore: AiConnectionStore? = null

@Synchronized
actual fun getAiConnectionStore(platformContext: Any?): AiConnectionStore = connectionStore
    ?: AiConnectionStore(AndroidAiConnectionStorage((platformContext as Context).applicationContext)).also { connectionStore = it }

internal class AndroidAiConnectionStorage(context: Context) : AiConnectionStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "openai-connection.enc"))
    private val cacheFile = AtomicFile(File(context.cacheDir, "openai-analysis-v1.json"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(): String? {
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully()
        check(bytes.size in 29..8192)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
        }
    }
    override fun write(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        writeAtomic(file, cipher.iv + cipher.doFinal(value.encodeToByteArray()))
    }
    override fun readCache(): String? = if (cacheFile.baseFile.exists() && cacheFile.baseFile.length() <= 4_000_000) cacheFile.readFully().decodeToString() else null
    override fun writeCache(value: String) = writeAtomic(cacheFile, value.encodeToByteArray())
    private fun writeAtomic(target: AtomicFile, value: ByteArray) {
        val stream = target.startWrite()
        try { stream.write(value); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
    }
    private companion object { const val ALIAS = "futacha.openai.credentials.v1" }
}
