package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatCatalogLayout
import com.valoser.futacha.shared.compat.CompatSettingsBackupImportReport
import com.valoser.futacha.shared.compat.decodeCompatSettingsBackup
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.ui.image.CompatibilityCacheLocation
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFails

class CompatPreferenceSchemaTest {
    @Test
    fun rootSettingsKeepTheReferenceCoreExactAndIsolateCurrentExtensions() {
        val groups = compatRootSettingsGroups("8.5")
        assertEquals(
            listOf("基本設定", "表示オプション", "バックアップ", "その他", "ふたちゃ拡張"),
            groups.map { it.first }
        )
        assertEquals(
            listOf("デザイン", "コントロール", "ストレージ", "バックグラウンド", "ネットワーク", "画像検索"),
            groups[0].second.map { it.title }
        )
        assertEquals(
            listOf("カタログ画面", "スレッド画面", "画像ビューア"),
            groups[1].second.map { it.title }
        )
        assertEquals(
            listOf(
                "基本的な設定の復元", "基本的な設定の保存", "監視･ＮＧワードの復元",
                "監視･ＮＧワードの保存", "ptmtクッキーの編集"
            ),
            groups[2].second.map { it.title }
        )
        assertTrue(groups[2].second.all { it.summary.isEmpty() })
        assertEquals(
            listOf("更新情報", "ライセンス", "Twitter", "バージョン"),
            groups[3].second.map { it.title }
        )
        assertEquals("@AndosanDev", groups[3].second[2].summary)
        assertEquals("8.5 Database v26", groups[3].second[3].summary)
        assertTrue(groups[4].second.any { it.title == "アップデート確認" })
        assertTrue(groups[4].second.any { it.title == "保存済みスレッド" })
        assertTrue(groups[3].second.none { it.title in setOf("アップデート確認", "保存済みスレッド") })
    }

    @Test
    fun referenceBackupDatesAndVersionMessagesMatchTheApkContracts() {
        assertEquals(
            "2026/04/21 03:04:05",
            formatCompatBackupTimestamp(1_776_740_645_000L, TimeZone.UTC)
        )
        assertEquals("compat.root.backupSettingImportDate", compatBackupDatePreferenceKey("settings"))
        assertEquals("compat.root.backupSettingExportDate", compatBackupDatePreferenceKey("save_settings"))
        assertEquals("compat.root.backupKeywordImportDate", compatBackupDatePreferenceKey("ng"))
        assertEquals("compat.root.backupKeywordExportDate", compatBackupDatePreferenceKey("save_ng"))
        assertNull(compatBackupDatePreferenceKey("legacy"))
        assertEquals(15, COMPAT_REFERENCE_VERSION_MESSAGES.size)
        assertEquals(14, COMPAT_REFERENCE_VERSION_RANDOM_BOUND)
        assertEquals("エンジョイ＆エキサイティング", compatReferenceVersionMessage(0))
        assertEquals("教授！！これはいったい？", compatReferenceVersionMessage(14))
    }

    @Test
    fun backupCompletionAndFailureCopyMatchesBothReferenceApks() {
        val report = CompatSettingsBackupImportReport(
            boardsImported = 0,
            tabsImported = 0,
            historyImported = 0,
            preferencesImported = 1,
            ngRulesImported = 3,
            toolbarsImported = 0
        )
        assertEquals("基本的な設定を復元しました", compatBackupSuccessMessage("settings", report))
        assertEquals("基本的な設定を保存しました", compatBackupSuccessMessage("save_settings"))
        assertEquals(
            "監視･ＮＧワードを復元しました\n設定項目 1件、ＮＧ項目 3件",
            compatBackupSuccessMessage("ng", report)
        )
        assertEquals("監視･ＮＧワードを保存しました", compatBackupSuccessMessage("save_ng"))
        assertEquals(
            "ファイルの形式が不明です",
            compatBackupFailureMessage("settings", IllegalArgumentException("invalid json"))
        )
        val malformedJsonFailure = assertFails { decodeCompatSettingsBackup("not-json") }
        assertEquals(
            "ファイルの形式が不明です",
            compatBackupFailureMessage("settings", malformedJsonFailure)
        )
        assertEquals(
            "書き込みエラーです",
            compatBackupFailureMessage("save_ng", IllegalStateException("disk full"))
        )
        assertEquals(
            "ファイルの読み込みができません",
            compatBackupFailureMessage("ng", IllegalStateException("ファイルシステムを利用できません"))
        )
        assertEquals(
            "復元に失敗しました",
            compatBackupFailureMessage("settings", IllegalStateException("database"))
        )
    }

    @Test
    fun helpCoversEveryReferenceApkSectionAndItsFineGrainedOperations() {
        val html = COMPAT_REFERENCE_HELP_HTML
        assertTrue(html.length > 100_000)
        assertTrue(html.contains("<title>ヘルプ</title>"))
        assertEquals(12, Regex("<label").findAll(html).count())
        assertEquals(50, Regex("<img ").findAll(html).count())
        assertEquals(51, Regex("data:image/png;base64,").findAll(html).count())
        listOf(
            "板一覧", "カタログ", "スレッド", "送信画面", "手書き", "画像一覧",
            "画像ビューア", "ドロワー", "バックグラウンド", "ネットワーク", "設定画面", "よくある質問"
        ).forEach { assertTrue(html.contains(it), "missing reference help section: $it") }
        assertTrue(html.contains("タブに追加する"))
        assertTrue(html.contains("通信の軽量化"))
        assertTrue(html.contains("delを送信する"))
        assertTrue(html.contains("あぷ小アップロード"))

        assertTrue(compatibilityReferenceHelpHtml(compatibilityPaletteFor("futaba")).contains("#542d24"))
        assertTrue(compatibilityReferenceHelpHtml(compatibilityPaletteFor("black")).contains("#222222"))
        assertTrue(compatibilityReferenceHelpHtml(compatibilityPaletteFor("blue")).contains("#03a9f4"))
        assertTrue(compatibilityReferenceHelpHtml(compatibilityPaletteFor("pink")).contains("#e91e63"))
    }

    @Test
    fun imageSearchExplainsTheReferenceFileAndUrlModes() {
        assertTrue(COMPAT_IMAGE_SEARCH_DESCRIPTION.contains("File方式は画像そのものを送り"))
        assertTrue(COMPAT_IMAGE_SEARCH_DESCRIPTION.contains("URL方式は画像のURLを外部ブラウザへ渡します"))
        val rootEntry = compatImageSearchRootEntry()
        assertEquals("画像検索", rootEntry.title)
        assertEquals("長押しメニューの整理", rootEntry.summary)
        assertEquals("image_search", rootEntry.route)
        assertEquals("画像検索", "image_search".compatSettingsTitle())
        assertEquals("カタログ設定", "catalog".compatSettingsTitle())
        assertEquals("スレッド設定", "thread".compatSettingsTitle())
        assertEquals("画像ビューア設定", "viewer".compatSettingsTitle())
        assertEquals(
            "長押しメニューに出す検索先",
            compatSettingsGroups("image_search").single().first
        )
        val entries = "image_search".compatSettingsEntries()
        assertEquals(COMPAT_IMAGE_SEARCH_DESCRIPTION, entries.first().summary)
        assertEquals(CompatImageSearchTarget.entries.size + 1, entries.size)
    }

    @Test
    fun removableCacheChoiceShowsUnavailableWithoutPretendingItCanBeSelected() {
        assertEquals(
            listOf("端末ストレージ", "外部SDカード(利用不可)"),
            compatCacheLocationOptions(removableAvailable = false)
        )
        assertEquals(
            listOf("端末ストレージ", "外部SDカード"),
            compatCacheLocationOptions(removableAvailable = true)
        )
        assertEquals(
            listOf("内部ストレージ", "端末ストレージ", "外部SDカード"),
            compatCacheLocationOptions(removableAvailable = true, includeInternal = true)
        )
        assertEquals("最速・小容量", compatCacheLocationNote("内部ストレージ"))
        assertEquals("高速", compatCacheLocationNote("端末ストレージ"))
        assertEquals("低速・大容量", compatCacheLocationNote("外部SDカード(利用不可)"))
        assertEquals(
            "高速・空き 1.5GB",
            compatCacheLocationNote("端末ストレージ", 1536L * 1024L * 1024L)
        )
        assertEquals("512MB", formatCompatAvailableSpace(512L * 1024L * 1024L))
        assertEquals(
            CompatibilityCacheLocation.EXTERNAL_SD,
            compatCacheLocation("外部SDカード(利用不可)")
        )
    }

    @Test
    fun childScreensMatchApkPreferenceRowsAndUniqueKeys() {
        val expectedCounts = mapOf(
            "background" to 2,
            "catalog" to 21,
            "control" to 11,
            "design" to 7,
            "network" to 5,
            "storage" to 10,
            "thread" to 16,
            "viewer" to 3
        )
        val entries = expectedCounts.flatMap { (path, count) ->
            path.compatSettingsEntries().also { assertEquals(count, it.size, path) }
                .map { path to it }
        }

        assertEquals(75, entries.size)
        assertEquals(
            74,
            entries.map { (path, entry) -> compatPreferenceStorageKey(path, entry.preferenceKey) }.toSet().size,
            "Catalog and Thread must share commonPrivacyAlpha"
        )
    }

    @Test
    fun backgroundScreenMatchesReferenceRowsAndRawValues() {
        val entries = "background".compatSettingsEntries()
        assertEquals(
            listOf("スレッドの生存確認", "スレッドの更新確認"),
            entries.map { it.title }
        )
        assertEquals(
            listOf("backgroundThreadExistCheck", "backgroundThreadUpdateCheck"),
            entries.map { it.preferenceKey }
        )
        listOf(
            "常に確認する" to "usually",
            "Wi-Fi回線のみ" to "wifi",
            "利用しない" to "none"
        ).forEach { (display, raw) ->
            entries.forEach { entry ->
                assertEquals(raw, compatPreferenceStoredValue(entry.preferenceKey, display))
                assertEquals(display, compatPreferenceDisplayValue(entry.preferenceKey, raw))
            }
        }
        // Values written by earlier compatibility builds must still render
        // correctly while all new writes use the reference raw values above.
        assertEquals(
            "常に確認する",
            compatPreferenceDisplayValue("backgroundThreadExistCheck", "常に確認する")
        )
        entries.forEach { entry -> assertEquals("選択", compatPreferenceDialogTitle(entry)) }
        assertEquals(
            "選択",
            compatPreferenceDialogTitle(
                "viewer".compatSettingsEntries().single { it.preferenceKey == "viewerPreloadMode" }
            )
        )
        assertEquals(
            "スレッド文の長さ",
            compatPreferenceDialogTitle(
                "catalog".compatSettingsEntries().single { it.preferenceKey == "catalogTitleLength" }
            )
        )
    }

    @Test
    fun exactDiscreteDomainsMatchApk() {
        fun options(path: String, key: String): List<String> {
            val entry = path.compatSettingsEntries().single { it.preferenceKey == key }
            return compatPreferenceOptions(path, entry)
        }

        assertEquals(
            listOf("0（ソートしない）") + (1..30).map(Int::toString),
            options("catalog", "delayFewReplies")
        )
        assertEquals((90 downTo 10 step 10).map { "$it%" }, options("catalog", "commonPrivacyAlpha"))
        assertEquals((0..30).map(Int::toString), options("catalog", "catalogGridViewTitleLength"))
        assertEquals(
            listOf("50スレ", "100スレ", "200スレ", "300スレ", "500スレ", "800スレ", "1000スレ", "2000スレ", "3000スレ"),
            options("catalog", "catalogThreadSize")
        )
        assertEquals((1..30).map(Int::toString), options("thread", "autoScrollPixel"))
        assertEquals(
            ((10..100 step 5) + listOf(150, 200)).map(Int::toString),
            options("thread", "autoScrollSpeed")
        )
        assertEquals(
            listOf("1本(1枚ずつ)", "2本", "3本", "4本", "5本", "6本(既定)", "8本"),
            options("network", "networkImageParallel")
        )
        assertEquals(listOf("何もしない", "スクロール"), options("control", "controlCatalogVolumeKey"))
        assertEquals(
            listOf("表示しない", "表示する", "表示する(先読み)", "Wi-Fi回線のみ先読み"),
            options("thread", "threadUpsThumbMethod")
        )
        assertEquals(
            listOf("常に利用する", "Wi-Fi回線のみ", "利用しない"),
            options("viewer", "viewerPreloadMode")
        )
        listOf(
            Triple("常に利用する", "usually", "常に利用する"),
            Triple("Wi-Fi回線のみ", "wifi", "Wi-Fi回線のみ"),
            Triple("利用しない", "none", "利用しない")
        ).forEach { (display, raw, restoredDisplay) ->
            assertEquals(raw, compatPreferenceStoredValue("viewerPreloadMode", display))
            assertEquals(restoredDisplay, compatPreferenceDisplayValue("viewerPreloadMode", raw))
        }
        assertEquals(
            listOf("150", "200", "250", "300", "360", "410", "480", "640", "720", "800", "1000", "1200"),
            options("thread", "threadThumbSize")
        )
    }

    @Test
    fun threadSettingsMatchTheFinalApkGroupsDefaultsAndStoredValues() {
        val groups = compatSettingsGroups("thread")
        assertEquals(listOf("全般", "画面表示", "抽出する閾値"), groups.map { it.first })
        assertEquals(
            listOf(
                "threadPullToRefresh", "threadFastScroll", "autoScrollPixel", "autoScrollSpeed", "threadNg"
            ),
            groups[0].second.map(CompatSettingEntry::preferenceKey)
        )
        assertEquals(
            listOf(
                "threadHideDefaultNameAndSubject", "threadHeaderQuoteSimple", "threadHeaderSoudaneDisplay",
                "threadAdminDeleteShow", "commonPrivacyAlpha", "threadFontSize", "threadThumbSize",
                "threadUpsThumbSize", "threadUpsThumbMethod"
            ),
            groups[1].second.map(CompatSettingEntry::preferenceKey)
        )
        assertEquals(
            listOf("threadExtractSoudaneNum", "threadExtractQuoteNum"),
            groups[2].second.map(CompatSettingEntry::preferenceKey)
        )
        assertTrue(groups.flattenEntries().none { it.preferenceKey == "threadImageNgPhashThreshold" })
        assertEquals(
            "",
            groups.flattenEntries().single { it.preferenceKey == "threadUpsThumbMethod" }.summary,
            "1.apk shows no summary before its initially-unset uploader policy is selected"
        )

        assertEquals("simple|right", compatPreferenceStoredValue("threadHeaderSoudaneDisplay", "シンプル(右寄せ)"))
        assertEquals("シンプル(右寄せ)", compatPreferenceDisplayValue("threadHeaderSoudaneDisplay", "simple|right"))
        assertEquals("wifi", compatPreferenceStoredValue("threadUpsThumbMethod", "Wi-Fi回線のみ先読み"))
        assertEquals("Wi-Fi回線のみ先読み", compatPreferenceDisplayValue("threadUpsThumbMethod", "wifi"))
    }

    @Test
    fun controlSettingsKeepTheFinalApkRowsAndRawValuesAheadOfIsolatedExtensions() {
        val groups = compatSettingsGroups("control")
        assertEquals(
            listOf("カタログ画面", "スレッド画面", "ツールバー", "書き込み画面", "画面ビューア"),
            groups.take(5).map { it.first }
        )
        assertEquals(
            listOf(
                "controlCatalogVolumeKey", "controlCatalogLongTap",
                "controlThreadVolumeKey", "controlTouchScroll", "controlTouchOpenDrawer",
                "controlThreadCloseBack", "controlTabSelectorLongTap", "controlPostConfirm",
                "controlViewerSwipeClose"
            ),
            groups.take(5).flattenEntries().map(CompatSettingEntry::preferenceKey)
        )
        assertEquals("ふたちゃ拡張", groups.single { it.first == "ふたちゃ拡張" }.first)
        assertEquals(
            listOf("controlCloseToastDuration", "controlPostDestinationConfirm"),
            groups.single { it.first == "ふたちゃ拡張" }.second.map(CompatSettingEntry::preferenceKey)
        )

        fun options(key: String): List<String> = compatPreferenceOptions(
            "control",
            "control".compatSettingsEntries().single { it.preferenceKey == key }
        )
        assertEquals(listOf("何もしない", "スクロール"), options("controlCatalogVolumeKey"))
        assertEquals(
            listOf("何もしない", "選択メニュー", "NGスレッドに登録", "delを送信する", "タブに追加する"),
            options("controlCatalogLongTap")
        )
        assertEquals(
            listOf("何もしない", "1レス分スクロール", "1画面分スクロール", "スレッドの切り替え"),
            options("controlThreadVolumeKey")
        )
        assertEquals(
            listOf("何もしない", "選択メニュー", "更新の確認", "再読み込み", "レスを書き込む", "スレを閉じる"),
            options("controlTabSelectorLongTap")
        )

        val mappings = listOf(
            Triple("controlCatalogVolumeKey", "スクロール", "screen"),
            Triple("controlCatalogLongTap", "NGスレッドに登録", "ng"),
            Triple("controlThreadVolumeKey", "1レス分スクロール", "response"),
            Triple("controlTabSelectorLongTap", "レスを書き込む", "post")
        )
        mappings.forEach { (key, display, raw) ->
            assertEquals(raw, compatPreferenceStoredValue(key, display), key)
            assertEquals(display, compatPreferenceDisplayValue(key, raw), key)
        }
    }

    @Test
    fun storageSettingsMatchTheFinalApkRowsRawValuesAndDirectorySummaries() {
        val groups = compatSettingsGroups("storage")
        assertEquals(listOf("保存先", "キャッシュ"), groups.map { it.first })
        assertEquals(
            listOf("dummyDownloadDir", "dummyDrawingDir"),
            groups[0].second.map(CompatSettingEntry::preferenceKey)
        )
        assertEquals(
            listOf(
                "commonImageCache", "dummyImageCacheLocation", "dummyImageCacheClear",
                "commonCatalogImageCache", "dummyCatalogImageCacheLocation",
                "commonThreadCache", "dummyThreadCacheClear", "dummyAttachFileClear"
            ),
            groups[1].second.map(CompatSettingEntry::preferenceKey)
        )

        val capacityOptions = listOf("32MB", "64MB", "128MB", "256MB", "512MB", "1GB", "2GB", "無制限")
        listOf("commonImageCache", "commonCatalogImageCache", "commonThreadCache").forEach { key ->
            val entry = groups.flattenEntries().single { it.preferenceKey == key }
            assertEquals(capacityOptions, compatPreferenceOptions("storage", entry), key)
        }
        val rawMappings = listOf(
            "32MB" to "32", "64MB" to "64", "128MB" to "128", "256MB" to "256",
            "512MB" to "512", "1GB" to "1024", "2GB" to "2048", "無制限" to "131072"
        )
        rawMappings.forEach { (display, raw) ->
            assertEquals(raw, compatPreferenceStoredValue("commonImageCache", display))
            assertEquals(display, compatPreferenceDisplayValue("commonImageCache", raw))
        }
        assertEquals("1024MB", compatPreferenceSummaryValue("commonImageCache", "1024"))
        assertEquals("131072MB", compatPreferenceSummaryValue("commonThreadCache", "131072"))

        assertEquals("device", compatPreferenceStoredValue("dummyImageCacheLocation", "端末ストレージ"))
        assertEquals("sdcard", compatPreferenceStoredValue("dummyImageCacheLocation", "外部SDカード"))
        assertEquals("internal", compatPreferenceStoredValue("dummyCatalogImageCacheLocation", "内部ストレージ"))
        assertEquals("外部SDカード", compatPreferenceDisplayValue("dummyImageCacheLocation", "sdcard"))

        assertEquals(
            "未設定時：標準フォルダに保存",
            compatStorageDirectorySummary("dummyDownloadDir", null)
        )
        assertEquals(
            "未設定時: 一時保存。残す場合は保存先を設定",
            compatStorageDirectorySummary("dummyDrawingDir", "")
        )
        assertEquals(
            "任意フォルダ：保存先",
            compatStorageDirectorySummary(
                "dummyDownloadDir",
                "tree:content://com.android.externalstorage.documents/tree/primary%3A%E4%BF%9D%E5%AD%98%E5%85%88"
            )
        )
        assertEquals(
            "任意フォルダ：Drawings",
            compatStorageDirectorySummary("dummyDrawingDir", "/var/mobile/Documents/Drawings")
        )
        assertEquals(
            "任意フォルダ：選択済み",
            compatStorageDirectorySummary("dummyDrawingDir", "bookmark:opaque-security-scoped-data")
        )
    }

    @Test
    fun attachmentCacheUsageMeasuresTheActualTemporaryFiles() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.createDirectory("private/compat_post_attachments").getOrThrow()
        fileSystem.writeBytes("private/compat_post_attachments/drawing_a.png", ByteArray(3)).getOrThrow()
        fileSystem.writeBytes("private/compat_post_attachments/compressed_b.jpg", ByteArray(5)).getOrThrow()

        assertEquals(8L, compatibilityAttachmentCacheUsageBytes(fileSystem))
        fileSystem.deleteRecursively("private/compat_post_attachments").getOrThrow()
        assertEquals(0L, compatibilityAttachmentCacheUsageBytes(fileSystem))
    }

    @Test
    fun imageParallelUsesExactLabelButPersistsTheApkNumericValue() {
        assertEquals("6", compatPreferenceStoredValue("networkImageParallel", "6本(既定)"))
        assertEquals("6本(既定)", compatPreferenceDisplayValue("networkImageParallel", "6"))
        assertEquals("8本", compatPreferenceDisplayValue("networkImageParallel", "8"))
        assertEquals("6本", compatPreferenceSummaryValue("networkImageParallel", "6"))
    }

    @Test
    fun catalogChoicesWithUnitsPersistTheFinalApkEntryValues() {
        assertEquals("0", compatPreferenceStoredValue("delayFewReplies", "0（ソートしない）"))
        assertEquals("20", compatPreferenceStoredValue("commonPrivacyAlpha", "20%"))
        assertEquals("300", compatPreferenceStoredValue("catalogThreadSize", "300スレ"))
        assertEquals("10", compatPreferenceStoredValue("catalogTitleLength", "10文字"))

        assertEquals("0（ソートしない）", compatPreferenceDisplayValue("delayFewReplies", "0"))
        assertEquals("20%", compatPreferenceDisplayValue("commonPrivacyAlpha", "20"))
        assertEquals("300スレ", compatPreferenceDisplayValue("catalogThreadSize", "300"))
        assertEquals("10文字", compatPreferenceDisplayValue("catalogTitleLength", "10"))
    }

    @Test
    fun networkSettingsMatchTheFinalApkRowsAndReadOnlyStatusDefault() {
        val groups = compatSettingsGroups("network")
        assertEquals(
            listOf("キャッシュサーバー機能", "画像の取得", "ふたちゃ拡張"),
            groups.map { it.first }
        )
        assertEquals(
            listOf("通信の軽量化", "ステータス"),
            groups[0].second.map { it.title }
        )
        assertEquals(" - ", groups[0].second.single { it.title == "ステータス" }.summary)
        assertEquals(
            listOf("画像の同時取得数", "画像取得数の説明"),
            groups[1].second.map { it.title }
        )
        assertEquals("画像の同時取得数", compatPreferenceDialogTitle(groups[1].second.first()))
    }

    @Test
    fun preferenceSummariesUseTheUnitsAndLabelsShownByTheReferenceApk() {
        assertEquals("モノクローム", compatPreferenceSummaryValue("designTheme", "モノクロ"))
        assertEquals("アオいいよね", compatPreferenceSummaryValue("designTheme", "ブルー"))
        assertEquals(
            "ツールバーの上",
            compatPreferenceSummaryValue("designTabSelectorLocation", "ツールバーと二段で表示")
        )
        assertEquals("0レス以上", compatPreferenceSummaryValue("delayFewReplies", "0"))
        assertEquals("20%", compatPreferenceSummaryValue("commonPrivacyAlpha", "20%"))
        assertEquals("4文字", compatPreferenceSummaryValue("catalogGridViewTitleLength", "4"))
        assertEquals("14sp", compatPreferenceSummaryValue("catalogGridViewTitleFontSize", "14"))
        assertEquals("5列", compatPreferenceSummaryValue("catalogGridViewPortraitClmNum", "5"))
        assertEquals("7行", compatPreferenceSummaryValue("catalogListViewLineNum", "7"))
        assertEquals("300スレ", compatPreferenceSummaryValue("catalogThreadSize", "300スレ"))
        assertEquals("50ミリ秒", compatPreferenceSummaryValue("autoScrollSpeed", "50 ms"))
        assertEquals("250dp", compatPreferenceSummaryValue("threadThumbSize", "250"))
        assertEquals("3件", compatPreferenceSummaryValue("threadExtractQuoteNum", "3"))
        assertEquals("5列", compatPreferenceSummaryValue("galleryGridViewPortraitClmNum", "5"))
        assertEquals(
            "通信に成功した更新の後だけ先頭へ戻り、キャッシュ表示時は位置を保ちます",
            compatBooleanPreferenceSummary("catalogReloadScrollTop")
        )
        assertEquals(
            "今回のリロードで消えたスレをカタログの末尾に継ぎ足します",
            compatBooleanPreferenceSummary("catalogAppendDropped")
        )
    }

    @Test
    fun settingsSnapshotLoadsDependenciesSynchronouslyAndKeepsLegacyAliases() {
        val groups = compatSettingsGroups("catalog")
        val values = compatSettingsSavedValues(
            path = "catalog",
            groups = groups,
            preferences = mapOf(
                "compat.catalog.catalogFindThreadDeleted" to "ON",
                "compat.catalog.catalogAppendDropped" to "ON",
                // A stable APK key wins over the former title-based key.
                "compat.catalog.catalogThreadSize" to "500スレ",
                "compat.catalog.スレッド数" to "300スレ",
                // Older builds stored this shared setting under either page.
                "compat.thread.プライバシー透明度" to "40%"
            )
        )

        assertEquals("ON", values["catalogFindThreadDeleted"])
        assertEquals("ON", values["catalogAppendDropped"])
        assertEquals("500スレ", values["catalogThreadSize"])
        assertEquals("40%", values["commonPrivacyAlpha"])
    }

    @Test
    fun designChoicesPersistTheExactReferenceApkRawValuesAndReadLegacyJapaneseValues() {
        val choices = mapOf(
            "designTheme" to listOf(
                "デフォルト" to "default",
                "モノクロ" to "mono",
                "ふたば" to "futaba",
                "ブルー" to "blue",
                "ピンク" to "pink",
                "ブラック" to "black"
            ),
            "designLoading" to listOf(
                "デフォルト" to "default",
                "アイコン" to "icon"
            ),
            "designTabSelectorLocation" to listOf(
                "ツールバーと二段で表示" to "above",
                "ツールバーの上に重ねる" to "over"
            )
        )
        choices.forEach { (key, values) ->
            values.forEach { (display, raw) ->
                assertEquals(raw, compatPreferenceStoredValue(key, display), "$key:$display")
                assertEquals(display, compatPreferenceDisplayValue(key, raw), "$key:$raw")
                assertEquals(display, compatPreferenceDisplayValue(key, display), "$key:legacy:$display")
            }
        }
        assertEquals("モノクローム", compatPreferenceSummaryValue("designTheme", "mono"))
        assertEquals("ツールバーの上", compatPreferenceSummaryValue("designTabSelectorLocation", "above"))
        assertEquals("ツールバーに重ねる", compatPreferenceSummaryValue("designTabSelectorLocation", "over"))
    }

    @Test
    fun stableKeyReadsLegacyTitleAndSharesPrivacyAlpha() {
        val legacy = mapOf("compat.thread.フォントサイズ" to "18")
        assertEquals("18", legacy.compatPreferenceValue("thread", "threadFontSize", "フォントサイズ"))
        assertEquals(
            "表示しない",
            mapOf("compat.thread.あぷ小のサムネイルの読み込み" to "表示しない")
                .compatPreferenceValue(
                    "thread",
                    "threadUpsThumbMethod",
                    "あぷ小のサムネイルの読み込み",
                    "あぷ小の読み込み"
                )
        )

        val shared = mapOf(compatPreferenceStorageKey("catalog", "commonPrivacyAlpha") to "40%")
        assertEquals("40%", shared.compatPreferenceValue("thread", "commonPrivacyAlpha"))
        assertEquals(0.4f, parseCompatPercent("40%"))
        assertEquals(0.4f, compatPrivacyContentAlpha(parseCompatPercent("40%")))
        assertEquals(0.2f, compatPrivacyContentAlpha(parseCompatPercent("20%")))
        assertEquals(1f, compatPrivacyContentAlpha(parseCompatPercent("100%")))
        assertEquals(0.2f, compatPrivacyRenderAlpha(enabled = true, transparency = 0.2f))
        assertEquals(1f, compatPrivacyRenderAlpha(enabled = false, transparency = 0.2f))
        assertEquals(0.8f, compatPrivacyOverlayAlpha(enabled = true, transparency = 0.2f))
        assertEquals(0f, compatPrivacyOverlayAlpha(enabled = false, transparency = 0.2f))
        assertTrue(mapOf(COMPAT_COMMON_PRIVACY_STORAGE_KEY to "ON").compatPrivacyEnabled())
        assertTrue(mapOf("compat.catalog.プライバシー" to "ON").compatPrivacyEnabled())
        assertTrue(mapOf("compat.thread.プライバシー" to "ON").compatPrivacyEnabled())
        assertFalse(mapOf("compat.catalog.プライバシー" to "OFF").compatPrivacyEnabled())
        assertFalse(emptyMap<String, String>().compatPrivacyEnabled())
        assertFalse(
            mapOf(
                COMPAT_COMMON_PRIVACY_STORAGE_KEY to "OFF",
                "compat.catalog.プライバシー" to "ON"
            ).compatPrivacyEnabled()
        )
        assertEquals(
            CompatCatalogLayout.LIST,
            mapOf(COMPAT_CATALOG_VIEW_MODE_STORAGE_KEY to "1")
                .compatCatalogLayout(CompatCatalogLayout.GRID)
        )
        assertEquals(
            CompatCatalogLayout.GRID,
            mapOf(COMPAT_CATALOG_VIEW_MODE_STORAGE_KEY to "0")
                .compatCatalogLayout(CompatCatalogLayout.LIST)
        )
        assertEquals(
            CompatCatalogLayout.LIST,
            emptyMap<String, String>().compatCatalogLayout(CompatCatalogLayout.LIST)
        )
        assertEquals("0", compatCatalogLayoutStorageValue(CompatCatalogLayout.GRID))
        assertEquals("1", compatCatalogLayoutStorageValue(CompatCatalogLayout.LIST))
        assertNull(emptyMap<String, String>().compatPreferenceValue("thread", "unknown"))
    }

    @Test
    fun cacheLocationChangeClearsOnlyOrdinaryImagesBeforeSaving() = runBlocking {
        val ordinaryEvents = mutableListOf<String>()
        applyCompatCacheLocationChange(
            preferenceKey = "dummyImageCacheLocation",
            storedValue = "external_sd",
            clearOrdinaryImageCache = { ordinaryEvents += "clear" },
            savePreference = { ordinaryEvents += "save:$it" }
        )
        assertEquals(listOf("clear", "save:external_sd"), ordinaryEvents)

        val catalogEvents = mutableListOf<String>()
        applyCompatCacheLocationChange(
            preferenceKey = "dummyCatalogImageCacheLocation",
            storedValue = "internal",
            clearOrdinaryImageCache = { catalogEvents += "clear" },
            savePreference = { catalogEvents += "save:$it" }
        )
        assertEquals(listOf("save:internal"), catalogEvents)
    }

    @Test
    fun threadCacheQuotaUsesEnforcedKeyAndReadsFirstBuildKey() {
        assertEquals(
            "compat.storage.スレッドキャッシュ上限",
            compatPreferenceStorageKey("storage", "commonThreadCache")
        )
        assertEquals(
            "64MB",
            mapOf("compat.storage.commonThreadCache" to "64MB")
                .compatPreferenceValue("storage", "commonThreadCache")
        )
    }
}

private fun List<Pair<String, List<CompatSettingEntry>>>.flattenEntries(): List<CompatSettingEntry> =
    flatMap { it.second }
