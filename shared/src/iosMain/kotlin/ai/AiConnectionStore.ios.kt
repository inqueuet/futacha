@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.ai

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Security.*
import platform.Foundation.*

private val connectionStore by lazy { AiConnectionStore(IosAiConnectionStorage()) }
actual fun getAiConnectionStore(platformContext: Any?): AiConnectionStore = connectionStore

internal class IosAiConnectionStorage(private val serviceName: String = "com.valoser.futacha.openai") : AiConnectionStorage {
    private fun <T> query(block: (CFMutableDictionaryRef) -> T): T {
        val dict = checkNotNull(CFDictionaryCreateMutable(null, 0, null, null))
        val service = checkNotNull(CFStringCreateWithCString(null, serviceName, kCFStringEncodingUTF8))
        try {
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, service)
            CFDictionarySetValue(dict, kSecAttrAccount, service)
            CFDictionarySetValue(dict, kSecAttrSynchronizable, kCFBooleanFalse)
            return block(dict)
        } finally { CFRelease(service); CFRelease(dict) }
    }
    private val installMarkerKey get() = "$serviceName.install.v1"

    /** Creation time of the stored item (seconds since 2001-01-01), or null when there is none. */
    private fun itemCreatedAtSeconds(): Double? = query { dict -> memScoped {
        CFDictionarySetValue(dict, kSecReturnAttributes, kCFBooleanTrue)
        CFDictionarySetValue(dict, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        result.value = null
        val status = SecItemCopyMatching(dict, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        val attributes: CFDictionaryRef = checkNotNull(result.value).reinterpret()
        try {
            val date = CFDictionaryGetValue(attributes, kSecAttrCreationDate) ?: return@memScoped null
            CFDateGetAbsoluteTime(date.reinterpret())
        } finally { CFRelease(attributes) }
    } }

    /** When this install's app container was created; it is deleted and recreated with the app. */
    private fun installedAtSeconds(): Double? {
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: return null
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(documents, null) ?: return null
        return (attributes[NSFileCreationDate] as? NSDate)?.timeIntervalSinceReferenceDate
    }

    private var installChecked = false

    /**
     * Once per process: drops a key left in the Keychain by an earlier install (see
     * [isAiKeychainItemFromEarlierInstall]) and records that this install was checked.
     */
    private fun discardKeychainItemFromEarlierInstall() {
        if (installChecked) return
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(installMarkerKey) == null) {
            val stale = try {
                isAiKeychainItemFromEarlierInstall(false, itemCreatedAtSeconds(), installedAtSeconds())
            } catch (_: Throwable) {
                false
            }
            if (stale) {
                val status = query { dict -> SecItemDelete(dict) }
                // Only a deleted (or already gone) item may be recorded as handled.
                if (status != errSecSuccess && status != errSecItemNotFound) return
            }
            defaults.setBool(true, forKey = installMarkerKey)
        }
        installChecked = true
    }

    override fun read(): String? {
        discardKeychainItemFromEarlierInstall()
        return readItem()
    }

    private fun readItem(): String? = query { dict -> memScoped {
        CFDictionarySetValue(dict, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(dict, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        result.value = null
        val status = SecItemCopyMatching(dict, result.ptr)
        if (status == errSecItemNotFound) return@memScoped null
        check(status == errSecSuccess)
        val data: CFDataRef = checkNotNull(result.value).reinterpret()
        try {
            val length = CFDataGetLength(data).toInt()
            check(length in 1..MAX_AI_CREDENTIAL_BYTES)
            checkNotNull(CFDataGetBytePtr(data)).readBytes(length).decodeToString()
        } finally { CFRelease(data) }
    } }
    override fun write(value: String) {
        discardKeychainItemFromEarlierInstall()
        writeItem(value)
    }

    private fun writeItem(value: String) = query { dict ->
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) }!!
        val update = CFDictionaryCreateMutable(null, 0, null, null)!!
        try {
            CFDictionarySetValue(update, kSecValueData, data)
            CFDictionarySetValue(update, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
            val status = SecItemUpdate(dict, update)
            if (status == errSecItemNotFound) {
                CFDictionarySetValue(dict, kSecValueData, data)
                CFDictionarySetValue(dict, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                val addStatus = SecItemAdd(dict, null)
                check(addStatus == errSecSuccess) { "Keychain write status: $addStatus" }
            } else check(status == errSecSuccess) { "Keychain update status: $status" }
        } finally { CFRelease(update); CFRelease(data) }
    }
    override fun readUsage(): String? = NSUserDefaults.standardUserDefaults.stringForKey("$serviceName.usage.v1")
    override fun writeUsage(value: String) { NSUserDefaults.standardUserDefaults.setObject(value, forKey = "$serviceName.usage.v1") }
    private val cachePath: String get() = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String) + "/openai-analysis-v1.json"
    override fun readCache(): String? = NSString.stringWithContentsOfFile(cachePath, NSUTF8StringEncoding, null)
    override fun writeCache(value: String) {
        check(NSString.create(string = value).writeToFile(cachePath, true, NSUTF8StringEncoding, null))
    }
}
