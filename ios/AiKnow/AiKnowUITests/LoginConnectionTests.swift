import XCTest

// Execute with ios/tests/test_content_ui.py, which supplies an isolated API fixture.
final class ContentConnectionTests: XCTestCase {
    @MainActor
    func testContentAndAccountScreens() throws {
        try XCTSkipIf((try? Data(contentsOf: URL(string: "http://localhost:18081/__fixture")!)) == nil,
                      "Run with ios/tests/test_content_ui.py")
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["건너뛰기"].waitForExistence(timeout: 30))
        app.buttons["건너뛰기"].tap()
        XCTAssertTrue(app.tabBars.buttons["아카이브"].waitForExistence(timeout: 20))
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        if springboard.alerts.firstMatch.waitForExistence(timeout: 3) {
            for label in ["Allow", "허용"] where springboard.alerts.buttons[label].exists {
                springboard.alerts.buttons[label].tap()
            }
        }
        app.tabBars.buttons["아카이브"].tap()
        XCTAssertTrue(app.staticTexts["API 연결 검증 카드"].waitForExistence(timeout: 15))
        let search = app.textFields["궁금한 AI 이야기를 검색해 보세요"]
        search.tap(); search.typeText("API\n")
        app.buttons["기술"].firstMatch.tap()
        let card = app.buttons["archive.card.9001"]
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: card)], timeout: 10), .completed)
        card.tap()
        XCTAssertTrue(app.staticTexts["서버 슬라이드"].waitForExistence(timeout: 15))
        app.buttons["좋아요"].tap()
        XCTAssertTrue(app.buttons["좋아요 취소"].waitForExistence(timeout: 10))
        app.buttons["다 읽었어요"].tap()
        XCTAssertTrue(app.staticTexts["읽음"].waitForExistence(timeout: 10))
        app.buttons["좋아요"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["API 연결 검증 카드"].waitForExistence(timeout: 10))

        app.tabBars.buttons["마이"].tap()
        app.buttons["공지사항"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "API 공지")).firstMatch.waitForExistence(timeout: 10))
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "API 공지")).firstMatch.tap()
        XCTAssertTrue(app.staticTexts["서버에서 받은 공지 본문"].waitForExistence(timeout: 10))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["프로필 편집"].tap()
        XCTAssertTrue(app.buttons["테스트 프로필"].waitForExistence(timeout: 10))
        let nickname = app.textFields["닉네임 (20자 이내)"]
        nickname.tap(); nickname.typeText(" 수정"); app.buttons["입력 완료"].tap()
        app.buttons["테스트 프로필"].tap()
        app.buttons["저장"].tap()
        XCTAssertTrue(app.staticTexts["저장했습니다."].waitForExistence(timeout: 10))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["알림 설정"].tap()
        XCTAssertTrue(app.buttons["알림 설정 저장"].waitForExistence(timeout: 10))
        app.buttons["알림 설정 저장"].tap()
        XCTAssertTrue(app.staticTexts["저장했습니다."].waitForExistence(timeout: 10))
        app.terminate()
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["오늘"].waitForExistence(timeout: 30))
        XCTAssertFalse(app.buttons["건너뛰기"].exists)
    }
}

final class LoginConnectionTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    @MainActor
    private func launchLogin() -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["Google 로그인"].waitForExistence(timeout: 30))
        return app
    }

    @MainActor
    func testGoogleReachesAccountPage() {
        checkAccountPage(button: "Google 로그인", name: "Google")
    }

    @MainActor
    func testKakaoReachesAccountPage() {
        checkAccountPage(button: "카카오 로그인", name: "Kakao")
    }

    @MainActor
    private func checkAccountPage(button: String, name: String) {
        let app = launchLogin()
        app.buttons[button].tap()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for host in [app, springboard] {
            if host.alerts.firstMatch.waitForExistence(timeout: 5) {
                for label in ["Continue", "계속"] {
                    if host.alerts.buttons[label].exists { host.alerts.buttons[label].tap(); break }
                }
            }
        }
        let reached = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.webViews.textFields.count > 0 ||
            app.webViews.staticTexts["계정을 선택하세요."].exists ||
            app.webViews.staticTexts["Choose an account"].exists ||
            app.staticTexts["login.error"].exists
        }, object: nil)
        let result = XCTWaiter.wait(for: [reached], timeout: 45)
        attachScreenshot("\(name) account authentication page")
        XCTAssertEqual(result, .completed, "Provider account entry form did not load")
        XCTAssertFalse(app.staticTexts["login.error"].exists,
                       app.staticTexts["login.error"].exists ? app.staticTexts["login.error"].label : "")
        XCTAssertTrue(app.webViews.textFields.count > 0 ||
                      app.webViews.staticTexts["계정을 선택하세요."].exists ||
                      app.webViews.staticTexts["Choose an account"].exists)
        // No credentials are entered. This verifies the provider UI, not account login.
        app.terminate()
    }

    @MainActor
    func testApplePresentsAuthorizationWithoutImmediateFailure() {
        let app = launchLogin()
        app.buttons["login.apple"].tap()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let boundary = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.alerts.count > 0 || springboard.alerts.count > 0 ||
            app.staticTexts["login.error"].exists || app.otherElements["Sign in with Apple"].exists ||
            app.buttons["Continue"].exists || app.buttons["계속"].exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [boundary], timeout: 25), .completed)
        attachScreenshot("Apple authorization UI")
        XCTAssertFalse(app.staticTexts["login.error"].exists,
                       app.staticTexts["login.error"].exists ? app.staticTexts["login.error"].label : "")
        cancelPrompt(app: app, springboard: springboard)
        app.terminate()
    }

    @MainActor
    private func cancelPrompt(app: XCUIApplication, springboard: XCUIApplication) {
        for host in [app, springboard] {
            for label in ["Cancel", "취소", "OK", "확인", "닫기"] {
                let button = host.alerts.buttons[label].firstMatch
                if button.exists { button.tap(); return }
            }
        }
    }

    @MainActor
    private func attachScreenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

// Real HTTP transport against the isolated fixture in test_admin_ui.py.
final class AdminAPIConnectionTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        try XCTSkipIf((try? Data(contentsOf: URL(string: "http://localhost:18082/__fixture")!)) == nil,
                      "Run with ios/tests/test_admin_ui.py")
    }
    @MainActor private func launch() throws -> XCUIApplication {
        _ = try Data(contentsOf: URL(string: "http://localhost:18082/__role?role=ADMIN")!)
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.tabBars.buttons["관리자"].firstMatch.waitForExistence(timeout: 30))
        app.tabBars.buttons["관리자"].firstMatch.tap()
        return app
    }
    @MainActor private func field(_ name: String, _ app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "(elementType == %d OR elementType == %d) AND (identifier == %@ OR label == %@ OR placeholderValue == %@)", XCUIElement.ElementType.textField.rawValue, XCUIElement.ElementType.textView.rawValue, name, name, name)).firstMatch
    }
    @MainActor private func reveal(_ element: XCUIElement, _ app: XCUIApplication) {
        for _ in 0..<12 {
            if element.exists && element.isHittable { return }
            if element.exists && element.frame.minY < 120 { app.swipeDown() } else { app.swipeUp() }
        }
        XCTAssertTrue(element.exists && element.isHittable, app.debugDescription)
    }
    @MainActor private func reason(_ app: XCUIApplication) {
        let input = field("수정·반려·복원 사유 (필수)", app)
        reveal(input, app); input.tap(); input.typeText("HTTP 검증 사유"); app.buttons["입력 완료"].tap()
    }
    @MainActor private func apply(_ name: String, _ app: XCUIApplication) {
        let button = app.buttons[name].firstMatch; reveal(button, app); button.tap()
        app.buttons["적용"].tap()
        XCTAssertTrue(app.staticTexts["반영했습니다."].waitForExistence(timeout: 10))
    }
    @MainActor private func row(_ text: String, _ app: XCUIApplication) -> XCUIElement {
        let row = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10), app.debugDescription); return row
    }
    @MainActor func testAReviewAndPublication() throws {
        let app = try launch()
        XCTAssertGreaterThan(app.tabBars.buttons["관리자"].firstMatch.frame.midX, app.tabBars.buttons["마이"].firstMatch.frame.midX)
        app.buttons["뉴스 검수"].tap(); row("검수 요청 #1", app).tap()
        reason(app); apply("수정 저장", app)
        reason(app); apply("반려", app)
        reason(app); apply("재검수로 돌리기", app)
        let type = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "콘텐츠 유형")).firstMatch
        reveal(type, app); type.tap(); app.buttons["AI 이론"].tap()
        XCTAssertTrue(app.staticTexts["승인된 AI 이론은 아카이브에 바로 공개됩니다."].exists)
        apply("승인", app)
        let approved = app.buttons["승인된 카드 관리"]; reveal(approved, app); approved.tap()
        XCTAssertTrue(app.navigationBars["카드뉴스 수정"].waitForExistence(timeout: 10))
        reason(app); apply("수정 저장", app)
        reason(app); apply("숨김", app)
        reason(app); apply("복원", app)
        let history = app.buttons["변경 이력"]; reveal(history, app); history.tap()
        XCTAssertTrue(app.navigationBars["변경 이력"].waitForExistence(timeout: 10))
        app.terminate()
    }
    @MainActor func testBNoticeAndProfileCRUD() throws {
        let app = try launch()
        app.buttons["공지 관리"].tap(); app.buttons["추가"].tap()
        let title = field("admin.notice.title", app)
        XCTAssertTrue(title.waitForExistence(timeout: 10)); title.tap(); title.typeText("운영 공지")
        let content = field("admin.notice.content", app); content.tap(); content.typeText("공지 본문 검증"); app.buttons["입력 완료"].tap()
        app.buttons["저장"].tap(); row("운영 공지", app).tap()
        XCTAssertTrue(title.waitForExistence(timeout: 10)); title.tap(); title.typeText(" 수정"); app.buttons["입력 완료"].tap()
        app.buttons["저장"].tap(); row("수정", app).tap()
        XCTAssertTrue(app.buttons["공지 삭제"].waitForExistence(timeout: 10)); app.buttons["공지 삭제"].tap(); app.buttons["삭제"].tap()
        XCTAssertTrue(app.staticTexts["등록된 항목이 없습니다"].waitForExistence(timeout: 10))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["프로필 이미지 관리"].tap(); app.buttons["추가"].tap()
        let name = field("이름 (20자 이내)", app)
        XCTAssertTrue(name.waitForExistence(timeout: 10)); name.tap(); name.typeText("운영 프로필")
        let url = field("이미지 주소", app); url.tap(); url.typeText("/profile/admin"); app.buttons["입력 완료"].tap()
        app.buttons["저장"].tap(); row("운영 프로필", app).tap()
        XCTAssertTrue(name.waitForExistence(timeout: 10)); name.tap(); name.typeText(" 수정"); app.buttons["입력 완료"].tap()
        app.buttons["저장"].tap(); row("수정", app).tap()
        XCTAssertTrue(app.buttons["프로필 이미지 삭제"].waitForExistence(timeout: 10))
        app.buttons["프로필 이미지 삭제"].tap(); app.buttons["삭제"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "테스트 프로필")).firstMatch.waitForExistence(timeout: 10))
        app.terminate()
    }
    @MainActor func testCGenerationRecovery() throws {
        let app = try launch()
        app.buttons["생성 복구"].tap(); row("draft #1", app).tap()
        XCTAssertTrue(app.buttons["이미지 선택 참고 자료"].waitForExistence(timeout: 10))
        let reason = field("복구 사유 (필수)", app); reveal(reason, app); reason.tap(); reason.typeText("HTTP 복구 검증")
        app.buttons["입력 완료"].tap()
        let recovery = app.buttons["응답 복구"]; reveal(recovery, app); recovery.tap(); app.buttons["복구"].tap()
        XCTAssertTrue(app.staticTexts["복구했습니다."].waitForExistence(timeout: 10))
        app.terminate()
    }
    @MainActor func testEUserAdministration() throws {
        let app = try launch()
        app.buttons["계정 목록·권한 관리"].tap()
        _ = row("검증 사용자", app)
        app.buttons["더 보기"].tap()
        row("내 관리자 계정", app).tap()
        XCTAssertTrue(app.switches["admin.account.suspended"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.switches["admin.account.suspended"].isEnabled)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["admin.users.role"].tap(); app.buttons["일반 사용자"].tap()
        app.buttons["admin.users.state"].tap(); app.buttons["정상"].tap()
        let search = app.searchFields.firstMatch
        if !search.isHittable { app.swipeDown() }
        XCTAssertTrue(search.waitForExistence(timeout: 10)); search.tap(); search.typeText("검증")
        app.keyboards.buttons["Search"].firstMatch.tap()
        row("검증 사용자", app).tap()
        let save = app.buttons["변경 사항 저장"]
        XCTAssertTrue(save.waitForExistence(timeout: 10)); XCTAssertFalse(save.isEnabled)
        func edit() {
            let picker = app.buttons["admin.account.role"]; reveal(picker, app); picker.tap()
            app.buttons.matching(identifier: "관리자").matching(NSPredicate(format: "isSelected == false")).firstMatch.tap()
            let suspended = app.switches["admin.account.suspended"]
            suspended.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
            XCTAssertEqual(suspended.value as? String, "1")
            let input = field("admin.account.reason", app); reveal(input, app); input.tap()
            if (input.value as? String)?.contains("HTTP") != true { input.typeText("HTTP 계정 검증") }
            app.buttons["입력 완료"].tap()
            reveal(save, app); save.tap(); app.buttons["변경 적용"].tap()
        }
        edit()
        let reload = app.buttons["계정 다시 조회"]
        XCTAssertTrue(reload.waitForExistence(timeout: 10))
        XCTAssertFalse(save.isEnabled, app.debugDescription)
        reveal(reload, app); reload.tap()
        XCTAssertTrue(app.buttons["admin.account.role"].waitForExistence(timeout: 10))
        edit()
        XCTAssertTrue(app.staticTexts["저장했습니다"].waitForExistence(timeout: 10))
        let capture = XCTAttachment(screenshot: app.screenshot()); capture.name = "account-management"; capture.lifetime = .keepAlways; add(capture)
        let history = app.buttons["변경 이력"]; reveal(history, app); history.tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "ACCOUNT_UPDATE")).firstMatch.waitForExistence(timeout: 10))
        app.terminate()
    }
    @MainActor func testFAnalytics() throws {
        let app = try launch()
        app.buttons["조회수·운영 현황"].tap()
        XCTAssertTrue(app.staticTexts["전체 조회수, 1234회"].waitForExistence(timeout: 10))
        _ = row("통계 첫 카드", app)
        let capture = XCTAttachment(screenshot: app.screenshot()); capture.name = "analytics-summary"; capture.lifetime = .keepAlways; add(capture)
        let more = app.buttons["더 보기"]; reveal(more, app); more.tap()
        let second = row("통계 둘째 카드", app); reveal(second, app)
        app.swipeDown(); app.swipeDown()
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 10)); search.tap(); search.typeText("검색")
        app.keyboards.buttons["Search"].firstMatch.tap()
        let result = row("검색 통계 카드", app); reveal(result, app); result.tap()
        XCTAssertTrue(app.navigationBars["카드뉴스 수정"].waitForExistence(timeout: 10))
        app.terminate()
    }
    @MainActor func testGReadyArticle() throws {
        let app = try launch()
        app.buttons["카드뉴스 관리"].tap(); row("최초 발송 전 카드", app).tap()
        XCTAssertTrue(app.navigationBars["카드뉴스 수정"].waitForExistence(timeout: 10))
        let notice = app.staticTexts["최초 발송 전 콘텐츠는 공개 상태를 변경할 수 없습니다."]
        reveal(notice, app)
        XCTAssertFalse(app.buttons["숨김"].exists)
        XCTAssertFalse(app.buttons["복원"].exists)
        app.terminate()
    }
    @MainActor func testDRoleChangesUpdateRightmostTab() throws {
        let app = try launch()
        XCUIDevice.shared.press(.home)
        _ = try Data(contentsOf: URL(string: "http://localhost:18082/__role?role=USER")!)
        app.activate()
        let hidden = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.tabBars.buttons["관리자"].firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for: [hidden], timeout: 15), .completed)
        XCTAssertTrue(app.tabBars.buttons["마이"].firstMatch.isSelected)
        XCUIDevice.shared.press(.home)
        _ = try Data(contentsOf: URL(string: "http://localhost:18082/__role?role=ADMIN")!)
        app.activate()
        XCTAssertTrue(app.tabBars.buttons["관리자"].firstMatch.waitForExistence(timeout: 15))
        XCTAssertGreaterThan(app.tabBars.buttons["관리자"].firstMatch.frame.midX, app.tabBars.buttons["마이"].firstMatch.frame.midX)
        app.terminate()
    }
}

// Official assets and native Apple control, using an isolated all-provider fixture.
final class LoginBrandingTests: XCTestCase {
    @MainActor func testLight() throws { try verify("light") }
    @MainActor func testDark() throws { try verify("dark") }

    @MainActor func testLegacyPreviewArgumentsCannotBypassLogin() throws {
        try XCTSkipIf((try? Data(contentsOf: URL(string: "http://localhost:18083/__fixture")!)) == nil,
                      "Run with ios/tests/test_login_ui.py")
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--preview-admin", "--preview-user"]
        app.launch()
        XCTAssertTrue(app.buttons["Google 로그인"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["화면 미리보기"].exists)
        XCTAssertFalse(app.buttons["관리자 미리보기"].exists)
        XCTAssertFalse(app.tabBars.buttons["관리자"].exists)
        XCTAssertFalse(app.tabBars.buttons["마이"].exists)
        app.terminate()
    }

    @MainActor private func verify(_ mode: String) throws {
        try XCTSkipIf((try? Data(contentsOf: URL(string: "http://localhost:18083/__fixture")!)) == nil,
                      "Run with ios/tests/test_login_ui.py")
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-appearance", mode, "-AppleLanguages", "(ko)", "-AppleLocale", "ko_KR"]
        app.launch()
        for label in ["Google 로그인", "카카오 로그인", "login.apple"] {
            XCTAssertTrue(app.buttons[label].waitForExistence(timeout: 20))
            XCTAssertTrue(app.buttons[label].isHittable)
        }
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "Official login buttons — \(mode)"
        image.lifetime = .keepAlways
        add(image)
        for (label, provider) in [("Google 로그인", "Google"), ("카카오 로그인", "Kakao"), ("login.apple", "Apple")] {
            app.buttons[label].tap()
            let error = app.staticTexts["login.error"]
            let reported = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND label BEGINSWITH %@", provider + ":"), object: error)
            XCTAssertEqual(XCTWaiter.wait(for: [reported], timeout: 10), .completed)
        }
        app.terminate()
    }
}

// Explicit opt-in inspection of a real session. Only opens/scrolls published cards;
// the server may count those opens. Never likes, marks read or edits content.
final class ProductionLayoutInspectionTests: XCTestCase {
    @MainActor
    func testInspectPublishedCards() throws {
        try XCTSkipIf((try? Data(contentsOf: URL(string: "http://localhost:18089/")!)) == nil,
                      "Start the local inspection marker on port 18089 first")
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["아카이브"].waitForExistence(timeout: 30))
        capture("production-today", app)
        app.tabBars.buttons["아카이브"].tap()
        let first = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "archive.card.")).firstMatch
        XCTAssertTrue(first.waitForExistence(timeout: 30))
        capture("production-archive-initial", app)
        Thread.sleep(forTimeInterval: 5)
        capture("production-archive-loaded", app)
        first.tap()
        XCTAssertTrue(app.buttons["다음 카드"].waitForExistence(timeout: 30) || app.buttons["다 읽었어요"].exists)
        for index in 0..<10 {
            capture("production-slide-\(index + 1)-initial", app)
            Thread.sleep(forTimeInterval: 8)
            capture("production-slide-\(index + 1)-loaded", app)
            app.swipeUp()
            capture("production-slide-\(index + 1)-bottom", app)
            if !app.buttons["다음 카드"].exists { break }
            app.buttons["다음 카드"].tap()
        }
        XCTAssertTrue(app.buttons["다 읽었어요"].exists)
        // Return without marking read. Reopen to inspect cache reuse.
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(first.waitForExistence(timeout: 15))
        first.tap()
        XCTAssertTrue(app.buttons["다음 카드"].waitForExistence(timeout: 30) || app.buttons["다 읽었어요"].exists)
        capture("production-revisit", app)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.tabBars.buttons["관리자"].exists)
    }
    @MainActor
    func testArtworkZoomInBothAppearances() throws {
        try XCTSkipIf((try? Data(contentsOf: URL(string: "http://localhost:18089/")!)) == nil,
                      "Start the local inspection marker on port 18089 first")
        continueAfterFailure = false
        for mode in ["light", "dark"] {
            let app = XCUIApplication()
            app.launchArguments = ["-appearance", mode]
            app.launch()
            XCTAssertTrue(app.tabBars.buttons["아카이브"].waitForExistence(timeout: 30))
            app.tabBars.buttons["아카이브"].tap()
            let first = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "archive.card.")).firstMatch
            XCTAssertTrue(first.waitForExistence(timeout: 30)); first.tap()
            XCTAssertTrue(app.buttons["다음 카드"].waitForExistence(timeout: 30))
            for _ in 0..<3 {
                app.buttons["다음 카드"].tap()
                Thread.sleep(forTimeInterval: 1)
            }
            capture("\(mode)-long-diagram", app)
            let expand = try XCTUnwrap(app.buttons.matching(identifier: "이미지 확대").allElementsBoundByIndex.first(where: { $0.isHittable }))
            expand.tap()
            let zoom = app.scrollViews["image.zoom"]
            XCTAssertTrue(zoom.waitForExistence(timeout: 30))
            capture("\(mode)-image-fit", app)
            zoom.pinch(withScale: 3, velocity: 1)
            capture("\(mode)-image-zoom", app)
            app.buttons["닫기"].tap()
            XCTAssertTrue(app.buttons["다음 카드"].exists)
            app.navigationBars.buttons.element(boundBy: 0).tap()
            XCTAssertTrue(app.tabBars.buttons["관리자"].exists)
        }
    }
    @MainActor private func capture(_ name: String, _ app: XCUIApplication) {
        let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        shot.name = name; shot.lifetime = .keepAlways; add(shot)
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name + "-tree"; tree.lifetime = .keepAlways; add(tree)
    }
}
