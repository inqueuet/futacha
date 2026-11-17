@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package com.valoser.futacha.shared.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.util.findIosRootViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIView
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject
import kotlinx.cinterop.ObjCAction

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    iosEdgeGestureEnabled: Boolean,
    onBack: () -> Unit
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = remember { IosBackHandlerEntry { currentOnBack() } }

    // Registered once, in composition order, like androidx BackHandler; the
    // enabled flags change without moving the entry in the stack.
    DisposableEffect(entry) {
        IosBackGestureRegistry.register(entry)
        onDispose { IosBackGestureRegistry.unregister(entry) }
    }
    SideEffect {
        entry.enabled = enabled
        entry.edgeGestureEnabled = iosEdgeGestureEnabled
        IosBackGestureRegistry.refresh()
    }
}

internal class IosBackHandlerEntry(val onBack: () -> Unit) {
    var enabled: Boolean = false
    var edgeGestureEnabled: Boolean = true
}

/**
 * One left-edge recognizer for every PlatformBackHandler, mirroring Android's
 * OnBackPressedDispatcher: the most recently registered enabled handler gets
 * the gesture. Separate recognizers per handler let UIKit pick an arbitrary
 * winner, e.g. the catalog's NavigateBack beneath an open settings overlay.
 * A topmost handler that opted out of the edge gesture disables it, leaving
 * the edge to the screen (for example a drawer swipe).
 *
 * The recognizer lives on the key window's root view (not the topmost
 * presented controller), so a picker, share sheet or saved-HTML sheet neither
 * steals it nor leaves it on a view that disappears with the modal. It is
 * re-attached when the window changes or the app becomes active again, and a
 * swipe inside a presented modal is never treated as back for the screen behind.
 *
 * Composition and UIKit gesture callbacks both run on the main thread.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosBackGestureRegistry {
    private val entries = mutableListOf<IosBackHandlerEntry>()
    private val target = BackGestureTarget(
        isModalPresented = { findIosRootViewController()?.presentedViewController != null }
    ) { dispatch() }
    private var gesture: UIScreenEdgePanGestureRecognizer? = null
    private var attachedView: UIView? = null
    private var activeObserver: Any? = null
    internal var rootViewProvider: () -> UIView? = { findIosRootViewController()?.view }

    fun register(entry: IosBackHandlerEntry) {
        entries.remove(entry)
        entries += entry
        observeActivation()
        refresh()
    }

    fun unregister(entry: IosBackHandlerEntry) {
        entries.remove(entry)
        refresh()
    }

    private fun observeActivation() {
        if (activeObserver != null) return
        activeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null
        ) { refresh() }
    }

    private fun stopObservingActivation() {
        val observer = activeObserver ?: return
        NSNotificationCenter.defaultCenter.removeObserver(observer)
        activeObserver = null
    }

    /** The handler Android would invoke for a Back event. */
    internal fun activeEntry(): IosBackHandlerEntry? = entries.lastOrNull { it.enabled }

    internal fun edgeGestureWanted(): Boolean = activeEntry()?.edgeGestureEnabled == true

    fun refresh() {
        if (entries.isEmpty()) {
            detach()
            stopObservingActivation()
            return
        }
        // The window that held the recognizer can go away (scene reconnect,
        // root view controller swap); a detached view would never deliver edges.
        if (attachedView != null && attachedView?.window == null) detach()
        if (!edgeGestureWanted()) {
            gesture?.enabled = false
            return
        }
        val view = rootViewProvider() ?: return
        val recognizer = gesture ?: UIScreenEdgePanGestureRecognizer(
            target = target,
            action = NSSelectorFromString("handleBackGesture:")
        ).apply {
            edges = UIRectEdgeLeft
        }.also { gesture = it }
        if (attachedView !== view) {
            attachedView?.removeGestureRecognizer(recognizer)
            view.addGestureRecognizer(recognizer)
            attachedView = view
        }
        recognizer.enabled = true
    }

    internal fun dispatch() {
        val active = activeEntry() ?: return
        if (active.edgeGestureEnabled) active.onBack()
    }

    private fun detach() {
        val recognizer = gesture ?: return
        attachedView?.removeGestureRecognizer(recognizer)
        attachedView = null
        recognizer.enabled = false
    }
}

private class BackGestureTarget(
    private val isModalPresented: () -> Boolean,
    private val onEnded: () -> Unit
) : NSObject() {
    @ObjCAction
    fun handleBackGesture(recognizer: UIScreenEdgePanGestureRecognizer) {
        if (recognizer.state == UIGestureRecognizerStateEnded && !isModalPresented()) {
            onEnded()
        }
    }
}
