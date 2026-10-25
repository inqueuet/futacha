import XCTest
import CryptoKit

/// Product Root -> Kotlin draw acknowledgement -> native cover and saved-HTML host.
/// Complements the standalone UIKit ordering fixture; it does not pretend to inspect an OS snapshot bitmap.
final class IosAppLockSnapshotUITests: XCTestCase {
    private let password = "m65-local-password"

    private func application(lock: Bool, savedHtml: Bool, profile: String = "futacha", extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        let salt = "m65-ui-fixture"
        let digest = SHA256.hash(data: Data("\(salt):\(password)".utf8)).map { String(format: "%02x", $0) }.joined()
        app.launchArguments += [
            "-review.ugc_eula_accepted_version", "2026-08-22", "-commonUsedVersion", "9999.0",
            "-experience.active_profile", profile, "-experience.profile_generation", "2026102265",
            "-update_check_enabled", "false", "-telemetry_collection_enabled", "false",
            "-background_refresh_enabled", "false", "-watch_alert_enabled", "false",
            "-app_lock_password_hash", lock ? "sha256-v1:\(salt):\(digest)" : ""
        ]
        if savedHtml { app.launchArguments.append("-futacha.issue78.saved_html_fixture") }
        app.launchArguments += extra
        return app
    }
    private func cover(_ app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "app-lock-snapshot-cover").firstMatch
    }
    @discardableResult
    private func wait(_ predicate: @escaping () -> Bool, _ message: String) -> Bool {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in predicate() }, object: nil)
        let result = XCTWaiter().wait(for: [expectation], timeout: 5)
        XCTAssertEqual(result, .completed, message)
        return result == .completed
    }
    private func passwordField(_ app: XCUIApplication) -> XCUIElement {
        // Compose exposes this password input as a labelled TextView on the
        // current runtime. Keep secure-field compatibility, without matching
        // unrelated editable content in an EULA or saved-HTML sheet.
        let types = [XCUIElement.ElementType.textView.rawValue, XCUIElement.ElementType.secureTextField.rawValue]
        return app.descendants(matching: .any).matching(
            NSPredicate(format: "(label == %@ OR label BEGINSWITH %@) AND elementType IN %@", "パスワード", "パスワード, ", types as NSArray)
        ).firstMatch
    }
    private enum UnlockFixtureFailure: Error { case preconditionFailed }
    private func maskedLength(_ field: XCUIElement) -> Int? {
        guard let value = field.value as? String, value.allSatisfy({ $0 == "•" || $0 == "●" }) else { return nil }
        return value.count
    }
    private func unlock(_ app: XCUIApplication) throws {
        let field = passwordField(app)
        guard field.waitForExistence(timeout: 5) else {
            XCTFail("Actual Root did not present its password field"); throw UnlockFixtureFailure.preconditionFailed
        }
        guard wait({ field.isHittable && !self.cover(app).exists }, "Native cover must release after the actual Compose lock draw") else {
            throw UnlockFixtureFailure.preconditionFailed
        }
        field.tap()
        guard app.keyboards.firstMatch.waitForExistence(timeout: 5) else {
            XCTFail("The real password input must receive keyboard focus"); throw UnlockFixtureFailure.preconditionFailed
        }
        // Send only once. Confirm the first input reached Compose before sending
        // the rest, and never mistake a label changed by supporting error text for unlock.
        field.typeText(String(password.prefix(1)))
        guard wait({ self.maskedLength(field) == 1 }, "The first password character must be committed and masked") else {
            throw UnlockFixtureFailure.preconditionFailed
        }
        field.typeText(String(password.dropFirst()))
        guard wait({ self.maskedLength(field) == self.password.count }, "The complete password must reach Compose before submission") else {
            throw UnlockFixtureFailure.preconditionFailed
        }
        app.buttons["解除"].firstMatch.tap()
        guard wait({ !self.passwordField(app).exists && !app.buttons["解除"].firstMatch.exists &&
                     !app.staticTexts["パスワードが違います。"].exists },
                   "The product password must remove both the lock input and unlock button without an error") else {
            throw UnlockFixtureFailure.preconditionFailed
        }
    }
    private func attach(_ name: String) {
        let image = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        image.name = name; image.lifetime = .keepAlways; add(image)
    }

    func testActualRootCanDrawAndUnlockAgainAfterBackground() throws {
        let app = application(lock: true, savedHtml: false)
        app.launch(); defer { app.terminate() }
        try unlock(app)
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 5))
        app.activate(); attach("m65-root-immediate-foreground")
        let field = passwordField(app)
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        wait({ field.isHittable && !self.cover(app).exists }, "Real Root draw must acknowledge native foreground protection")
        attach("m65-root-locked")
        try unlock(app)
    }

    /// L127: ふたったー風's in-app browser (SFSafariViewController) is outside Compose. It must not stay above the lock,
    /// and after the password the same page is opened again.
    func testInAppBrowserClosesUnderTheLockAndReopensAfterUnlock() throws {
        let boards = [["id": "t", "name": "チュートリアル＠ふたちゃ", "category": "チュートリアル",
            "url": "https://www.example.com/t/futaba.php", "description": "チュートリアル"]]
        let boardJson = String(data: try JSONEncoder().encode(boards), encoding: .utf8)!
        let boardArgument = String(data: try JSONEncoder().encode(boardJson), encoding: .utf8)!
        let app = application(lock: true, savedHtml: false, profile: "ftutter", extra: ["-boards_json", boardArgument])
        app.launch(); defer { app.terminate() }
        try unlock(app)
        func element(_ identifier: String) -> XCUIElement {
            app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        }
        XCTAssertTrue(element("ftutter-board-row").waitForExistence(timeout: 20), "The board list is empty.")
        element("ftutter-board-row").tap()
        XCTAssertTrue(element("ftutter-catalog-item").waitForExistence(timeout: 20), "The catalog lists no thread.")
        element("ftutter-catalog-item").tap()
        let post = element("ftutter-post")
        XCTAssertTrue(post.waitForExistence(timeout: 20), "The thread shows no post.")
        element("ftutter-thread-tool-menu").tap()
        let browserItem = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "ブラウザで開く")).firstMatch
        XCTAssertTrue(browserItem.waitForExistence(timeout: 5), "The thread menu has no 「ブラウザで開く」.")
        browserItem.tap()
        wait({ !post.isHittable }, "The in-app browser must cover the thread")
        attach("l127-browser-open")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 5))
        app.activate()
        let field = passwordField(app)
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        wait({ field.isHittable && !self.cover(app).exists }, "The lock must be on top: the in-app browser is closed under it")
        attach("l127-locked")
        try unlock(app)
        // A full-screen browser hides the thread from the accessibility tree, so only "not hittable" is checked.
        wait({ !post.isHittable && !self.passwordField(app).exists }, "The same page must be opened again after the password")
        attach("l127-reopened")
    }

    func testSavedHtmlReturnsOnlyAfterActualRootLockAndFreshPassword() throws {
        let app = application(lock: true, savedHtml: true)
        app.launch(); defer { app.terminate() }
        try unlock(app)
        let html = app.navigationBars["保存済みスレ"]
        XCTAssertTrue(html.waitForExistence(timeout: 5), "Native saved-HTML fixture did not open; record WebKit/host availability separately")
        attach("m65-html-before-background")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 5))
        app.activate(); attach("m65-html-immediate-foreground")
        let field = passwordField(app)
        wait({ field.isHittable && !self.cover(app).exists }, "HTML dismissal and actual Compose lock draw must both complete")
        XCTAssertFalse(html.isHittable, "Saved HTML must remain below the locked Root")
        attach("m65-html-held-under-lock")
        try unlock(app)
        XCTAssertTrue(html.waitForExistence(timeout: 5), "The same pending saved document must reopen after the new password")
        attach("m65-html-restored")
    }

    func testLockOffKeepsSavedHtmlUsableAcrossBackground() {
        let app = application(lock: false, savedHtml: true)
        app.launch(); defer { app.terminate() }
        let html = app.navigationBars["保存済みスレ"]
        XCTAssertTrue(html.waitForExistence(timeout: 5), "Saved-HTML native host fixture did not open")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 5))
        app.activate()
        wait({ html.isHittable && !self.cover(app).exists }, "OFF must retain the native sheet and remove transient protection")
        XCTAssertFalse(passwordField(app).exists)
        attach("m65-html-lock-off")
    }

    private func verifyEulaRecovery(lock: Bool, savedHtml: Bool = false) throws {
        let app = application(lock: lock, savedHtml: savedHtml)
        // Uses the same production branch as an unaccepted/new-version EULA and
        // existing test-only override, without deleting shared Simulator defaults.
        app.launchArguments.append("-review.force_ugc_eula")
        app.launch(); defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["利用規約（EULA）"].waitForExistence(timeout: 5))
        XCTAssertFalse(passwordField(app).exists, "The Compose Root must not exist before agreement")
        let html = app.navigationBars["保存済みスレ"]
        XCTAssertFalse(html.exists, "Pending HTML must stay below the public EULA")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 5))
        app.activate()
        wait({ !self.cover(app).exists }, "Native EULA must redraw and release protection without a nonexistent Root lock frame")
        let agreement = app.buttons["ugc-eula-agreement"]
        for _ in 0..<8 where !agreement.isHittable { app.swipeUp() }
        XCTAssertTrue(agreement.isHittable)
        agreement.tap()
        let accept = app.buttons["ugc-eula-accept"]
        XCTAssertTrue(accept.isEnabled)
        accept.tap()
        wait({ !accept.exists }, "Agreement must still create the real app host after background recovery")
        if lock {
            // The public EULA acknowledgement must not become an acknowledgement
            // of unlocked/private Root content when the EULA is replaced.
            XCTAssertTrue(passwordField(app).waitForExistence(timeout: 5))
            XCTAssertFalse(html.isHittable, "The EULA draw must not unlock pending HTML over the password gate")
            try unlock(app)
        } else {
            XCTAssertFalse(passwordField(app).exists)
        }
        if savedHtml { XCTAssertTrue(html.waitForExistence(timeout: 5), "Pending HTML must remain usable after the real password unlock") }
        attach(lock ? "m65-eula-preserved-lock" : "m65-eula-lock-off")
    }

    func testUnacceptedEulaCanResumeAndAcceptBeforeTheRootExists() throws { try verifyEulaRecovery(lock: false) }
    func testEulaRecoveryDoesNotBypassThePersistedRootPassword() throws { try verifyEulaRecovery(lock: true) }
    func testPendingHtmlStillWaitsForThePasswordAfterEulaRecovery() throws { try verifyEulaRecovery(lock: true, savedHtml: true) }
}
