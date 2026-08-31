package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIModalPresentationFullScreen
import com.valoser.futacha.shared.util.currentIosPresentationController

@Composable
actual fun rememberCompatShareLauncher(): (
    text: String,
    mimeType: String,
    absoluteFilePath: String?
) -> Unit = remember {
    { text, _, absoluteFilePath ->
        val item: Any = absoluteFilePath?.let { NSURL.fileURLWithPath(it) } ?: text
        val controller = UIActivityViewController(listOf(item), null).apply {
            // A UIActivityViewController presented from Compose has no stable
            // UIKit source rect.  Full-screen presentation is the iPad-safe
            // adaptive substitute and avoids the popover-anchor crash.
            modalPresentationStyle = UIModalPresentationFullScreen
        }
        val presenter = currentIosPresentationController() ?: return@remember
        // Resolve the active Scene and top-most controller rather than the
        // deprecated global keyWindow/root controller.  UIKit picks the
        // appropriate adaptive presentation for the current size class.
        presenter.presentViewController(
            controller,
            animated = true,
            completion = null
        )
    }
}
