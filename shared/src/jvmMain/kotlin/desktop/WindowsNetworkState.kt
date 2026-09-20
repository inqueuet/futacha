package com.valoser.futacha.shared.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

private interface Wlan : StdCallLibrary {
    fun WlanOpenHandle(version: Int, reserved: Pointer?, negotiated: IntByReference, handle: PointerByReference): Int
    fun WlanEnumInterfaces(handle: Pointer, reserved: Pointer?, interfaces: PointerByReference): Int
    fun WlanFreeMemory(memory: Pointer)
    fun WlanCloseHandle(handle: Pointer, reserved: Pointer?): Int
}

internal fun windowsWifiConnected(): Boolean {
    val wlan = Native.load("wlanapi", Wlan::class.java)
    val handle = PointerByReference()
    if (wlan.WlanOpenHandle(2, null, IntByReference(), handle) != 0) return false
    try {
        val interfaces = PointerByReference()
        if (wlan.WlanEnumInterfaces(handle.value, null, interfaces) != 0) return false
        try {
            val count = interfaces.value.getInt(0)
            check(count in 0..256)
            // WLAN_INTERFACE_INFO: GUID(16), WCHAR description[256](512), state(4).
            return (0 until count).any { interfaces.value.getInt(8L + it * 532L + 528) == 1 }
        } finally { wlan.WlanFreeMemory(interfaces.value) }
    } finally { wlan.WlanCloseHandle(handle.value, null) }
}
