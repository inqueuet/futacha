package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlin.test.Test
import kotlin.test.assertEquals

class SavePathDisplaySupportTest {
    @Test
    fun resolveManualSaveInputResolution_classifiesShorthandsAndPaths() {
        assertEquals(
            ManualSaveInputResolution(
                normalizedInput = "saved_threads",
                kind = ManualSaveInputKind.RELATIVE,
                relativePath = "saved_threads"
            ),
            resolveManualSaveInputResolution("  ")
        )
        assertEquals(
            ManualSaveInputResolution(
                normalizedInput = "Documents/work",
                kind = ManualSaveInputKind.DOCUMENTS,
                relativePath = "work"
            ),
            resolveManualSaveInputResolution("Documents/work")
        )
        assertEquals(
            ManualSaveInputResolution(
                normalizedInput = "Download/futacha",
                kind = ManualSaveInputKind.DOWNLOAD,
                relativePath = "futacha"
            ),
            resolveManualSaveInputResolution("downloads/futacha")
        )
        assertEquals(
            ManualSaveInputResolution(
                normalizedInput = "/storage/emulated/0/custom",
                kind = ManualSaveInputKind.ABSOLUTE
            ),
            resolveManualSaveInputResolution("/storage/emulated/0/custom")
        )
        assertEquals(
            ManualSaveInputResolution(
                normalizedInput = "custom/path",
                kind = ManualSaveInputKind.RELATIVE,
                relativePath = "custom/path"
            ),
            resolveManualSaveInputResolution("custom/path")
        )
    }

    @Test
    fun normalizeManualSaveInputValue_normalizesDocumentsAndDownloadShorthands() {
        assertEquals("saved_threads", normalizeManualSaveInputValue("  "))
        assertEquals("Documents", normalizeManualSaveInputValue("documents"))
        assertEquals("Documents/work", normalizeManualSaveInputValue("Documents/work"))
        assertEquals("Download", normalizeManualSaveInputValue("downloads"))
        assertEquals("Download/futacha", normalizeManualSaveInputValue("download/futacha"))
        assertEquals("/storage/emulated/0/custom", normalizeManualSaveInputValue("/storage/emulated/0/custom"))
    }

    @Test
    fun resolveFallbackManualSavePathValue_buildsExpectedDefaultDisplayPaths() {
        assertEquals(
            "Documents/futacha/saved_threads",
            resolveFallbackManualSavePathValue("Documents")
        )
        assertEquals(
            "Download/futacha/saved_threads",
            resolveFallbackManualSavePathValue("Download")
        )
        assertEquals(
            "Documents/custom/path",
            resolveFallbackManualSavePathValue("Documents/custom/path")
        )
        assertEquals(
            "Documents/futacha/custom/path",
            resolveFallbackManualSavePathValue("custom/path")
        )
    }

    @Test
    fun saveDestinationLabels_andHints_reflectSelectionAndPlatform() {
        assertEquals(
            "手入力の保存先",
            buildSaveDestinationModeLabelValue(SaveDirectorySelection.MANUAL_INPUT, isAndroidPlatform = true)
        )
        assertEquals(
            "選択フォルダへの保存先",
            buildSaveDestinationModeLabelValue(SaveDirectorySelection.PICKER, isAndroidPlatform = true)
        )
        assertEquals(
            "選択フォルダの保存先",
            buildSaveDestinationModeLabelValue(SaveDirectorySelection.PICKER, isAndroidPlatform = false)
        )
        assertEquals(
            "この場所にスレ保存と画像・動画の単体保存をまとめて保存します。",
            buildSaveDestinationHintValue(SaveDirectorySelection.MANUAL_INPUT, isAndroidPlatform = true)
        )
        assertEquals(
            "SAF で選んだフォルダ配下に保存します。画像・動画の単体保存も同じフォルダ系統です。",
            buildSaveDestinationHintValue(SaveDirectorySelection.PICKER, isAndroidPlatform = true)
        )
    }

    @Test
    fun buildDisplayedSavePathValue_usesResolvedAbsolutePathForPathLocations() {
        assertEquals(
            "/storage/emulated/0/Documents/futacha/b__777_hash",
            buildDisplayedSavePathValue(
                manualSaveDirectory = "Documents",
                manualSaveLocation = SaveLocation.Path("Documents"),
                resolvedManualSaveDirectory = "/storage/emulated/0/Documents/futacha",
                relativePath = "b__777_hash"
            )
        )
    }

    @Test
    fun buildDisplayedSavePathValue_usesSelectedFolderPrefixForTreeUriAndBookmarkLocations() {
        assertEquals(
            "選択フォルダ/b__777_hash/preview_media/images/file.jpg",
            buildDisplayedSavePathValue(
                manualSaveDirectory = "tree:content://example",
                manualSaveLocation = SaveLocation.TreeUri("content://example"),
                resolvedManualSaveDirectory = null,
                relativePath = "b__777_hash/preview_media/images/file.jpg"
            )
        )
        assertEquals(
            "選択フォルダ/b__777_hash",
            buildDisplayedSavePathValue(
                manualSaveDirectory = "bookmark:abc",
                manualSaveLocation = SaveLocation.Bookmark("abc"),
                resolvedManualSaveDirectory = null,
                relativePath = "b__777_hash"
            )
        )
    }
}
