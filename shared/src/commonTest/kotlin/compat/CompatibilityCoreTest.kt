package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.compat.COMPAT_REFERENCE_CATALOG_DROPPED_PATHS
import com.valoser.futacha.shared.ui.compat.COMPAT_REFERENCE_CATALOG_UNDO_PATH
import com.valoser.futacha.shared.ui.compat.COMPAT_REFERENCE_THREAD_SCROLL_PATH
import com.valoser.futacha.shared.ui.compat.COMPAT_REFERENCE_VIEWER_SCREEN_PATH
import com.valoser.futacha.shared.ui.compat.CompatReferenceCatalogDroppedIcon
import com.valoser.futacha.shared.ui.compat.CompatReferenceCatalogUndoIcon
import com.valoser.futacha.shared.ui.compat.CompatReferenceThreadScrollIcon
import com.valoser.futacha.shared.ui.compat.CompatReferenceViewerScreenIcon
import com.valoser.futacha.shared.ui.compat.CompatToolbarArtwork
import com.valoser.futacha.shared.ui.compat.buildCompatForestUrl
import com.valoser.futacha.shared.ui.compat.buildCompatFtbucketUrl
import com.valoser.futacha.shared.ui.compat.buildCompatFutapoUrl
import com.valoser.futacha.shared.ui.compat.buildCompatLegacyCatalogExtractRules
import com.valoser.futacha.shared.ui.compat.applyCompatLegacyPortableSettings
import com.valoser.futacha.shared.ui.compat.compatToolbarIcon
import com.valoser.futacha.shared.ui.compat.compatToolbarArtwork
import futacha.shared.generated.resources.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatibilityCoreTest {
    @Test
    fun helpUsesTheUnprefixedReferenceTitle() {
        assertEquals("ヘルプ", COMPAT_REFERENCE_HELP_TITLE)
    }

    @Test
    fun ngExtractionUsesTheReferenceTapAndLongPressActions() {
        assertEquals(CompatNgExtractionAction.REQUEST_DEL, compatNgExtractionAction(isLongClick = false))
        assertEquals(
            CompatNgExtractionAction.REQUEST_USER_DELETE,
            compatNgExtractionAction(isLongClick = true)
        )
    }

    @Test
    fun compatPlainTextPreservesLiteralAndNumericEmoji() {
        assertEquals(
            "😀👍🏽\n💡 😊 & 😀",
            "😀👍🏽<br>&#x1F4A1; &#128522; &amp; &#x1F600;".toCompatPlainText()
        )
        assertEquals("&#xD800;", "&#xD800;".toCompatPlainText())
    }

    @Test
    fun compatPlainTextRemovesFtbucketPreviewControlButKeepsOrdinaryText() {
        val html =
            "<a href=\"other/fu7199371.png\">fu7199371.png</a>" +
                "<span onclick=\"previewImg('id','other/fu7199371.png')\">[見る]</span><br>" +
                "本文[見る]"

        assertEquals("fu7199371.png\n本文[見る]", html.toCompatPlainText())
        assertEquals("fu7199371.png\n返信", "fu7199371.png[見る]<br>返信".toCompatPlainText())
    }

    @Test
    fun searchNormalizationFoldsReferenceCaseWidthAndHalfWidthKana() {
        assertEquals("abc ガッツポーズ", normalizeCompatSearchText("ＡｂＣ　ｶﾞｯﾂﾎﾟｰｽﾞ"))
        assertEquals("パピプペポ", normalizeCompatSearchText("ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ"))
    }

    @Test
    fun toolbarMastersAndReconciliationPreserveLegacyCountsDefaultsAndUserOrder() {
        val expected = mapOf(
            // The catalog's dropped-thread action is a normal default command;
            // "その他" remains the fixed overflow action outside this list.
            CompatToolbarSurface.CATALOG to (13 to 10),
            CompatToolbarSurface.THREAD to (19 to 14),
            CompatToolbarSurface.VIEWER to (10 to 8),
            CompatToolbarSurface.POST to (9 to 4)
        )
        expected.forEach { (surface, counts) ->
            val defaults = reconcileCompatToolbar(surface, emptyList())
            assertEquals(counts.first, defaults.size)
            assertEquals(counts.second, defaults.count(CompatToolbarItem::active))
            assertTrue(validateCompatToolbar(surface, defaults))
        }
        assertEquals(
            listOf("post", "reload", "search", "sort", "board", "tab", "privacy", "bypass", "check", "undo", "dropped", "quickng", "drawer"),
            compatToolbarMaster(CompatToolbarSurface.CATALOG).map { it.key }
        )
        assertEquals(
            "リロード前に戻す",
            compatToolbarMaster(CompatToolbarSurface.CATALOG).single { it.key == "undo" }.label
        )
        assertEquals(
            "リロード前に戻す",
            compatToolbarMaster(CompatToolbarSurface.THREAD).single { it.key == "undo" }.label
        )
        assertEquals(
            CompatToolbarRefreshTarget.CURRENT_CONTENT,
            compatToolbarRefreshTarget(CompatToolbarSurface.CATALOG, "reload")
        )
        assertEquals(
            CompatToolbarRefreshTarget.OPEN_THREADS,
            compatToolbarRefreshTarget(CompatToolbarSurface.CATALOG, "check")
        )
        assertEquals(
            CompatToolbarRefreshTarget.OPEN_THREADS,
            compatToolbarRefreshTarget(CompatToolbarSurface.THREAD, "check")
        )
        assertEquals(
            setOf("privacy", "bypass", "check"),
            compatToolbarMaster(CompatToolbarSurface.CATALOG).filterNot { it.defaultActive }.mapTo(mutableSetOf()) { it.key }
        )
        assertEquals(
            setOf("privacy", "bypass", "scroll", "check", "close"),
            compatToolbarMaster(CompatToolbarSurface.THREAD).filterNot { it.defaultActive }.mapTo(mutableSetOf()) { it.key }
        )
        assertEquals(
            listOf("download", "search", "back", "gallery", "left", "right", "share", "info", "screen", "privacy"),
            compatToolbarMaster(CompatToolbarSurface.VIEWER).map { it.key }
        )
        val postDefaults = reconcileCompatToolbar(CompatToolbarSurface.POST, emptyList())
        assertFalse(postDefaults.first { it.key == "sio" }.active)
        assertTrue(postDefaults.first { it.key == "send" }.active)
        assertTrue(postDefaults.first { it.key == "discard" }.active)
        val viewerDefaults = reconcileCompatToolbar(CompatToolbarSurface.VIEWER, emptyList())
        assertTrue(viewerDefaults.first { it.key == "left" }.active)
        assertTrue(viewerDefaults.first { it.key == "right" }.active)
        assertFalse(viewerDefaults.first { it.key == "share" }.active)
        assertFalse(viewerDefaults.first { it.key == "screen" }.active)
        val migratedViewer = reconcileCompatToolbar(
            CompatToolbarSurface.VIEWER,
            listOf(CompatToolbarItem("previous", 0, false), CompatToolbarItem("next", 1, true))
        )
        assertFalse(migratedViewer.first { it.key == "left" }.active)
        assertTrue(migratedViewer.first { it.key == "right" }.active)

        val upgraded = reconcileCompatToolbar(
            CompatToolbarSurface.CATALOG,
            listOf(
                CompatToolbarItem("drawer", 0, false),
                CompatToolbarItem("removed-command", 1, true),
                CompatToolbarItem("reload", 2, false),
                CompatToolbarItem("drawer", 3, true)
            )
        )
        assertFalse(upgraded.any { it.key == "removed-command" })
        assertTrue(upgraded.indexOfFirst { it.key == "drawer" } < upgraded.indexOfFirst { it.key == "reload" })
        assertFalse(upgraded.first { it.key == "drawer" }.active)
        assertFalse(upgraded.first { it.key == "reload" }.active)
        assertTrue(validateCompatToolbar(CompatToolbarSurface.CATALOG, upgraded))
    }

    @Test
    fun referenceToolbarKeepsDifferentCommandsOnDifferentGlyphs() {
        assertEquals(CompatToolbarGlyph.BOTTOM, compatToolbarGlyph("bottom"))
        assertEquals(CompatToolbarGlyph.AUTO_SCROLL, compatToolbarGlyph("autoscroll"))
        assertEquals(CompatToolbarGlyph.FILTER, compatToolbarGlyph("extract"))
        assertEquals(CompatToolbarGlyph.NG_TOGGLE, compatToolbarGlyph("quickng"))
        assertEquals(CompatToolbarGlyph.UNDO, compatToolbarGlyph("undo"))
        assertEquals(CompatToolbarGlyph.HISTORY, compatToolbarGlyph("dropped"))
        assertTrue(compatToolbarGlyph("bottom") != compatToolbarGlyph("autoscroll"))
        assertTrue(compatToolbarGlyph("extract") != compatToolbarGlyph("quickng"))
        assertTrue(compatToolbarGlyph("undo") != compatToolbarGlyph("dropped"))
        CompatToolbarSurface.entries.forEach { surface ->
            val glyphs = compatToolbarMaster(surface).map { compatToolbarGlyph(it.key) }
            assertFalse(
                CompatToolbarGlyph.MORE in glyphs,
                "$surface contains an unmapped toolbar glyph"
            )
            assertEquals(
                glyphs.size,
                glyphs.toSet().size,
                "$surface reuses one glyph for different commands"
            )
        }
    }

    @Test
    fun catalogUndoAndDroppedIconsKeepTheExactFinalApkArtwork() {
        assertTrue(COMPAT_REFERENCE_CATALOG_UNDO_PATH.startsWith("M151.17,196.03"))
        assertTrue(COMPAT_REFERENCE_CATALOG_UNDO_PATH.contains("L26.36,75.6"))
        assertEquals(3, COMPAT_REFERENCE_CATALOG_DROPPED_PATHS.size)
        assertTrue(COMPAT_REFERENCE_CATALOG_DROPPED_PATHS.first().contains("0.985 -0.17"))
        assertTrue(COMPAT_REFERENCE_CATALOG_DROPPED_PATHS.last().contains("v 5.21"))
        assertEquals("ReferenceCatalogUndo", CompatReferenceCatalogUndoIcon.name)
        assertEquals(256f, CompatReferenceCatalogUndoIcon.viewportWidth)
        assertEquals("ReferenceCatalogDropped", CompatReferenceCatalogDroppedIcon.name)
        assertEquals(16f, CompatReferenceCatalogDroppedIcon.viewportWidth)
        assertTrue(compatToolbarIcon("undo") === CompatReferenceCatalogUndoIcon)
        assertTrue(compatToolbarIcon("dropped") === CompatReferenceCatalogDroppedIcon)
        assertTrue(compatToolbarIcon("undo") !== compatToolbarIcon("dropped"))
    }

    @Test
    fun threadScrollAndViewerScreenKeepTheirDifferentFinalApkSilhouettes() {
        assertTrue(COMPAT_REFERENCE_THREAD_SCROLL_PATH.contains("M18,84H85V108H18Z"))
        assertTrue(COMPAT_REFERENCE_THREAD_SCROLL_PATH.contains("A35.5,35.5"))
        assertTrue(COMPAT_REFERENCE_VIEWER_SCREEN_PATH.contains("Q167,38 167,51"))
        assertTrue(COMPAT_REFERENCE_VIEWER_SCREEN_PATH.contains("M43,96L57,84V108Z"))
        assertEquals(192f, CompatReferenceThreadScrollIcon.viewportWidth)
        assertEquals(192f, CompatReferenceViewerScreenIcon.viewportWidth)
        assertTrue(compatToolbarIcon("scroll") === CompatReferenceThreadScrollIcon)
        assertTrue(compatToolbarIcon("screen") === CompatReferenceViewerScreenIcon)
        assertTrue(compatToolbarIcon("scroll") !== compatToolbarIcon("screen"))
    }

    @Test
    fun everyReferenceToolbarCommandUsesSurfaceSpecificFinalApkArtwork() {
        CompatToolbarSurface.entries.forEach { surface ->
            compatToolbarMaster(surface).forEach { item ->
                assertIs<CompatToolbarArtwork.Resource>(
                    compatToolbarArtwork(surface, item.key),
                    "$surface/${item.key} fell back to a generic Material icon"
                )
            }
            assertIs<CompatToolbarArtwork.Resource>(compatToolbarArtwork(surface, "other"))
        }

        fun resource(
            surface: CompatToolbarSurface,
            key: String,
            selected: Boolean = false
        ) = assertIs<CompatToolbarArtwork.Resource>(
            compatToolbarArtwork(surface, key, selected)
        ).drawable

        // Identical keys intentionally resolve to different reference files
        // on different screens.
        assertEquals(Res.drawable.menu_ico_thread_toolbar_gallery, resource(CompatToolbarSurface.THREAD, "gallery"))
        assertEquals(Res.drawable.menu_ico_viewer_toolbar_gallery, resource(CompatToolbarSurface.VIEWER, "gallery"))
        assertEquals(Res.drawable.menu_ico_post_attach, resource(CompatToolbarSurface.POST, "attach"))
        assertEquals(Res.drawable.menu_ico_post_pallete, resource(CompatToolbarSurface.POST, "pallete"))
        assertEquals(Res.drawable.menu_ico_post_reset, resource(CompatToolbarSurface.POST, "reset"))
        assertEquals(Res.drawable.menu_ico_post_discard, resource(CompatToolbarSurface.POST, "discard"))
        assertEquals(Res.drawable.menu_ico_post_network_info, resource(CompatToolbarSurface.POST, "network_info"))
        assertEquals(Res.drawable.menu_ico_viewer_toolbar_screen, resource(CompatToolbarSurface.VIEWER, "screen"))
        assertEquals(Res.drawable.catalog_undo, resource(CompatToolbarSurface.CATALOG, "undo"))
        assertEquals(Res.drawable.menu_ico_catalog_toolbar_dropped, resource(CompatToolbarSurface.CATALOG, "dropped"))
    }

    @Test
    fun referenceToolbarStateChangesSwapTheActualFinalApkResources() {
        fun resource(surface: CompatToolbarSurface, key: String, selected: Boolean) =
            assertIs<CompatToolbarArtwork.Resource>(
                compatToolbarArtwork(surface, key, selected)
            ).drawable

        assertEquals(
            Res.drawable.menu_ico_catalog_toolbar_server_bypass_off,
            resource(CompatToolbarSurface.CATALOG, "bypass", false)
        )
        assertEquals(
            Res.drawable.menu_ico_catalog_toolbar_server_bypass_on,
            resource(CompatToolbarSurface.CATALOG, "bypass", true)
        )
        assertEquals(
            Res.drawable.menu_ico_thread_toolbar_tab,
            resource(CompatToolbarSurface.THREAD, "tab", false)
        )
        assertEquals(
            Res.drawable.menu_ico_thread_toolbar_tab_update,
            resource(CompatToolbarSurface.THREAD, "tab", true)
        )
        assertEquals(Res.drawable.ngoff, resource(CompatToolbarSurface.CATALOG, "quickng", false))
        assertEquals(Res.drawable.ngon, resource(CompatToolbarSurface.CATALOG, "quickng", true))
        assertEquals(Res.drawable.autoscroll, resource(CompatToolbarSurface.THREAD, "autoscroll", false))
        assertEquals(Res.drawable.pause, resource(CompatToolbarSurface.THREAD, "autoscroll", true))
    }

    @Test
    fun postToolbarOnlyShowsOverflowWhileACommandIsHidden() {
        CompatToolbarSurface.entries.filterNot { it == CompatToolbarSurface.POST }.forEach { surface ->
            assertTrue(
                compatToolbarShowsOverflow(
                    surface,
                    reconcileCompatToolbar(surface, emptyList()).map { it.copy(active = true) }
                )
            )
        }
        val post = reconcileCompatToolbar(CompatToolbarSurface.POST, emptyList())
        assertTrue(compatToolbarShowsOverflow(CompatToolbarSurface.POST, post))
        assertFalse(
            compatToolbarShowsOverflow(
                CompatToolbarSurface.POST,
                post.map { it.copy(active = true) }
            )
        )
    }

    @Test
    fun ngScopeMatchesCatalogBoardsAndThreadTabs() {
        val boards = setOf("board")
        val tabs = setOf("tab")
        assertTrue(isCompatNgScopeValid(CompatNgKind.CATALOG_IMAGE, "board", boards, tabs))
        assertFalse(isCompatNgScopeValid(CompatNgKind.CATALOG_IMAGE, "tab", boards, tabs))
        assertTrue(isCompatNgScopeValid(CompatNgKind.CATALOG_EXTRACT, "*", boards, tabs))
        assertTrue(isCompatNgScopeValid(CompatNgKind.CATALOG_REFUSE, "*", boards, tabs))
        assertTrue(isCompatNgScopeValid(CompatNgKind.THREAD_IMAGE, "tab", boards, tabs))
        assertTrue(isCompatNgScopeValid(CompatNgKind.THREAD_IMAGE, "board", boards, tabs))
        assertTrue(isCompatNgScopeValid(CompatNgKind.THREAD_IGNORE, "*", boards, tabs))
        assertFalse(isCompatNgScopeValid(CompatNgKind.THREAD_IMAGE, "missing", boards, tabs))
    }

    @Test
    fun catalogLegacyExtractIgnoreAndRefuseRules_matchTheirSeparateSemantics() {
        val item = CatalogItem(
            id = "12345",
            threadUrl = "https://may.2chan.net/b/res/12345.htm",
            title = "猫の話題",
            thumbnailUrl = "https://may.2chan.net/b/thumb/123.jpg",
            fullImageUrl = "https://may.2chan.net/b/src/123.jpg",
            replyCount = 4
        )
        val extract = CompatNgRule("extract", CompatNgKind.CATALOG_EXTRACT, "*", "猫", 1)
        val ignore = CompatNgRule("ignore", CompatNgKind.CATALOG_IGNORE, "board", "猫", 1)
        val refuse = CompatNgRule("refuse", CompatNgKind.CATALOG_REFUSE, "board", item.threadUrl, 1)

        assertTrue(compatCatalogItemIsExtracted(item, listOf(extract)))
        assertTrue(compatCatalogItemMatchesRule(item, ignore))
        assertTrue(compatCatalogItemMatchesRule(item, refuse))
        assertEquals(listOf(extract), compatCatalogRulesForBoard(listOf(extract), "board"))

        val index = buildCompatCatalogRuleIndex(listOf(ignore, refuse, extract))
        assertTrue(index.hides(item))
        assertTrue(index.extracts(item))
    }

    @Test
    fun tenThousandNgRulesCanBeIndexedWithoutLegacyUpdateCrash() {
        val rules = (0 until MAX_COMPAT_NG_RULES).map { index ->
            CompatNgRule(
                id = "rule-$index",
                kind = CompatNgKind.CATALOG_REFUSE,
                scopeKey = "board",
                normalizedValue = "https://may.2chan.net/b/res/$index.htm",
                createdAtEpochMillis = index.toLong()
            )
        }

        val index = buildCompatCatalogRuleIndex(rules)

        assertEquals(MAX_COMPAT_NG_RULES, index.hiddenThreadValues.size)
        assertTrue(
            index.hides(
                CatalogItem(
                    id = "9999",
                    threadUrl = "https://may.2chan.net/b/res/9999.htm",
                    title = "last",
                    thumbnailUrl = null,
                    fullImageUrl = null,
                    replyCount = 0
                )
            )
        )

        val threadRules = rules.mapIndexed { ruleIndex, rule ->
            rule.copy(
                id = "thread-$ruleIndex",
                kind = CompatNgKind.THREAD_IGNORE,
                scopeKey = "tab",
                normalizedValue = if (ruleIndex == MAX_COMPAT_NG_RULES - 1) "ｶﾞｯﾂ" else "word-$ruleIndex"
            )
        }
        val threadIndex = buildCompatThreadNgRuleIndex(threadRules, "tab")
        assertEquals(MAX_COMPAT_NG_RULES, threadIndex.bodyWords.size)
        assertTrue(post(0, "1", "ガッツポーズ").matchesCompatThreadNg(threadIndex))
    }

    @Test
    fun threadLegacyIgnoreMatchesBodyAndRefuseMatchesHeaderOnly() {
        val body = post(0, "100", "本文の語").copy(subject = "別の題名")
        val headerOnly = post(1, "101", "本文は別").copy(subject = "別の題名")
        val bodyRule = CompatNgRule("body", CompatNgKind.THREAD_IGNORE, "*", "本文の語", 1)
        val headerRule = CompatNgRule("header", CompatNgKind.THREAD_REFUSE, "*", "別の題名", 1)
        assertTrue(body.matchesCompatThreadNg(listOf(bodyRule), "tab"))
        assertTrue(body.matchesCompatThreadNg(listOf(headerRule), "tab"))
        assertFalse(headerOnly.matchesCompatThreadNg(listOf(bodyRule), "tab"))
        assertTrue(headerOnly.matchesCompatThreadNg(listOf(headerRule), "tab"))
        val widthRule = CompatNgRule("width", CompatNgKind.THREAD_IGNORE, "*", "ｶﾞｯﾂ", 1)
        assertTrue(post(2, "102", "ガッツポーズ").matchesCompatThreadNg(listOf(widthRule), "tab"))
    }

    @Test
    fun threadImageNgUsesBoardScopeInContentAndNgExtraction() {
        val imagePost = post(0, "100", "画像").copy(imageUrl = "https://may.2chan.net/b/src/100.jpg")
        val boardRule = CompatNgRule(
            id = "image",
            kind = CompatNgKind.THREAD_IMAGE,
            scopeKey = "may-b",
            normalizedValue = checkNotNull(imagePost.imageUrl),
            createdAtEpochMillis = 1L
        )

        assertTrue(imagePost.matchesCompatThreadNg(listOf(boardRule), "tab-100", boardKey = "may-b"))
        assertFalse(imagePost.matchesCompatThreadNg(listOf(boardRule), "tab-100", boardKey = "img-b"))
        assertEquals(
            listOf(imagePost),
            extractCompatPosts(
                listOf(imagePost),
                CompatExtractionKind.NG,
                scopeKey = "tab-100",
                boardKey = "may-b",
                ngRules = listOf(boardRule)
            )
        )
    }

    @Test
    fun threadStatusNotice_keepsDeletedIsolatedAndAdminDeletedSeparate() {
        assertEquals(
            CompatThreadStatusFlags(isDeleted = true),
            parseCompatThreadStatusFlags("スレッドを立てた人によって削除されました")
        )
        assertEquals(
            CompatThreadStatusFlags(isIsolated = true),
            parseCompatThreadStatusFlags("削除依頼によって隔離されました")
        )
        assertEquals(
            CompatThreadStatusFlags(isAdminDeleted = true),
            parseCompatThreadStatusFlags("管理者によって削除されました")
        )
    }

    @Test
    fun viewerBackReturnsToItsActualCaller() {
        val threadViewer = CompatibilityWorkspaceState(
            host = CompatHost.Viewer("tab", 2, CompatViewerCaller.THREAD)
        )
        assertIs<CompatHost.ThreadWorkspace>(
            reduceCompatibilityWorkspace(threadViewer, CompatibilityEvent.Back).state.host
        )

        val galleryViewer = threadViewer.copy(
            host = CompatHost.Viewer("tab", 2, CompatViewerCaller.GALLERY)
        )
        assertEquals(
            CompatHost.Gallery("tab", index = 2),
            reduceCompatibilityWorkspace(galleryViewer, CompatibilityEvent.Back).state.host
        )
    }

    @Test
    fun settingsChildBackReturnsToSettingsRoot_beforeReturningToCaller() {
        val child = CompatibilityWorkspaceState(
            host = CompatHost.Settings(
                path = "design",
                origin = CompatHost.Main,
                returnToRoot = true
            )
        )

        val root = reduceCompatibilityWorkspace(child, CompatibilityEvent.Back).state
        assertEquals(
            CompatHost.Settings(path = "root", origin = CompatHost.Main, returnToRoot = true),
            root.host
        )

        val caller = reduceCompatibilityWorkspace(root, CompatibilityEvent.Back).state
        assertEquals(CompatHost.Main, caller.host)
    }

    @Test
    fun directlyOpenedSettingsChildFinishesBackToItsActualCaller() {
        val caller = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG)
        val child = CompatibilityWorkspaceState(
            host = CompatHost.Settings(path = "thread", origin = caller)
        )

        assertEquals(
            caller,
            reduceCompatibilityWorkspace(child, CompatibilityEvent.Back).state.host
        )
    }

    @Test
    fun openingHostAlwaysClearsDrawerState() {
        val state = CompatibilityWorkspaceState(
            host = CompatHost.Main,
            drawerPage = CompatDrawerPage.HISTORY
        )

        val result = reduceCompatibilityWorkspace(
            state,
            CompatibilityEvent.OpenHost(CompatHost.Settings(origin = CompatHost.Main))
        ).state

        assertEquals(CompatHost.Settings(origin = CompatHost.Main), result.host)
        assertNull(result.drawerPage)
    }

    @Test
    fun drawerSelectionIsRememberedWhenItIsOpenedAgain() {
        val openedHistory = reduceCompatibilityWorkspace(
            CompatibilityWorkspaceState(),
            CompatibilityEvent.OpenDrawer(CompatDrawerPage.HISTORY)
        ).state
        val closed = reduceCompatibilityWorkspace(
            openedHistory,
            CompatibilityEvent.CloseDrawer
        ).state
        val reopened = reduceCompatibilityWorkspace(
            closed,
            CompatibilityEvent.OpenDrawer(closed.lastDrawerPage ?: CompatDrawerPage.TABS)
        ).state

        assertEquals(CompatDrawerPage.HISTORY, closed.lastDrawerPage)
        assertEquals(CompatDrawerPage.HISTORY, reopened.drawerPage)
    }

    @Test
    fun openingThreadFromDrawerClearsDrawerBeforePersistenceCompletes() {
        val state = CompatibilityWorkspaceState(
            host = CompatHost.Main,
            drawerPage = CompatDrawerPage.HISTORY
        )

        // The reducer must also be safe when the navigation event arrives
        // before the drawer animation callback. This is the path used by
        // history/tab selection and protects the UI from a stale modal layer.
        val directOpen = reduceCompatibilityWorkspace(
            state,
            CompatibilityEvent.OpenThread("tab-1", CompatThreadOrigin.MAIN)
        ).state
        assertNull(directOpen.drawerPage)
        assertEquals(CompatHost.ThreadWorkspace(CompatThreadOrigin.MAIN), directOpen.host)

        val result = reduceCompatibilityWorkspace(
            state,
            CompatibilityEvent.CloseDrawer
        ).state

        assertNull(result.drawerPage)
        assertEquals(CompatHost.Main, result.host)

        val opened = reduceCompatibilityWorkspace(
            result,
            CompatibilityEvent.OpenThread("tab-1", CompatThreadOrigin.MAIN)
        ).state
        assertNull(opened.drawerPage)
        assertEquals(CompatHost.ThreadWorkspace(CompatThreadOrigin.MAIN), opened.host)
    }

    @Test
    fun replacingTabsDoesNotDropThreadDuringTransientWorkspaceFlowOrdering() {
        val old = tab("old", 2)
        val current = tab("current", 1)
        val state = CompatibilityWorkspaceState(
            host = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG),
            activeTabKey = old.key,
            tabs = listOf(old, current)
        )

        val replacement = reduceCompatibilityWorkspace(
            state,
            CompatibilityEvent.ReplaceTabs(listOf(current, current), activeTabKey = old.key)
        ).state

        assertEquals(listOf(current.key), replacement.tabs.map { it.key })
        assertEquals(current.key, replacement.activeTabKey)
        assertEquals(CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG), replacement.host)
    }

    @Test
    fun catalogBackReturnsToLaunchingBoardAfterSelectingAnotherBoardsTab() {
        val boardAThread = tab("a-thread", 1).copy(boardKey = "board-a")
        val boardBThread = tab("b-thread", 2).copy(boardKey = "board-b")
        val catalog = CompatibilityWorkspaceState(
            host = CompatHost.Catalog("board-a"),
            tabs = listOf(boardAThread, boardBThread),
            activeTabKey = boardAThread.key
        )
        val opened = reduceCompatibilityWorkspace(
            catalog,
            CompatibilityEvent.OpenThread(boardAThread.key, CompatThreadOrigin.CATALOG)
        )
        assertEquals("board-a", opened.state.catalogHostBoardKey)
        assertTrue(opened.effects.contains(CompatibilityEffect.PersistCatalogHost("board-a")))

        val selectedOtherBoard = reduceCompatibilityWorkspace(
            opened.state,
            CompatibilityEvent.SelectTab(boardBThread.key)
        ).state
        val returned = reduceCompatibilityWorkspace(selectedOtherBoard, CompatibilityEvent.Back).state

        assertEquals(CompatHost.Catalog("board-a"), returned.host)
    }

    @Test
    fun closingLastCatalogThreadReturnsToLaunchingCatalog() {
        val onlyTab = tab("only", 1).copy(boardKey = "thread-board")
        val initial = CompatibilityWorkspaceState(
            host = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG),
            catalogHostBoardKey = "launching-board",
            activeTabKey = onlyTab.key,
            tabs = listOf(onlyTab)
        )

        val closed = reduceCompatibilityWorkspace(
            initial,
            CompatibilityEvent.CloseTab(onlyTab.key, 100L)
        )

        assertEquals(CompatHost.Catalog("launching-board"), closed.state.host)
        assertTrue(closed.state.tabs.isEmpty())
        assertNull(closed.state.activeTabKey)
    }

    @Test
    fun closingLastCatalogThreadFallsBackToItsBoardWhenWorkspaceWasNotPersisted() {
        val onlyTab = tab("only", 1).copy(boardKey = "thread-board")
        val initial = CompatibilityWorkspaceState(
            host = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG),
            activeTabKey = onlyTab.key,
            tabs = listOf(onlyTab)
        )

        val closed = reduceCompatibilityWorkspace(
            initial,
            CompatibilityEvent.CloseTab(onlyTab.key, 100L)
        )

        assertEquals(CompatHost.Catalog("thread-board"), closed.state.host)
    }

    @Test
    fun reopeningCatalogTabKeepsReadBaselineAndMarksVisibleCountRead() {
        val existing = tab("thread", 1).copy(
            replyCount = 5,
            checkedReplyCount = 5,
            favorite = true,
            snapshotRevision = 77L,
            scrollAnchor = ScrollAnchor(postNo = "5", offsetPx = 12)
        )
        val candidate = existing.copy(
            title = "最新タイトル",
            replyCount = 7,
            checkedReplyCount = 0,
            favorite = false,
            snapshotRevision = 0L,
            scrollAnchor = ScrollAnchor(),
            insertedAtEpochMillis = 999L
        )

        val reopened = mergeCompatCatalogTab(existing, candidate, markCatalogCountRead = true)

        assertEquals(7, reopened.replyCount)
        assertEquals(7, reopened.checkedReplyCount)
        assertEquals(0, reopened.unreadCount)
        assertTrue(reopened.favorite)
        assertEquals(77L, reopened.snapshotRevision)
        assertEquals(existing.scrollAnchor, reopened.scrollAnchor)
        assertEquals(existing.insertedAtEpochMillis, reopened.insertedAtEpochMillis)
    }

    @Test
    fun addingCatalogTabInBackgroundKeepsUnreadBaseline() {
        val existing = tab("thread", 1).copy(replyCount = 5, checkedReplyCount = 3)
        val candidate = existing.copy(replyCount = 7, checkedReplyCount = 0)

        val merged = mergeCompatCatalogTab(existing, candidate, markCatalogCountRead = false)
        val newBackgroundTab = mergeCompatCatalogTab(null, candidate, markCatalogCountRead = false)

        assertEquals(3, merged.checkedReplyCount)
        assertEquals(4, merged.unreadCount)
        assertEquals(0, newBackgroundTab.checkedReplyCount)
        assertEquals(7, newBackgroundTab.unreadCount)
    }

    @Test
    fun catalogMatchedWordsKeepActualWatchAndScopedExtractLabels() {
        val item = CatalogItem(
            id = "100",
            title = "ねこ と いぬ のスレ",
            replyCount = 1,
            thumbnailUrl = null,
            fullImageUrl = null,
            threadUrl = "https://may.2chan.net/b/res/100.htm"
        )
        val rules = listOf(
            CompatNgRule("dog", CompatNgKind.CATALOG_EXTRACT, "board", "いぬ", 1L),
            CompatNgRule("other", CompatNgKind.CATALOG_EXTRACT, "other-board", "ねこ", 1L)
        )

        assertEquals(
            listOf("ねこ", "いぬ"),
            compatCatalogMatchedWords(item, listOf("ねこ"), compatCatalogRulesForBoard(rules, "board"))
        )
    }

    @Test
    fun undoCloseDoesNotReinsertATabThatWasReopenedWhileUndoWasPending() {
        val initial = CompatibilityWorkspaceState(
            activeTabKey = "b",
            tabs = listOf(tab("a", 2), tab("b", 1))
        )
        val closed = reduceCompatibilityWorkspace(initial, CompatibilityEvent.CloseTab("a", 100L))

        // A history/catalog tap can reopen the same thread during the short
        // window before the durable close finishes. Undo must not add the
        // serialized copy a second time.
        val reopened = closed.state.copy(
            tabs = listOf(tab("a", 3)) + closed.state.tabs
        )
        val restored = reduceCompatibilityWorkspace(reopened, CompatibilityEvent.UndoClose)

        assertEquals(listOf("a", "b"), restored.state.tabs.map { it.key })
    }

    @Test
    fun closeTabUndoExpirySaturatesAtLongMaximum() {
        val initial = CompatibilityWorkspaceState(
            activeTabKey = "a",
            tabs = listOf(tab("a", 1))
        )

        val reduced = reduceCompatibilityWorkspace(
            initial,
            CompatibilityEvent.CloseTab("a", Long.MAX_VALUE - 1L)
        )

        assertEquals(Long.MAX_VALUE, reduced.state.pendingClose?.expiresAtEpochMillis)
    }

    @Test
    fun closingAnotherTabWhileUndoToastIsVisibleKeepsTabsAndLatestUndoBatchValid() {
        val initial = CompatibilityWorkspaceState(
            activeTabKey = "a",
            tabs = listOf(tab("a", 1), tab("b", 2))
        )

        val afterFirstClose = reduceCompatibilityWorkspace(
            initial,
            CompatibilityEvent.CloseTab("a", 100L)
        ).state
        val afterSecondClose = reduceCompatibilityWorkspace(
            afterFirstClose,
            CompatibilityEvent.CloseTab("b", 101L)
        ).state

        assertTrue(afterSecondClose.tabs.isEmpty())
        assertEquals("b", afterSecondClose.pendingClose?.tabs?.single()?.tab?.key)
        assertEquals(
            listOf("b"),
            reduceCompatibilityWorkspace(afterSecondClose, CompatibilityEvent.UndoClose)
                .state.tabs
                .map(CompatTab::key)
        )
    }

    @Test
    fun duplicateHistoryEntriesAreCollapsedBeforeDrawerRendering() {
        val entry = CompatHistoryEntry(
            canonicalUrl = "https://may.2chan.net/b/res/123.htm",
            originalUrl = "https://may.2chan.net/b/res/123.htm",
            boardKey = "board",
            boardName = "二次元裏",
            threadNo = "123",
            title = "title",
            contentUpdatedAtEpochMillis = 1L
        )

        assertEquals(listOf(entry), distinctCompatHistory(listOf(entry, entry)))
    }

    @Test
    fun duplicateBoardKeysAreCollapsedBeforeMainListRendering() {
        val board = CompatBoard(
            key = compatBoardKey("https://img.2chan.net/t/"),
            name = "二次元裏",
            canonicalUrl = "https://img.2chan.net/t/",
            originalUrl = "https://img.2chan.net/t/",
            sortOrder = 0
        )
        val repeatedKey = board.copy(name = "重複", sortOrder = 1)

        assertEquals(listOf(board), distinctCompatBoards(listOf(board, repeatedKey)))
    }

    @Test
    fun headerActionsResolveTapIdentityAndExtractionWithoutDuplicatePrefix() {
        assertEquals(
            CompatHeaderTapTarget.Url("https://example.test/a"),
            compatHeaderTapTarget("name https://example.test/a。")
        )
        assertEquals(
            CompatHeaderTapTarget.Email("a.b@example.test"),
            compatHeaderTapTarget("mail a.b@example.test")
        )
        assertEquals("ID:abc", parseCompatPosterIdentity("ID:abc")?.display)
        assertEquals(CompatHeaderExtractionKind.IP, parseCompatPosterIdentity("IP:192.0.2.1")?.kind)

        val source = post(0, "100", "body").copy(posterId = "ID:abc")
        val reply = post(1, "101", ">No.100\nreply").copy(posterId = "ID:abc")
        val other = post(2, "102", "body").copy(posterId = "ID:def")
        assertEquals(listOf("101"), extractCompatHeaderPosts(listOf(source, reply, other), source, CompatHeaderExtractionKind.QUOTE).map { it.postNo })
        assertEquals(listOf("100", "101"), extractCompatHeaderPosts(listOf(source, reply, other), source, CompatHeaderExtractionKind.ID).map { it.postNo })
        assertEquals(listOf(CompatHeaderExtractionKind.QUOTE, CompatHeaderExtractionKind.ID), compatHeaderExtractionKinds(source, listOf(source, reply, other)))

        val textSource = post(0, "110", "ドラえもんはまだまだ続くと思うけど")
        val textReply = post(1, "111", ">ドラえもんはまだまだ続くと思うけど\nそうだね")
        assertEquals(
            listOf("111"),
            extractCompatHeaderPosts(listOf(textSource, textReply), textSource, CompatHeaderExtractionKind.QUOTE)
                .map { it.postNo }
        )

        val ipSource = post(0, "200", "body").copy(timestamp = "26/08/05(水)13:00:00 IP:192.0.2.1")
        val sameIp = post(1, "201", "body").copy(timestamp = "26/08/05(水)13:01:00 IP:192.0.2.1")
        assertEquals("IP:192.0.2.1", compatPosterIdentity(ipSource)?.display)
        assertEquals(listOf(CompatHeaderExtractionKind.IP), compatHeaderExtractionKinds(ipSource, listOf(ipSource, sameIp)))
        assertEquals(
            listOf("200", "201"),
            extractCompatHeaderPosts(listOf(ipSource, sameIp), ipSource, CompatHeaderExtractionKind.IP).map { it.postNo }
        )
        assertEquals(1, compatHeaderText(ipSource).split("IP:192.0.2.1").size - 1)
    }

    @Test
    fun quoteResolver_resolvesNoIdIpAndFileReferences_asDistinctSources() {
        val source = post(
            0,
            "100",
            "画像本文 fu100.png",
            imageUrl = "https://dec.2chan.net/up2/src/fu100.png"
        ).copy(timestamp = "26/08/12(水)12:00:00 ID:SOURCE01")
        val idSource = post(1, "101", "ID本文").copy(timestamp = "26/08/12(水)12:01:00 ID:SOURCE02")
        val ipSource = post(2, "102", "IP本文").copy(timestamp = "26/08/12(水)12:02:00 IP:192.0.2.10")
        val reply = post(
            3,
            "103",
            ">>No.100\n>ID:SOURCE02\n>IP:192.0.2.10\n>fu100.png"
        )
        val posts = listOf(source, idSource, ipSource, reply)

        assertEquals("no:100", compatQuoteQueryForLine(">>No.100"))
        assertEquals("id:SOURCE02", compatQuoteQueryForLine(">ID:SOURCE02"))
        assertEquals("ip:192.0.2.10", compatQuoteQueryForLine(">IP:192.0.2.10"))
        assertEquals("file:fu100.png", compatQuoteQueryForLine(">fu100.png"))
        assertEquals("file:1786103362453.jpg", compatQuoteQueryForLine(">1786103362453.jpg"))
        assertEquals("file:1457582498.webm", compatQuoteQueryForLine(">1457582498.webm"))
        assertEquals("no:1786103362453", compatQuoteQueryForLine(">1786103362453"))
        assertEquals(listOf("100"), resolveCompatQuotePosts(posts, 3, "no:100").map { it.postNo })
        assertEquals(listOf("101"), resolveCompatQuotePosts(posts, 3, "id:SOURCE02").map { it.postNo })
        assertEquals(listOf("102"), resolveCompatQuotePosts(posts, 3, "ip:192.0.2.10").map { it.postNo })
        assertEquals(listOf("100"), resolveCompatQuotePosts(posts, 3, "file:fu100.png").map { it.postNo })
    }

    @Test
    fun postActionCandidates_includeTimestampIdentityAndEveryVisibleFileName() {
        val post = post(
            1,
            "101",
            "本文 fu101.png と fu101.webm<br>本文2",
            imageUrl = "https://dec.2chan.net/up2/src/fu101.png"
        ).copy(
            timestamp = "26/08/12(水)12:00:00 ID:abc12345 IP:192.0.2.11",
            thumbnailUrl = "https://dec.2chan.net/up2/thumb/fu101s.jpg",
            mail = "sage"
        )

        val candidates = compatPostActionCandidates(post)
        assertEquals(listOf("No", "ID", "IP", "file", "file", "file", "mail", "本文", "本文"), candidates.map { it.label })
        assertEquals(listOf("fu101.png", "fu101s.jpg", "fu101.webm"), candidates.filter { it.label == "file" }.map { it.value })
    }

    @Test
    fun posterReplyCounts_includeIdsAndIps_fromRawSnapshot() {
        val posts = listOf(
            post(0, "1", "one").copy(posterId = "ID:abc"),
            post(1, "2", "two").copy(posterId = "ID:abc"),
            post(2, "3", "three").copy(timestamp = "26/08/09 IP:192.0.2.1"),
            post(3, "4", "four").copy(timestamp = "26/08/09 IP:192.0.2.1"),
            post(4, "5", "five").copy(posterId = "ID:other")
        )
        val counts = compatPosterReplyCounts(posts)
        assertEquals(2, compatPosterReplyCount(posts[0], counts))
        assertEquals(2, compatPosterReplyCount(posts[2], counts))
        assertNull(compatPosterReplyCount(posts[4], counts))
    }

    @Test
    fun posterIdentityProgress_matchesSampleCurrentOverTotalAndKeepsIp() {
        val posts = listOf(
            post(0, "1", "one").copy(posterId = "ID:abc", timestamp = "26/08/11 ID:abc"),
            post(1, "2", "two").copy(posterId = "ID:other", timestamp = "26/08/11 ID:other"),
            post(2, "3", "three").copy(posterId = "ID:abc", timestamp = "26/08/11 ID:abc"),
            post(3, "4", "four").copy(timestamp = "26/08/11 IP:192.0.2.1"),
            post(4, "5", "five").copy(timestamp = "26/08/11 IP:192.0.2.1")
        )

        val idProgress = compatPosterIdentityProgress(posts[2], posts)
            .single { it.identity == CompatPosterIdentity(CompatHeaderExtractionKind.ID, "abc") }
        assertEquals("2/2", idProgress.label)
        assertEquals(
            "1/2",
            compatPosterIdentityProgress(posts[0], posts)
                .single { it.identity == CompatPosterIdentity(CompatHeaderExtractionKind.ID, "abc") }
                .label
        )

        val ipProgress = compatPosterIdentityProgress(posts[4], posts)
            .single { it.identity == CompatPosterIdentity(CompatHeaderExtractionKind.IP, "192.0.2.1") }
        assertEquals("2/2", ipProgress.label)
        assertEquals(listOf("ID:abc"), compatPosterIdentities(posts[0]).map { it.display })

        val indexed = compatPosterIdentityProgressByPost(posts)
        assertEquals(
            "2/2",
            indexed.getValue("3")
                .single { it.identity == CompatPosterIdentity(CompatHeaderExtractionKind.ID, "abc") }
                .label
        )
        assertEquals(
            "1/2",
            indexed.getValue("1")
                .single { it.identity == CompatPosterIdentity(CompatHeaderExtractionKind.ID, "abc") }
                .label
        )
        assertEquals(
            "2/2",
            indexed.getValue("5")
                .single { it.identity == CompatPosterIdentity(CompatHeaderExtractionKind.IP, "192.0.2.1") }
                .label
        )
    }

    @Test
    fun legacyKeywordBackup_decodesBase64_andPreservesBoardScopes() {
        val json = """
            {"strFileType":"keyword",
             "arrCatalogExtractList":[["https%3A%2F%2Fmay.2chan.net%2Fb%2F","watch%20word"]],
             "arrCatalogIgnoreList":[["allboard","bad%20word"]],
             "arrThreadRefuseList":["header%20word"],
             "arrThreadRefuseOnlyList":[["board%20header","https%3A%2F%2Fmay.2chan.net%2Fb%2F"]],
             "arrThreadIgnoreList":["body%20word"],
             "arrThreadIgnoreOnlyList":[]}
        """.trimIndent()
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val encoded = kotlin.io.encoding.Base64.encode(json.encodeToByteArray())
        val result = decodeCompatLegacyBackup(encoded)

        assertEquals("keyword", result.fileType)
        assertEquals("https://may.2chan.net/b/", result.catalogWatchWords.single().boardUrl)
        assertEquals("watch word", result.catalogWatchWords.single().word)
        assertEquals(null, result.catalogNgWords.single().boardUrl)
        assertEquals(setOf("header word", "body word", "board header"),
            (result.threadNgHeaders + result.threadNgWords).map { it.word }.toSet())

        val board = CompatBoard(
            key = compatBoardKey("https://may.2chan.net/b/"),
            name = "may/b",
            canonicalUrl = "https://may.2chan.net/b/",
            originalUrl = "https://may.2chan.net/b/",
            sortOrder = 0
        )
        val extractRules = buildCompatLegacyCatalogExtractRules(listOf(result), listOf(board), 10L)
        assertEquals(listOf(board.key), extractRules.map(CompatNgRule::scopeKey))
        assertEquals("watch word", extractRules.single().normalizedValue)
        assertFalse(extractRules.any { it.scopeKey == "*" })
    }

    @Test
    fun legacySettingBackup_mapsSafeSettingsToCompatStorageKeys() {
        val json = """
            {"strFileType":"setting","designTheme":"black","designTabSelectorLocation":"above","designTabSelectorOpened":false,
             "arrBoardList":[["may%2Fb","http%3A%2F%2Fmay.2chan.net%2Fb%2F","999999","1","201"]],
             "catalogFastScroll":true,
             "threadFontSize":"18","commonPrivacy":true,"commonPrivacyAlpha":"40","commonPostDeleteKey":"1234",
             "controlTouchScroll":true,"controlThreadCloseBack":true,
             "threadHideDefaultNameAndSubject":true,"threadHeaderQuoteSimple":true,
             "viewerPreloadMode":"wifi","imageNgPhashThreshold":"11",
             "backgroundThreadExistCheck":"usually","backgroundThreadUpdateCheck":"always",
             "threadHeaderSoudaneDisplay":"simple|right","threadUpsThumbMethod":"preload",
             "catalogGridViewTitleLength":"6","catalogGridViewTitleFontSize":"13",
             "catalogListViewTitleLength":"8","catalogListViewTitleFontSize":"12",
             "galleryGridViewPortraitClmNum":"4","galleryGridViewLandscapeClmNum":"6",
             "networkImageParallel":"8","catalogAppendDropped":true,"catalogReloadScrollTop":true,"catalogViewMode":1,"catalogSort":3,
             "imageSearchTargets":["google.url","iqdb.file","bing.url"],
             "arrCatalogToolbarList":[["reload","1","2"],["post","0","0"],["unknown","1","1"]],
             "arrViewerToolbarList":[["previous","1","1"],["next","0","0"]],
             "networkCacheServerBypass":true,
             "networkCacheServerCheckDate":"2026/08/11",
             "networkCacheServerMessage":"使用可能"}
        """.trimIndent()
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val encoded = kotlin.io.encoding.Base64.encode(json.encodeToByteArray())
        val result = decodeCompatLegacyBackup(encoded)

        assertEquals("black", result.preferences["compat.design.designTheme"])
        assertEquals("may/b", result.boards.single().name)
        assertEquals("https://may.2chan.net/b/", result.boards.single().canonicalUrl)
        assertEquals(0, result.boards.single().sortOrder)
        assertEquals("above", result.preferences["compat.design.designTabSelectorLocation"])
        assertEquals("OFF", result.preferences["compat.design.designTabSelectorOpened"])
        assertEquals("ON", result.preferences["compat.catalog.catalogFastScroll"])
        assertEquals("18", result.preferences["compat.thread.threadFontSize"])
        assertEquals("40%", result.preferences["compat.common.commonPrivacyAlpha"])
        assertEquals("ON", result.preferences["compat.common.commonPrivacy"])
        assertEquals("1", result.preferences["compat.catalog.catalogViewMode"])
        assertEquals(CompatCatalogSort.MANY, result.catalogSort)
        assertEquals("1234", result.preferences["compat.common.commonPostDeleteKey"])
        assertEquals("ON", result.preferences["compat.control.controlTouchScroll"])
        assertEquals("ON", result.preferences["compat.control.controlThreadCloseBack"])
        assertEquals("ON", result.preferences["compat.thread.threadHideDefaultNameAndSubject"])
        assertEquals("ON", result.preferences["compat.thread.threadHeaderQuoteSimple"])
        assertEquals("wifi", result.preferences["compat.viewer.viewerPreloadMode"])
        assertEquals("usually", result.preferences["compat.background.backgroundThreadExistCheck"])
        assertEquals("usually", result.preferences["compat.background.backgroundThreadUpdateCheck"])
        assertEquals("シンプル(右寄せ)", result.preferences["compat.thread.threadHeaderSoudaneDisplay"])
        assertEquals("表示する(先読み)", result.preferences["compat.thread.threadUpsThumbMethod"])
        assertEquals("11", result.preferences["compat.thread.threadImageNgPhashThreshold"])
        assertEquals("6文字", result.preferences["compat.catalog.catalogGridViewTitleLength"])
        assertEquals("13", result.preferences["compat.catalog.catalogGridViewTitleFontSize"])
        assertEquals("8文字", result.preferences["compat.catalog.catalogListViewTitleLength"])
        assertEquals("12", result.preferences["compat.catalog.catalogListViewTitleFontSize"])
        assertEquals("4", result.preferences["compat.viewer.galleryGridViewPortraitClmNum"])
        assertEquals("6", result.preferences["compat.viewer.galleryGridViewLandscapeClmNum"])
        assertEquals("8", result.preferences["compat.network.networkImageParallel"])
        assertEquals("ON", result.preferences["compat.catalog.catalogAppendDropped"])
        assertEquals("ON", result.preferences["compat.catalog.catalogReloadScrollTop"])
        assertEquals(
            "google.url|iqdb.file|bing.url",
            result.preferences["compat.image_search.engines"]
        )
        val catalogToolbar = result.toolbars.getValue(CompatToolbarSurface.CATALOG)
        assertEquals(false, catalogToolbar.first { it.key == "post" }.active)
        assertEquals(true, catalogToolbar.first { it.key == "reload" }.active)
        assertFalse(catalogToolbar.any { it.key == "unknown" })
        val viewerToolbar = result.toolbars.getValue(CompatToolbarSurface.VIEWER)
        assertEquals(true, viewerToolbar.first { it.key == "left" }.active)
        assertEquals(false, viewerToolbar.first { it.key == "right" }.active)
        assertEquals("ON", result.preferences["compat.network.cache.enabled"])
        assertEquals(
            "2026/08/11 - 使用可能",
            result.preferences["compat.network.cache.status"]
        )

        val savedPreferences = mutableMapOf<String, String>()
        val savedCatalogPreferences = mutableMapOf<String, CompatCatalogPreference>()
        val savedToolbars = mutableMapOf<CompatToolbarSurface, List<CompatToolbarItem>>()
        val restoredBoard = CompatBoard(
            key = compatBoardKey(result.boards.single().canonicalUrl),
            name = result.boards.single().name,
            canonicalUrl = result.boards.single().canonicalUrl,
            originalUrl = result.boards.single().originalUrl,
            sortOrder = result.boards.single().sortOrder
        )
        runBlocking {
            applyCompatLegacyPortableSettings(
                backups = listOf(result),
                availableBoards = listOf(restoredBoard),
                savePreference = { key, value -> savedPreferences[key] = value },
                loadCatalogPreference = { key -> CompatCatalogPreference(key) },
                saveCatalogPreference = { preference ->
                    savedCatalogPreferences[preference.boardKey] = preference
                },
                saveToolbar = { surface, items -> savedToolbars[surface] = items }
            )
        }
        assertEquals("ON", savedPreferences["compat.common.commonPrivacy"])
        assertEquals("1", savedPreferences["compat.catalog.catalogViewMode"])
        assertEquals(CompatCatalogSort.MANY, savedCatalogPreferences.getValue(restoredBoard.key).sort)
        assertEquals(false, savedToolbars.getValue(CompatToolbarSurface.CATALOG).first { it.key == "post" }.active)
        assertEquals(true, savedToolbars.getValue(CompatToolbarSurface.VIEWER).first { it.key == "left" }.active)

        val overlayJson = """{"strFileType":"setting","designTabSelectorLocation":"over","threadHeaderSoudaneDisplay":"show|right","threadUpsThumbMethod":"wifi"}"""
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val overlay = decodeCompatLegacyBackup(
            kotlin.io.encoding.Base64.encode(overlayJson.encodeToByteArray())
        )
        assertEquals("over", overlay.preferences["compat.design.designTabSelectorLocation"])
        assertEquals("通常(右寄せ)", overlay.preferences["compat.thread.threadHeaderSoudaneDisplay"])
        assertEquals("Wi-Fi回線のみ先読み", overlay.preferences["compat.thread.threadUpsThumbMethod"])

        val legacySearchJson = """{"strFileType":"setting","commonAscii2dSearch":true,"customSearchUriMulti":["TinEye Search","SauceNAO Search"]}"""
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val legacySearch = decodeCompatLegacyBackup(
            kotlin.io.encoding.Base64.encode(legacySearchJson.encodeToByteArray())
        )
        assertEquals(
            "lens.file|ascii2d.url|tineye.url|saucenao.url",
            legacySearch.preferences["compat.image_search.engines"]
        )

        val emptySearchJson = """{"strFileType":"setting","imageSearchTargets":[]}"""
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val emptySearch = decodeCompatLegacyBackup(
            kotlin.io.encoding.Base64.encode(emptySearchJson.encodeToByteArray())
        )
        assertEquals("", emptySearch.preferences["compat.image_search.engines"])
    }

    @Test
    fun canonicalThreadUrl_normalizesSchemeHostQueryAndSlash() {
        val result = canonicalizeThreadUrl("HTTP://MAY.2CHAN.NET//B/res/12345.htm?foo=1#x")
        assertEquals("https://may.2chan.net/B/res/12345.htm", result?.canonicalUrl)
        assertEquals("https://may.2chan.net/B/", result?.canonicalBoardUrl)
        assertEquals("12345", result?.threadNo)
    }

    @Test
    fun externalArchiveLinks_matchReferenceApkRoutes() {
        val mayThread = "https://may.2chan.net/b/res/12345.htm"
        assertEquals(
            "http://futabaforest.net/b/res/12345.htm",
            buildCompatForestUrl(mayThread)
        )
        assertEquals(
            "https://kako.futakuro.com/futa/may_b/12345/",
            buildCompatFutapoUrl(mayThread)
        )
        assertTrue(buildCompatFtbucketUrl(mayThread).contains("rooturl=https%3A%2F%2Fmay.2chan.net%2Fb%2Fres%2F12345.htm"))
        assertEquals(null, buildCompatForestUrl("https://img.2chan.net/b/res/12345.htm"))
    }

    @Test
    fun canonicalUrls_rejectNonFutabaAndBoardThreadMixup() {
        assertNull(canonicalizeThreadUrl("https://example.com/b/res/1.htm"))
        assertNull(canonicalizeBoardUrl("https://may.2chan.net/b/res/1.htm"))
        assertEquals("https://may.2chan.net/b/", canonicalizeBoardUrl("http://MAY.2CHAN.NET/b?mode=cat"))
    }

    @Test
    fun canonicalBoardUrl_stripsLegacyBoardEntryPoint() {
        assertEquals(
            "https://may.2chan.net/b/",
            canonicalizeBoardUrl("https://may.2chan.net/b/futaba.php")
        )
        assertEquals(
            "https://may.2chan.net/b/",
            canonicalizeBoardUrl("https://may.2chan.net//b/futaba.htm/?mode=cat")
        )
        assertEquals(
            canonicalizeThreadUrl("https://may.2chan.net/b/res/123.htm")?.canonicalBoardUrl,
            canonicalizeBoardUrl("https://may.2chan.net/b/futaba.php")
        )
    }

    @Test
    fun closingNonCurrentTab_keepsSelectedTabKeyAndUndoRestoresOrder() {
        val tabs = listOf(tab("a", 3), tab("b", 2), tab("c", 1))
        val initial = CompatibilityWorkspaceState(activeTabKey = "b", tabs = tabs)
        val closed = reduceCompatibilityWorkspace(initial, CompatibilityEvent.CloseTab("a", 100L))
        assertEquals("b", closed.state.activeTabKey)
        assertEquals(listOf("b", "c"), closed.state.tabs.map { it.key })
        val restored = reduceCompatibilityWorkspace(closed.state, CompatibilityEvent.UndoClose)
        assertEquals(listOf("a", "b", "c"), restored.state.tabs.map { it.key })
        assertEquals("b", restored.state.activeTabKey)
        assertNull(restored.state.pendingClose)
    }

    @Test
    fun closingEarlierNonCurrentTab_doesNotShiftSelectedTabByNumericIndex() {
        val tabs = listOf(tab("a", 4), tab("b", 3), tab("c", 2), tab("d", 1))
        val initial = CompatibilityWorkspaceState(activeTabKey = "c", tabs = tabs)
        val closed = reduceCompatibilityWorkspace(initial, CompatibilityEvent.CloseTab("a", 100L))

        // The APK's position-based selection shifts to d here. Compat mode
        // deliberately keeps the selected tab by stable key instead.
        assertEquals(listOf("b", "c", "d"), closed.state.tabs.map { it.key })
        assertEquals("c", closed.state.activeTabKey)
    }

    @Test
    fun sameTabSelection_scrollsBottom_butOtherTabCollapsesSearch() {
        val state = CompatibilityWorkspaceState(
            activeTabKey = "a",
            tabs = listOf(tab("a", 2), tab("b", 1)),
            search = CompatSearchState(query = "ID", focused = true)
        )
        val same = reduceCompatibilityWorkspace(state, CompatibilityEvent.SelectTab("a"))
        assertIs<CompatibilityEffect.ScrollTabToBottom>(same.effects.single())
        val other = reduceCompatibilityWorkspace(state, CompatibilityEvent.SelectTab("b"))
        assertEquals("b", other.state.activeTabKey)
        assertNull(other.state.search)
    }

    @Test
    fun closingMultipleTabs_usesCallerSelection_andUndoRestoresExactOrder() {
        val tabs = listOf(tab("a", 4), tab("b", 3), tab("c", 2), tab("d", 1))
        val initial = CompatibilityWorkspaceState(activeTabKey = "c", tabs = tabs)
        val closed = reduceCompatibilityWorkspace(
            initial,
            CompatibilityEvent.CloseTabs(setOf("a", "c"), nowEpochMillis = 100L)
        )
        assertEquals(listOf("b", "d"), closed.state.tabs.map { it.key })
        assertEquals("d", closed.state.activeTabKey)
        val effect = assertIs<CompatibilityEffect.PersistClosedTabs>(closed.effects.first())
        assertEquals(setOf("a", "c"), effect.tabKeys)
        assertEquals(
            mapOf("a" to tabs[0].scrollAnchor, "c" to tabs[2].scrollAnchor),
            effect.finalScrollAnchors
        )

        val restored = reduceCompatibilityWorkspace(closed.state, CompatibilityEvent.UndoClose)
        assertEquals(listOf("a", "b", "c", "d"), restored.state.tabs.map { it.key })
        assertEquals("c", restored.state.activeTabKey)
    }

    @Test
    fun backConsumesDrawerThenOverSelectorBeforeSearchAndHost() {
        var state = CompatibilityWorkspaceState(
            host = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG),
            selectorOpen = true,
            selectorPresentation = SelectorPresentation.OVER,
            search = CompatSearchState(query = "x", imeVisible = true, focused = true),
            drawerPage = CompatDrawerPage.TABS
        )
        state = reduceCompatibilityWorkspace(state, CompatibilityEvent.Back).state
        assertNull(state.drawerPage)
        assertTrue(state.selectorOpen)
        assertTrue(state.search!!.imeVisible)
        state = reduceCompatibilityWorkspace(state, CompatibilityEvent.Back).state
        assertFalse(state.selectorOpen)
        assertTrue(state.search!!.imeVisible)
        state = reduceCompatibilityWorkspace(state, CompatibilityEvent.Back).state
        assertFalse(state.search!!.imeVisible)
        assertTrue(state.search!!.focused)
        state = reduceCompatibilityWorkspace(state, CompatibilityEvent.Back).state
        assertFalse(state.search!!.focused)
        state = reduceCompatibilityWorkspace(state, CompatibilityEvent.Back).state
        assertNull(state.search)
        state = reduceCompatibilityWorkspace(state, CompatibilityEvent.Back).state
        assertIs<CompatHost.Main>(state.host)
    }

    @Test
    fun aboveSelectorDoesNotConsumeHostBack() {
        val state = CompatibilityWorkspaceState(
            host = CompatHost.Catalog("board"),
            selectorOpen = true,
            selectorPresentation = SelectorPresentation.ABOVE
        )
        val result = reduceCompatibilityWorkspace(state, CompatibilityEvent.Back)
        assertIs<CompatHost.Main>(result.state.host)
        assertTrue(result.state.selectorOpen)
    }

    @Test
    fun quoteResolver_supportsNumberAndNoPrefix_withoutResolvingFuturePosts() {
        val posts = listOf(
            post(0, "100", "最初"),
            post(1, "101", ">>100<br>返信"),
            post(2, "102", ">>No. 101<br>次の返信")
        )
        assertEquals("no:100", compatQuoteQueryForLine(">>100 です"))
        assertEquals("no:101", compatQuoteQueryForLine(">>No. 101"))
        assertEquals("no:101", compatQuoteQueryForLine(">No.101"))
        assertEquals(listOf("100"), resolveCompatQuotePosts(posts, 1, "no:100").map { it.postNo })
        assertTrue(resolveCompatQuotePosts(posts, 1, "no:102").isEmpty())
        assertNull(compatMissingQuoteNotice())
    }

    @Test
    fun quoteResolver_textSearchesPreviousPostsNewestFirst_andCanMatchHeader() {
        val posts = listOf(
            post(0, "100", "同じ文章", author = "古い人"),
            post(1, "101", "同じ文章です", author = "新しい人"),
            post(2, "102", ">同じ文章")
        )
        assertEquals("text:同じ文章", compatQuoteQueryForLine("  >同じ文章"))
        assertEquals(
            listOf("101", "100"),
            resolveCompatQuotePosts(posts, 2, "text:同じ文章").map { it.postNo }
        )
        assertEquals(listOf("101"), resolveCompatQuotePosts(posts, 2, "text:新しい人").map { it.postNo })
    }

    @Test
    fun quoteResolver_matchesUploadedFileNameLikeReferenceApk() {
        val posts = listOf(
            post(
                0,
                "100",
                "画像レス",
                imageUrl = "https://dec.2chan.net/up2/src/fu12345.jpg"
            ),
            post(1, "101", ">fu12345.jpg")
        )

        assertEquals("file:fu12345.jpg", compatQuoteQueryForLine(">fu12345.jpg"))
        assertEquals(
            listOf("100"),
            resolveCompatQuotePosts(posts, 1, "file:fu12345.jpg").map { it.postNo }
        )
    }

    @Test
    fun quoteResolver_doesNotTreatAQuotedReuploadAsTheFileOwner() {
        val original = post(
            0,
            "100",
            "元画像 fu12345.jpg",
            imageUrl = "https://dec.2chan.net/up2/src/fu12345.jpg"
        )
        val reuser = post(1, "101", ">fu12345.jpg")
        val laterReply = post(2, "102", ">fu12345.jpg")

        assertEquals(
            listOf("100"),
            resolveCompatQuotePosts(listOf(original, reuser, laterReply), 2, "file:fu12345.jpg")
                .map { it.postNo }
        )
    }

    @Test
    fun quoteResolver_resolvesNumericFutabaImageAndVideoFileNames() {
        val image = post(
            0,
            "200",
            "画像ファイル名：1786103362453.jpg",
            imageUrl = "https://img.2chan.net/b/src/1786103362453.jpg",
            thumbnailUrl = "https://img.2chan.net/b/thumb/1786103362453s.jpg"
        )
        val video = post(
            1,
            "201",
            "動画ファイル名：1457582498.webm",
            imageUrl = "https://img.2chan.net/b/src/1457582498.webm",
            thumbnailUrl = "https://img.2chan.net/b/thumb/1457582498s.jpg"
        )
        val reply = post(
            2,
            "202",
            ">1786103362453.jpg\n>1457582498.webm"
        )

        assertEquals(
            listOf("200"),
            resolveCompatQuotePosts(listOf(image, video, reply), 2, "file:1786103362453.jpg")
                .map { it.postNo }
        )
        assertEquals(
            listOf("201"),
            resolveCompatQuotePosts(listOf(image, video, reply), 2, "file:1457582498.webm")
                .map { it.postNo }
        )
    }

    @Test
    fun scrollRestore_prefersStablePostNumber_thenClampsFallback() {
        val snapshot = CompatThreadSnapshot(
            tabKey = "t",
            revision = 2,
            fetchedAtEpochMillis = 2,
            posts = listOf(post(0, "100", "a"), post(1, "101", "b"), post(2, "102", "c"))
        )
        assertEquals(1, resolveCompatScrollIndex(snapshot, ScrollAnchor(postNo = "101", fallbackIndex = 0)))
        assertEquals(2, resolveCompatScrollIndex(snapshot, ScrollAnchor(postNo = "deleted", fallbackIndex = 99)))
    }

    @Test
    fun scrollRestore_preservesPixelOffsetForActiveAndPagerPages() {
        val snapshot = CompatThreadSnapshot(
            tabKey = "t",
            revision = 2,
            fetchedAtEpochMillis = 2,
            posts = listOf(post(0, "100", "a"), post(1, "101", "b"))
        )
        assertEquals(
            CompatScrollPosition(index = 1, offsetPx = 37),
            resolveCompatScrollPosition(
                snapshot,
                ScrollAnchor(postNo = "101", offsetPx = 37, fallbackIndex = 0)
            )
        )
        assertEquals(
            CompatScrollPosition(index = 0, offsetPx = 0),
            resolveCompatScrollPosition(
                snapshot,
                ScrollAnchor(postNo = "missing", offsetPx = -10, fallbackIndex = -1)
            )
        )
    }

    @Test
    fun postActionCandidates_andQuickQuote_keepLegacyFieldOrder() {
        val post = CompatPostSnapshot(
            position = 1,
            postNo = "101",
            mail = "sage",
            posterId = "AbCd",
            timestamp = "now",
            messageHtml = "一行目<br>二行目",
            imageUrl = "https://may.2chan.net/b/src/test.jpg"
        )
        val candidates = compatPostActionCandidates(post)
        assertEquals(listOf("No", "ID", "file", "mail", "本文", "本文"), candidates.map { it.label })
        assertEquals(">一行目\n>二行目\n\n", compatQuickQuoteText(post))
        assertEquals(">一行目\n>二行目\n\n", compatQuoteSelection(candidates, setOf(4, 5)))
        assertEquals("", compatQuoteSelection(candidates, emptySet()))
        assertEquals(
            listOf(
                listOf("web", "抽出", "NG登録"),
                listOf("del", "削除", "そうだね"),
                listOf("クイック", "返信", "コピー")
            ),
            compatReferencePostContextLabels()
        )
    }

    @Test
    fun extractionKinds_applyThresholdsAndKeepNgPostsAvailableForNgExtraction() {
        val posts = listOf(
            post(0, "100", "https://example.com", author = "a").copy(saidaneLabel = "そうだねx4"),
            post(1, "101", "needle", author = "b").copy(referencedCount = 3, imageUrl = "https://x/image.jpg"),
            post(2, "102", "hidden", author = "c").copy(isDeleted = true)
        )
        val ng = CompatNgRule("ng", CompatNgKind.THREAD_POST_NO, "tab", "102", 1)
        assertEquals(listOf("100"), extractCompatPosts(posts, CompatExtractionKind.MANY_SAIDANE, "tab").map { it.postNo })
        assertEquals(listOf("101"), extractCompatPosts(posts, CompatExtractionKind.MANY_REPLIES, "tab").map { it.postNo })
        assertEquals(listOf("100"), extractCompatPosts(posts, CompatExtractionKind.CONTAINS_URL, "tab").map { it.postNo })
        assertEquals(listOf("101"), extractCompatPosts(posts, CompatExtractionKind.HAS_IMAGE, "tab").map { it.postNo })
        assertEquals(listOf("102"), extractCompatPosts(posts, CompatExtractionKind.NG, "tab", listOf(ng)).map { it.postNo })
    }

    private fun tab(key: String, order: Long): CompatTab = CompatTab(
        key = key,
        canonicalUrl = "https://may.2chan.net/b/res/$order.htm",
        originalUrl = "https://may.2chan.net/b/res/$order.htm",
        boardKey = "board",
        boardName = "二次元裏",
        threadNo = order.toString(),
        title = key,
        insertedAtEpochMillis = order,
        contentUpdatedAtEpochMillis = order
    )

    private fun post(
        position: Int,
        no: String,
        message: String,
        author: String? = null,
        imageUrl: String? = null,
        thumbnailUrl: String? = null
    ) = CompatPostSnapshot(
        position = position,
        postNo = no,
        author = author,
        timestamp = "2026/08/05",
        messageHtml = message,
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl
    )
}
