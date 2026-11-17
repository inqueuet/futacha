package com.valoser.futacha.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.valoser.futacha.shared.desktop.*
import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.*
import java.awt.Dimension
import javax.swing.JOptionPane

fun main(args: Array<String>) {
    com.valoser.futacha.shared.ui.initializeFutachaPointerDispatch()
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", "ふたちゃ")
    val checkDirectory = args.firstOrNull { it.startsWith("--check-installation=") }
        ?.substringAfter('=')?.let { java.io.File(it).absoluteFile.also { file -> file.mkdirs() } }
    val environment = checkDirectory?.let { DesktopEnvironment(java.io.File(it, "data"), java.io.File(it, "cache")) }
        ?: DesktopEnvironment.default()
    try { environment.install() }
    catch (failure: Exception) { JOptionPane.showMessageDialog(null, failure.message, "ふたちゃ", JOptionPane.ERROR_MESSAGE); return }
    val graph = DesktopAppGraph(environment)
    val shutdown = DesktopShutdownCoordinator(cleanup = {
        runBlocking { withTimeoutOrNull(2_000) { DesktopOsIntegration.close() }; withTimeoutOrNull(15_000) { graph.close() } }
        environment.close()
    })
    Runtime.getRuntime().addShutdownHook(Thread(shutdown::onJvmShutdown, "futacha-shutdown"))
    installQuitHandler(shutdown)
    try {
        application(exitProcessOnExit = false) {
            DisposableEffect(Unit) {
                shutdown.attach { java.awt.EventQueue.invokeLater(::exitApplication) }
                onDispose { shutdown.detach() }
            }
            val state = rememberWindowState(width = 1100.dp, height = 800.dp)
            val scope = rememberCoroutineScope()
            Window(title = "ふたちゃ", state = state, onCloseRequest = ::exitApplication,
                onPreviewKeyEvent = { event ->
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape && DesktopBackDispatcher.dispatch()
                }) {
                LaunchedEffect(Unit) { window.minimumSize = Dimension(480, 480) }
                LaunchedEffect(window) {
                    DesktopLifecycle.activationRequests.collect { request ->
                        if (request > 0) { state.isMinimized = false; window.toFront(); window.requestFocus() }
                    }
                }
                DisposableEffect(window) {
                    // "Background" means the window is minimized or hidden (Cmd+H), not merely unfocused:
                    // clicking another app or opening an NSOpenPanel/share picker (not Java windows)
                    // must not stop read-aloud or re-lock the app.
                    var appHidden = false
                    fun update() {
                        DesktopLifecycle.foreground.value = desktopWindowInForeground(
                            showing = window.isShowing,
                            minimized = (window.extendedState and java.awt.Frame.ICONIFIED) != 0,
                            appHidden = appHidden
                        )
                    }
                    val stateListener = object : java.awt.event.WindowAdapter() {
                        override fun windowIconified(event: java.awt.event.WindowEvent) = update()
                        override fun windowDeiconified(event: java.awt.event.WindowEvent) = update()
                        override fun windowStateChanged(event: java.awt.event.WindowEvent) = update()
                        override fun windowGainedFocus(event: java.awt.event.WindowEvent) { DesktopLifecycle.focused.value = true }
                        override fun windowLostFocus(event: java.awt.event.WindowEvent) {
                            java.awt.EventQueue.invokeLater {
                                DesktopLifecycle.focused.value = java.awt.Window.getWindows().any { it.isFocused }
                            }
                        }
                    }
                    val visibilityListener = object : java.awt.event.ComponentAdapter() {
                        override fun componentShown(event: java.awt.event.ComponentEvent) = update()
                        override fun componentHidden(event: java.awt.event.ComponentEvent) = update()
                    }
                    window.addWindowListener(stateListener)
                    window.addWindowStateListener(stateListener)
                    window.addWindowFocusListener(stateListener)
                    window.addComponentListener(visibilityListener)
                    update()
                    val desktop = if (DesktopPlatform.isMac) java.awt.Desktop.getDesktop() else null
                    val reopen = java.awt.desktop.AppReopenedListener { DesktopLifecycle.activationRequests.value += 1 }
                    val hidden = object : java.awt.desktop.AppHiddenListener {
                        override fun appHidden(event: java.awt.desktop.AppHiddenEvent) {
                            java.awt.EventQueue.invokeLater { appHidden = true; update() }
                        }
                        override fun appUnhidden(event: java.awt.desktop.AppHiddenEvent) {
                            java.awt.EventQueue.invokeLater { appHidden = false; update() }
                        }
                    }
                    // Clicking a notification activates the app; wake the (slowed) notification poll.
                    val raised = object : java.awt.desktop.AppForegroundListener {
                        override fun appRaisedToForeground(event: java.awt.desktop.AppForegroundEvent) {
                            DesktopLifecycle.appActivations.value += 1
                        }
                        override fun appMovedToBackground(event: java.awt.desktop.AppForegroundEvent) = Unit
                    }
                    desktop?.addAppEventListener(reopen)
                    desktop?.addAppEventListener(hidden)
                    desktop?.addAppEventListener(raised)
                    onDispose {
                        window.removeWindowListener(stateListener)
                        window.removeWindowStateListener(stateListener)
                        window.removeWindowFocusListener(stateListener)
                        window.removeComponentListener(visibilityListener)
                        desktop?.removeAppEventListener(reopen)
                        desktop?.removeAppEventListener(hidden)
                        desktop?.removeAppEventListener(raised)
                    }
                }
                MenuBar {
                    Menu("ふたちゃ") {
                        Item("終了", shortcut = KeyShortcut(Key.Q, meta = DesktopPlatform.isMac, ctrl = DesktopPlatform.isWindows), onClick = ::exitApplication)
                    }
                    Menu("表示") {
                        Item("戻る", shortcut = desktopBackShortcut(DesktopPlatform.isMac)) { DesktopBackDispatcher.dispatch() }
                        Item("全画面", shortcut = if (DesktopPlatform.isWindows) KeyShortcut(Key.F11) else KeyShortcut(Key.F, meta = true, ctrl = true)) {
                            state.placement = if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                        }
                    }
                    Menu(if (DesktopPlatform.isWindows) "Windowsの設定" else "Macの設定") {
                        Item("通知設定を開く") { scope.launch {
                            try { DesktopOsIntegration.notificationSettings() }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { JOptionPane.showMessageDialog(window, failure.message, "通知設定", JOptionPane.ERROR_MESSAGE) }
                        } }
                        Menu(if (DesktopPlatform.isWindows) "アプリアイコン" else "Dockアイコン") {
                            AppIconVariant.entries.forEach { variant ->
                                Item(variant.label) { scope.launch {
                                    try { graph.stateStore.setAppIconVariant(variant) }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { JOptionPane.showMessageDialog(window, failure.message, "アプリアイコン", JOptionPane.ERROR_MESSAGE) }
                                } }
                            }
                        }
                    }
                }
                DesktopFutachaApp(graph, ::exitApplication)
                if (checkDirectory != null) LaunchedEffect(checkDirectory) {
                    try { checkInstallation(window, graph, checkDirectory) }
                    catch (failure: Throwable) { failure.printStackTrace(); java.io.File(checkDirectory, "FAILED.txt").writeText(failure.toString()) }
                    finally { exitApplication() }
                }
            }
        }
    } finally { shutdown.finish() }
}

/**
 * The menu's "戻る". On a Mac Cmd+← is the text fields' "move to line start", which a screen-menu
 * shortcut takes first, so Macs use Cmd+[ (the usual Back key there); Alt+← elsewhere.
 */
internal fun desktopBackShortcut(isMac: Boolean): KeyShortcut =
    if (isMac) KeyShortcut(Key.LeftBracket, meta = true) else KeyShortcut(Key.DirectionLeft, alt = true)

/** Cmd+Q, Dock → Quit and logout: exit through Compose so the cleanup runs, then let macOS quit. */
private fun installQuitHandler(shutdown: DesktopShutdownCoordinator) {
    try {
        if (!java.awt.Desktop.isDesktopSupported()) return
        val desktop = java.awt.Desktop.getDesktop()
        if (!desktop.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) return
        desktop.setQuitHandler { _, response -> shutdown.onQuitRequested(response::performQuit) }
    } catch (failure: Exception) {
        System.err.println("Quit handler unavailable: $failure")
    }
}
