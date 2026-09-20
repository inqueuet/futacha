package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.media.prompt.isLocalPromptMediaUrl
import kotlin.test.*

class WindowsMediaPathTest {
    @Test fun absoluteDriveAndUncPathsAreLocalWithoutDecodingLiteralPercent() {
        for (path in listOf("C:/画像 空白/#50%.png", "D:\\動画\\テスト.webm", "\\\\server\\共有\\画像.png", "//server/共有/画像.png")) {
            assertTrue(isSupportedMediaSaveSource(path), path)
            assertTrue(isLocalPromptMediaUrl(path), path)
            assertEquals(path, localMediaSavePath(path))
        }
        for (path in listOf("C:relative.png", "images/picture.png", "javascript:alert(1)")) {
            assertFalse(isSupportedMediaSaveSource(path), path)
            assertFalse(isLocalPromptMediaUrl(path), path)
        }
    }

    @Test fun fileUrisPreserveUnicodeSpacesPercentPlusAndNetworkAuthority() {
        for (prefix in listOf("file:///", "file:/", "FILE:///", "file://localhost/")) {
            val uri = prefix + "C:/%E7%94%BB%E5%83%8F%20%2350%25+test.png"
            assertTrue(isSupportedMediaSaveSource(uri))
            assertEquals("C:/画像 #50%+test.png", localMediaSavePath(uri))
        }
        assertEquals("//server/共有/image.png", localMediaSavePath("file://server/%E5%85%B1%E6%9C%89/image.png"))
        assertEquals("/Users/test/画像.png", localMediaSavePath("file:///Users/test/%E7%94%BB%E5%83%8F.png"))
    }

    @Test fun videoPreviewUriRoundTripsNativePaths() {
        for (path in listOf("C:/動画 #50%+test.mp4", "//server/共有/a.webm", "/Users/test/動画 #50%.mp4")) {
            assertEquals(path, localMediaSavePath(localMediaFileUri(path)))
        }
        assertEquals("C:/動画/a.mp4", localMediaSavePath(localMediaFileUri("C:\\動画\\a.mp4")))
    }
}
