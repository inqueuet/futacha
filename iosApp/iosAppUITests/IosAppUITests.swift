import XCTest

/**
 * Simulator-level smoke coverage for the native scene host.  Kotlin/Native
 * tests cover persistence and profile transitions; this target verifies that
 * Xcode can install and launch the complete SwiftUI/Compose application.
 */
final class IosAppUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func boardCard(in app: XCUIApplication, url: String) -> XCUIElement {
        app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", url)
        ).firstMatch
    }

    private func compatibilityBoardCardAfterUnwinding(in app: XCUIApplication) -> XCUIElement {
        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        if !board.waitForExistence(timeout: 2) {
            // The compatibility profile intentionally restores its last
            // destination. A full suite can therefore relaunch into a nested
            // reference page (for example 更新履歴) left by another test.
            // Unwind through the product's own Back route instead of assuming
            // that a fresh process always starts at the board list.
            for _ in 0..<8 where !board.exists {
                let back = app.buttons["戻る"].firstMatch
                guard back.waitForExistence(timeout: 1), back.isHittable else { break }
                back.tap()
                _ = board.waitForExistence(timeout: 2)
            }
        }
        return board
    }

    private func compatibilityBoardListAfterUnwinding(in app: XCUIApplication) -> XCUIElement {
        let boardList = app.otherElements["compat-board-list"]
        if !boardList.waitForExistence(timeout: 2) {
            for _ in 0..<8 where !boardList.exists {
                let back = app.buttons["戻る"].firstMatch
                guard back.waitForExistence(timeout: 1), back.isHittable else { break }
                back.tap()
                _ = boardList.waitForExistence(timeout: 2)
            }
        }
        return boardList
    }

    private func makeApplication() -> XCUIApplication {
        let app = XCUIApplication()
        // Product launches must show the EULA before Compose is created. Most
        // navigation tests start after that one-time agreement; the dedicated
        // EULA test below covers the blocked first-launch route itself.
        app.launchArguments += [
            "-review.ugc_eula_accepted_version", "2026-08-22",
            // sample/1.apk's commonUsedVersion is migrated into the KMP
            // compatibility store. Keep unrelated UI tests on the already-read
            // version; Android and common tests exercise the mismatch path.
            "-commonUsedVersion", "8.5"
        ]
        return app
    }

    private func rotateToLandscape(_ app: XCUIApplication) throws {
        // Use the opposite landscape side from XCTest's commonly retained
        // `.landscapeLeft` sensor state. A same-value assignment is ignored by
        // CoreSimulator even when the foreground scene was launched portrait.
        XCUIDevice.shared.orientation = .landscapeRight
        let landscape = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in
                let frame = app.windows.firstMatch.frame
                return frame.width > frame.height
            },
            object: nil
        )
        let result = XCTWaiter().wait(for: [landscape], timeout: 10)
#if targetEnvironment(simulator)
        if result != .completed {
            throw XCTSkip(
                "iOS 26.5 CoreSimulator accepted the sensor rotation but did not rotate even Mobile Safari; run this assertion on a physical iPhone."
            )
        }
#endif
        XCTAssertEqual(
            result,
            .completed,
            "The application did not lay out in landscape."
        )
    }

    func testFutachaProfileReachesForeground() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1001"
        ]
        app.launch()

        XCTAssertTrue(
            app.wait(for: .runningForeground, timeout: 15),
            "The iOS SwiftUI/Compose host did not reach the foreground."
        )
        XCTAssertTrue(
            app.staticTexts["チュートリアル＠ふたちゃ"].waitForExistence(timeout: 10),
            "The Futacha board card was not exposed by the Compose accessibility tree."
        )
        let topMenu = app.buttons["メニュー"]
        XCTAssertTrue(topMenu.waitForExistence(timeout: 10), "The top menu is missing.")
        XCTAssertTrue(
            app.windows.firstMatch.frame.contains(topMenu.frame),
            "The top menu is clipped outside the iPad/iPhone window bounds."
        )
        XCTAssertTrue(
            app.staticTexts["https://www.example.com/t/futaba.php"].waitForExistence(timeout: 10),
            "The Futacha profile did not render its canonical tutorial board URL."
        )
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        XCTAssertTrue(board.waitForExistence(timeout: 10), "The Futacha tutorial board card was not tappable.")
        board.tap()
        XCTAssertTrue(
            app.staticTexts["多い順"].waitForExistence(timeout: 10),
            "The Futacha board did not open its catalog."
        )
        XCTAssertTrue(
            app.buttons["カタログ更新"].waitForExistence(timeout: 10),
            "The Futacha catalog did not expose its catalog actions."
        )
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(
            app.otherElements["futacha-thread-content"].waitForExistence(timeout: 10),
            "The Futacha catalog thread did not open its thread content."
        )
        XCTAssertTrue(app.buttons["返信"].exists, "The Futacha thread did not expose reply.")
        XCTAssertTrue(app.buttons["保存"].exists, "The Futacha thread did not expose save.")
        app.buttons["返信"].tap()
        XCTAssertTrue(app.textViews["コメント"].waitForExistence(timeout: 10), "The Futacha reply form did not open.")
        XCTAssertTrue(app.buttons["画像を選択"].exists, "The Futacha reply form did not expose image selection.")
        XCTAssertTrue(app.buttons["動画を選択"].exists, "The Futacha reply form did not expose video selection.")
        app.buttons["閉じる"].firstMatch.tap()

        // #50/#51: the real thread's attachment gallery must be reachable
        // after closing the reply form. Badge/fit-crop construction is covered
        // by the Kotlin/Native test suite; this verifies the iOS sheet route.
        let attachments = app.buttons["添付"]
        XCTAssertTrue(attachments.waitForExistence(timeout: 10), "The Futacha thread did not expose attachments.")
        attachments.tap()
        XCTAssertTrue(
            app.staticTexts.matching(
                NSPredicate(format: "label BEGINSWITH %@", "添付一覧 (")
            ).firstMatch.waitForExistence(timeout: 10),
            "The Futacha attachment gallery did not open."
        )
    }

    func testFutachaBulkBoardAddHidesDiscoveryAddress() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1037"
        ]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["チュートリアル＠ふたちゃ"].waitForExistence(timeout: 10),
            "The Futacha board list did not reach the foreground."
        )
        app.buttons["メニュー"].tap()
        let addBoard = app.staticTexts["新規追加"].firstMatch
        XCTAssertTrue(addBoard.waitForExistence(timeout: 5))
        addBoard.tap()

        let bulkAdd = app.buttons["板一覧から一括追加"].firstMatch
        XCTAssertTrue(bulkAdd.waitForExistence(timeout: 5))
        bulkAdd.tap()

        XCTAssertTrue(app.staticTexts["未登録の板をまとめて追加します。"].exists)
        XCTAssertFalse(app.staticTexts["板一覧URL"].exists)
        XCTAssertFalse(app.textFields["板一覧URL"].exists)
        XCTAssertEqual(app.textFields.count, 0, "The bulk-add dialog must not expose an address field.")
        XCTAssertFalse(app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "ふたばの板一覧ページから")
        ).firstMatch.exists)
    }

    func testToshiakiCompatibilityProfileReachesForeground() {
        let app = makeApplication()
        // NSUserDefaults' argument domain takes precedence over persisted
        // simulator state, giving this test an isolated profile selection.
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1002"
        ]
        app.launch()

        XCTAssertTrue(
            app.wait(for: .runningForeground, timeout: 15),
            "The iOS compatibility-profile SwiftUI/Compose host did not reach the foreground."
        )
        XCTAssertTrue(
            app.staticTexts["チュートリアル＠ふたちゃ"].waitForExistence(timeout: 10),
            "The compatibility board card was not exposed by the Compose accessibility tree."
        )
        XCTAssertTrue(
            app.staticTexts["https://img.2chan.net/t/"].waitForExistence(timeout: 10),
            "The compatibility profile did not render its Futaba tutorial board URL."
        )
        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10), "The compatibility tutorial board card was not tappable.")
        board.tap()
        XCTAssertTrue(
            app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10),
            "The compatibility board did not open its catalog grid."
        )
        XCTAssertTrue(
            app.buttons["リロード"].waitForExistence(timeout: 10),
            "The compatibility catalog did not expose its catalog actions."
        )
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(
            app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10),
            "The compatibility catalog thread did not open its thread pager."
        )
        XCTAssertTrue(app.buttons["書き込み"].exists, "The compatibility thread did not expose reply.")
        XCTAssertTrue(app.buttons["画像一覧"].exists, "The compatibility thread did not expose gallery.")

        // #48: the three-dot menu must lead to the thread display options;
        // this used to be disabled in the Android compatibility screen.
        app.buttons["その他"].firstMatch.tap()
        let displayOptions = app.buttons["表示オプション"]
        XCTAssertTrue(displayOptions.waitForExistence(timeout: 5), "The display-options command did not open.")
        XCTAssertTrue(displayOptions.isEnabled, "The display-options command is disabled.")
        displayOptions.tap()
        XCTAssertTrue(
            app.staticTexts["スレッド設定"].waitForExistence(timeout: 10),
            "The display-options command did not navigate to the thread settings."
        )
        XCTAssertTrue(app.staticTexts["全般"].exists, "The reference general category is missing.")
        XCTAssertTrue(app.staticTexts["スクロール更新"].exists, "The reference pull-refresh setting is missing.")
        XCTAssertTrue(app.staticTexts["オートスクロール量"].exists, "The reference auto-scroll amount is missing.")

        let settingsList = app.otherElements["compat-settings-list-thread"]
        let saidaneMode = app.staticTexts["そうだねの表示方法"].firstMatch
        for _ in 0..<3 where !saidaneMode.exists {
            app.staticTexts["オートスクロール量"].firstMatch.swipeUp()
        }
        XCTAssertTrue(saidaneMode.waitForExistence(timeout: 5), "The reference saidane display mode is missing.")
        saidaneMode.tap()
        let compactRight = app.staticTexts["シンプル(右寄せ)"].firstMatch
        XCTAssertTrue(compactRight.waitForExistence(timeout: 5), "The final APK saidane choices are incomplete.")
        compactRight.tap()

        let extractionCategory = app.staticTexts["抽出する閾値"].firstMatch
        for _ in 0..<4 where !extractionCategory.exists {
            if settingsList.exists {
                settingsList.swipeUp()
            } else {
                saidaneMode.swipeUp()
            }
        }
        XCTAssertTrue(extractionCategory.waitForExistence(timeout: 5), "The reference extraction category is missing.")
        XCTAssertTrue(app.staticTexts["そうだねが多いレス"].exists, "The saidane extraction threshold is missing.")
        XCTAssertTrue(app.staticTexts["返信が多いレス"].exists, "The quote extraction threshold is missing.")
        XCTAssertFalse(app.staticTexts["ふたちゃ拡張"].exists, "A non-reference category leaked into ThreadSettingActivity.")
        XCTAssertFalse(app.staticTexts["画像NG類似度閾値"].exists, "The image-NG threshold belongs in ImageNgActivity.")

        // ThreadTabActivity launches ThreadSettingActivity directly. Back
        // finishes to the thread instead of detouring through AppSettingActivity.
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(
            app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10),
            "The compatibility thread did not resume after closing settings."
        )

        app.buttons["書き込み"].tap()
        XCTAssertTrue(app.textViews["コメント"].waitForExistence(timeout: 10), "The compatibility reply form did not open.")
        XCTAssertTrue(app.buttons["送信する"].exists, "The compatibility reply form did not expose send.")
        XCTAssertTrue(app.buttons["添付画像"].exists, "The compatibility reply form did not expose image attachment.")
        XCTAssertTrue(app.buttons["手書き"].exists, "The compatibility reply form did not expose drawing attachment.")
        app.buttons["手書き"].tap()
        XCTAssertTrue(
            app.staticTexts["手書き"].waitForExistence(timeout: 10),
            "The reference drawing screen did not open."
        )
        app.buttons["パレット"].tap()
        XCTAssertTrue(app.buttons["主筆"].waitForExistence(timeout: 5), "The reference main brush is missing.")
        XCTAssertTrue(app.buttons["副筆"].exists, "The reference sub brush is missing.")
        XCTAssertTrue(app.buttons["色見本"].exists, "The reference colour picker is missing.")
        XCTAssertTrue(app.buttons["リセット"].exists, "The reference brush reset is missing.")
        app.buttons["色見本"].tap()
        let presets = app.descendants(matching: .any).matching(identifier: "compat-drawing-preset")
        XCTAssertEqual(presets.count, 12, "The drawing picker did not expose the reference twelve colours.")
    }

    func testToshiakiExplicitBackKeepsThreadTabAndReturnsThroughCatalog() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1102"
        ]
        app.launch()

        let boardList = compatibilityBoardListAfterUnwinding(in: app)
        XCTAssertTrue(boardList.waitForExistence(timeout: 15))
        let board = boardList.buttons.firstMatch
        XCTAssertTrue(board.waitForExistence(timeout: 15))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))

        let catalogItem = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "compat-catalog-item-")
        ).firstMatch
        XCTAssertTrue(catalogItem.waitForExistence(timeout: 10))
        catalogItem.tap()
        XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))

        let threadBack = app.buttons["compat-navigation-back"]
        XCTAssertTrue(threadBack.waitForExistence(timeout: 10), "The iOS thread navigation Back button is missing.")
        threadBack.tap()
        XCTAssertTrue(
            app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10),
            "The explicit Back button did not return to the originating catalog."
        )

        let tabSelectorButton = app.buttons["compat-toolbar-command-tab"]
        XCTAssertTrue(tabSelectorButton.waitForExistence(timeout: 10))
        tabSelectorButton.tap()
        XCTAssertTrue(
            app.otherElements["compat-tab-selector"].waitForExistence(timeout: 10),
            "Returning to the catalog unexpectedly closed the thread tab."
        )
        tabSelectorButton.tap()

        let catalogBack = app.buttons["compat-navigation-back"]
        XCTAssertTrue(catalogBack.waitForExistence(timeout: 10), "The iOS catalog navigation Back button is missing.")
        catalogBack.tap()
        XCTAssertTrue(
            app.otherElements["compat-board-list"].waitForExistence(timeout: 10),
            "The explicit Back button did not return from the catalog to the board list."
        )
    }

    func testToshiakiReplyAttachmentCanReachThePhotoPicker() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1088"
        ]
        app.launch()

        let board = compatibilityBoardCardAfterUnwinding(in: app)
        XCTAssertTrue(board.waitForExistence(timeout: 15))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))

        app.buttons["書き込み"].tap()
        XCTAssertTrue(app.textViews["コメント"].waitForExistence(timeout: 10))
        app.buttons["添付画像"].tap()

        XCTAssertTrue(
            app.staticTexts["添付ファイルを選択"].waitForExistence(timeout: 5),
            "Compatibility attachment incorrectly skipped the iOS source chooser."
        )
        XCTAssertTrue(app.buttons["フォトライブラリ"].exists)
        XCTAssertTrue(app.buttons["ファイル"].exists)
        app.buttons["フォトライブラリ"].tap()

        XCTAssertTrue(app.staticTexts["メディアを選択"].waitForExistence(timeout: 5))
        app.buttons["写真"].tap()
        let photoGrid = app.collectionViews.firstMatch
        XCTAssertTrue(
            photoGrid.waitForExistence(timeout: 10),
            "The compatibility reply form did not reach the system photo picker."
        )
        // Local/device runs seed a photo before this test. CI images may have
        // an empty library, in which case reaching PHPicker is still the
        // platform contract and attachment persistence remains Kotlin-tested.
        let firstPhoto = photoGrid.cells.firstMatch
        if firstPhoto.waitForExistence(timeout: 2) {
            firstPhoto.tap()
            XCTAssertTrue(
                app.otherElements["compat-post-attachment-preview"].waitForExistence(timeout: 10),
                "The selected photo did not return to the compatibility reply form."
            )
        }
    }

    func testToshiakiPostOverflowRemainsCompactTextMenu() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1110"
        ]
        app.launch()

        let boardList = compatibilityBoardListAfterUnwinding(in: app)
        XCTAssertTrue(boardList.waitForExistence(timeout: 15))
        let board = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "https://")
        ).firstMatch
        XCTAssertTrue(board.waitForExistence(timeout: 15))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))
        let catalogItem = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "compat-catalog-item-")
        ).firstMatch
        XCTAssertTrue(catalogItem.waitForExistence(timeout: 15))
        catalogItem.tap()
        XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))

        app.buttons["書き込み"].tap()
        XCTAssertTrue(app.textViews["コメント"].waitForExistence(timeout: 10))
        let overflows = app.buttons.matching(NSPredicate(format: "label == %@", "その他"))
        XCTAssertGreaterThan(overflows.count, 0)
        let bottomOverflow = (0..<overflows.count)
            .map { overflows.element(boundBy: $0) }
            .max { $0.frame.midY < $1.frame.midY }
        XCTAssertNotNil(bottomOverflow)
        bottomOverflow?.tap()

        let referenceLabels = ["あぷ小", "音声入力", "回線情報", "機種情報", "リセット"]
        let menuItem = referenceLabels
            .map { app.buttons[$0] }
            .first { $0.waitForExistence(timeout: 2) }
        XCTAssertNotNil(menuItem, "The reference post overflow menu did not open.")
        if let menuItem {
            XCTAssertLessThanOrEqual(
                menuItem.frame.height,
                64,
                "A toolbar artwork image expanded the text-only post menu row."
            )
        }
    }

    func testToshiakiDrawerMatchesReferenceToolbarAndHeaders() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1040"
        ]
        app.launch()

        let drawer = app.buttons["ドロワー"]
        XCTAssertTrue(drawer.waitForExistence(timeout: 15), "The compatibility Drawer entry is missing.")
        drawer.tap()

        let toolbarLabels = ["開いているタブ", "履歴", "巡回結果", "全タブ更新確認", "設定"]
        let toolbarButtons = toolbarLabels.map { app.buttons[$0] }
        for button in toolbarButtons {
            XCTAssertTrue(button.waitForExistence(timeout: 10), "A reference Drawer toolbar command is missing.")
            XCTAssertTrue(app.windows.firstMatch.frame.contains(button.frame), "A Drawer toolbar command is clipped.")
        }
        let widths = toolbarButtons.map { $0.frame.width }
        XCTAssertLessThanOrEqual((widths.max() ?? 0) - (widths.min() ?? 0), 2)

        app.buttons["巡回結果"].tap()
        XCTAssertTrue(app.staticTexts["巡回結果"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["アプリ内バックグラウンド巡回の結果"].waitForExistence(timeout: 10))

        app.buttons["履歴"].tap()
        XCTAssertTrue(app.staticTexts["履歴"].waitForExistence(timeout: 10))

        app.buttons["開いているタブ"].tap()
        XCTAssertTrue(app.staticTexts["閲覧中のスレッド"].waitForExistence(timeout: 10))
    }

    func testToshiakiLeftEdgeSwipeIsOwnedByDrawerWithoutBackOrProfileSwitch() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1091"
        ]
        app.launch()

        let canonicalBoard = compatibilityBoardCardAfterUnwinding(in: app)
        // A developer's physical device can intentionally keep a custom board
        // list instead of the simulator seed. The edge-owner contract is board
        // independent, so use the first persisted HTTP(S) board in that case.
        let board = canonicalBoard.exists
            ? canonicalBoard
            : app.buttons.matching(
                NSPredicate(format: "label CONTAINS %@", "https://")
            ).firstMatch
        XCTAssertTrue(board.waitForExistence(timeout: 15))

        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.55))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.72, dy: 0.55))
        start.press(forDuration: 0.05, thenDragTo: end)

        XCTAssertTrue(
            app.buttons["開いているタブ"].waitForExistence(timeout: 10),
            "The board-list edge swipe did not remain inside compatibility mode."
        )

        // Dismiss the modal drawer through its scrim and repeat on Catalog.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.96, dy: 0.50)).tap()
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))

        start.press(forDuration: 0.05, thenDragTo: end)
        XCTAssertTrue(
            app.buttons["開いているタブ"].waitForExistence(timeout: 10),
            "The compatibility drawer did not own the catalog left-edge swipe."
        )
        XCTAssertFalse(
            board.isHittable,
            "The same edge gesture also navigated back to the board list."
        )
    }

    func testToshiakiDrawerFavoriteProtectionMatchesReferenceDeletion() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1041"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 15))
        board.tap()
        let catalogItem = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "compat-catalog-item-")
        ).firstMatch
        XCTAssertTrue(catalogItem.waitForExistence(timeout: 10))
        catalogItem.tap()
        XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))

        app.buttons["ドロワー"].tap()
        app.buttons["開いているタブ"].tap()
        func currentTabRow() -> XCUIElement {
            app.descendants(matching: .any).matching(
                NSPredicate(format: "identifier BEGINSWITH %@", "compat-drawer-tab-row-")
            ).firstMatch
        }

        var row = currentTabRow()
        XCTAssertTrue(row.waitForExistence(timeout: 10), "The opened thread is missing from the Drawer.")
        // A physical device can retain other open tabs from an earlier signed
        // build. Track the row selected by this test instead of interpreting
        // the existence of any remaining row as a failed deletion.
        let selectedTabIdentifier = row.identifier
        row.press(forDuration: 1.0)
        XCTAssertTrue(app.buttons["お気に入り"].waitForExistence(timeout: 5))
        app.buttons["お気に入り"].tap()

        row = currentTabRow()
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.press(forDuration: 1.0)
        let protect = app.descendants(matching: .any)["compat-drawer-protect-favorites"]
        XCTAssertTrue(protect.waitForExistence(timeout: 5), "The reference favorite-protection checkbox is missing.")
        app.buttons["削除する"].tap()
        XCTAssertTrue(
            currentTabRow().waitForExistence(timeout: 5),
            "Checked favorite protection did not protect the selected row."
        )

        row = currentTabRow()
        row.press(forDuration: 1.0)
        let reopenedProtect = app.descendants(matching: .any)["compat-drawer-protect-favorites"]
        XCTAssertTrue(reopenedProtect.waitForExistence(timeout: 5))
        reopenedProtect.tap()
        app.buttons["削除する"].tap()
        let selectedTabRow = app.descendants(matching: .any)[selectedTabIdentifier]
        let removed = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: selectedTabRow
        )
        XCTAssertEqual(
            XCTWaiter().wait(for: [removed], timeout: 5),
            .completed,
            "Turning protection off did not allow the selected favorite tab to close."
        )
    }

    func testToshiakiBoardDialogsMatchOldAndFinalApk() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1027"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))

        app.buttons["その他"].firstMatch.tap()
        app.buttons["板一覧"].tap()
        XCTAssertTrue(app.staticTexts["板一覧の取得"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["ふたばちゃんねるアドレス"].exists)
        XCTAssertTrue(app.buttons["更新する"].exists)
        app.buttons["更新する"].tap()
        XCTAssertTrue(app.staticTexts["アドレスを確認して下さい"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["板一覧の取得"].waitForExistence(timeout: 5))
        app.buttons["キャンセル"].tap()

        app.buttons["その他"].firstMatch.tap()
        app.buttons["新規追加"].tap()
        XCTAssertTrue(app.staticTexts["新しい板の追加"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["表示名"].exists)
        XCTAssertTrue(app.staticTexts["URL"].exists)
        XCTAssertTrue(app.buttons["追加する"].exists)
        app.buttons["キャンセル"].tap()

        board.press(forDuration: 1.0)
        XCTAssertTrue(app.buttons["名前を変更"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["削除する"].exists)
        app.buttons["名前を変更"].tap()
        XCTAssertTrue(app.staticTexts["名前の変更"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["表示名"].exists)
        XCTAssertTrue(app.buttons["更新する"].exists)
        app.buttons["キャンセル"].tap()

        board.press(forDuration: 1.0)
        app.buttons["削除する"].tap()
        XCTAssertTrue(app.staticTexts["板の削除"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["本当によろしいですか？"].exists)
        XCTAssertTrue(app.buttons["削除する"].exists)
        app.buttons["キャンセル"].tap()
    }

    func testToshiakiPtmtDialogMatchesFinalApkFieldsAndActions() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1041"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        app.buttons["その他"].firstMatch.tap()
        let settings = app.buttons["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()

        let settingsList = app.otherElements["compat-settings-list-root"]
        XCTAssertTrue(settingsList.waitForExistence(timeout: 10))
        let ptmt = app.descendants(matching: .any)["compat-setting-ptmtEditor"]
        for _ in 0..<10 where !ptmt.exists { settingsList.swipeUp(velocity: .slow) }
        let settledPtmt = app.descendants(matching: .any)["compat-setting-ptmtEditor"]
        XCTAssertTrue(settledPtmt.waitForExistence(timeout: 5), "The final-APK ptmt editor row is missing.")
        XCTAssertFalse(app.staticTexts["Cookie管理を初期化できません"].exists)
        XCTAssertTrue(settledPtmt.isEnabled, "The ptmt editor is unexpectedly disabled on iOS.")
        XCTAssertTrue(settledPtmt.isHittable, "The ptmt editor is obscured on iOS.")
        settledPtmt.tap()
        // XCUI occasionally spends the first synthesized tap only refreshing
        // the Compose accessibility focus after this LazyColumn swipe. A real
        // touch and Android Compose tests exercise the row callback directly;
        // retry only when the modal has not appeared.
        if !app.buttons["変更する"].waitForExistence(timeout: 1) {
            settledPtmt.tap()
        }

        XCTAssertTrue(
            app.buttons["変更する"].waitForExistence(timeout: 5),
            "The ptmt dialog did not open."
        )
        XCTAssertTrue(app.staticTexts["ptmtクッキーの編集"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "誤操作防止の為")
        ).firstMatch.exists)
        for label in ["リセット", "キャンセル", "変更する"] {
            XCTAssertTrue(app.buttons[label].exists, "Missing ptmt dialog action: \(label)")
        }
        app.buttons["キャンセル"].tap()
    }

    func testToshiakiCatalogSeparatesUndoAndDroppedThreadCommands() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1013"
        ]
        app.launch()

        // Reinstalling a debug build after the selected alternate icon changed
        // can leave a SpringBoard-owned confirmation in front of Compose.
        // Dismiss only that simulator dialog; production UI remains untouched.
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let iconChangeOK = springboard.alerts.buttons["OK"]
        if iconChangeOK.waitForExistence(timeout: 2) {
            iconChangeOK.tap()
        }

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))

        let undo = app.buttons["リロード前に戻す"]
        let dropped = app.buttons["消えたスレ"]
        XCTAssertTrue(undo.waitForExistence(timeout: 10), "The catalog rollback command is missing.")
        XCTAssertTrue(dropped.waitForExistence(timeout: 10), "The dropped-thread command is missing.")
        XCTAssertNotEqual(undo.frame, dropped.frame, "Two different catalog commands occupy the same control.")

        dropped.tap()
        XCTAssertTrue(
            app.staticTexts["消えたスレはありません"].waitForExistence(timeout: 10),
            "The dropped-thread command did not open its own destination."
        )
    }

    func testToshiakiCatalogWatchWordsMatchesReferenceActivityAndAddDialog() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1022"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))

        let catalogOverflowButtons = app.buttons.matching(
            NSPredicate(format: "label == %@", "その他")
        )
        let more = catalogOverflowButtons.element(boundBy: catalogOverflowButtons.count - 1)
        XCTAssertTrue(more.waitForExistence(timeout: 10), "The catalog overflow command is missing.")
        more.tap()
        let watchWords = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == %@", "監視ワード")
        ).firstMatch
        XCTAssertTrue(watchWords.waitForExistence(timeout: 5), "The reference watch-word entry is missing.")
        watchWords.tap()

        let title = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "スレッド監視 ")
        ).firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 10), "Watch words did not open the dedicated reference screen directly.")
        XCTAssertFalse(app.staticTexts["抽出"].exists, "An extra nested extract menu was inserted before the reference screen.")
        XCTAssertTrue(app.buttons["検索"].exists)
        XCTAssertTrue(app.buttons["新規追加"].exists)

        app.buttons["新規追加"].tap()
        XCTAssertTrue(app.staticTexts["監視ワード"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["単語"].exists)
        XCTAssertTrue(app.staticTexts["全ての板"].exists)
        XCTAssertTrue(
            app.staticTexts.matching(
                NSPredicate(format: "label CONTAINS %@", "大文字と小文字を区別しません")
            ).firstMatch.exists
        )
        XCTAssertTrue(app.buttons["追加する"].exists)
        XCTAssertTrue(app.buttons["キャンセル"].exists)
        app.buttons["キャンセル"].tap()
        XCTAssertTrue(title.waitForExistence(timeout: 5))
    }

    func testToshiakiCatalogNgManagersMatchReferenceSeparateActivities() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1023"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))

        func openCatalogNg(_ label: String) {
            let overflowButtons = app.buttons.matching(NSPredicate(format: "label == %@", "その他"))
            let overflow = overflowButtons.element(boundBy: overflowButtons.count - 1)
            XCTAssertTrue(overflow.waitForExistence(timeout: 10))
            overflow.tap()
            let management = app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@", "NG管理")
            ).firstMatch
            XCTAssertTrue(management.waitForExistence(timeout: 5))
            management.tap()
            let destination = app.descendants(matching: .any).matching(
                NSPredicate(format: "label == %@", label)
            ).firstMatch
            XCTAssertTrue(destination.waitForExistence(timeout: 5))
            destination.tap()
        }

        openCatalogNg("NGワード")
        let ignoreTitle = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "ＮＧワード ")
        ).firstMatch
        XCTAssertTrue(ignoreTitle.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["検索"].exists)
        XCTAssertTrue(app.buttons["新規追加"].exists)
        app.buttons["新規追加"].tap()
        XCTAssertTrue(app.staticTexts["ＮＧワード"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["単語"].exists)
        XCTAssertTrue(app.staticTexts["全ての板"].exists)
        XCTAssertTrue(app.buttons["追加する"].exists)
        app.buttons["キャンセル"].tap()
        XCTAssertTrue(ignoreTitle.waitForExistence(timeout: 5))
        app.buttons["戻る"].tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 5))

        openCatalogNg("NGスレッド")
        let refuseTitle = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "ＮＧスレッド ")
        ).firstMatch
        XCTAssertTrue(refuseTitle.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["検索"].exists)
        XCTAssertFalse(app.buttons["新規追加"].exists, "CatalogRefuseActivity has no manual add action.")
        app.buttons["その他"].tap()
        XCTAssertTrue(app.buttons["全て削除"].waitForExistence(timeout: 5))
        app.buttons["全て削除"].tap()
        XCTAssertTrue(app.staticTexts["本当によろしいですか？"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["削除する"].exists)
        app.buttons["キャンセル"].tap()
        XCTAssertTrue(refuseTitle.waitForExistence(timeout: 5))
    }

    func testToshiakiCatalogReplyPriorityFlagsPersistPerBoard() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1035"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))

        func openPriorityMenu() {
            let overflowButtons = app.buttons.matching(NSPredicate(format: "label == %@", "その他"))
            let overflow = overflowButtons.element(boundBy: overflowButtons.count - 1)
            XCTAssertTrue(overflow.waitForExistence(timeout: 10))
            overflow.tap()
            let management = app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@", "NG管理")
            ).firstMatch
            XCTAssertTrue(management.waitForExistence(timeout: 5))
            management.tap()
        }

        openPriorityMenu()
        let priorityWasEnabled = app.buttons["レス数優先を無効にする"].exists
        let priorityToggle = app.buttons[
            priorityWasEnabled ? "レス数優先を無効にする" : "レス数優先を有効にする"
        ]
        XCTAssertTrue(priorityToggle.exists)
        let nonPriorityWasShown = app.buttons["レス数非優先を隠す"].exists
        let nonPriorityToggle = app.buttons[
            nonPriorityWasShown ? "レス数非優先を隠す" : "レス数非優先を表示する"
        ]
        XCTAssertTrue(nonPriorityToggle.exists)
        nonPriorityToggle.tap()

        openPriorityMenu()
        XCTAssertTrue(
            app.buttons[
                nonPriorityWasShown ? "レス数非優先を表示する" : "レス数非優先を隠す"
            ].waitForExistence(timeout: 5),
            "The per-board non-priority visibility was not persisted."
        )
        app.buttons[
            nonPriorityWasShown ? "レス数非優先を表示する" : "レス数非優先を隠す"
        ].tap()

        openPriorityMenu()
        app.buttons[
            priorityWasEnabled ? "レス数優先を無効にする" : "レス数優先を有効にする"
        ].tap()
        openPriorityMenu()
        XCTAssertTrue(
            app.buttons[
                priorityWasEnabled ? "レス数優先を有効にする" : "レス数優先を無効にする"
            ].waitForExistence(timeout: 5),
            "The per-board reply-priority flag was not persisted."
        )
        // Restore the pre-test state for deterministic repeated runs.
        app.buttons[
            priorityWasEnabled ? "レス数優先を有効にする" : "レス数優先を無効にする"
        ].tap()
    }

    func testToshiakiCatalogLayoutUsesSharedReferencePreferenceAndPersists() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1040"
        ]
        app.launch()

        func openTutorialCatalog() {
            let board = boardCard(in: app, url: "https://img.2chan.net/t/")
            XCTAssertTrue(board.waitForExistence(timeout: 10))
            board.tap()
        }

        func toggleLayout() {
            let overflows = app.buttons.matching(NSPredicate(format: "label == %@", "その他"))
            let overflow = overflows.element(boundBy: overflows.count - 1)
            XCTAssertTrue(overflow.waitForExistence(timeout: 10))
            overflow.tap()
            let display = app.buttons["表示の切り替え"]
            XCTAssertTrue(display.waitForExistence(timeout: 5))
            display.tap()
        }

        openTutorialCatalog()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))
        toggleLayout()
        XCTAssertTrue(app.otherElements["compat-catalog-list"].waitForExistence(timeout: 10))

        app.terminate()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        openTutorialCatalog()
        XCTAssertTrue(
            app.otherElements["compat-catalog-list"].waitForExistence(timeout: 10),
            "The global catalog layout was not restored after relaunch."
        )

        // Restore Grid so the reference tests that follow keep their initial state.
        toggleLayout()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))
    }

    func testToshiakiCatalogSourceTitleLimitPersistsAndRestores() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1036"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let more = app.buttons["その他"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()

        let catalogSettings = app.staticTexts["カタログ画面"].firstMatch
        XCTAssertTrue(catalogSettings.waitForExistence(timeout: 10))
        catalogSettings.tap()
        XCTAssertTrue(app.staticTexts["カタログ設定"].waitForExistence(timeout: 10))

        let settingsList = app.otherElements["compat-settings-list-catalog"]
        let gridLimit = app.descendants(matching: .any)["compat-setting-catalogGridViewTitleLength"]
        XCTAssertTrue(gridLimit.waitForExistence(timeout: 5))
        for _ in 0..<6 where !gridLimit.isHittable {
            settingsList.swipeUp()
        }
        XCTAssertTrue(gridLimit.isHittable, "The grid title limit is not reachable on a compact screen.")
        Thread.sleep(forTimeInterval: 0.8)
        let optionList = app.otherElements["compat-setting-options"]
        gridLimit.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        if !optionList.waitForExistence(timeout: 1) {
            gridLimit.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        XCTAssertTrue(optionList.waitForExistence(timeout: 5))
        let thirtyCharacters = optionList.staticTexts["30"].firstMatch
        for _ in 0..<8 where !thirtyCharacters.isHittable {
            optionList.swipeUp()
        }
        XCTAssertTrue(thirtyCharacters.isHittable)
        Thread.sleep(forTimeInterval: 0.8)
        thirtyCharacters.tap()

        let sourceLimit = app.descendants(matching: .any)["compat-setting-catalogTitleLength"]
        for _ in 0..<8 where !sourceLimit.isHittable {
            settingsList.swipeUp()
        }
        XCTAssertTrue(sourceLimit.isHittable, "The reference thread-text source limit is not reachable.")
        // Compose's fling can continue after XCTest considers the application
        // idle. A tap during that decay correctly stops scrolling instead of
        // activating the row, so wait for the same settled state in which a
        // user intentionally selects the preference.
        Thread.sleep(forTimeInterval: 0.8)
        // Compose exposes the row title as a child accessibility node on
        // iOS. XCTest may choose the title glyph's top edge as its activation
        // point, which does not reliably reach the parent's combinedClickable
        // at the bottom of a LazyColumn. Tap the tagged preference row at its
        // centre, matching a real user tap on the row itself.
        sourceLimit.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let tenCharacters = app.staticTexts["10文字"].firstMatch
        XCTAssertTrue(tenCharacters.waitForExistence(timeout: 5))
        tenCharacters.tap()
        XCTAssertTrue(
            app.staticTexts["10文字"].firstMatch.waitForExistence(timeout: 5),
            "The parser-stage source limit did not persist in Catalog settings."
        )

        // Restore the final APK source-stage default for deterministic runs.
        sourceLimit.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(app.staticTexts["20文字"].firstMatch.waitForExistence(timeout: 5))
        app.staticTexts["20文字"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["20文字"].firstMatch.waitForExistence(timeout: 5))

        // Restore the independent grid-stage default after proving that a
        // long option list cannot leak its final scroll index into the short
        // source-stage dialog.
        for _ in 0..<8 where !gridLimit.isHittable {
            settingsList.swipeDown()
        }
        XCTAssertTrue(gridLimit.isHittable)
        Thread.sleep(forTimeInterval: 1.5)
        gridLimit.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        if !optionList.waitForExistence(timeout: 1) {
            // A retained Compose fling consumes one tap to stop, exactly as a
            // finger tap does. The following deliberate tap activates the row.
            gridLimit.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        XCTAssertTrue(optionList.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["4"].firstMatch.waitForExistence(timeout: 5))
        app.staticTexts["4"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["4文字"].firstMatch.waitForExistence(timeout: 5))
    }

    func testToshiakiCatalogDroppedAppendFollowsReferenceDependency() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1038"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let more = app.buttons["その他"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        let catalogSettings = app.staticTexts["カタログ画面"].firstMatch
        XCTAssertTrue(catalogSettings.waitForExistence(timeout: 10))
        catalogSettings.tap()
        XCTAssertTrue(app.staticTexts["カタログ設定"].waitForExistence(timeout: 10))

        let settingsList = app.otherElements["compat-settings-list-catalog"]
        let findDropped = app.descendants(matching: .any)["compat-setting-catalogFindThreadDeleted"]
        let appendDropped = app.descendants(matching: .any)["compat-setting-catalogAppendDropped"]
        for _ in 0..<8 where !appendDropped.exists {
            settingsList.swipeUp()
        }
        XCTAssertTrue(findDropped.waitForExistence(timeout: 5))
        XCTAssertTrue(appendDropped.waitForExistence(timeout: 5))
        XCTAssertTrue(findDropped.isEnabled)
        XCTAssertFalse(
            appendDropped.isEnabled,
            "The dependent append-dropped row must be disabled while dropped-thread detection is off."
        )

        func waitForAppendDependency(_ enabled: Bool, timeout: TimeInterval) -> Bool {
            let currentAppend = app.descendants(matching: .any)["compat-setting-catalogAppendDropped"]
            let expectation = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "enabled == %@", NSNumber(value: enabled)),
                object: currentAppend
            )
            return XCTWaiter.wait(for: [expectation], timeout: timeout) == .completed
        }

        findDropped.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        if !waitForAppendDependency(true, timeout: 2) {
            // Compose can keep a LazyColumn decelerating after XCTest reports
            // it idle. In that case the first tap only settles the scroll;
            // reacquire the row before the deliberate preference toggle.
            let settledFindDropped = app.descendants(matching: .any)["compat-setting-catalogFindThreadDeleted"]
            XCTAssertTrue(settledFindDropped.isHittable)
            settledFindDropped.tap()
        }
        XCTAssertTrue(waitForAppendDependency(true, timeout: 5))

        // Exercise the dependent row itself while it is enabled. Tapping it
        // twice restores its own default before the parent is turned off.
        appendDropped.tap()
        appendDropped.tap()

        // Restore the final APK default and verify the dependency closes
        // again, rather than only changing the row's color.
        app.descendants(matching: .any)["compat-setting-catalogFindThreadDeleted"].tap()
        if !waitForAppendDependency(false, timeout: 2) {
            app.descendants(matching: .any)["compat-setting-catalogFindThreadDeleted"].tap()
        }
        XCTAssertTrue(waitForAppendDependency(false, timeout: 5))
    }

    func testToshiakiImageNgMatchesReferenceDedicatedScreenThresholdAndClearDialogs() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1025"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))

        let overflowButtons = app.buttons.matching(NSPredicate(format: "label == %@", "その他"))
        let overflow = overflowButtons.element(boundBy: overflowButtons.count - 1)
        XCTAssertTrue(overflow.waitForExistence(timeout: 10))
        overflow.tap()
        let management = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "NG管理")
        ).firstMatch
        XCTAssertTrue(management.waitForExistence(timeout: 5))
        management.tap()
        let imageNg = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == %@", "NG画像")
        ).firstMatch
        XCTAssertTrue(imageNg.waitForExistence(timeout: 5))
        imageNg.tap()

        let title = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "ＮＧ画像 ")
        ).firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["検索"].exists)
        XCTAssertFalse(app.buttons["新規追加"].exists, "ImageNgActivity has no manual add action.")

        app.buttons["その他"].tap()
        XCTAssertTrue(app.buttons["類似判定のしきい値"].waitForExistence(timeout: 5))
        app.buttons["類似判定のしきい値"].tap()
        XCTAssertTrue(app.staticTexts["しきい値"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.staticTexts.matching(
                NSPredicate(format: "label CONTAINS %@", "64bit pHashのハミング距離です。")
            ).firstMatch.exists
        )
        XCTAssertTrue(app.buttons["初期値に戻す"].exists)
        XCTAssertTrue(app.buttons["保存"].exists)
        XCTAssertTrue(app.buttons["キャンセル"].exists)
        app.buttons["キャンセル"].tap()
        XCTAssertTrue(title.waitForExistence(timeout: 5))

        app.buttons["その他"].tap()
        XCTAssertTrue(app.buttons["全て削除"].waitForExistence(timeout: 5))
        app.buttons["全て削除"].tap()
        XCTAssertTrue(
            app.staticTexts["登録済みのNG画像を全て削除します。よろしいですか？"]
                .waitForExistence(timeout: 5)
        )
        XCTAssertTrue(app.buttons["削除する"].exists)
        app.buttons["キャンセル"].tap()
        XCTAssertTrue(title.waitForExistence(timeout: 5))
    }

    func testToshiakiThreadNgManagersMatchReferenceDedicatedActivities() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1024"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        let threadPager = app.otherElements["compat-thread-pager"]
        XCTAssertTrue(threadPager.waitForExistence(timeout: 10))

        func openThreadNg(_ label: String) {
            let threadToolbarOther = app.buttons.matching(
                NSPredicate(format: "label == %@", "その他")
            ).allElementsBoundByIndex
                .filter { $0.isHittable }
                .max { $0.frame.minY < $1.frame.minY }
            XCTAssertNotNil(threadToolbarOther, "The compatibility thread overflow command is missing.")
            threadToolbarOther?.tap()
            let management = app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@", "NG管理")
            ).firstMatch
            XCTAssertTrue(management.waitForExistence(timeout: 5))
            management.tap()
            let destination = app.descendants(matching: .any).matching(
                NSPredicate(format: "label == %@", label)
            ).firstMatch
            XCTAssertTrue(destination.waitForExistence(timeout: 5))
            destination.tap()
        }

        openThreadNg("NGヘッダー")
        let refuseTitle = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "ＮＧヘッダー ")
        ).firstMatch
        XCTAssertTrue(refuseTitle.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["検索"].exists)
        XCTAssertTrue(app.buttons["新規追加"].exists)
        app.buttons["新規追加"].tap()
        XCTAssertTrue(app.staticTexts["ＮＧヘッダー"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["単語"].exists)
        XCTAssertTrue(app.staticTexts["このスレッドのみ"].exists)
        XCTAssertTrue(
            app.staticTexts.matching(
                NSPredicate(format: "label CONTAINS %@", "読み込みが長くなります")
            ).firstMatch.exists
        )
        XCTAssertTrue(app.buttons["追加する"].exists)
        XCTAssertTrue(app.buttons["キャンセル"].exists)
        app.buttons["キャンセル"].tap()
        XCTAssertTrue(refuseTitle.waitForExistence(timeout: 5))
        app.buttons["戻る"].tap()
        XCTAssertTrue(threadPager.waitForExistence(timeout: 5))

        openThreadNg("NGワード")
        let ignoreTitle = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "ＮＧワード ")
        ).firstMatch
        XCTAssertTrue(ignoreTitle.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["検索"].exists)
        XCTAssertTrue(app.buttons["新規追加"].exists)
        app.buttons["新規追加"].tap()
        XCTAssertTrue(app.staticTexts["ＮＧワード"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["単語"].exists)
        XCTAssertTrue(app.staticTexts["このスレッドのみ"].exists)
        XCTAssertTrue(app.buttons["追加する"].exists)
        XCTAssertTrue(app.buttons["キャンセル"].exists)
    }

    func testToshiakiCatalogToolbarEditorMatchesReferencePreviewsAndImmediateSave() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1021"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))

        let more = app.buttons["その他"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10), "The catalog overflow command is missing.")
        more.tap()
        let edit = app.buttons["ツールバー編集"]
        XCTAssertTrue(edit.waitForExistence(timeout: 5), "The toolbar editor command is missing.")
        edit.tap()

        XCTAssertTrue(app.staticTexts["ツールバー編集"].waitForExistence(timeout: 10))
        let inactive = app.descendants(matching: .any)["compat-toolbar-preview-inactive"]
        let active = app.descendants(matching: .any)["compat-toolbar-preview-active"]
        XCTAssertTrue(inactive.waitForExistence(timeout: 10), "The inactive preview row is missing.")
        XCTAssertTrue(active.exists, "The active preview row is missing.")
        XCTAssertEqual(inactive.frame.height, 40, accuracy: 2)
        XCTAssertEqual(active.frame.height, 40, accuracy: 2)
        XCTAssertLessThanOrEqual(inactive.frame.maxY, active.frame.minY + 1)
        XCTAssertFalse(app.buttons["初期設定に戻す"].exists, "The reference editor has no reset action.")

        let activePost = app.descendants(matching: .any)["compat-toolbar-preview-active-post"]
        let inactivePost = app.descendants(matching: .any)["compat-toolbar-preview-inactive-post"]
        let togglePost = app.descendants(matching: .any)["compat-toolbar-toggle-post"]
        let wasActive = activePost.exists
        XCTAssertTrue(wasActive || inactivePost.exists, "The thread-creation preview is missing.")
        XCTAssertTrue(togglePost.exists, "The thread-creation checkbox is missing.")
        togglePost.tap()
        let movedPost = app.descendants(matching: .any)[
            wasActive ? "compat-toolbar-preview-inactive-post" : "compat-toolbar-preview-active-post"
        ]
        XCTAssertTrue(
            movedPost.waitForExistence(timeout: 5),
            "A checkbox change was not reflected in the opposite preview immediately."
        )

        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))
        XCTAssertEqual(
            app.buttons["スレ立て"].exists,
            !wasActive,
            "Leaving immediately after a toolbar edit lost the persisted checkbox change."
        )

        // Restore the pre-test state so repeated runs and later UI cases are deterministic.
        app.buttons["その他"].firstMatch.tap()
        XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
        app.buttons["ツールバー編集"].tap()
        let restoreToggle = app.descendants(matching: .any)["compat-toolbar-toggle-post"]
        XCTAssertTrue(restoreToggle.waitForExistence(timeout: 10))
        restoreToggle.tap()
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons["スレ立て"].exists, wasActive)
    }

    func testToshiakiThreadScrollAndViewerScreenUseSeparateReferenceIcons() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1032"
        ]
        app.launch()

        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))

        app.buttons["その他"].firstMatch.tap()
        XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
        app.buttons["ツールバー編集"].tap()
        let inactiveThreadScroll = app.descendants(matching: .any)["compat-toolbar-preview-inactive-scroll"]
        let activeThreadScroll = app.descendants(matching: .any)["compat-toolbar-preview-active-scroll"]
        let threadScrollWasActive = activeThreadScroll.exists
        XCTAssertTrue(
            threadScrollWasActive || inactiveThreadScroll.waitForExistence(timeout: 10),
            "The reference thread scroll-bar icon is missing from both preview rows."
        )
        if !threadScrollWasActive {
            let threadScrollToggle = app.descendants(matching: .any)["compat-toolbar-toggle-scroll"]
            let threadEditorList = app.otherElements["compat-toolbar-editor-list"]
            for _ in 0..<6 where !threadScrollToggle.isHittable { threadEditorList.swipeUp() }
            XCTAssertTrue(threadScrollToggle.waitForExistence(timeout: 5))
            XCTAssertTrue(threadScrollToggle.isHittable)
            Thread.sleep(forTimeInterval: 0.8)
            let activeThreadScrollPreview = app.descendants(matching: .any)["compat-toolbar-preview-active-scroll"]
            threadScrollToggle.tap()
            if !activeThreadScrollPreview.waitForExistence(timeout: 1) {
                threadScrollToggle.tap()
            }
            XCTAssertTrue(activeThreadScrollPreview.waitForExistence(timeout: 5))
        }
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))
        XCTAssertTrue(
            app.descendants(matching: .any)["compat-toolbar-icon-scroll"]
                .waitForExistence(timeout: 5),
            "The real thread toolbar did not render the reference scroll artwork."
        )

        if !threadScrollWasActive {
            // Restore the pre-test state before continuing to Gallery/Viewer.
            app.buttons["その他"].firstMatch.tap()
            XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
            app.buttons["ツールバー編集"].tap()
            let restoreThreadScroll = app.descendants(matching: .any)["compat-toolbar-toggle-scroll"]
            let restoreThreadList = app.otherElements["compat-toolbar-editor-list"]
            for _ in 0..<6 where !restoreThreadScroll.isHittable { restoreThreadList.swipeUp() }
            XCTAssertTrue(restoreThreadScroll.isHittable)
            Thread.sleep(forTimeInterval: 0.8)
            let inactiveThreadScrollPreview = app.descendants(matching: .any)["compat-toolbar-preview-inactive-scroll"]
            restoreThreadScroll.tap()
            if !inactiveThreadScrollPreview.waitForExistence(timeout: 1) {
                restoreThreadScroll.tap()
            }
            XCTAssertTrue(inactiveThreadScrollPreview.waitForExistence(timeout: 5))
            app.buttons["戻る"].firstMatch.tap()
            XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))
            XCTAssertFalse(app.descendants(matching: .any)["compat-toolbar-icon-scroll"].exists)
        }

        app.buttons["画像一覧"].tap()
        let galleryItem = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "compat-gallery-item-")
        ).firstMatch
        XCTAssertTrue(galleryItem.waitForExistence(timeout: 10))
        galleryItem.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)["compat-viewer-image-page"].waitForExistence(timeout: 10)
        )
        let viewerMoreButtons = app.buttons.matching(
            NSPredicate(format: "label == %@", "その他")
        )
        XCTAssertGreaterThanOrEqual(viewerMoreButtons.count, 2)
        viewerMoreButtons.element(boundBy: 1).tap()
        XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
        app.buttons["ツールバー編集"].tap()
        let viewerScreen = app.descendants(matching: .any)["compat-toolbar-preview-inactive-screen"]
        XCTAssertTrue(viewerScreen.waitForExistence(timeout: 10), "The reference viewer screen-mode icon is missing.")
        XCTAssertFalse(
            app.descendants(matching: .any)["compat-toolbar-preview-inactive-scroll"].exists,
            "The viewer screen-mode row reused the thread scroll-bar command."
        )
        let viewerScreenToggle = app.descendants(matching: .any)["compat-toolbar-toggle-screen"]
        let viewerEditorList = app.otherElements["compat-toolbar-editor-list"]
        for _ in 0..<6 where !viewerScreenToggle.isHittable { viewerEditorList.swipeUp() }
        XCTAssertTrue(viewerScreenToggle.waitForExistence(timeout: 5))
        XCTAssertTrue(viewerScreenToggle.isHittable)
        Thread.sleep(forTimeInterval: 0.8)
        let activeViewerScreenPreview = app.descendants(matching: .any)["compat-toolbar-preview-active-screen"]
        viewerScreenToggle.tap()
        if !activeViewerScreenPreview.waitForExistence(timeout: 1) {
            viewerScreenToggle.tap()
        }
        XCTAssertTrue(activeViewerScreenPreview.waitForExistence(timeout: 5))
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)["compat-viewer-toolbar-icon-screen"]
                .waitForExistence(timeout: 10),
            "The real viewer toolbar did not render the reference screen-mode artwork."
        )

        // Restore the default hidden state so the test is repeatable.
        let restoredViewerMore = app.buttons.matching(
            NSPredicate(format: "label == %@", "その他")
        )
        XCTAssertGreaterThanOrEqual(restoredViewerMore.count, 2)
        restoredViewerMore.element(boundBy: 1).tap()
        XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
        app.buttons["ツールバー編集"].tap()
        let restoreViewerScreen = app.descendants(matching: .any)["compat-toolbar-toggle-screen"]
        let restoreViewerList = app.otherElements["compat-toolbar-editor-list"]
        for _ in 0..<6 where !restoreViewerScreen.isHittable { restoreViewerList.swipeUp() }
        XCTAssertTrue(restoreViewerScreen.isHittable)
        Thread.sleep(forTimeInterval: 0.8)
        let inactiveViewerScreenPreview = app.descendants(matching: .any)["compat-toolbar-preview-inactive-screen"]
        restoreViewerScreen.tap()
        if !inactiveViewerScreenPreview.waitForExistence(timeout: 1) {
            restoreViewerScreen.tap()
        }
        XCTAssertTrue(inactiveViewerScreenPreview.waitForExistence(timeout: 5))
        app.buttons["戻る"].firstMatch.tap()
    }

    func testToshiakiImageSearchUsesReferenceDedicatedSettingsPage() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1014"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let more = app.buttons["その他"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10), "The compatibility board menu is missing.")
        more.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5), "The compatibility settings command is missing.")
        settings.tap()

        let imageSearch = app.staticTexts["画像検索"].firstMatch
        XCTAssertTrue(imageSearch.waitForExistence(timeout: 10), "The image-search settings entry is missing.")
        XCTAssertTrue(
            app.staticTexts["長押しメニューの整理"].firstMatch.exists,
            "The reference image-search summary is missing."
        )
        imageSearch.tap()

        XCTAssertTrue(
            app.staticTexts["長押しメニューに出す検索先"].firstMatch.waitForExistence(timeout: 10),
            "Image search did not open its reference dedicated settings page."
        )
        XCTAssertTrue(
            app.staticTexts["Google画像検索 (File)"].firstMatch.exists,
            "The reference File search provider is missing."
        )
        XCTAssertTrue(
            app.staticTexts["Google画像検索 (URL)"].firstMatch.exists,
            "The reference URL search provider is missing."
        )
        XCTAssertFalse(
            app.staticTexts["選択中"].firstMatch.exists,
            "The dedicated checkbox page still exposes the non-reference selection summary."
        )
    }

    func testToshiakiFutachaInformationScreensOpenInternally() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1029"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        if app.staticTexts["アップデートのお知らせ"].waitForExistence(timeout: 2) {
            app.buttons["OK"].tap()
        }
        XCTAssertTrue(
            compatibilityBoardListAfterUnwinding(in: app).waitForExistence(timeout: 10),
            "The compatibility board list could not be restored."
        )
        let more = app.buttons["その他"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()

        let update = app.buttons["更新情報"].firstMatch
        XCTAssertTrue(update.waitForExistence(timeout: 10), "The reference update action is missing.")
        update.tap()
        XCTAssertTrue(app.staticTexts["更新履歴"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["9.9"].waitForExistence(timeout: 10))
        let readableChange = app.staticTexts[
            "としあき（仮）モードの更新履歴を、Android／iOSとも読みやすい文字サイズと行間で表示するよう修正しました。"
        ]
        XCTAssertTrue(
            readableChange.waitForExistence(timeout: 10)
        )
        XCTAssertGreaterThanOrEqual(
            readableChange.frame.height,
            20,
            "The change-log body regressed to an unreadably small rendered font."
        )
        let changeLogScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        changeLogScreenshot.name = "toshiaki-change-log-readable"
        changeLogScreenshot.lifetime = .keepAlways
        add(changeLogScreenshot)
        XCTAssertTrue(app.buttons["ストア"].exists)
        XCTAssertTrue(app.buttons["ヘルプ"].exists)

        app.buttons["ヘルプ"].tap()
        XCTAssertTrue(app.staticTexts["ヘルプ"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["変更履歴"].exists)
        XCTAssertTrue(app.buttons["ストア"].exists)
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["更新履歴"].waitForExistence(timeout: 10))

        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["設定"].waitForExistence(timeout: 10))
    }

    func testToshiakiStorageMatchesReferenceRowsDialogsRawPersistenceAndCacheUsage() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1015"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let more = app.buttons["その他"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        let storage = app.staticTexts["ストレージ"].firstMatch
        XCTAssertTrue(storage.waitForExistence(timeout: 10), "The storage settings entry is missing.")
        storage.tap()

        XCTAssertTrue(app.staticTexts["保存先"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["未設定時：標準フォルダに保存"].exists)
        let download = app.descendants(matching: .any)["compat-setting-dummyDownloadDir"]
        XCTAssertTrue(download.waitForExistence(timeout: 5))
        download.tap()
        XCTAssertTrue(app.staticTexts["ダウンロード"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "画像の保存などに利用します")
        ).firstMatch.exists)
        XCTAssertTrue(app.buttons["フォルダ選択"].exists)
        app.buttons["リセット"].tap()
        XCTAssertTrue(app.staticTexts["未設定時：標準フォルダに保存"].waitForExistence(timeout: 5))

        let storageList = app.otherElements["compat-settings-list-storage"]
        let imageQuota = app.descendants(matching: .any)["compat-setting-commonImageCache"]
        for _ in 0..<3 where !imageQuota.exists {
            storageList.swipeUp()
        }
        XCTAssertTrue(imageQuota.waitForExistence(timeout: 5))
        imageQuota.tap()
        XCTAssertTrue(app.staticTexts["1GB"].waitForExistence(timeout: 5))
        app.staticTexts["1GB"].tap()
        XCTAssertTrue(app.staticTexts["1024MB"].waitForExistence(timeout: 5))

        let catalogLocation = app.descendants(matching: .any)["compat-setting-dummyCatalogImageCacheLocation"]
        for _ in 0..<4 where !catalogLocation.exists {
            storageList.swipeUp()
        }
        XCTAssertTrue(catalogLocation.waitForExistence(timeout: 5))
        catalogLocation.tap()
        XCTAssertTrue(app.staticTexts["内部ストレージ"].waitForExistence(timeout: 5))
        app.staticTexts["内部ストレージ"].tap()
        XCTAssertTrue(app.staticTexts["内部ストレージ"].waitForExistence(timeout: 5))

        let usage = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@", "現在の使用量:画像 ", " / カタログ ")
        ).firstMatch
        for _ in 0..<4 where !usage.exists {
            storageList.swipeUp()
        }
        XCTAssertTrue(
            usage.waitForExistence(timeout: 10),
            "The reference image/cache usage breakdown is not visible."
        )
        let zeroUsage = app.staticTexts["現在の使用量:0.00MB"].firstMatch
        for _ in 0..<4 where !zeroUsage.exists {
            storageList.swipeUp()
        }
        XCTAssertTrue(zeroUsage.waitForExistence(timeout: 5), "Thread/attachment usage lost the reference prefix.")

        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["設定"].waitForExistence(timeout: 10))
        app.staticTexts["ストレージ"].firstMatch.tap()
        let reopenedList = app.otherElements["compat-settings-list-storage"]
        let persistedQuota = app.staticTexts["1024MB"].firstMatch
        for _ in 0..<3 where !persistedQuota.exists {
            reopenedList.swipeUp()
        }
        XCTAssertTrue(persistedQuota.waitForExistence(timeout: 5), "The APK raw quota was not restored.")
        let persistedLocation = app.staticTexts["内部ストレージ"].firstMatch
        for _ in 0..<4 where !persistedLocation.exists {
            reopenedList.swipeUp()
        }
        XCTAssertTrue(persistedLocation.waitForExistence(timeout: 5), "The APK raw cache location was not restored.")
    }

    func testToshiakiRootSettingsKeepReferenceCoreAndIsolateExtensions() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1031"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        app.buttons["その他"].firstMatch.tap()
        let settings = app.buttons["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()

        let settingsList = app.otherElements["compat-settings-list-root"]
        XCTAssertTrue(settingsList.waitForExistence(timeout: 10))
        for category in ["基本設定", "表示オプション", "バックアップ", "その他"] {
            let heading = app.staticTexts[category].firstMatch
            for _ in 0..<8 where !heading.exists { settingsList.swipeUp() }
            XCTAssertTrue(heading.waitForExistence(timeout: 5), "Missing reference category: \(category)")
        }
        for row in ["更新情報", "ライセンス", "Twitter", "バージョン"] {
            let item = app.staticTexts[row].firstMatch
            for _ in 0..<5 where !item.exists { settingsList.swipeUp() }
            XCTAssertTrue(item.waitForExistence(timeout: 5), "Missing reference row: \(row)")
        }
        let databaseVersion = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "Database v26")
        ).firstMatch
        XCTAssertTrue(databaseVersion.waitForExistence(timeout: 5), "The reference database summary is missing.")

        let extensionHeading = app.staticTexts["ふたちゃ拡張"].firstMatch
        for _ in 0..<8 where !extensionHeading.exists { settingsList.swipeUp() }
        XCTAssertTrue(extensionHeading.waitForExistence(timeout: 5))
        for row in ["アップデート確認", "保存済みスレッド"] {
            let item = app.staticTexts[row].firstMatch
            for _ in 0..<5 where !item.exists { settingsList.swipeUp() }
            XCTAssertTrue(item.waitForExistence(timeout: 5), "Missing current extension row: \(row)")
        }

        let version = app.buttons["compat-setting-commonAppVersion"].firstMatch
        for _ in 0..<10 where !version.exists { settingsList.swipeDown() }
        XCTAssertTrue(version.waitForExistence(timeout: 5))
        version.tap()
        let messages = [
            "エンジョイ＆エキサイティング", "ペイパーキャノーーーン！", "肩が赤い",
            "完成してるの初めて見た", "こいつ、動くぞ・・・", "ツァ", "なんか寒くね！？",
            "念レス成功", "よしなに", "やよエな", "ねないこだれだ", "タキシードクイズ",
            "しもんきん", "ワグナス！"
        ]
        let versionMessage = app.staticTexts.matching(
            NSPredicate(format: "label IN %@", messages)
        ).firstMatch
        XCTAssertTrue(versionMessage.waitForExistence(timeout: 5), "The reference version easter egg was not shown.")
    }

    func testToshiakiDesignSettingsExposeReferenceChoicesNoticeAndFontDialog() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1020"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let more = app.buttons["その他"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        let design = app.staticTexts["デザイン"].firstMatch
        XCTAssertTrue(design.waitForExistence(timeout: 10))
        design.tap()

        XCTAssertTrue(app.staticTexts["スタイル"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["タブ一覧"].exists)
        let theme = app.descendants(matching: .any)["compat-setting-designTheme"]
        XCTAssertTrue(theme.waitForExistence(timeout: 5))
        theme.tap()
        for label in ["デフォルト", "モノクロ", "ふたば", "ブルー", "ピンク", "ブラック"] {
            XCTAssertTrue(app.staticTexts[label].firstMatch.exists, "Missing reference theme choice: \(label)")
        }
        app.staticTexts["ブラック"].firstMatch.tap()
        XCTAssertTrue(
            app.staticTexts["画面の再描画時に反映されます"].waitForExistence(timeout: 2),
            "Theme changes lost the reference redraw notice."
        )
        XCTAssertTrue(app.staticTexts["ブラック"].firstMatch.exists)

        let loading = app.descendants(matching: .any)["compat-setting-designLoading"]
        XCTAssertTrue(loading.waitForExistence(timeout: 5))
        loading.tap()
        XCTAssertTrue(app.staticTexts["アイコン"].waitForExistence(timeout: 5))
        app.staticTexts["アイコン"].tap()
        XCTAssertTrue(app.staticTexts["アイコン"].waitForExistence(timeout: 5))

        let selector = app.descendants(matching: .any)["compat-setting-designTabSelectorLocation"]
        XCTAssertTrue(selector.waitForExistence(timeout: 5))
        selector.tap()
        XCTAssertTrue(app.staticTexts["ツールバーの上に重ねる"].waitForExistence(timeout: 5))
        app.staticTexts["ツールバーの上に重ねる"].tap()
        XCTAssertTrue(app.staticTexts["ツールバーに重ねる"].waitForExistence(timeout: 5))

        let customFont = app.descendants(matching: .any)["compat-setting-dummyCustomFont"]
        XCTAssertTrue(customFont.waitForExistence(timeout: 5))
        customFont.tap()
        XCTAssertTrue(app.staticTexts["カスタムフォント"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["*.ttf  *.otf"].exists)
        for label in ["選択", "リセット", "キャンセル"] {
            XCTAssertTrue(app.buttons[label].exists, "Missing reference font-dialog action: \(label)")
        }
        app.buttons["キャンセル"].tap()
    }

    func testLightClassicThemeReachesSettingsFromThread() {
        let app = makeApplication()
        // #47 reproduces only for an explicit light/classic selection.  Feed
        // the same persisted representation used by the Kotlin state store.
        app.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1003",
            "-theme_mode", "Light",
            "-theme_palette", "FutabaClassic"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(app.otherElements["futacha-thread-content"].waitForExistence(timeout: 10))

        let topOverflow = app.buttons.matching(
            NSPredicate(format: "label == %@", "その他")
        ).allElementsBoundByIndex
            .filter { $0.isHittable }
            .min { $0.frame.minY < $1.frame.minY }
        XCTAssertNotNil(topOverflow, "The thread top-bar overflow command is missing.")
        topOverflow?.tap()
        let settings = app.buttons.matching(
            NSPredicate(format: "label == %@", "設定")
        ).allElementsBoundByIndex
            .filter { $0.isHittable }
            .min { $0.frame.minY < $1.frame.minY }
        XCTAssertNotNil(settings, "The thread top-bar settings command did not open.")
        settings?.tap()
        let displaySection = app.staticTexts["表示"].firstMatch
        XCTAssertTrue(
            displaySection.waitForExistence(timeout: 10),
            "Global Settings did not expose its display section."
        )
        displaySection.tap()
        let themeMode = app.staticTexts["テーマモード"]
        XCTAssertTrue(
            themeMode.waitForExistence(timeout: 10),
            "Global Settings did not render the theme mode controls after expanding display."
        )
        XCTAssertTrue(themeMode.exists, "Global Settings did not expose the theme mode controls.")
        let light = app.staticTexts["ライト"]
        // Scroll the settings content itself rather than the whole app.  At
        // the largest Dynamic Type size the expanded section needs more than
        // one short scroll, while repeated app-wide swipes wait for Compose
        // idle and make this UI test take several minutes.
        for _ in 0..<3 where !light.exists {
            themeMode.swipeUp()
        }
        XCTAssertTrue(
            light.waitForExistence(timeout: 5),
            "The light theme option was not composed after scrolling display settings."
        )
    }

    func testFutachaThreadExposesReadAloudPlaybackControls() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1010"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(app.otherElements["futacha-thread-content"].waitForExistence(timeout: 10))

        let actionBarSettings = app.buttons.matching(
            NSPredicate(format: "label == %@", "設定")
        ).allElementsBoundByIndex
            .filter { $0.isHittable }
            .max { $0.frame.minY < $1.frame.minY }
        XCTAssertNotNil(actionBarSettings, "The thread action-bar settings command is missing.")
        actionBarSettings?.tap()
        XCTAssertTrue(app.staticTexts["設定メニュー"].waitForExistence(timeout: 10))
        let readAloud = app.buttons["読み上げ"]
        XCTAssertTrue(readAloud.waitForExistence(timeout: 10), "The settings sheet read-aloud action is missing.")
        readAloud.tap()

        XCTAssertTrue(
            app.staticTexts["読み上げプレーヤー"].waitForExistence(timeout: 10),
            "The read-aloud player did not open from the thread action bar."
        )
        XCTAssertTrue(app.buttons["再生"].exists, "The read-aloud play control is missing.")
        XCTAssertTrue(app.buttons["一時停止"].exists, "The read-aloud pause control is missing.")
        XCTAssertTrue(app.buttons["停止"].exists, "The read-aloud stop control is missing.")
        let visiblePostSeek = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "表示位置 (")
        ).firstMatch
        XCTAssertTrue(visiblePostSeek.exists, "The visible-post seek control is missing.")
    }

    func testToshiakiThreadExposesReadAloudCommand() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1011"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))

        let threadToolbarOther = app.buttons.matching(
            NSPredicate(format: "label == %@", "その他")
        ).allElementsBoundByIndex
            .filter { $0.isHittable }
            .max { $0.frame.minY < $1.frame.minY }
        XCTAssertNotNil(threadToolbarOther, "The compatibility thread toolbar overflow command is missing.")
        threadToolbarOther?.tap()
        let readAloud = app.buttons["読み上げ"]
        XCTAssertTrue(
            readAloud.waitForExistence(timeout: 5),
            "The compatibility thread overflow menu does not expose read aloud."
        )
        XCTAssertTrue(readAloud.isEnabled, "The compatibility read-aloud command is disabled.")
        readAloud.tap()
        let speechDialog = app.descendants(matching: .any)["compat-thread-speech-dialog"]
        XCTAssertTrue(
            speechDialog.waitForExistence(timeout: 10),
            "The reference-compatible full-width speech dialog did not open."
        )
        XCTAssertFalse(
            app.staticTexts["読み上げプレーヤー"].exists,
            "The compatibility mode must use the reference titleless speech dialog."
        )
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.10)).tap()
        XCTAssertFalse(
            speechDialog.waitForExistence(timeout: 2),
            "Closing the reference speech dialog did not stop and dismiss it."
        )
    }

    func testFutachaThreadSearchAndFilterOpenAndReturnToContent() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1012"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        let threadContent = app.otherElements["futacha-thread-content"]
        XCTAssertTrue(threadContent.waitForExistence(timeout: 10))

        let search = app.buttons["スレ内検索"]
        XCTAssertTrue(search.waitForExistence(timeout: 10), "The thread search action is missing.")
        search.tap()
        let closeSearch = app.buttons["検索を閉じる"]
        XCTAssertTrue(closeSearch.waitForExistence(timeout: 5), "Thread search did not open.")
        XCTAssertTrue(app.buttons["前の検索結果"].exists)
        XCTAssertTrue(app.buttons["次の検索結果"].exists)
        closeSearch.tap()
        XCTAssertTrue(threadContent.waitForExistence(timeout: 5), "Closing search left the thread screen.")

        let filter = app.buttons["レスフィルター"]
        XCTAssertTrue(filter.waitForExistence(timeout: 10), "The response filter action is missing.")
        filter.tap()
        XCTAssertTrue(
            app.staticTexts.matching(
                NSPredicate(format: "label CONTAINS %@", "絞り込みたい条件")
            ).firstMatch.waitForExistence(timeout: 5),
            "The response filter sheet did not open."
        )
        app.buttons["閉じる"].firstMatch.tap()
        XCTAssertTrue(threadContent.waitForExistence(timeout: 5), "Closing the filter left the thread screen.")
    }

    func testToshiakiThreadSearchAndGalleryOpenAndReturnToPager() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1013"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        let threadPager = app.otherElements["compat-thread-pager"]
        XCTAssertTrue(threadPager.waitForExistence(timeout: 10))

        var search = app.buttons["レス検索"]
        let searchWasEnabled = search.waitForExistence(timeout: 2)
        if !searchWasEnabled {
            // Toolbar visibility is a user preference and a signed physical
            // device legitimately retains it across XCTest installations.
            // Enable the command through the same editor a user would use,
            // then restore the previous state at the end of this test.
            app.buttons["その他"].firstMatch.tap()
            XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
            app.buttons["ツールバー編集"].tap()
            let searchToggle = app.descendants(matching: .any)["compat-toolbar-toggle-search"]
            let editorList = app.otherElements["compat-toolbar-editor-list"]
            for _ in 0..<6 where !searchToggle.isHittable { editorList.swipeUp() }
            XCTAssertTrue(searchToggle.waitForExistence(timeout: 5))
            XCTAssertTrue(searchToggle.isHittable)
            Thread.sleep(forTimeInterval: 0.8)
            let activePreview = app.descendants(matching: .any)["compat-toolbar-preview-active-search"]
            searchToggle.tap()
            if !activePreview.waitForExistence(timeout: 1) {
                searchToggle.tap()
            }
            XCTAssertTrue(activePreview.waitForExistence(timeout: 5))
            app.buttons["戻る"].firstMatch.tap()
            XCTAssertTrue(threadPager.waitForExistence(timeout: 10))
            search = app.buttons["レス検索"]
        }
        XCTAssertTrue(search.waitForExistence(timeout: 10), "Compatibility thread search is missing.")
        search.tap()
        let closeSearch = app.buttons["検索を閉じる"]
        XCTAssertTrue(closeSearch.waitForExistence(timeout: 5), "Compatibility thread search did not open.")
        XCTAssertTrue(app.buttons["前の検索結果"].exists)
        XCTAssertTrue(app.buttons["次の検索結果"].exists)
        closeSearch.tap()
        XCTAssertTrue(threadPager.waitForExistence(timeout: 5), "Closing search left the compatibility thread.")

        var gallery = app.buttons["画像一覧"]
        let galleryWasEnabled = gallery.waitForExistence(timeout: 2)
        if !galleryWasEnabled {
            app.buttons["その他"].firstMatch.tap()
            XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
            app.buttons["ツールバー編集"].tap()
            let galleryToggle = app.descendants(matching: .any)["compat-toolbar-toggle-gallery"]
            let editorList = app.otherElements["compat-toolbar-editor-list"]
            for _ in 0..<6 where !galleryToggle.isHittable { editorList.swipeUp() }
            XCTAssertTrue(galleryToggle.waitForExistence(timeout: 5))
            XCTAssertTrue(galleryToggle.isHittable)
            Thread.sleep(forTimeInterval: 0.8)
            let activePreview = app.descendants(matching: .any)["compat-toolbar-preview-active-gallery"]
            galleryToggle.tap()
            if !activePreview.waitForExistence(timeout: 1) {
                galleryToggle.tap()
            }
            XCTAssertTrue(activePreview.waitForExistence(timeout: 5))
            app.buttons["戻る"].firstMatch.tap()
            XCTAssertTrue(threadPager.waitForExistence(timeout: 10))
            gallery = app.buttons["画像一覧"]
        }
        XCTAssertTrue(gallery.waitForExistence(timeout: 10), "Compatibility gallery action is missing.")
        gallery.tap()
        XCTAssertTrue(
            app.staticTexts["画像一覧"].waitForExistence(timeout: 10),
            "Compatibility gallery did not open."
        )
        let galleryItem = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "compat-gallery-item-")
        ).firstMatch
        XCTAssertTrue(galleryItem.waitForExistence(timeout: 5), "Compatibility gallery has no long-press target.")
        galleryItem.press(forDuration: 0.8)
        let returnToPost = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == %@", "元レスに移動する")
        ).firstMatch
        XCTAssertTrue(
            returnToPost.waitForExistence(timeout: 5),
            "The reference titleless gallery choice list did not open."
        )
        XCTAssertFalse(
            app.descendants(matching: .any).matching(
                NSPredicate(format: "label == %@", "キャンセル")
            ).firstMatch.exists,
            "The reference setItems menu must not add a cancel row."
        )
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.50)).tap()

        galleryItem.tap()
        let viewerImage = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "compat-viewer-image-page")
        ).firstMatch
        XCTAssertTrue(viewerImage.waitForExistence(timeout: 10), "Compatibility viewer image did not open.")
        viewerImage.press(forDuration: 0.8)
        for label in ["保存", "共有", "検索"] {
            XCTAssertTrue(
                app.descendants(matching: .any).matching(
                    NSPredicate(format: "label == %@", label)
                ).firstMatch.waitForExistence(timeout: 5),
                "Reference viewer quick menu is missing \(label)."
            )
        }
        XCTAssertFalse(
            app.descendants(matching: .any).matching(
                NSPredicate(format: "label == %@", "NG画像に登録")
            ).firstMatch.exists,
            "Viewer quick menu contains a non-reference NG row."
        )
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.50)).tap()
        app.buttons["戻る"].firstMatch.tap()

        let saveMode = app.buttons["保存モード"]
        XCTAssertTrue(saveMode.waitForExistence(timeout: 5), "Compatibility gallery save mode is missing.")
        saveMode.tap()
        XCTAssertTrue(
            app.staticTexts["保存モード"].waitForExistence(timeout: 5),
            "Enabling gallery save mode did not update its persistent state label."
        )
        app.buttons["その他"].firstMatch.tap()
        XCTAssertTrue(app.buttons["表示オプション"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["設定"].exists)
        XCTAssertTrue(app.buttons["ヘルプ"].exists)
        XCTAssertFalse(app.buttons["すべて保存"].exists)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.25)).tap()
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(threadPager.waitForExistence(timeout: 10), "Gallery Back did not restore the thread pager.")

        if !galleryWasEnabled {
            app.buttons["その他"].firstMatch.tap()
            XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
            app.buttons["ツールバー編集"].tap()
            let restoreGallery = app.descendants(matching: .any)["compat-toolbar-toggle-gallery"]
            let restoreList = app.otherElements["compat-toolbar-editor-list"]
            for _ in 0..<6 where !restoreGallery.isHittable { restoreList.swipeUp() }
            XCTAssertTrue(restoreGallery.isHittable)
            Thread.sleep(forTimeInterval: 0.8)
            let inactivePreview = app.descendants(matching: .any)["compat-toolbar-preview-inactive-gallery"]
            restoreGallery.tap()
            if !inactivePreview.waitForExistence(timeout: 1) {
                restoreGallery.tap()
            }
            XCTAssertTrue(inactivePreview.waitForExistence(timeout: 5))
            app.buttons["戻る"].firstMatch.tap()
            XCTAssertTrue(threadPager.waitForExistence(timeout: 10))
        }

        if !searchWasEnabled {
            app.buttons["その他"].firstMatch.tap()
            XCTAssertTrue(app.buttons["ツールバー編集"].waitForExistence(timeout: 5))
            app.buttons["ツールバー編集"].tap()
            let restoreSearch = app.descendants(matching: .any)["compat-toolbar-toggle-search"]
            let restoreList = app.otherElements["compat-toolbar-editor-list"]
            for _ in 0..<6 where !restoreSearch.isHittable { restoreList.swipeUp() }
            XCTAssertTrue(restoreSearch.isHittable)
            Thread.sleep(forTimeInterval: 0.8)
            let inactivePreview = app.descendants(matching: .any)["compat-toolbar-preview-inactive-search"]
            restoreSearch.tap()
            if !inactivePreview.waitForExistence(timeout: 1) {
                restoreSearch.tap()
            }
            XCTAssertTrue(inactivePreview.waitForExistence(timeout: 5))
            app.buttons["戻る"].firstMatch.tap()
            XCTAssertTrue(threadPager.waitForExistence(timeout: 10))
        }
    }

    func testToshiakiCompatibilityProfileWorksInLandscape() throws {
        // XCUIDevice can retain a landscape sensor value between test
        // processes while SpringBoard launches the next application scene in
        // portrait. In that split state, assigning .landscapeLeft again is
        // treated as a no-op and the app never receives a rotation event.
        // Establish a portrait baseline before launch so this test exercises
        // a real portrait -> landscape transition every time.
        XCUIDevice.shared.orientation = .portrait
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1004"
        ]
        app.launch()
        defer { XCUIDevice.shared.orientation = .portrait }

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = boardCard(in: app, url: "https://img.2chan.net/t/")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        try rotateToLandscape(app)

        XCTAssertTrue(board.waitForExistence(timeout: 10), "The board card disappeared after rotation.")
        board.tap()
        XCTAssertTrue(
            app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10),
            "The compatibility catalog did not survive landscape layout."
        )
        XCTAssertTrue(app.buttons["リロード"].isHittable, "The catalog action is obscured in landscape.")
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(
            app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10),
            "The compatibility thread did not open in landscape."
        )
        XCTAssertTrue(app.buttons["書き込み"].isHittable, "The reply action is obscured in landscape.")
        XCTAssertTrue(app.buttons["画像一覧"].isHittable, "The gallery action is obscured in landscape.")
    }

    func testToshiakiBackgroundMatchesReferenceRowsWarningAndRawBackedChoices() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1005"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = compatibilityBoardCardAfterUnwinding(in: app)
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        XCTAssertTrue(app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10))

        app.buttons["その他"].firstMatch.tap()
        let displayOptions = app.buttons["表示オプション"]
        XCTAssertTrue(displayOptions.waitForExistence(timeout: 5))
        displayOptions.tap()
        XCTAssertTrue(
            app.staticTexts["スレッド設定"].waitForExistence(timeout: 10),
            "The display-options command did not enter the thread settings."
        )
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(
            app.otherElements["compat-thread-pager"].waitForExistence(timeout: 10),
            "Direct ThreadSettingActivity Back did not return to its thread caller."
        )

        app.buttons["その他"].firstMatch.tap()
        let commonSettings = app.buttons["設定"].firstMatch
        XCTAssertTrue(commonSettings.waitForExistence(timeout: 5), "The common settings command is missing.")
        commonSettings.tap()
        let background = app.staticTexts["バックグラウンド"].firstMatch
        XCTAssertTrue(background.waitForExistence(timeout: 10), "The compatibility background settings entry is missing.")
        background.tap()

        XCTAssertTrue(app.staticTexts["スレッド関連"].waitForExistence(timeout: 10))
        let updateCheck = app.staticTexts["スレッドの更新確認"].firstMatch
        XCTAssertTrue(updateCheck.waitForExistence(timeout: 10), "The background update policy is missing.")
        XCTAssertTrue(
            app.staticTexts["スレッドの生存確認"].firstMatch.exists,
            "The background existence policy is missing."
        )
        updateCheck.tap()
        XCTAssertTrue(app.staticTexts["選択"].waitForExistence(timeout: 5))
        let always = app.staticTexts["常に確認する"].firstMatch
        XCTAssertTrue(always.waitForExistence(timeout: 5), "The always policy is unavailable.")
        always.tap()
        XCTAssertTrue(app.staticTexts["注意事項"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.staticTexts[
                "カタログからレス数を取得して更新分を履歴やツールバーに反映させます\n" +
                "常に確認する場合は通信量などに十分注意してください"
            ].waitForExistence(timeout: 5)
        )
        app.buttons["OK"].tap()
        XCTAssertTrue(app.staticTexts["常に確認する"].firstMatch.waitForExistence(timeout: 5))

        updateCheck.tap()
        let wifiOnly = app.staticTexts["Wi-Fi回線のみ"].firstMatch
        XCTAssertTrue(wifiOnly.waitForExistence(timeout: 5), "The Wi-Fi-only policy is unavailable.")
        wifiOnly.tap()
        XCTAssertTrue(
            app.staticTexts["Wi-Fi回線のみ"].firstMatch.waitForExistence(timeout: 5),
            "Selecting Wi-Fi-only did not update the visible policy."
        )

        // Restore the shipped no-background-network default so this test does
        // not alter the scheduler state for later Simulator tests.
        updateCheck.tap()
        let disabled = app.staticTexts["利用しない"].firstMatch
        XCTAssertTrue(disabled.waitForExistence(timeout: 5), "The disable policy is unavailable.")
        disabled.tap()
    }

    func testToshiakiNetworkMatchesReferenceRowsWarningAndParallelChoices() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1006"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        app.buttons["その他"].firstMatch.tap()
        let settings = app.buttons["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        let network = app.staticTexts["ネットワーク"].firstMatch
        XCTAssertTrue(network.waitForExistence(timeout: 10))
        network.tap()

        XCTAssertTrue(app.staticTexts["キャッシュサーバー機能"].waitForExistence(timeout: 10))
        let lightweight = app.staticTexts["通信の軽量化"].firstMatch
        XCTAssertTrue(lightweight.exists)
        let status = app.staticTexts["ステータス"].firstMatch
        XCTAssertTrue(status.exists)
        status.tap()
        XCTAssertFalse(app.staticTexts["確認中…"].exists, "The reference status row must be read-only.")
        XCTAssertTrue(app.staticTexts["画像の取得"].exists)
        let parallel = app.staticTexts["画像の同時取得数"].firstMatch
        XCTAssertTrue(parallel.exists)
        XCTAssertTrue(
            app.staticTexts[
                "減らすと1枚あたりの読み込みは速くなりますが、画面全体が出そろうまでは遅くなります。" +
                "回線が細い場合は少なめが有利なことがあります。"
            ].exists
        )

        lightweight.tap()
        let confirmation = app.staticTexts["確認"]
        if !confirmation.waitForExistence(timeout: 2) {
            // A prior interrupted run may have left the shared Simulator
            // preference ON. The first tap then restores OFF; enable again.
            lightweight.tap()
        }
        XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.staticTexts[
                "本来のHTMLからタグを削除したり内容をコンパクトにした解析済みのデータを" +
                "サーバーから取得します\n詳しい仕様と注意点はヘルプを確認して下さい"
            ].waitForExistence(timeout: 5)
        )
        XCTAssertFalse(app.buttons["キャンセル"].exists)
        app.buttons["OK"].tap()

        parallel.tap()
        let eight = app.staticTexts["8本"].firstMatch
        XCTAssertTrue(eight.waitForExistence(timeout: 5))
        eight.tap()
        XCTAssertTrue(app.staticTexts["8本"].firstMatch.waitForExistence(timeout: 5))

        // Restore defaults for later shared-Simulator tests.
        parallel.tap()
        let six = app.staticTexts["6本(既定)"].firstMatch
        XCTAssertTrue(six.waitForExistence(timeout: 5))
        six.tap()
        lightweight.tap()
    }

    func testToshiakiViewerPreloadChoicesUseReferencePolicy() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1040"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        app.buttons["その他"].firstMatch.tap()
        let settings = app.buttons["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()

        let settingsList = app.otherElements["compat-settings-list-root"]
        XCTAssertTrue(settingsList.waitForExistence(timeout: 10))
        let viewer = app.buttons["compat-setting-画像ビューア"].firstMatch
        for _ in 0..<5 where !viewer.isHittable { settingsList.swipeUp() }
        XCTAssertTrue(viewer.waitForExistence(timeout: 10))
        XCTAssertTrue(viewer.isHittable)
        // A synthetic swipe can leave Compose's LazyColumn decelerating after
        // XCTest reports the app idle. The first immediate tap then only
        // stops that scroll. Wait for the same settled state as a deliberate
        // user tap and activate the centre of the tagged row.
        Thread.sleep(forTimeInterval: 0.8)
        viewer.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let viewerTitle = app.staticTexts["画像ビューア設定"].firstMatch
        if !viewerTitle.waitForExistence(timeout: 2) {
            let settledViewer = app.buttons["compat-setting-画像ビューア"].firstMatch
            XCTAssertTrue(settledViewer.isHittable)
            settledViewer.tap()
        }
        XCTAssertTrue(viewerTitle.waitForExistence(timeout: 5))

        let preload = app.descendants(matching: .any)["compat-setting-viewerPreloadMode"]
        XCTAssertTrue(preload.waitForExistence(timeout: 10))
        preload.tap()
        let wifi = app.staticTexts["Wi-Fi回線のみ"].firstMatch
        XCTAssertTrue(wifi.waitForExistence(timeout: 5))
        wifi.tap()
        XCTAssertTrue(app.staticTexts["Wi-Fi回線のみ"].firstMatch.waitForExistence(timeout: 5))

        preload.tap()
        let none = app.staticTexts["利用しない"].firstMatch
        XCTAssertTrue(none.waitForExistence(timeout: 5))
        none.tap()
        XCTAssertTrue(app.staticTexts["利用しない"].firstMatch.waitForExistence(timeout: 5))

        // Restore the final APK default for repeatable full-suite runs.
        preload.tap()
        let usually = app.staticTexts["常に利用する"].firstMatch
        XCTAssertTrue(usually.waitForExistence(timeout: 5))
        usually.tap()
        XCTAssertTrue(app.staticTexts["常に利用する"].firstMatch.waitForExistence(timeout: 5))
    }

    func testToshiakiCompatibilityControlSettingsMatchReferenceRowsAndPersist() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1030"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        app.buttons["その他"].firstMatch.tap()
        let settings = app.buttons["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5), "The settings command is missing.")
        settings.tap()

        let control = app.staticTexts["コントロール"].firstMatch
        XCTAssertTrue(control.waitForExistence(timeout: 10), "The control settings entry is missing.")
        control.tap()
        XCTAssertTrue(app.staticTexts["カタログ画面"].waitForExistence(timeout: 10))

        let catalogLongTap = app.staticTexts["ロングタップ"].firstMatch
        XCTAssertTrue(catalogLongTap.waitForExistence(timeout: 5))
        catalogLongTap.tap()
        let catalogNg = app.staticTexts["NGスレッドに登録"].firstMatch
        XCTAssertTrue(catalogNg.waitForExistence(timeout: 5))
        catalogNg.tap()

        let controlList = app.otherElements["compat-settings-list-control"]
        let threadVolume = app.descendants(matching: .any)["compat-setting-controlThreadVolumeKey"]
        for _ in 0..<3 where !threadVolume.exists {
            controlList.swipeUp()
        }
        XCTAssertTrue(threadVolume.waitForExistence(timeout: 5), "The reference thread volume-key row is missing.")
        threadVolume.tap()
        let oneReply = app.staticTexts["1レス分スクロール"].firstMatch
        XCTAssertTrue(oneReply.waitForExistence(timeout: 5))
        oneReply.tap()

        for category in ["スレッド画面", "ツールバー", "書き込み画面", "画面ビューア"] {
            let heading = app.staticTexts[category].firstMatch
            for _ in 0..<4 where !heading.exists {
                controlList.swipeUp()
            }
            XCTAssertTrue(heading.waitForExistence(timeout: 5), "The reference \(category) category is missing.")
        }
        let extensionHeading = app.staticTexts["ふたちゃ拡張"].firstMatch
        for _ in 0..<4 where !extensionHeading.exists {
            controlList.swipeUp()
        }
        XCTAssertTrue(extensionHeading.waitForExistence(timeout: 5), "Current-only controls are not isolated.")
        let closeNotice = app.staticTexts["タブを閉じた時の通知"].firstMatch
        let destinationConfirm = app.staticTexts["板名の誤投稿確認"].firstMatch
        for _ in 0..<3 where !closeNotice.exists {
            controlList.swipeUp()
        }
        XCTAssertTrue(closeNotice.waitForExistence(timeout: 5))
        for _ in 0..<3 where !destinationConfirm.exists {
            controlList.swipeUp()
        }
        XCTAssertTrue(destinationConfirm.waitForExistence(timeout: 5))

        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["設定"].waitForExistence(timeout: 10))
        let reopenedControl = app.staticTexts["コントロール"].firstMatch
        XCTAssertTrue(reopenedControl.waitForExistence(timeout: 5))
        reopenedControl.tap()
        XCTAssertTrue(
            app.staticTexts["NGスレッドに登録"].waitForExistence(timeout: 10),
            "The catalog long-tap choice did not survive reopening."
        )
        let persistedThreadVolume = app.staticTexts["1レス分スクロール"].firstMatch
        for _ in 0..<3 where !persistedThreadVolume.exists {
            app.otherElements["compat-settings-list-control"].swipeUp()
        }
        XCTAssertTrue(persistedThreadVolume.waitForExistence(timeout: 5), "The thread volume choice was not restored.")
    }

    func testFutachaSavedThreadsDestinationIsReachable() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1006"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        XCTAssertTrue(
            app.staticTexts["チュートリアル＠ふたちゃ"].waitForExistence(timeout: 10),
            "The Futacha board list did not reach the foreground."
        )
        let menu = app.buttons["メニュー"]
        XCTAssertTrue(menu.waitForExistence(timeout: 10), "The board-list menu is missing.")
        menu.tap()
        let saved = app.staticTexts["保存済み"].firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 5), "The saved-threads menu entry is missing.")
        saved.tap()
        XCTAssertTrue(
            app.staticTexts["保存済みスレッド"].waitForExistence(timeout: 10),
            "The saved-threads destination did not open."
        )
        // This is the app's persistent Documents-backed location, so a prior
        // manual save may legitimately yield cards instead of the empty state.
        // The destination title proves that the injected iOS repository opened
        // without assuming or deleting any user data in the Simulator.
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(
            app.staticTexts["チュートリアル＠ふたちゃ"].waitForExistence(timeout: 10),
            "Returning from saved threads did not restore the board list."
        )
    }

    func testFutachaModeSwitchDialogExplainsCompatibilityProfile() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1007"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let menu = app.buttons["メニュー"]
        XCTAssertTrue(menu.waitForExistence(timeout: 10))
        menu.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5), "The board-list settings command is missing.")
        settings.tap()
        let mode = app.staticTexts["モード"].firstMatch
        XCTAssertTrue(mode.waitForExistence(timeout: 10), "Global Settings did not expose the profile mode section.")
        mode.tap()
        let compatibility = app.staticTexts["としあき(仮)モード"].firstMatch
        XCTAssertTrue(
            compatibility.waitForExistence(timeout: 10),
            "The compatibility-profile choice is not exposed from Futacha settings."
        )
        compatibility.tap()
        XCTAssertTrue(
            app.staticTexts["としあき(仮)モードへ切り替えますか？"].waitForExistence(timeout: 5),
            "Selecting the compatibility profile did not show its confirmation dialog."
        )
        XCTAssertTrue(app.buttons["切り替える"].exists, "The mode-switch confirmation action is missing.")
        app.buttons["キャンセル"].firstMatch.tap()
        XCTAssertTrue(
            app.staticTexts["設定"].waitForExistence(timeout: 5),
            "Cancelling the mode switch did not keep the user in settings."
        )
    }

    func testIosReviewCanReportAndBlockFromPostMenu() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1009"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")
        ).firstMatch.tap()
        let threadContent = app.otherElements["futacha-thread-content"]
        XCTAssertTrue(threadContent.waitForExistence(timeout: 10))

        let safetyActions = app.buttons["通報・ブロック"].firstMatch
        for _ in 0..<4 where !safetyActions.exists {
            threadContent.swipeUp()
        }
        XCTAssertTrue(safetyActions.waitForExistence(timeout: 10), "The review actions were not visible.")
        safetyActions.tap()

        let report = app.staticTexts["不適切な投稿を通報"].firstMatch
        let block = app.staticTexts["この利用者をブロック"].firstMatch
        XCTAssertTrue(report.waitForExistence(timeout: 5), "The report mechanism is not clearly labelled.")
        XCTAssertTrue(block.exists, "The user-blocking mechanism is not clearly labelled.")

        report.tap()
        XCTAssertTrue(
            app.staticTexts["不適切な投稿を通報"].firstMatch.waitForExistence(timeout: 5),
            "Selecting report did not open its confirmation."
        )
        XCTAssertTrue(app.buttons["通報する"].exists, "The report confirmation action is missing.")
        app.buttons["キャンセル"].firstMatch.tap()

        XCTAssertTrue(block.waitForExistence(timeout: 5))
        block.tap()
        XCTAssertTrue(
            app.staticTexts["この利用者をブロック"].firstMatch.waitForExistence(timeout: 5),
            "Selecting block did not open the local block form."
        )
        let blockConfirmation = app.buttons["この利用者をブロック"]
        XCTAssertTrue(blockConfirmation.exists, "The block confirmation action is missing.")
        if !blockConfirmation.isEnabled {
            // Compose can change the native accessibility type from TextView
            // to Other when the editor receives focus, so keep the query
            // independent of the transient UIKit element type.
            let identity = app.descendants(matching: .any)["ng-management-input"]
            XCTAssertTrue(identity.waitForExistence(timeout: 5), "The block identity field is missing.")
            identity.tap()
            identity.typeText("review-test-id")
        }
        XCTAssertTrue(blockConfirmation.isEnabled, "Entering an ID did not enable user blocking.")
    }

    func testFirstLaunchRequiresUserGeneratedContentEulaAgreement() {
        let app = XCUIApplication()
        app.launchArguments += [
            "-review.ugc_eula_accepted_version", "2026-08-22",
            "-review.force_ugc_eula",
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1008"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        XCTAssertTrue(
            app.staticTexts["利用規約（EULA）"].waitForExistence(timeout: 10),
            "The UGC terms were not presented before the content browser."
        )
        XCTAssertTrue(app.staticTexts["不適切なコンテンツ・迷惑行為を一切容認しません"].exists)
        XCTAssertFalse(
            app.staticTexts["チュートリアル＠ふたちゃ"].exists,
            "User-generated content became visible before EULA acceptance."
        )

        let accept = app.buttons["ugc-eula-accept"]
        XCTAssertTrue(accept.exists)
        XCTAssertFalse(accept.isEnabled, "The EULA can be accepted without explicit agreement.")
        app.buttons["ugc-eula-agreement"].tap()
        XCTAssertTrue(accept.isEnabled)
        accept.tap()
        XCTAssertTrue(
            app.staticTexts["チュートリアル＠ふたちゃ"].waitForExistence(timeout: 15),
            "Accepting the EULA did not start the content browser."
        )
    }

    /// Opt-in device smoke test for the cold, real-network thread path.
    /// `tools/run-ios-real-cold-thread-test.sh` discovers a live thread and
    /// injects both URLs into the generated xctestrun environment. Ordinary CI
    /// remains deterministic because direct runs still skip without those URLs.
    func testRealColdThreadsLoadWithoutManualRefresh() throws {
        let environment = ProcessInfo.processInfo.environment
        guard
            let compatibilityUrlValue = environment["FUTACHA_REAL_THREAD_URL_COMPAT"],
            let futachaUrlValue = environment["FUTACHA_REAL_THREAD_URL_FUTACHA"],
            let compatibilityUrl = URL(string: compatibilityUrlValue),
            let futachaUrl = URL(string: futachaUrlValue)
        else {
            throw XCTSkip("Real Futaba thread URLs were not supplied; use tools/run-ios-real-cold-thread-test.sh.")
        }

        func assertPostsAppear(in app: XCUIApplication, profile: String, url: URL) {
            app.open(url)
            let addAndOpen = app.buttons["追加して開く"]
            if addAndOpen.waitForExistence(timeout: 5) {
                addAndOpen.tap()
            }

            let postLabels = app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@", "No.")
            )
            let postsLoaded = XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in postLabels.count >= 2 },
                object: nil
            )
            XCTAssertEqual(
                XCTWaiter().wait(for: [postsLoaded], timeout: 45),
                .completed,
                "\(profile) did not render a cold real thread without pressing reload."
            )
            XCTAssertFalse(
                app.staticTexts["読み込み中…"].exists,
                "\(profile) still displayed the loading indicator after posts appeared."
            )
        }

        let compatibilityApp = makeApplication()
        compatibilityApp.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1101"
        ]
        compatibilityApp.launch()
        XCTAssertTrue(compatibilityApp.wait(for: .runningForeground, timeout: 15))
        assertPostsAppear(
            in: compatibilityApp,
            profile: "としあき(仮)モード",
            url: compatibilityUrl
        )
        compatibilityApp.terminate()

        let futachaApp = makeApplication()
        futachaApp.launchArguments += [
            "-experience.active_profile", "futacha",
            "-experience.profile_generation", "1102"
        ]
        futachaApp.launch()
        XCTAssertTrue(futachaApp.wait(for: .runningForeground, timeout: 15))
        assertPostsAppear(in: futachaApp, profile: "ふたちゃモード", url: futachaUrl)
    }
}
