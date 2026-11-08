package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

class FtutterNgPagesTest {
    @Test fun eachKindHasTheOriginalsDescriptionAndDialogTitle() {
        assertEquals("この文字列が含まれるスレをカタログから非表示にします", ftutterNgTypeDescription(FtutterNgType.TITLE))
        assertEquals("この文字列が含まれるレスを非表示にします", ftutterNgTypeDescription(FtutterNgType.WORD))
        assertEquals("このID・IPからの投稿を非表示にします", ftutterNgTypeDescription(FtutterNgType.ID))
        assertEquals("この文字列が含まれるスレをカタログの上部に強調表示します", ftutterNgTypeDescription(FtutterNgType.PICKUP))
        assertEquals("NGタイトル", ftutterNgDialogTitle(FtutterNgType.TITLE))
        assertEquals("NGID・IP", ftutterNgDialogTitle(FtutterNgType.ID))
        assertEquals("ピックアップ", ftutterNgDialogTitle(FtutterNgType.PICKUP))
    }
}
