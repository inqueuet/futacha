package com.valoser.futacha.shared.desktop

import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO
import javax.swing.JOptionPane

/** OS services used by the shared desktop host. Windows never loads the AppKit bridge. */
object DesktopOsIntegration {
    private var tray: TrayIcon? = null // confined to the AWT event thread
    private val balloonLink = DesktopTrayBalloonLink() // confined to the AWT event thread
    private val links = ConcurrentLinkedQueue<String>()

    /** Created by the first notification only, so the icon never appears with notifications off. */
    private fun ensureTray(): TrayIcon {
        check(SystemTray.isSupported()) { "Windowsの通知領域を利用できません" }
        return tray ?: TrayIcon(ImageIO.read(desktopResource("icons/Current.png")), "ふたちゃ").also { icon ->
            icon.isImageAutoSize = true
            // AWT reports a balloon click and an icon double-click as the same action.
            icon.addActionListener {
                balloonLink.consume(System.currentTimeMillis())?.let(links::add)
                DesktopLifecycle.activationRequests.value += 1
                DesktopLifecycle.appActivations.value += 1
            }
            SystemTray.getSystemTray().add(icon)
            tray = icon
        }
    }

    suspend fun notificationPermission(): Boolean = if (DesktopPlatform.isWindows) notificationAllowed()
        else MacOsIntegration.notificationPermission()

    suspend fun notificationAllowed(): Boolean = if (DesktopPlatform.isWindows) withContext(Dispatchers.Main) {
        SystemTray.isSupported()
    } else MacOsIntegration.notificationAllowed()

    fun openNotificationSettings() {
        if (DesktopPlatform.isWindows) ProcessBuilder("explorer.exe", "ms-settings:notifications").start()
        else Desktop.getDesktop().browse(java.net.URI("x-apple.systempreferences:com.apple.Notifications-Settings.extension?id=com.valoser.futacha.desktop"))
    }

    suspend fun notificationSettings() = withContext(Dispatchers.IO) { openNotificationSettings() }

    suspend fun notify(identifier: String, title: String, body: String, threadUrl: String) {
        require(canonicalizeThreadUrl(threadUrl) != null)
        if (!DesktopPlatform.isWindows) return MacOsIntegration.notify(identifier, title, body, threadUrl)
        withContext(Dispatchers.Main) {
            balloonLink.show(threadUrl, System.currentTimeMillis())
            ensureTray().displayMessage(title.take(120), body.take(256), TrayIcon.MessageType.INFO)
        }
    }

    suspend fun notificationLinks(): List<String> = if (!DesktopPlatform.isWindows) MacOsIntegration.notificationLinks()
        else buildList { while (true) add(links.poll() ?: break) }

    suspend fun applyDockIcon(variant: AppIconVariant) {
        if (!DesktopPlatform.isWindows) return MacOsIntegration.applyDockIcon(variant)
        withContext(Dispatchers.Main) {
            val icon = ImageIO.read(desktopResource("icons/${variant.name}.png"))
            Window.getWindows().filterIsInstance<Frame>().forEach { it.iconImage = icon }
            tray?.image = icon
        }
    }

    suspend fun share(text: String, file: String?) {
        if (!DesktopPlatform.isWindows) return MacOsIntegration.share(text, file)
        val attachment = file?.let(::File)?.also { require(it.isFile) { "共有するファイルが見つかりません" } }
        withContext(Dispatchers.Main) {
            val options = if (attachment == null) arrayOf("テキストをコピー", "キャンセル")
                else arrayOf("ファイルをコピー", "テキストをコピー", "キャンセル")
            val choice = JOptionPane.showOptionDialog(Window.getWindows().firstOrNull { it.isFocused },
                "コピーした内容を共有先のアプリに貼り付けてください。", "共有", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options.first())
            when (options.getOrNull(choice)) {
                "テキストをコピー" -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                "ファイルをコピー" -> Toolkit.getDefaultToolkit().systemClipboard.setContents(object : Transferable {
                    override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
                    override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
                    override fun getTransferData(flavor: DataFlavor): Any {
                        if (!isDataFlavorSupported(flavor)) throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
                        return listOf(checkNotNull(attachment))
                    }
                }, null)
            }
        }
    }

    suspend fun checkInstallation(output: File) {
        if (!DesktopPlatform.isWindows) return MacOsIntegration.checkInstallation(output)
        for (variant in AppIconVariant.entries) applyDockIcon(variant)
        applyDockIcon(AppIconVariant.Current)
        File(output, "windows-integrations.txt").writeText("Windows x64; window icons applied. Notification area supported: ${SystemTray.isSupported()}\n")
    }

    suspend fun close() = withContext(Dispatchers.Main) {
        tray?.let { SystemTray.getSystemTray().remove(it) }
        tray = null; balloonLink.clear(); links.clear()
    }
}

/**
 * The thread of the latest tray balloon. A click opens it once; a later click
 * (an icon double-click, an expired balloon) only brings the window forward.
 * AWT gives one tray icon a single action for every balloon, so an older
 * balloon still clicked opens the latest thread.
 */
internal class DesktopTrayBalloonLink(private val lifetimeMillis: Long = 5 * 60_000L) {
    private var url: String? = null
    private var shownAtMillis = 0L

    fun show(threadUrl: String, nowMillis: Long) { url = threadUrl; shownAtMillis = nowMillis }

    fun consume(nowMillis: Long): String? {
        val pending = url ?: return null
        clear()
        return pending.takeIf { nowMillis - shownAtMillis in 0..lifetimeMillis }
    }

    fun clear() { url = null }
}
