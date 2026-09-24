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

    func testPhysicalHelpStoreOpensIosFutachaListing() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("App Store handoff requires a physical iPhone.")
#else
        let app = makeApplication()
        // Argument-domain settings last for this process and preserve the
        // user's saved mode and update preference.
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-update_check_enabled", "false"
        ]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        XCTAssertTrue(compatibilityBoardListAfterUnwinding(in: app).waitForExistence(timeout: 10))
        let more = app.buttons["その他"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        let help = app.buttons["ヘルプ"].firstMatch
        XCTAssertTrue(help.waitForExistence(timeout: 10))
        help.tap()
        XCTAssertTrue(app.staticTexts["ヘルプ"].waitForExistence(timeout: 10))
        let before = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        before.name = "physical-iphone-help-before-store"
        before.lifetime = .keepAlways
        add(before)
        app.buttons["ストア"].firstMatch.tap()

        let store = XCUIApplication(bundleIdentifier: "com.apple.AppStore")
        XCTAssertTrue(store.wait(for: .runningForeground, timeout: 30), "The help action must open the native iOS App Store.")
        let listing = store.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "ふたちゃ")).firstMatch
        let foundListing = listing.waitForExistence(timeout: 30)
        let tree = XCTAttachment(string: store.debugDescription)
        tree.name = "physical-iphone-app-store-hierarchy"
        tree.lifetime = .keepAlways
        add(tree)
        let after = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        after.name = "physical-iphone-futacha-app-store"
        after.lifetime = .keepAlways
        add(after)
        XCTAssertTrue(foundListing, "The destination must show Futacha's iOS listing.")
#endif
    }

    func testSimulatorHelpStoreHandsOffToBrowser() throws {
#if targetEnvironment(simulator)
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        safari.launch()
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-update_check_enabled", "false"
        ]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        XCTAssertTrue(compatibilityBoardListAfterUnwinding(in: app).waitForExistence(timeout: 10))
        app.buttons["その他"].firstMatch.tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        let help = app.buttons["ヘルプ"].firstMatch
        XCTAssertTrue(help.waitForExistence(timeout: 10))
        help.tap()
        XCTAssertTrue(app.staticTexts["ヘルプ"].waitForExistence(timeout: 10))
        let before = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        before.name = "simulator-help-before-store"
        before.lifetime = .keepAlways
        add(before)
        app.buttons["ストア"].firstMatch.tap()

        // Simulator has no native App Store. Inspect the actual outgoing URL
        // in Safari, without replacing the production URL launcher.
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 30))
        // tools/run-ios-store-link-test.sh verifies the complete URL from
        // SpringBoard's real OpenURL event. Simulator Safari may replace
        // App Store addresses with a blank tab because StoreKit is absent.
        let tree = XCTAttachment(string: safari.debugDescription)
        tree.name = "simulator-store-safari-hierarchy"
        tree.lifetime = .keepAlways
        add(tree)
        let after = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        after.name = "simulator-store-browser-handoff"
        after.lifetime = .keepAlways
        add(after)
#else
        throw XCTSkip("Use the native App Store handoff test on physical iPhones.")
#endif
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
            // Keep unrelated UI tests on an already-read version so the
            // automatic change log does not replace their intended start
            // screen. Android and common tests exercise the mismatch path.
            "-commonUsedVersion", Self.alreadyReadChangeLogVersion
        ]
        return app
    }

    // Newer than any release: a hard-coded current version (10.8, 11.0) went
    // stale on the next MARKETING_VERSION bump and reopened the change log on
    // fresh simulators. A saved version above the running one never triggers it.
    private static let alreadyReadChangeLogVersion = "9999.0"

    private func makeUpdatePromptApplication(
        style: String,
        generation: String,
        updateCheckEnabled: Bool = true,
        profile: String = "futacha"
    ) -> XCUIApplication {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", profile,
            "-experience.profile_generation", generation,
            "-update_check_enabled", updateCheckEnabled ? "true" : "false"
        ]
        app.launchEnvironment["FUTACHA_UI_TEST_UPDATE_PROMPT"] = style
        return app
    }

    private func waitForDisappearance(
        of element: XCUIElement,
        timeout: TimeInterval
    ) -> Bool {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: element
        )
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
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

    func testFutachaHistoryWrapsTextAndMediaHelpOpensLicenses() throws {
        try verifyFutachaHistoryAndMediaHelp(black: false)
    }

    func testFutachaBlackHistoryAndMediaHelpRemainReadable() throws {
        try verifyFutachaHistoryAndMediaHelp(black: true)
    }

    private func openFutachaHistoryForInspection(black: Bool) throws -> XCUIApplication {
        let app = makeApplication()
        let boards = [["id": "t", "name": "チュートリアル＠ふたちゃ", "category": "チュートリアル",
            "url": "https://www.example.com/t/futaba.php", "description": "チュートリアル"]]
        let boardJson = String(data: try JSONEncoder().encode(boards), encoding: .utf8)!
        let boardArgument = String(data: try JSONEncoder().encode(boardJson), encoding: .utf8)!
        app.launchArguments += ["-experience.active_profile", "futacha", "-update_check_enabled", "false",
            "-thread_body_text_size", "ExtraLarge", "-boards_json", boardArgument,
            "-theme_palette", black ? "FutabaBlack" : "FutabaClassic", "-theme_mode", black ? "Dark" : "Light"]
        app.launch()
        // Create a real history entry through the bundled tutorial. History is file-backed,
        // so overriding the legacy history_json preference would not replace it.
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        XCTAssertTrue(board.waitForExistence(timeout: 15))
        board.tap()
        let thread = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")).firstMatch
        XCTAssertTrue(thread.waitForExistence(timeout: 10))
        thread.tap()
        XCTAssertTrue(app.otherElements["futacha-thread-content"].waitForExistence(timeout: 10))
        let drawerButton = app.buttons["履歴を開く"].firstMatch
        XCTAssertTrue(drawerButton.waitForExistence(timeout: 15))
        drawerButton.tap()
        let card = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS %@", "history-entry-", "チュートリアル")).firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        XCTAssertTrue(app.windows.firstMatch.frame.contains(card.frame))
        let tree = card.debugDescription
        XCTAssertTrue(tree.contains("レス"))
        XCTAssertFalse(tree.contains("https://www.example.com/t/futaba.php"))
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = black ? "futacha-history-black-large-text" : "futacha-history-large-text"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        return app
    }

    func testFutachaHistoryTabsAndWatcherKeepReadableColors() throws {
        for black in [false, true] {
            let app = try openFutachaHistoryForInspection(black: black)
            let tabs = app.buttons["タブ一覧"].firstMatch
            let watcher = app.buttons["巡回"].firstMatch
            XCTAssertTrue(tabs.isHittable)
            XCTAssertTrue(watcher.isHittable)
            tabs.tap()
            let close = app.buttons["閉じる"].firstMatch
            XCTAssertTrue(close.waitForExistence(timeout: 5))
            let tabsScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            tabsScreenshot.name = black ? "readable-tabs-black" : "readable-tabs-classic"
            tabsScreenshot.lifetime = .keepAlways
            add(tabsScreenshot)
            close.tap()
            watcher.tap()
            let manage = app.buttons["巡回管理"].firstMatch
            XCTAssertTrue(manage.waitForExistence(timeout: 5))
            XCTAssertTrue(manage.isHittable)
            let watcherScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            watcherScreenshot.name = black ? "readable-watcher-black" : "readable-watcher-classic"
            watcherScreenshot.lifetime = .keepAlways
            add(watcherScreenshot)
            close.tap()
            app.terminate()
        }
    }

    private func verifyFutachaHistoryAndMediaHelp(black: Bool) throws {
        let app = try openFutachaHistoryForInspection(black: black)
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "設定")).firstMatch.tap()
        func reveal(_ target: XCUIElement) {
            let window = app.windows.firstMatch.frame
            // Keep clear of the top bar without excluding controls at the end of the list.
            let visible = CGRect(x: window.minX, y: window.minY + 90,
                                 width: window.width, height: window.height - 98)
            for _ in 0..<20 {
                if target.exists && target.isHittable && visible.contains(target.frame) { return }
                let up = !target.exists || target.frame.midY > visible.midY
                let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: up ? 0.70 : 0.35))
                let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: up ? 0.35 : 0.70))
                start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.1)
                // Native idleness does not wait for Compose's fling animation to settle.
                Thread.sleep(forTimeInterval: 0.8)
            }
            XCTAssertTrue(target.isHittable)
        }
        func tapVisibleCenter(_ target: XCUIElement) {
            // Compose's virtual node may retain its offscreen activation point after scrolling.
            // Use the current visible frame, as for other scrolled settings controls.
            let frame = target.frame
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
        }
        let media = app.staticTexts["メディア機能"].firstMatch
        reveal(media)
        let promptSettings = app.descendants(matching: .any).matching(identifier: "prompt-settings-toggle").firstMatch
        let imageSettings = app.descendants(matching: .any).matching(identifier: "image-editor-settings-toggle").firstMatch
        let videoSettings = app.descendants(matching: .any).matching(identifier: "video-editor-settings-toggle").firstMatch
        let help = app.descendants(matching: .any).matching(identifier: "media-help-open").firstMatch
        XCTAssertFalse(promptSettings.exists)
        XCTAssertFalse(imageSettings.exists)
        XCTAssertFalse(videoSettings.exists)
        XCTAssertFalse(help.exists)
        attachCompactHeader(app, name: "media-settings-group-collapsed")
        tapVisibleCenter(media)
        reveal(promptSettings)
        reveal(imageSettings)
        reveal(videoSettings)
        attachCompactHeader(app, name: "media-settings-group-expanded")
        reveal(media)
        tapVisibleCenter(media)
        XCTAssertTrue(promptSettings.waitForNonExistence(timeout: 5))
        XCTAssertFalse(imageSettings.exists)
        XCTAssertFalse(videoSettings.exists)
        XCTAssertFalse(help.exists)
        tapVisibleCenter(media)
        reveal(help)
        tapVisibleCenter(help)
        XCTAssertTrue(app.staticTexts["メディア機能の使い方"].waitForExistence(timeout: 10))
        let licenses = app.buttons["オープンソースライセンスを読む"].firstMatch
        reveal(licenses)
        attachCompactHeader(app, name: black ? "media-help-black" : "media-help-light")
        tapVisibleCenter(licenses)
        let mit = app.descendants(matching: .any).matching(identifier: "compat-license-onnxruntime-license").firstMatch
        XCTAssertTrue(mit.waitForExistence(timeout: 10))
        tapVisibleCenter(mit)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Copyright (c) Microsoft Corporation")).firstMatch.waitForExistence(timeout: 10))
        let licenseShot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        licenseShot.name = black ? "media-license-black" : "media-license-light"
        licenseShot.lifetime = .keepAlways
        add(licenseShot)
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

    func testIosFlexibleUpdatePromptCanBeDeferred() {
        let app = makeUpdatePromptApplication(style: "flexible", generation: "1201")
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let title = app.staticTexts["アップデートのお知らせ"]
        XCTAssertTrue(title.waitForExistence(timeout: 20), "The Flexible update prompt was not shown.")
        XCTAssertTrue(app.buttons["App Storeで更新"].exists)

        let deferButton = app.buttons["後で"]
        XCTAssertTrue(deferButton.exists, "The Flexible prompt must expose the defer action.")
        deferButton.tap()

        XCTAssertTrue(
            waitForDisappearance(of: title, timeout: 5),
            "Tapping 後で did not dismiss the Flexible update prompt."
        )
        XCTAssertEqual(app.state, .runningForeground, "Deferring the update left the application.")
    }

    func testIosImmediateUpdatePromptCannotBeDismissed() {
        let app = makeUpdatePromptApplication(style: "immediate", generation: "1202")
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let title = app.staticTexts["アップデートのお知らせ"]
        XCTAssertTrue(title.waitForExistence(timeout: 20), "The Immediate update prompt was not shown.")
        XCTAssertFalse(app.buttons["後で"].exists, "The Immediate prompt must not expose a defer action.")

        app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.03, dy: 0.12)).tap()
        XCTAssertTrue(
            title.waitForExistence(timeout: 2),
            "Tapping outside unexpectedly dismissed the Immediate update prompt."
        )
        XCTAssertTrue(app.buttons["App Storeで更新"].exists)
    }

    func testIosImmediateUpdatePromptOverridesDisabledSetting() {
        for (index, profile) in ["futacha", "toshiaki_compat"].enumerated() {
            let app = makeUpdatePromptApplication(
                style: "immediate",
                generation: "\(1204 + index)",
                updateCheckEnabled: false,
                profile: profile
            )
            app.launch()

            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
            let title = app.staticTexts["アップデートのお知らせ"]
            XCTAssertTrue(
                title.waitForExistence(timeout: 20),
                "The emergency iOS update prompt was suppressed in profile \(profile)."
            )
            XCTAssertFalse(app.buttons["後で"].exists)
            XCTAssertTrue(app.buttons["App Storeで更新"].exists)
            app.terminate()
        }
    }

    func testIosUpdateActionOpensAppStoreOnPhysicalDevice() throws {
#if targetEnvironment(simulator)
        throw XCTSkip("CoreSimulator does not provide the production App Store handoff.")
#else
        let app = makeUpdatePromptApplication(style: "immediate", generation: "1203")
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let updateButton = app.buttons["App Storeで更新"]
        XCTAssertTrue(updateButton.waitForExistence(timeout: 20))
        updateButton.tap()

        let appStore = XCUIApplication(bundleIdentifier: "com.apple.AppStore")
        XCTAssertTrue(
            appStore.wait(for: .runningForeground, timeout: 20),
            "The App Store was not opened from the update action."
        )
#endif
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
        XCTAssertTrue(app.textViews["compat-post-comment-field"].waitForExistence(timeout: 10), "The compatibility reply form did not open.")
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
        if app.staticTexts["板が登録されていません。右上のメニューから板を追加してください。"].exists {
            app.buttons["その他"].firstMatch.tap()
            app.buttons["新規追加"].tap()
            let name = app.textViews["compat-board-name-input"]
            XCTAssertTrue(name.waitForExistence(timeout: 5))
            name.tap()
            name.typeText("mayb")
            let url = app.textViews["compat-board-url-input"]
            url.tap()
            url.typeText("https://may.2chan.net/b/")
            app.buttons["追加する"].tap()
        }
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

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.96, dy: 0.50)).tap()
        let catalogItem = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "compat-catalog-item-")
        ).firstMatch
        XCTAssertTrue(catalogItem.waitForExistence(timeout: 15))
        catalogItem.tap()
        let thread = app.otherElements["compat-thread-pager"]
        XCTAssertTrue(thread.waitForExistence(timeout: 15))

        // Exercise the thread itself, including different heights over its
        // rendered posts. Catalog-only coverage misses thread pager and
        // selectable text interference with the drawer's edge recognizer.
        for height in [0.30, 0.55, 0.80] {
            let threadStart = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: height))
            let threadEnd = app.coordinate(withNormalizedOffset: CGVector(dx: 0.72, dy: height))
            threadStart.press(forDuration: 0.05, thenDragTo: threadEnd)
            XCTAssertTrue(app.buttons["開いているタブ"].waitForExistence(timeout: 10))
            app.buttons["履歴"].tap()
            XCTAssertTrue(app.staticTexts["履歴"].waitForExistence(timeout: 10))
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = "thread-history-edge-\(height)"
            screenshot.lifetime = .keepAlways
            add(screenshot)
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.96, dy: 0.50)).tap()
            XCTAssertTrue(thread.waitForExistence(timeout: 10))
        }
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
        XCTAssertTrue(app.staticTexts["10.8"].waitForExistence(timeout: 10))
        let readableChange = app.staticTexts[
            "としあき（仮）モードの画面下部にあるタブ一覧で、白いタイトル文字が背景帯より上にずれる問題を修正しました。"
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
        XCTAssertTrue(compatibilityBoardListAfterUnwinding(in: app).waitForExistence(timeout: 10))
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

        let media = app.buttons["compat-setting-メディア機能"].firstMatch
        for _ in 0..<8 where !media.isHittable { settingsList.swipeUp() }
        XCTAssertTrue(media.isHittable)
        XCTAssertFalse(app.descendants(matching: .any)["prompt-settings-toggle"].exists)
        let rootShot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        rootShot.name = "toshiaki-grouped-media-entry"
        rootShot.lifetime = .keepAlways
        add(rootShot)
        media.tap()
        let mediaList = app.otherElements["compat-settings-list-media"]
        XCTAssertTrue(mediaList.waitForExistence(timeout: 5))
        XCTAssertFalse(app.descendants(matching: .any)["prompt-settings-toggle"].exists)
        for tag in ["image-editor-settings-toggle", "video-editor-settings-toggle", "media-help-open"] {
            let control = app.descendants(matching: .any)[tag].firstMatch
            for _ in 0..<8 where !control.isHittable { mediaList.swipeUp() }
            XCTAssertTrue(control.isHittable, "Missing grouped media setting: \(tag)")
        }
        let mediaShot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        mediaShot.name = "toshiaki-grouped-media-settings"
        mediaShot.lifetime = .keepAlways
        add(mediaShot)
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertTrue(settingsList.waitForExistence(timeout: 5))
        XCTAssertTrue(media.isHittable)

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

    func testFutachaImageSearchOpensFromPreviewAndAttachmentMenu() throws {
        let app = makeApplication()
        // Give the bundled tutorial its own board so an earlier test's offline
        // snapshot cannot replace its original image with a missing thumbnail.
        let boardId = "image-search-\(UUID().uuidString)"
        let boardUrl = "https://www.example.com/\(boardId)/futaba.php"
        let boards = [["id": boardId, "name": "チュートリアル", "category": "テスト",
                       "url": boardUrl, "description": "画像検索テスト"]]
        let boardJson = String(data: try JSONEncoder().encode(boards), encoding: .utf8)!
        let boardArgument = String(data: try JSONEncoder().encode(boardJson), encoding: .utf8)!
        app.launchArguments += ["-experience.active_profile", "futacha", "-update_check_enabled", "false",
                                "-boards_json", boardArgument]
        app.launch()
        let board = boardCard(in: app, url: boardUrl)
        XCTAssertTrue(board.waitForExistence(timeout: 15))
        board.tap()
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")).firstMatch.tap()
        let attachment = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "1762576973515")).firstMatch
        XCTAssertTrue(attachment.waitForExistence(timeout: 10), app.debugDescription)
        attachment.tap()
        XCTAssertTrue(app.buttons["画像検索"].waitForExistence(timeout: 5))
        app.buttons["画像検索"].tap()
        XCTAssertTrue(app.buttons["Google画像検索 (File)"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Google Lens (URL)"].exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "futacha-image-search-providers"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.buttons["閉じる"].firstMatch.tap()
        app.buttons["プレビューを閉じる"].tap()
        attachment.press(forDuration: 1.0)
        let search = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "画像検索")).firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 5), app.debugDescription)
        search.tap()
        XCTAssertTrue(app.buttons["Google画像検索 (File)"].waitForExistence(timeout: 5))
        app.buttons["閉じる"].firstMatch.tap()
        XCTAssertTrue(app.otherElements["futacha-thread-content"].waitForExistence(timeout: 5))
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

        let saveMode = app.buttons["一括保存"]
        XCTAssertTrue(saveMode.waitForExistence(timeout: 5), "Compatibility gallery save mode is missing.")
        saveMode.tap()
        let endSelection = app.buttons["選択を終了"]
        XCTAssertTrue(endSelection.waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label ENDSWITH %@", "件選択"))
                .firstMatch.waitForExistence(timeout: 5),
            "Enabling gallery save mode did not expose the selected media count."
        )
        app.buttons["その他"].firstMatch.tap()
        XCTAssertTrue(app.buttons["表示オプション"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["設定"].exists)
        XCTAssertTrue(app.buttons["ヘルプ"].exists)
        XCTAssertFalse(app.buttons["すべて保存"].exists)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.25)).tap()
        endSelection.tap()
        XCTAssertTrue(saveMode.waitForExistence(timeout: 5))
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

    func testFutachaDeviceVideoEditorSelectsPhotoAndSavesIndependently() { verifyDeviceVideoEditor(compat: false) }
    func testCompatDeviceVideoEditorSelectsPhotoAndSavesIndependently() { verifyDeviceVideoEditor(compat: true) }
    func testFutachaVideoAutomaticEditingRequiresReviewBeforeSaving() { verifyDeviceVideoEditor(compat: false, analysis: true) }
    func testCompatVideoAutomaticEditingRequiresReviewBeforeSaving() { verifyDeviceVideoEditor(compat: true, analysis: true) }

    private func verifyDeviceVideoEditor(compat: Bool, analysis: Bool = false) {
        let app = makeApplication()
        app.launchArguments += ["-experience.active_profile", compat ? "toshiaki_compat" : "futacha",
            "-update_check_enabled", "false", "-commonUsedVersion", Self.alreadyReadChangeLogVersion,
            "-media_feature_settings_v1", "\"{\\\"video_editor_enabled\\\":true,\\\"image_editor_enabled\\\":false,\\\"prompt_display_enabled\\\":false}\""]
        app.launch()
        if compat { _ = compatibilityBoardListAfterUnwinding(in: app) }
        func element(_ tag: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: tag).firstMatch }
        func reveal(_ target: XCUIElement, upwards: Bool = true) -> XCUIElement {
            let panel = element("video-editor-controls")
            for _ in 0..<16 {
                let visible = panel.frame.intersection(app.frame).insetBy(dx: 0, dy: 2)
                if target.exists && target.isHittable && visible.contains(target.frame) { return target }
                let up = target.exists ? target.frame.midY > visible.midY : upwards
                panel.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: up ? 0.70 : 0.30))
                    .press(forDuration: 0.05, thenDragTo: panel.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: up ? 0.42 : 0.58)),
                           withVelocity: .slow, thenHoldForDuration: 0.2)
            }
            XCTAssertTrue(target.isHittable, "Video editor control must be reachable: \(target)")
            return target
        }
        func control(_ tag: String) -> XCUIElement { reveal(element(tag)) }
        let menu = app.buttons[compat ? "その他" : "メニュー"].firstMatch
        XCTAssertTrue(menu.waitForExistence(timeout: 15)); menu.tap()
        XCTAssertFalse(element("device-image-editor-menu").exists)
        let editor = element("device-video-editor-menu")
        XCTAssertTrue(editor.waitForExistence(timeout: 5)); editor.tap()
        let photos = app.buttons["写真ライブラリ"].firstMatch
        XCTAssertTrue(photos.waitForExistence(timeout: 5)); photos.tap()
        let photo = app.images.matching(identifier: "PXGGridLayout-Info").firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 15), "Seed a small SDR/AAC MP4 in Photos.")
        let cancel = app.navigationBars.buttons.matching(identifier: "Cancel").firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 5), app.debugDescription)
        cancel.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"), object: photo)], timeout: 10), .completed)
        XCTAssertTrue(menu.waitForExistence(timeout: 10)); menu.tap(); editor.tap()
        XCTAssertTrue(photos.waitForExistence(timeout: 5)); photos.tap()
        XCTAssertTrue(photo.waitForExistence(timeout: 15))
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: photo.frame.midX, dy: photo.frame.midY)).tap()
        let add = element("video-editor-add")
        XCTAssertTrue(add.waitForExistence(timeout: 30)); reveal(add).tap()
        let black = reveal(app.buttons["黒塗り"].firstMatch)
        XCTAssertTrue(black.waitForExistence(timeout: 5)); black.tap()
        let export = element("video-editor-export")
        XCTAssertTrue(export.isEnabled)
        if !analysis {
            let play = reveal(element("video-editor-play-pause"), upwards: false)
            let before = element("video-editor-time").label
            play.tap()
            XCTAssertTrue(element("video-editor-playback").waitForExistence(timeout: 15))
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "label != %@", before), object: element("video-editor-time"))], timeout: 15), .completed)
            XCTAssertFalse(element("video-editor-error").exists)
            if element("video-editor-playback").exists { play.tap() }
            XCTAssertTrue(element("video-editor-canvas").waitForExistence(timeout: 10))
            XCTAssertTrue(export.isEnabled)
        }
        if analysis {
            control("video-track-forward").tap()
            XCTAssertTrue(element("video-review-confirm").waitForExistence(timeout: 30))
            XCTAssertFalse(export.isEnabled)
            control("video-review-confirm").tap()
            XCTAssertTrue(export.isEnabled)
            control("video-contour-frame").tap()
            XCTAssertTrue(element("video-review-confirm").waitForExistence(timeout: 120),
                          "Seed both verified MobileSAM models in the simulator app before this test.")
            XCTAssertFalse(element("video-editor-error").exists)
            XCTAssertFalse(export.isEnabled)
            control("video-review-confirm").tap()
            control("video-contour-tool-erase").tap()
            let frame = element("video-editor-canvas").frame
            app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
            XCTAssertFalse(export.isEnabled, "Correcting the silhouette must revoke review.")
            // Search one actual frame with the installed detector, retaining the manual cover.
            reveal(app.buttons["解析はこのコマまで"].firstMatch, upwards: false).tap()
            control("video-editor-detect").tap()
            XCTAssertTrue(element("video-detection-start").waitForExistence(timeout: 5))
            element("video-detection-start").tap()
            let progress = element("video-editor-progress")
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "exists == false"), object: progress)], timeout: 60), .completed)
            XCTAssertFalse(element("video-editor-error").exists)
            XCTAssertFalse(export.isEnabled)
            control("video-review-confirm").tap()
            XCTAssertTrue(export.isEnabled)
        }
        export.tap()
        let save = element("video-editor-save")
        XCTAssertTrue(save.waitForExistence(timeout: 90), app.debugDescription)
        XCTAssertTrue(save.isEnabled); save.tap()
        XCTAssertTrue(app.staticTexts["編集した動画を保存しました"].waitForExistence(timeout: 30))
        app.buttons["閉じる"].firstMatch.tap()
        XCTAssertFalse(element("video-editor").exists)
        app.terminate()
        let index = app.launchArguments.firstIndex(of: "-media_feature_settings_v1")!
        app.launchArguments[index + 1] = "\"{}\""
        app.launch()
        if compat { _ = compatibilityBoardListAfterUnwinding(in: app) }
        XCTAssertTrue(menu.waitForExistence(timeout: 15)); menu.tap()
        XCTAssertFalse(editor.exists)
    }

    func testFutachaDeviceImageEditorSelectsPhotoAndSavesIndependently() {
        verifyDeviceImageEditor(compat: false)
    }

    func testCompatDeviceImageEditorSelectsPhotoAndSavesIndependently() {
        verifyDeviceImageEditor(compat: true)
    }

    func testFutachaDeviceImageEditorZoomAndPanBeforeSaving() {
        verifyDeviceImageEditor(compat: false, zoom: true)
    }

    func testCompatDeviceImageEditorZoomAndPanBeforeSaving() {
        verifyDeviceImageEditor(compat: true, zoom: true)
    }

    func testFutachaDeviceImageContourExtractionCanBeCorrectedBeforeSaving() {
        verifyDeviceImageEditor(compat: false, contour: true)
    }

    func testCompatDeviceImageContourExtractionCanBeCorrectedBeforeSaving() {
        verifyDeviceImageEditor(compat: true, contour: true)
    }

    func testFutachaDeviceImageDetectionRequiresReviewBeforeSaving() {
        verifyDeviceImageEditor(compat: false, automatic: true)
    }

    func testCompatDeviceImageDetectionRequiresReviewBeforeSaving() {
        verifyDeviceImageEditor(compat: true, automatic: true)
    }

    func testDeviceModelImportsFromFilesAfterCancellation() {
        verifyDeviceImageEditor(compat: false, automatic: true, modelImport: true)
    }

    private func verifyDeviceImageEditor(compat: Bool, automatic: Bool = false, zoom: Bool = false, contour: Bool = false, modelImport: Bool = false) {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", compat ? "toshiaki_compat" : "futacha",
            "-update_check_enabled", "false", "-commonUsedVersion", Self.alreadyReadChangeLogVersion,
            // NSArgumentDomain expects a property-list string, not unquoted JSON.
            "-media_feature_settings_v1", "\"{\\\"image_editor_enabled\\\":true,\\\"prompt_display_enabled\\\":false}\""
        ]
        app.launch()
        if compat { _ = compatibilityBoardListAfterUnwinding(in: app) }
        let menu = app.buttons[compat ? "その他" : "メニュー"].firstMatch
        XCTAssertTrue(menu.waitForExistence(timeout: 15))
        menu.tap()
        func element(_ tag: String) -> XCUIElement {
            app.descendants(matching: .any).matching(identifier: tag).firstMatch
        }
        func control(_ tag: String, upwards: Bool = true) -> XCUIElement {
            let target = element(tag)
            let panel = element("image-editor-controls")
            for _ in 0..<12 {
                let visible = panel.frame.intersection(app.frame).insetBy(dx: 0, dy: 2)
                var moveUp = upwards
                if target.exists {
                    if target.isHittable && visible.contains(target.frame) { return target }
                    moveUp = target.frame.midY > visible.midY
                }
                // Short gestures avoid scrolling a newly inserted control past the viewport.
                // A partially clipped button can report hittable but still ignore a native tap.
                let start = panel.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: moveUp ? 0.70 : 0.30))
                let end = panel.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: moveUp ? 0.42 : 0.58))
                start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.2)
            }
            XCTAssertTrue(target.isHittable, "Editor control must be reachable: \(tag)")
            return target
        }
        let editorMenu = element("device-image-editor-menu")
        XCTAssertTrue(editorMenu.waitForExistence(timeout: 5))
        editorMenu.tap()
        let photo = app.images.matching(identifier: "PXGGridLayout-Info").firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 15), "Seed the simulator Photos library before running.")
        app.buttons["Cancel"].firstMatch.tap()
        XCTAssertTrue(menu.waitForExistence(timeout: 10))
        menu.tap()
        XCTAssertTrue(editorMenu.waitForExistence(timeout: 5))
        editorMenu.tap()
        XCTAssertTrue(photo.waitForExistence(timeout: 15))
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: photo.frame.midX, dy: photo.frame.midY)).tap()
        let save = element("image-editor-export")
        XCTAssertTrue(save.waitForExistence(timeout: 30))
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "enabled == true"), object: save)], timeout: 30), .completed)
        if modelImport {
            control("image-editor-models").tap()
            let installed = element("analysis-model-state-NUDE_NET")
            XCTAssertTrue(installed.waitForExistence(timeout: 15))
            XCTAssertTrue(installed.label.contains("未導入"), "Move the verified NudeNet fixture to Documents/NudeNet-validation.onnx before this test.")
            let importButton = element("analysis-model-import-NUDE_NET")
            let panel = element("analysis-model-dialog")
            for _ in 0..<6 {
                if importButton.exists && importButton.isHittable && panel.frame.contains(importButton.frame) { break }
                panel.swipeUp(velocity: .slow)
            }
            XCTAssertTrue(importButton.isHittable); importButton.tap()
            let cancel = app.buttons.matching(NSPredicate(format: "label IN %@", ["Cancel", "キャンセル"])).firstMatch
            XCTAssertTrue(cancel.waitForExistence(timeout: 10)); cancel.tap()
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "enabled == true"), object: importButton)], timeout: 10), .completed)
            XCTAssertTrue(installed.label.contains("未導入"))
            importButton.tap()
            let file = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "NudeNet-validation")).firstMatch
            if !file.waitForExistence(timeout: 3) {
                let browse = app.tabBars.buttons.matching(NSPredicate(format: "label IN %@", ["Browse", "ブラウズ"])).firstMatch
                if browse.exists { browse.tap() }
                let local = app.staticTexts.matching(NSPredicate(format: "label IN %@", ["On My iPhone", "このiPhone内"])).firstMatch
                if !local.waitForExistence(timeout: 3) {
                    let back = app.buttons.matching(NSPredicate(format: "label IN %@", ["Browse", "ブラウズ"])).firstMatch
                    if back.exists { back.tap() }
                }
                XCTAssertTrue(local.waitForExistence(timeout: 10), app.debugDescription); local.tap()
                let folder = app.staticTexts["futacha"].firstMatch
                XCTAssertTrue(folder.waitForExistence(timeout: 10), app.debugDescription); folder.tap()
            }
            XCTAssertTrue(file.waitForExistence(timeout: 10), app.debugDescription); file.tap()
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "label CONTAINS %@", "導入済み"), object: installed)], timeout: 30), .completed)
            XCTAssertFalse(element("analysis-model-error").exists)
            app.buttons["閉じる"].firstMatch.tap()
        }
        if automatic {
            control("image-editor-models").tap()
            let installed = element("analysis-model-state-NUDE_NET")
            XCTAssertTrue(installed.waitForExistence(timeout: 15))
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "label CONTAINS %@", "導入済み"), object: installed)], timeout: 30),
                .completed, "Seed the verified NudeNet model in the simulator app's analysis_models directory.")
            app.buttons["閉じる"].firstMatch.tap()
            control("image-editor-detect").tap()
            element("image-detection-start").tap()
            XCTAssertTrue(element("image-editor-confirm-review").waitForExistence(timeout: 60))
            XCTAssertFalse(save.isEnabled)
            control("image-editor-confirm-review").tap()
            XCTAssertTrue(save.isEnabled)
        }
        if zoom {
            element("image-editor-zoom-in").tap()
            XCTAssertTrue(element("image-editor-zoom-level").label.contains("200"))
            control("image-editor-tool-view").tap()
            let canvas = element("image-editor-canvas")
            canvas.pinch(withScale: 1.5, velocity: 1)
            let zoomPercent = Int(element("image-editor-zoom-level").label.filter { $0.isNumber }) ?? 0
            XCTAssertGreaterThan(zoomPercent, 200, "Pinch must enlarge the image.")
            XCTAssertLessThanOrEqual(zoomPercent, 800)
            canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                .press(forDuration: 0.1, thenDragTo: canvas.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.5)))
            XCTAssertFalse(control("image-editor-undo").isEnabled)
        }
        control("image-editor-tool-regions").tap()
        let addRegion = control("image-editor-add-region")
        print("Image editor add region frame: \(addRegion.frame), panel: \(element("image-editor-controls").frame)")
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: addRegion.frame.midX, dy: addRegion.frame.midY)).tap()
        if automatic { XCTAssertFalse(save.isEnabled, "Adding a region must revoke review immediately.") }
        let black = control("image-editor-black")
        XCTAssertTrue(black.waitForExistence(timeout: 5))
        black.tap()
        XCTAssertTrue(control("image-editor-undo", upwards: false).isEnabled)
        if contour {
            control("image-contour-extract").tap()
            XCTAssertTrue(element("image-editor-confirm-review").waitForExistence(timeout: 120),
                          "Seed both verified MobileSAM models before running this test.")
            XCTAssertFalse(save.isEnabled)
            control("image-editor-confirm-review").tap()
            XCTAssertTrue(save.isEnabled)
            // Rectangle restoration is also an edit, including an uncertain automatic result.
            control("image-contour-reset").tap()
            XCTAssertFalse(save.isEnabled)
            control("image-editor-confirm-review").tap()
            control("image-contour-tool-erase").tap()
            let canvasFrame = element("image-editor-canvas").frame
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: canvasFrame.midX, dy: canvasFrame.midY)).tap()
            XCTAssertFalse(save.isEnabled, "Manual contour correction must revoke review.")
            control("image-editor-undo").tap()
            XCTAssertTrue(save.isEnabled, "Undo must restore the exact reviewed contour.")
        }
        if zoom {
            element("image-editor-zoom-reset").tap()
            XCTAssertTrue(element("image-editor-zoom-level").label.contains("100"))
        }
        if automatic {
            XCTAssertFalse(save.isEnabled, "Editing after review must require a new review.")
            control("image-editor-confirm-review", upwards: false).tap()
            XCTAssertTrue(save.isEnabled)
        }
        // Assertions and saved pixels verify this flow. Optional app.screenshot() can block
        // Simulator automation for minutes even when the editor remains responsive.
        save.tap()
        XCTAssertTrue(app.staticTexts["編集した画像を保存しました"].waitForExistence(timeout: 30))
        app.buttons["閉じる"].firstMatch.tap()
        XCTAssertFalse(element("image-editor").exists)
        app.terminate()
        let index = app.launchArguments.firstIndex(of: "-media_feature_settings_v1")!
        app.launchArguments[index + 1] = "\"{}\""
        app.launch()
        if compat { _ = compatibilityBoardListAfterUnwinding(in: app) }
        XCTAssertTrue(menu.waitForExistence(timeout: 15))
        menu.tap()
        XCTAssertFalse(editorMenu.exists, "The device editor must be hidden when its own setting is OFF.")
    }

    // Seed the simulator library with the generated camera.heic / camera.mov fixtures.
    // These checks require a real selection and never submit a network post.
    func testFutachaReplyAcceptsPhotoAndCameraVideo() {
        verifyPostingMedia(compat: false, build: false)
    }

    func testFutachaNewThreadAcceptsPhotoAndCameraVideo() {
        verifyPostingMedia(compat: false, build: true)
    }

    func testCompatReplyAcceptsPhotoAndCameraVideo() {
        verifyPostingMedia(compat: true, build: false)
    }

    func testCompatNewThreadAcceptsPhotoAndCameraVideo() {
        verifyPostingMedia(compat: true, build: true)
    }

    private func verifyPostingMedia(compat: Bool, build: Bool) {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", compat ? "toshiaki_compat" : "futacha",
            "-update_check_enabled", "false", "-commonUsedVersion", Self.alreadyReadChangeLogVersion,
            "-attachment_picker_preference", "MEDIA"
        ]
        app.launch()
        if compat {
            let board = compatibilityBoardCardAfterUnwinding(in: app)
            XCTAssertTrue(board.waitForExistence(timeout: 15))
            board.tap()
            XCTAssertTrue(app.otherElements["compat-catalog-grid"].waitForExistence(timeout: 10))
        } else {
            ensureCompactHeaderTutorialBoard(in: app).tap()
        }
        if build {
            app.buttons[compat ? "スレ立て" : "スレッド作成"].firstMatch.tap()
        } else {
            app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")).firstMatch.tap()
            let reply = app.buttons[compat ? "書き込み" : "返信"].firstMatch
            XCTAssertTrue(reply.waitForExistence(timeout: 10))
            reply.tap()
        }
        XCTAssertTrue(app.textViews[compat ? "compat-post-comment-field" : "コメント"].waitForExistence(timeout: 10))
        Thread.sleep(forTimeInterval: 1)
        for video in [false, true] {
            if compat && video {
                // The toolbar changes its label while an attachment is present.
                app.buttons["添付削除"].firstMatch.tap()
            }
            openPostingLibrary(in: app, compat: compat, video: video)
            let photo = app.images.matching(identifier: "PXGGridLayout-Info").firstMatch
            XCTAssertTrue(photo.waitForExistence(timeout: 15), "Seed the simulator with image and MOV fixtures before running.")
            let cancel = app.buttons["Cancel"].firstMatch
            XCTAssertTrue(cancel.waitForExistence(timeout: 5))
            cancel.tap()
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: photo)], timeout: 10), .completed)
            openPostingLibrary(in: app, compat: compat, video: video)
            XCTAssertTrue(photo.waitForExistence(timeout: 15))
            // Interactive dismissal must release the same picker session as Cancel.
            let photoBar = app.navigationBars.firstMatch.frame
            // Avoid dragging the Photos/Collections segmented control itself.
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: photoBar.maxX - 16, dy: photoBar.midY))
                .press(forDuration: 0.01,
                       thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.9)),
                       withVelocity: .fast, thenHoldForDuration: 0)
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: photo)], timeout: 10), .completed)
            openPostingLibrary(in: app, compat: compat, video: video)
            XCTAssertTrue(photo.waitForExistence(timeout: 15))
            // iOS 26's remote Photos images can report not-hittable despite
            // visible frames; use their displayed coordinates for the tap.
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: photo.frame.midX, dy: photo.frame.midY)).tap()
            let name = app.descendants(matching: .any).matching(NSPredicate(format: "label MATCHES %@", video ? ".*\\.mp4" : ".*\\.jpe?g")).firstMatch
            XCTAssertTrue(name.waitForExistence(timeout: 30), "The selected camera media did not become a posting attachment.")
            XCTAssertFalse(photo.exists, "The camera roll remained open after selection.")
            attachCompactHeader(app, name: "attachment-\(compat ? "compat" : "futacha")-\(build ? "build" : "reply")-\(video ? "video" : "image")")
        }
    }

    private func openPostingLibrary(in app: XCUIApplication, compat: Bool, video: Bool) {
        let button = app.buttons[compat ? "添付画像" : (video ? "動画を選択" : "画像を選択")].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        // Let the composer finish its keyboard/layout transition before tapping.
        Thread.sleep(forTimeInterval: 0.5)
        button.tap()
        if compat {
            XCTAssertTrue(app.buttons["フォトライブラリ"].waitForExistence(timeout: 5))
            app.buttons["フォトライブラリ"].tap()
            XCTAssertTrue(app.buttons[video ? "動画" : "写真"].waitForExistence(timeout: 5))
            app.buttons[video ? "動画" : "写真"].tap()
        }
    }

    func testFutachaCompactHeaderShrinksAndPersistsInFlatMode() {
        verifyCompactHeader(mode: "Flat")
    }

    func testFutachaCompactHeaderShrinksAndPersistsInTreeMode() {
        verifyCompactHeader(mode: "Tree")
    }

    private func verifyCompactHeader(mode: String) {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "futacha", "-update_check_enabled", "false",
            "-commonUsedVersion", Self.alreadyReadChangeLogVersion,
            "-thread_display_mode", mode, "-compact_thread_header_enabled", "false"
        ]
        app.launch()
        openCompactHeaderTutorial(in: app)
        let bar = app.otherElements["futacha-thread-top-bar"].firstMatch
        let header = app.otherElements["futacha-post-header-1364612020"].firstMatch
        XCTAssertTrue(bar.waitForExistence(timeout: 10))
        XCTAssertTrue(header.waitForExistence(timeout: 10))
        let originalBarHeight = bar.frame.height
        let originalPostHeight = header.frame.height
        attachCompactHeader(app, name: "compact-\(mode)-off")
        toggleCompactHeader(in: app)
        XCTAssertGreaterThanOrEqual(originalBarHeight - bar.frame.height, 15)
        let compactBarHeight = bar.frame.height
        XCTAssertLessThan(header.frame.height, originalPostHeight)
        let compactPostHeight = header.frame.height
        attachCompactHeader(app, name: "compact-\(mode)-on")
        XCTAssertTrue(app.buttons["スレ内検索"].firstMatch.isHittable)
        app.buttons["スレ内検索"].firstMatch.tap()
        let closeSearch = app.buttons["検索を閉じる"].firstMatch
        XCTAssertTrue(closeSearch.waitForExistence(timeout: 5))
        closeSearch.tap()
        XCTAssertEqual(bar.frame.height, compactBarHeight, accuracy: 1)

        // Drop the initial argument-domain override, then read the actual saved value.
        app.terminate()
        if let index = app.launchArguments.firstIndex(of: "-compact_thread_header_enabled") {
            app.launchArguments.removeSubrange(index...(index + 1))
        }
        app.launch()
        openCompactHeaderTutorial(in: app)
        XCTAssertEqual(bar.frame.height, compactBarHeight, accuracy: 1)
        XCTAssertEqual(header.frame.height, compactPostHeight, accuracy: 1)
        toggleCompactHeader(in: app)
        XCTAssertEqual(bar.frame.height, originalBarHeight, accuracy: 1)
        app.buttons["最上部"].firstMatch.tap()
        Thread.sleep(forTimeInterval: 0.8)
        // The tutorial randomizes ID/saidane/image variants on process launch,
        // so normal metadata can legitimately gain or lose a wrapped line.
        XCTAssertGreaterThan(header.frame.height, compactPostHeight)
        attachCompactHeader(app, name: "compact-\(mode)-restored")
    }

    private func ensureCompactHeaderTutorialBoard(in app: XCUIApplication) -> XCUIElement {
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        if !board.waitForExistence(timeout: 3) {
            app.buttons["メニュー"].tap()
            app.buttons["新規追加"].tap()
            let name = app.textViews["板の名前"]
            XCTAssertTrue(name.waitForExistence(timeout: 5))
            name.tap()
            for character in "Tutorial" { name.typeText(String(character)) }
            let url = app.textViews["板のURL"]
            url.tap()
            for character in "https://www.example.com/t/futaba.php" { url.typeText(String(character)) }
            app.buttons["追加"].tap()
        }
        XCTAssertTrue(board.waitForExistence(timeout: 15))
        return board
    }

    private func openCompactHeaderTutorial(in app: XCUIApplication) {
        ensureCompactHeaderTutorialBoard(in: app).tap()
        let thread = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")).firstMatch
        XCTAssertTrue(thread.waitForExistence(timeout: 10))
        thread.tap()
        let top = app.buttons["最上部"].firstMatch
        XCTAssertTrue(top.waitForExistence(timeout: 10))
        top.tap()
        Thread.sleep(forTimeInterval: 0.8)
    }

    private func toggleCompactHeader(in app: XCUIApplication) {
        app.buttons["その他"].firstMatch.tap()
        let settings = app.buttons.matching(NSPredicate(format: "label == %@", "設定"))
            .allElementsBoundByIndex.last { $0.isHittable }
        XCTAssertNotNil(settings)
        settings?.tap()
        let display = app.staticTexts["表示"].firstMatch
        XCTAssertTrue(display.waitForExistence(timeout: 10))
        display.tap()
        let toggle = app.descendants(matching: .any)["compact-thread-header-switch"].firstMatch
        // Compose exposes the expanded section's offscreen children with an
        // empty activation point; asking isHittable for those fails in XCTest.
        for _ in 0..<8 {
            if toggle.exists, !toggle.frame.isEmpty,
               toggle.frame.minY > 84, toggle.frame.maxY < app.frame.height - 20 { break }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
                .press(forDuration: 0.1,
                       thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)),
                       withVelocity: .slow, thenHoldForDuration: 0.6)
        }
        XCTAssertTrue(toggle.exists)
        XCTAssertGreaterThan(toggle.frame.minY, 84)
        XCTAssertLessThan(toggle.frame.maxY, app.frame.height - 20)
        Thread.sleep(forTimeInterval: 1.5)
        attachCompactHeader(app, name: "compact-settings-before-tap")
        let point = toggle.frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: point.midX, dy: point.midY)).tap()
        Thread.sleep(forTimeInterval: 0.8)
        attachCompactHeader(app, name: "compact-settings-after-tap")
        let back = app.buttons.matching(NSPredicate(format: "label == %@", "戻る"))
            .allElementsBoundByIndex.last { $0.isHittable }
        XCTAssertNotNil(back)
        back?.tap()
        XCTAssertTrue(app.otherElements["futacha-thread-top-bar"].firstMatch.waitForExistence(timeout: 10))
    }

    private func attachCompactHeader(_ app: XCUIApplication, name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testCompatHeaderIgnoresFutachaOnlyCompactPreference() {
        var heights: [CGFloat] = []
        for value in ["false", "true"] {
            let app = makeApplication()
            app.launchArguments += [
                "-experience.active_profile", "toshiaki_compat", "-update_check_enabled", "false",
                "-commonUsedVersion", Self.alreadyReadChangeLogVersion,
                "-futacha.issue78.archive_fixture", "-compact_thread_header_enabled", value
            ]
            app.launch()
            let body = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "りんみ")).firstMatch
            XCTAssertTrue(body.waitForExistence(timeout: 20))
            heights.append(body.frame.height)
            XCTAssertFalse(app.otherElements["futacha-thread-top-bar"].exists)
            attachCompactHeader(app, name: "compat-global-compact-\(value)")
            app.terminate()
        }
        XCTAssertEqual(heights[0], heights[1], accuracy: 1)
    }

    func testFutachaQuoteSelectionHasBulkActions() {
        let app = makeApplication()
        app.launchArguments += ["-experience.active_profile", "futacha", "-update_check_enabled", "false"]
        app.launch()
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        if !board.waitForExistence(timeout: 3) {
            // Compatibility fixture imports can leave only the remote board registered.
            app.buttons["メニュー"].tap()
            app.buttons["新規追加"].tap()
            let name = app.textViews["板の名前"]
            XCTAssertTrue(name.waitForExistence(timeout: 5))
            name.tap()
            for character in "Tutorial" { name.typeText(String(character)) }
            let url = app.textViews["板のURL"]
            url.tap()
            for character in "https://www.example.com/t/futaba.php" { url.typeText(String(character)) }
            XCTAssertEqual(url.value as? String, "https://www.example.com/t/futaba.php")
            app.buttons["追加"].tap()
        }
        XCTAssertTrue(board.waitForExistence(timeout: 10))
        board.tap()
        let thread = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")).firstMatch
        XCTAssertTrue(thread.waitForExistence(timeout: 10))
        thread.tap()
        let top = app.buttons["最上部"].firstMatch
        XCTAssertTrue(top.waitForExistence(timeout: 10))
        top.tap()
        let post = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "No.")).firstMatch
        XCTAssertTrue(post.waitForExistence(timeout: 10))
        post.press(forDuration: 1.0)
        let quote = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "引用")).firstMatch
        XCTAssertTrue(quote.waitForExistence(timeout: 5))
        quote.tap()
        verifyQuoteBulkActions(in: app, confirmLabel: "コピー", screenshotName: "futacha-quote-bulk-actions")
    }

    func testCompatQuoteSelectionHasBulkActions() {
        let app = makeApplication()
        app.launchArguments += ["-experience.active_profile", "toshiaki_compat", "-futacha.issue78.archive_fixture", "-update_check_enabled", "false"]
        app.launch()
        let body = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "りんみ")).firstMatch
        XCTAssertTrue(body.waitForExistence(timeout: 20))
        body.press(forDuration: 1.0)
        let reply = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "返信")).firstMatch
        XCTAssertTrue(reply.waitForExistence(timeout: 5))
        reply.tap()
        verifyQuoteBulkActions(in: app, confirmLabel: "上書き", screenshotName: "compat-quote-bulk-actions")
    }

    private func verifyQuoteBulkActions(in app: XCUIApplication, confirmLabel: String, screenshotName: String) {
        let all = app.buttons["全選択"].firstMatch
        let clear = app.buttons["全解除"].firstMatch
        let confirm = app.buttons[confirmLabel].firstMatch
        XCTAssertTrue(all.waitForExistence(timeout: 5))
        XCTAssertTrue(all.isHittable)
        all.tap()
        XCTAssertFalse(all.isEnabled)
        XCTAssertTrue(clear.isEnabled)
        XCTAssertTrue(confirm.isEnabled)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = screenshotName
        screenshot.lifetime = .keepAlways
        add(screenshot)
        clear.tap()
        XCTAssertTrue(all.isEnabled)
        XCTAssertFalse(clear.isEnabled)
        XCTAssertFalse(confirm.isEnabled)
        app.buttons["キャンセル"].firstMatch.tap()
    }

    func testSavedDocumentsAreVisibleInFiles() throws {
#if targetEnvironment(simulator)
        let app = makeApplication()
        app.launchArguments += ["-experience.active_profile", "futacha", "-commonUsedVersion", Self.alreadyReadChangeLogVersion, "-update_check_enabled", "false"]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let board = boardCard(in: app, url: "https://www.example.com/t/futaba.php")
        XCTAssertTrue(board.waitForExistence(timeout: 15))
        board.tap()
        let thread = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "チュートリアル")).firstMatch
        XCTAssertTrue(thread.waitForExistence(timeout: 10))
        thread.tap()
        let save = app.buttons["保存"].firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.tap()
        XCTAssertTrue(app.staticTexts["保存結果"].waitForExistence(timeout: 90), "The real thread save must finish before looking for its files.")
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "保存先:")).firstMatch.exists)

        let files = XCUIApplication(bundleIdentifier: "com.apple.DocumentsApp")
        files.launch()
        let browse = files.tabBars.buttons.matching(NSPredicate(format: "label IN %@", ["Browse", "ブラウズ"])).firstMatch
        if browse.waitForExistence(timeout: 10) { browse.tap() }
        let local = files.staticTexts.matching(NSPredicate(format: "label IN %@", ["On My iPhone", "このiPhone内"])).firstMatch
        if !local.waitForExistence(timeout: 5) {
            let back = files.buttons.matching(NSPredicate(format: "label IN %@", ["Browse", "ブラウズ"])).firstMatch
            if back.exists { back.tap() }
        }
        XCTAssertTrue(local.waitForExistence(timeout: 10))
        local.tap()
        let folder = files.staticTexts["futacha"].firstMatch
        let tree = XCTAttachment(string: files.debugDescription)
        tree.name = "save-audit-files-app-folders"
        tree.lifetime = .keepAlways
        add(tree)
        XCTAssertTrue(folder.waitForExistence(timeout: 10), "Files must expose Futacha Documents so saved files can be found.")
        folder.tap()
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = "save-audit-futacha-documents"
        screenshot.lifetime = .keepAlways
        add(screenshot)
#else
        throw XCTSkip("Run this navigation check on the dedicated Simulator.")
#endif
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

    func testBothModesOpenPatrolSettingsAndSearchHelp() throws {
        for compat in [false, true] {
            let app = makeApplication()
            app.launchArguments += ["-experience.active_profile", compat ? "toshiaki_compat" : "futacha",
                                    "-experience.profile_generation", "1140"]
            app.launch()
            if compat {
                XCTAssertTrue(compatibilityBoardListAfterUnwinding(in: app).waitForExistence(timeout: 15))
                app.buttons["その他"].firstMatch.tap()
            } else {
                XCTAssertTrue(app.buttons["メニュー"].waitForExistence(timeout: 20))
                app.buttons["メニュー"].tap()
            }
            let settings = app.staticTexts["設定"].firstMatch
            XCTAssertTrue(settings.waitForExistence(timeout: 10))
            settings.tap()
            func reveal(_ text: String) -> XCUIElement {
                let target = app.staticTexts[text].firstMatch
                for _ in 0..<18 {
                    if target.exists && target.isHittable { return target }
                    let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72))
                    start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.4)))
                }
                XCTAssertTrue(target.isHittable, "Missing settings row: \(text)")
                return target
            }
            if !compat { reveal("バックグラウンド・通信").tap() }
            reveal("巡回管理").tap()
            let help = app.buttons["履歴・巡回のヘルプ"].firstMatch
            XCTAssertTrue(help.waitForExistence(timeout: 10))
            XCTAssertTrue(help.isHittable)
            help.tap()
            let field = app.textViews["help-search-field"].firstMatch
            XCTAssertTrue(field.waitForExistence(timeout: 10))
            let document = app.webViews.firstMatch
            XCTAssertTrue(document.waitForExistence(timeout: 10))
            let patrolHeading = document.staticTexts["履歴・巡回の使い方"].firstMatch
            for _ in 0..<8 where !patrolHeading.isHittable { document.swipeUp() }
            XCTAssertTrue(patrolHeading.isHittable)
            XCTAssertFalse(document.staticTexts["巡回を始める"].exists)
            patrolHeading.tap()
            XCTAssertTrue(document.staticTexts["巡回を始める"].waitForExistence(timeout: 5))
            patrolHeading.tap()
            XCTAssertTrue(document.staticTexts["巡回を始める"].waitForNonExistence(timeout: 5))
            field.tap()
            field.typeText("Wi-Fi")
            XCTAssertTrue(app.otherElements["help-search-results"].waitForExistence(timeout: 10))
            XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "今すぐ巡回・結果の再読込")).firstMatch.waitForExistence(timeout: 10))
            let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            screenshot.name = compat ? "v11.4-toshiaki-help-search" : "v11.4-futacha-help-search"
            screenshot.lifetime = .keepAlways
            add(screenshot)
            app.buttons["クリア"].firstMatch.tap()
            field.tap()
            field.typeText("no-such-help-word-114")
            XCTAssertTrue(app.staticTexts["一致する項目がありません"].waitForExistence(timeout: 10))
            app.buttons["クリア"].firstMatch.tap()
            XCTAssertTrue(app.otherElements["compat-help-content"].waitForExistence(timeout: 10))
            app.terminate()
        }
    }

    func testFutachaSharedDetailedSettingsOpenWithoutChangingMode() {
        let app = makeApplication()
        app.launchArguments += ["-experience.active_profile", "futacha", "-experience.profile_generation", "1020"]
        app.launch()
        XCTAssertTrue(app.buttons["メニュー"].waitForExistence(timeout: 20))
        app.buttons["メニュー"].tap()
        let settings = app.staticTexts["設定"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()
        let shared = app.staticTexts["操作"].firstMatch
        for _ in 0..<6 where !shared.isHittable { app.swipeUp() }
        XCTAssertTrue(shared.waitForExistence(timeout: 10))
        shared.tap()
        let control = app.staticTexts["コントロール"].firstMatch
        XCTAssertTrue(control.waitForExistence(timeout: 10))
        control.tap()
        let confirmation = app.staticTexts["送信時の確認"].firstMatch
        for _ in 0..<8 where !confirmation.isHittable { app.swipeUp() }
        XCTAssertTrue(confirmation.isHittable)
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = "futacha-shared-control-settings"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.buttons["戻る"].firstMatch.tap()
        XCTAssertFalse(app.staticTexts["共通の詳細設定"].firstMatch.exists)
        XCTAssertTrue(app.staticTexts["モード"].firstMatch.waitForExistence(timeout: 10))
        let display = app.staticTexts["表示"].firstMatch
        for _ in 0..<6 where !display.isHittable { app.swipeUp() }
        XCTAssertTrue(display.isHittable)
        display.tap()
        let fontAndTabs = app.staticTexts["フォント・タブ一覧"].firstMatch
        for _ in 0..<6 where !fontAndTabs.isHittable { app.swipeUp() }
        XCTAssertTrue(fontAndTabs.isHittable)
        fontAndTabs.tap()
        XCTAssertTrue(app.staticTexts["カスタムフォント"].waitForExistence(timeout: 10))
        for legacyColorSetting in ["カラーテーマ", "文字色", "ナビゲーションバー背景色"] {
            XCTAssertFalse(app.staticTexts[legacyColorSetting].exists)
        }
        let themeScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        themeScreenshot.name = "futacha-shared-design-follows-modern-theme"
        themeScreenshot.lifetime = .keepAlways
        add(themeScreenshot)
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

    func testIssue78PersistedArchiveLabelsAreAbsentFromBodyAndQuoteOnDevice() {
        let app = makeApplication()
        app.launchArguments += [
            "-experience.active_profile", "toshiaki_compat",
            "-experience.profile_generation", "1178",
            "-futacha.issue78.archive_fixture"
        ]
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        let visibleElements = app.descendants(matching: .any)
        let bodyLabels = visibleElements.matching(
            NSPredicate(format: "label CONTAINS %@", "りんみ")
        )
        let labelsLoaded = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in bodyLabels.count >= 1 },
            object: nil
        )
        XCTAssertEqual(
            XCTWaiter().wait(for: [labelsLoaded], timeout: 20),
            .completed,
            "The Issue #78 body and quoted uploader labels did not render."
        )
        XCTAssertTrue(visibleElements.matching(
            NSPredicate(format: "label CONTAINS %@", "りんみ")
        ).firstMatch.exists)
        XCTAssertTrue(visibleElements.matching(
            NSPredicate(format: "label CONTAINS %@", "失恋はほむらもだろ…")
        ).firstMatch.exists)
        XCTAssertEqual(
            visibleElements.matching(
                NSPredicate(format: "label CONTAINS %@", "[見る]")
            ).count,
            0,
            "Issue #78 regressed: a persisted archive label still contains [見る]."
        )

        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "issue78-ios-device"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testIssue78SavedHtmlViewerRemovesFtbucketPreviewControl() {
        let app = makeApplication()
        app.launchArguments.append("-futacha.issue78.saved_html_fixture")
        app.launch()

        XCTAssertTrue(app.navigationBars["保存済みスレ"].waitForExistence(timeout: 20))
        XCTAssertTrue(
            app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@", "保存本文")
            ).firstMatch.waitForExistence(timeout: 10)
        )
        XCTAssertEqual(
            app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@", "[見る]")
            ).count,
            0,
            "Issue #78 regressed in the saved HTML viewer."
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
    func testWebmStabilityH264Aac() { verifyWebmStability("h264-aac.mp4", expected: "playing") }
    func testWebmStabilityVp8Opus() { verifyWebmStability("vp8-opus.webm", expected: "playing") }
    func testWebmStabilityVp8OpusMuted() { verifyWebmStability("vp8-opus.webm", expected: "playing", muted: true) }
    func testWebmStabilityLocalVp8Opus() { verifyWebmStability("vp8-opus.webm", expected: "playing", localFile: true) }
    func testWebmStabilityVp9Opus() { verifyWebmStability("vp9-opus.webm", expected: "capability") }
    func testWebmStabilityVp9Vorbis() { verifyWebmStability("vp9-vorbis.webm", expected: "capability") }
    func testWebmStabilityBrokenFile() { verifyWebmStability("broken.webm", expected: "error") }
    func testWebmStabilityUnsupportedCodec() { verifyWebmStability("unsupported.webm", expected: "error") }
    func testWebmStabilityTenBit() { verifyWebmStability("vp9-10bit.webm", expected: "capability") }
    func testWebmStabilityExplicitMp4Fallback() { verifyWebmStability("broken.webm", expected: "fallback") }

    func testWebmStabilityHttp404() { verifyWebmStability("vp8-opus.webm", expected: "error", httpCase: "missing.webm") }
    func testWebmStabilityHttpWrongMime() { verifyWebmStability("vp8-opus.webm", expected: "playing", httpCase: "wrong-type.webm") }
    func testWebmStabilityHttpNoRanges() { verifyWebmStability("vp8-opus.webm", expected: "playing", httpCase: "no-range.webm") }
    func testWebmStabilityHttpDisconnect() { verifyWebmStability("vp8-opus.webm", expected: "error", httpCase: "disconnect.webm") }

    private func verifyWebmStability(_ name: String, expected: String, httpCase: String? = nil, localFile: Bool = false, muted: Bool = false) {
        let app = makeApplication()
        app.launchEnvironment["FUTACHA_VIDEO_FIXTURE_BASE64"] = webmStabilityFixtures[name]!
        app.launchEnvironment["FUTACHA_VIDEO_FIXTURE_EXTENSION"] = name.hasSuffix(".mp4") ? "mp4" : "webm"
        if localFile { app.launchEnvironment["FUTACHA_VIDEO_LOCAL_FILE"] = "1" }
        if muted { app.launchEnvironment["FUTACHA_VIDEO_MUTED"] = "1" }
        if expected == "fallback" { app.launchEnvironment["FUTACHA_VIDEO_FALLBACK_BASE64"] = webmStabilityFixtures["h264-aac.mp4"]! }
        if let httpCase = httpCase { app.launchEnvironment["FUTACHA_VIDEO_REMOTE_URL"] = "http://127.0.0.1:18962/" + httpCase }
        app.launch()
        let error = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "state:Error")).firstMatch
        if expected == "error" {
            XCTAssertTrue(error.waitForExistence(timeout: 35), app.debugDescription)
        } else {
            let loaded = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "size:96x64")).firstMatch
            let source = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "source:fallback.mp4")).firstMatch
            if expected == "fallback" { XCTAssertTrue(source.waitForExistence(timeout: 35), app.debugDescription) }
            let readyOrError = NSPredicate { _, _ in loaded.exists || error.exists }
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: readyOrError, object: nil)], timeout: 35), .completed)
            if !error.exists {
                let web = app.webViews.firstMatch
                let isWeb = name.hasSuffix(".webm") && expected != "fallback"
                let view = isWeb ? web : app.otherElements["ビデオ"].firstMatch
                XCTAssertTrue(view.waitForExistence(timeout: 5), app.debugDescription)
                view.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                let play = app.buttons.matching(NSPredicate(format: "label ==[c] %@ OR label == %@", "play", "再生")).firstMatch
                // WebKit's inline play control is already activated by the center tap.
                if !isWeb && play.waitForExistence(timeout: 2) { play.tap() }
                let played = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "played:true")).firstMatch
                let done = NSPredicate { _, _ in played.exists || error.exists }
                XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: done, object: nil)], timeout: 25), .completed, app.debugDescription)
                if expected == "playing" || expected == "fallback" { XCTAssertTrue(played.exists, app.debugDescription) }
            } else if expected == "playing" || expected == "fallback" { XCTFail(app.debugDescription) }
        }
        let state = XCTAttachment(string: app.debugDescription)
        state.name = "webm-stability-\(localFile ? "local-" : "")\(httpCase ?? name)-\(expected)-state"
        state.lifetime = .keepAlways; add(state)
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "webm-stability-\(httpCase ?? name)-\(expected)"
        shot.lifetime = .keepAlways; add(shot)
        let expectedRequests = localFile ? 0 : (expected == "fallback" ? 2 : 1)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "requests:\(expectedRequests) ")).firstMatch.exists, app.debugDescription)
        XCTAssertTrue(app.buttons["Hide video"].exists)
        app.buttons["Hide video"].tap()
        XCTAssertTrue(app.buttons["Show video"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "documents:0")).firstMatch.waitForExistence(timeout: 5))
    }

    func testSharedOriginalMp4ViewerCopiesAndSavesWithoutAnotherAcquisition() { verifySharedOriginalVideo(ext: "mp4", fixture: originalVideoMp4Fixture) }
    func testSharedOriginalVp8WebmViewerUsesLocalWebKitAndReleasesViewPins() { verifySharedOriginalVideo(ext: "webm", fixture: originalVideoWebmFixture) }

    func testSavedLocalVideoShowsAndCopiesPromptWithoutNetwork() {
        let app = makeApplication()
        app.launchEnvironment["FUTACHA_VIDEO_FIXTURE_BASE64"] = originalVideoMp4Fixture
        app.launchEnvironment["FUTACHA_VIDEO_FIXTURE_EXTENSION"] = "mp4"
        app.launchEnvironment["FUTACHA_VIDEO_LOCAL_FILE"] = "1"
        app.launch()
        let loaded = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "requests:0 size:240x320")).firstMatch
        XCTAssertTrue(loaded.waitForExistence(timeout: 30), app.debugDescription)
        XCTAssertFalse(app.buttons["生成情報"].exists)
        app.buttons["Prompt ON"].tap()
        XCTAssertTrue(app.buttons["生成情報"].waitForExistence(timeout: 10))
        let inline = app.staticTexts["viewer-prompt-inline-text"].firstMatch
        let toggle = app.buttons["viewer-prompt-toggle"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        XCTAssertFalse(inline.exists)
        XCTAssertTrue(app.staticTexts["AI"].exists)
        toggle.tap()
        XCTAssertTrue(inline.waitForExistence(timeout: 10))
        XCTAssertTrue(inline.label.contains("青い鳥"))
        XCTAssertTrue(app.staticTexts["AI"].exists)
        app.buttons["生成情報"].tap()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "青い鳥")).firstMatch.waitForExistence(timeout: 10))
        app.buttons["プロンプト をコピー"].firstMatch.tap()
        XCTAssertTrue(app.buttons["コピーしました"].firstMatch.waitForExistence(timeout: 5))
        app.buttons["閉じる"].firstMatch.tap()
        XCTAssertTrue(inline.exists)
        app.buttons["Hide video"].tap()
        XCTAssertFalse(inline.exists)
        app.buttons["Show video"].tap()
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        XCTAssertFalse(inline.exists)
        XCTAssertTrue(app.staticTexts["AI"].exists)
        toggle.tap()
        XCTAssertTrue(inline.waitForExistence(timeout: 5))
        app.buttons["Prompt OFF"].tap()
        XCTAssertFalse(app.buttons["生成情報"].exists)
        XCTAssertTrue(loaded.exists)
    }

    private func verifySharedOriginalVideo(ext: String, fixture: String) {
        let app = makeApplication()
        app.launchEnvironment["FUTACHA_VIDEO_FIXTURE_BASE64"] = fixture
        app.launchEnvironment["FUTACHA_VIDEO_FIXTURE_EXTENSION"] = ext
        app.launch()
        let loaded = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "requests:1 size:240x320")).firstMatch
        XCTAssertTrue(loaded.waitForExistence(timeout: 30), app.debugDescription)
        if ext == "webm" {
            XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "documents:1")).firstMatch.waitForExistence(timeout: 5))
        }
        let video = ext == "webm" ? app.webViews.firstMatch : app.otherElements["ビデオ"].firstMatch
        XCTAssertTrue(video.waitForExistence(timeout: 5))
        video.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let play = app.buttons.matching(NSPredicate(format: "label ==[c] %@ OR label == %@", "play", "再生")).firstMatch
        if ext == "mp4" {
            XCTAssertTrue(play.waitForExistence(timeout: 5), app.debugDescription)
            play.tap()
        }
        let playing = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "played:true")).firstMatch
        let didPlay = playing.waitForExistence(timeout: 15)
        let playerScreenshot = XCTAttachment(screenshot: app.screenshot())
        playerScreenshot.name = "shared-original-\(ext)-playback"
        playerScreenshot.lifetime = .keepAlways
        add(playerScreenshot)
        XCTAssertTrue(didPlay, app.debugDescription)
        XCTAssertFalse(app.buttons["生成情報"].exists)
        app.buttons["Prompt ON"].tap()
        XCTAssertTrue(app.buttons["生成情報"].waitForExistence(timeout: 10))
        let inline = app.staticTexts["viewer-prompt-inline-text"].firstMatch
        let toggle = app.buttons["viewer-prompt-toggle"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        XCTAssertFalse(inline.exists)
        XCTAssertTrue(app.staticTexts["AI"].exists)
        let collapsedPrompt = XCTAttachment(screenshot: app.screenshot())
        collapsedPrompt.name = "shared-original-\(ext)-collapsed-prompt"
        collapsedPrompt.lifetime = .keepAlways
        add(collapsedPrompt)
        toggle.tap()
        XCTAssertTrue(inline.waitForExistence(timeout: 10))
        XCTAssertTrue(inline.label.contains("青い鳥"))
        XCTAssertTrue(app.staticTexts["AI"].exists)
        let expandedPrompt = XCTAttachment(screenshot: app.screenshot())
        expandedPrompt.name = "shared-original-\(ext)-expanded-prompt"
        expandedPrompt.lifetime = .keepAlways
        add(expandedPrompt)
        toggle.tap()
        XCTAssertFalse(inline.exists)
        XCTAssertTrue(app.staticTexts["AI"].exists)
        toggle.tap()
        XCTAssertTrue(inline.waitForExistence(timeout: 5))
        app.buttons["生成情報"].tap()
        let prompt = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "青い鳥")).firstMatch
        XCTAssertTrue(prompt.waitForExistence(timeout: 10), app.debugDescription)
        app.buttons["プロンプト をコピー"].firstMatch.tap()
        XCTAssertTrue(app.buttons["コピーしました"].firstMatch.waitForExistence(timeout: 5))
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "shared-original-\(ext)-prompt"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.buttons["閉じる"].firstMatch.tap()
        app.buttons["Save original"].tap()
        let saved = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "saved:")).firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 10), app.debugDescription)
        app.buttons["Hide video"].tap()
        XCTAssertFalse(inline.exists)
        XCTAssertTrue(app.buttons["Show video"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "documents:0")).firstMatch.waitForExistence(timeout: 5))
        app.buttons["Show video"].tap()
        XCTAssertTrue(loaded.waitForExistence(timeout: 15))
        XCTAssertTrue(toggle.waitForExistence(timeout: 10))
        XCTAssertFalse(inline.exists)
        XCTAssertTrue(app.staticTexts["AI"].exists)
        toggle.tap()
        XCTAssertTrue(inline.waitForExistence(timeout: 5))
        app.buttons["Prompt OFF"].tap()
        XCTAssertFalse(app.buttons["生成情報"].exists)
        XCTAssertTrue(loaded.exists)
    }

}
