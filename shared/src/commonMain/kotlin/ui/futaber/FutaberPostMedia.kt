package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.util.ImageData

/*
 * L-B8 (M37): the camera menu's OS photo picker. Plan §4.2: 「最近の画像」 is the OS photo picker as 「ライブラリ」 is (no
 * permission to read the photo library: Android's Photo Picker, iOS's PHPicker). Nothing is sent: the picture goes to the
 * attachment as a picked file does (the board's rules, the location data stripped). The menu's 「カメラ」 was taken out
 * (2026-11-04, the user's decision: it was never seen taking a picture in futaber), so the app uses no camera.
 */

/** The message of the photo picker. */
internal const val FUTABER_PHOTO_PICKER_FAILED_MESSAGE = "写真を読み込めませんでした"

/**
 * Opens the OS photo picker for one picture. [onPicked] gets it; [onFinished] is called after every end (a pick, a
 * cancel, a failure), so the writing can take the keyboard back (F28).
 */
@Composable
internal expect fun rememberFutaberPhotoPicker(
    maxBytes: Long,
    onPicked: (ImageData) -> Unit,
    onFinished: () -> Unit,
    onError: (String) -> Unit
): () -> Unit
