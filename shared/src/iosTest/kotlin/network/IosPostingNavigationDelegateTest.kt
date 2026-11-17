@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.valoser.futacha.shared.network

import kotlinx.coroutines.CompletableDeferred
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSSelectorFromString
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * L76: a page that fails after it was committed, or a web content process that ends, used to leave the
 * preparation waiting for the whole timeout ("時間内に完了しなかった"). WebKit calls these only when the delegate
 * answers to their selectors; the wait has to end at once with the real reason, and nothing is sent.
 * No page is loaded here: the WebKit child process is not needed.
 */
class IosPostingNavigationDelegateTest {
    private fun webView() = WKWebView(CGRectMake(0.0, 0.0, 1.0, 1.0), WKWebViewConfiguration())

    @Test
    fun aLoadFailureAfterTheCommitEndsThePreparationAtOnce() {
        val result = CompletableDeferred<String>()
        val delegate = IosPostingNavigationDelegate("'unused'", result)
        assertTrue(delegate.respondsToSelector(NSSelectorFromString("webView:didFailNavigation:withError:")))

        delegate.webView(webView(), didFailNavigation = null, withError = NSError.errorWithDomain("test", 1, null))

        val failure = assertIs<NetworkException>(result.getCompletionExceptionOrNull())
        assertEquals("投稿準備画面を読み込めませんでした", failure.message)
    }

    @Test
    fun anEndedWebContentProcessEndsThePreparationAtOnce() {
        val result = CompletableDeferred<String>()
        val delegate = IosPostingNavigationDelegate("'unused'", result)
        assertTrue(delegate.respondsToSelector(NSSelectorFromString("webViewWebContentProcessDidTerminate:")))

        delegate.webViewWebContentProcessDidTerminate(webView())

        val failure = assertIs<NetworkException>(result.getCompletionExceptionOrNull())
        assertEquals("投稿の準備中にブラウザー部品が停止したため送信していません", failure.message)
    }

    @Test
    fun aLoadFailureBeforeTheCommitStillEndsThePreparation() {
        val result = CompletableDeferred<String>()
        val delegate = IosPostingNavigationDelegate("'unused'", result)
        assertTrue(delegate.respondsToSelector(NSSelectorFromString("webView:didFailProvisionalNavigation:withError:")))

        delegate.webView(webView(), didFailProvisionalNavigation = null, withError = NSError.errorWithDomain("test", 1, null))

        val failure = assertIs<NetworkException>(result.getCompletionExceptionOrNull())
        assertEquals("投稿準備画面を読み込めませんでした", failure.message)
    }
}
