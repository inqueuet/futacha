package com.valoser.futacha.shared.media

import kotlin.coroutines.cancellation.CancellationException

internal const val MEDIA_EDITOR_OUT_OF_MEMORY_MESSAGE =
    "メモリが不足したため処理できませんでした。ほかのアプリを終了するか、小さいファイルを選んでください"
internal const val MEDIA_EDITOR_NATIVE_LIBRARY_MESSAGE =
    "編集用のライブラリを読み込めませんでした。アプリを再インストールしてください"

/**
 * The editors' error text for [failure] (B-13). Allocation failures and native-library link
 * failures are Errors, which `catch (Exception)` let through and crash the app; they are shown
 * like any other failure instead. Cancellation and every other Error are rethrown unchanged.
 */
internal fun mediaEditorFailureMessage(failure: Throwable, fallback: String): String = when {
    failure is CancellationException -> throw failure
    failure is Exception -> failure.message ?: fallback
    failure.isMemoryExhaustion() -> MEDIA_EDITOR_OUT_OF_MEMORY_MESSAGE
    failure.isNativeLinkFailure() -> MEDIA_EDITOR_NATIVE_LIBRARY_MESSAGE
    else -> throw failure
}

/** OutOfMemoryError where the platform can recover from it (JVM/Android). */
internal expect fun Throwable.isMemoryExhaustion(): Boolean

/** A missing or unloadable native library: UnsatisfiedLinkError and the other LinkageErrors. */
internal expect fun Throwable.isNativeLinkFailure(): Boolean
