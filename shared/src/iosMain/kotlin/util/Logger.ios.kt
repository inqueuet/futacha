package com.valoser.futacha.shared.util

import platform.Foundation.NSLog

actual object Logger {
    actual fun d(tag: String, message: String) {
        NSLog("DEBUG [$tag]: $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            NSLog("ERROR [$tag]: $message - ${throwable.message}")
            throwable.printStackTrace()
        } else {
            NSLog("ERROR [$tag]: $message")
        }
    }

    actual fun w(tag: String, message: String) {
        NSLog("WARN [$tag]: $message")
    }

    actual fun i(tag: String, message: String) {
        NSLog("INFO [$tag]: $message")
    }
}
