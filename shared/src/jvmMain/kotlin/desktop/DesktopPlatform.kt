package com.valoser.futacha.shared.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary

object DesktopPlatform {
    val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", true)
    val isMac: Boolean = System.getProperty("os.name").startsWith("Mac", true)
    val resourceDirectory: String get() = if (isWindows) "windows" else "macos"
    val displayName: String get() = if (isWindows) "Windows" else "macOS"

    internal fun setEnvironment(name: String, value: String) {
        if (isWindows) {
            check(Native.load("kernel32", WindowsEnvironment::class.java)
                .SetEnvironmentVariableW(WString(name), WString(value))) { "環境を初期化できません: $name" }
            // DLLs can use either the universal CRT or the older CRT (VLC).
            check(Native.load("ucrtbase", UniversalEnvironment::class.java)._putenv_s(name, value) == 0)
            check(Native.load("msvcrt", LegacyEnvironment::class.java)._putenv("$name=$value") == 0)
        } else {
            check(Native.load("c", PosixEnvironment::class.java).setenv(name, value, 1) == 0)
        }
    }
}

private interface WindowsEnvironment : StdCallLibrary {
    fun SetEnvironmentVariableW(name: WString, value: WString): Boolean
}
private interface UniversalEnvironment : Library { fun _putenv_s(name: String, value: String): Int }
private interface LegacyEnvironment : Library { fun _putenv(value: String): Int }
private interface PosixEnvironment : Library { fun setenv(name: String, value: String, overwrite: Int): Int }
