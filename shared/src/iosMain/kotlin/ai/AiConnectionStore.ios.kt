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
    override fun read(): String? = query { dict -> memScoped {
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
            check(length in 1..8192)
            checkNotNull(CFDataGetBytePtr(data)).readBytes(length).decodeToString()
        } finally { CFRelease(data) }
    } }
    override fun write(value: String) = query { dict ->
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
    private val cachePath: String get() = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String) + "/openai-analysis-v1.json"
    override fun readCache(): String? = NSString.stringWithContentsOfFile(cachePath, NSUTF8StringEncoding, null)
    override fun writeCache(value: String) {
        check(NSString.create(string = value).writeToFile(cachePath, true, NSUTF8StringEncoding, null))
    }
}
