@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.valoser.futacha.shared.ui.board.pickerHostContext
import com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Thread 01a1cd5d #52-#55: inside a dialog LocalContext wraps the Activity, and the
 * attachment picker refused to start with "この画面では添付ピッカーを起動できません".
 */
class PickerInsideDialogInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun theAttachmentPickerStartsFromInsideADialog() {
        val errors = mutableListOf<String>()
        var launch: (() -> Unit)? = null
        var dialogContext: android.content.Context? = null
        rule.setContent {
            Dialog(onDismissRequest = {}) {
                dialogContext = LocalContext.current
                launch = rememberAttachmentPickerLauncher(
                    preference = AttachmentPickerPreference.DOCUMENT,
                    mimeType = "image/*",
                    maxBytes = 1_000_000,
                    onImageSelected = {},
                    preferredFileManagerPackage = null,
                    onSelectionError = { errors += it }
                )
                Text("dialog")
            }
        }
        rule.waitForIdle()
        assertTrue("the dialog's context is a wrapper", dialogContext !is Activity)
        assertSame(rule.activity, pickerHostContext(checkNotNull(dialogContext)))
        rule.runOnUiThread { checkNotNull(launch).invoke() }
        rule.waitForIdle()
        assertTrue("no picker error: $errors", errors.isEmpty())
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
    }
}
