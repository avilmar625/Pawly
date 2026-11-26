import XCTest

final class PawlyPetNavigationUITests: XCTestCase {
    private func send(to url: URL, method: String, body: [String: Any], token: String? = nil) async throws -> [String: Any] {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (bytes, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let result = try JSONSerialization.jsonObject(with: bytes) as? [String: Any] else {
            throw NSError(domain: "Pawly.localFixture", code: (response as? HTTPURLResponse)?.statusCode ?? -1)
        }
        return result
    }

    @MainActor func testSwitchPetInCalendarAndChangeLanguageWithoutLosingSelection() async throws {
        continueAfterFailure = false
        addUIInterruptionMonitor(withDescription: "System password prompt") { alert in
            let action = alert.buttons.matching(NSPredicate(format: "label IN %@", ["Ahora no", "Not Now"])).firstMatch
            guard action.exists else { return false }
            action.tap()
            return true
        }
        let email = "ui-\(UUID().uuidString)@example.test"
        let password = "local-fixture-123"
        let signup = URL(string: "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local")!
        let account = try await send(to: signup, method: "POST", body: ["email": email, "password": password, "returnSecureToken": true])
        let uid = try XCTUnwrap(account["localId"] as? String)
        let profile = URL(string: "http://127.0.0.1:8080/v1/projects/demo-pawly/databases/(default)/documents/users/" + uid)!
        let pets = ["Uma", "Nora"].map { name in
            ["mapValue": ["fields": ["id": ["stringValue": name], "name": ["stringValue": name]]]]
        }
        _ = try await send(to: profile, method: "PATCH", body: ["fields": [
            "uid": ["stringValue": uid], "dogProfiles": ["arrayValue": ["values": pets]]
        ]], token: "owner")
        let app = XCUIApplication()
        app.launchArguments = ["-PawlyLocalEmulators", "-PawlyResetTestSession"]
        app.launch()
        let emailField = app.textFields["auth.envelope"]
        XCTAssertTrue(emailField.waitForExistence(timeout: 15))
        emailField.tap()
        emailField.typeText(email)
        let passwordField = app.secureTextFields["auth.password"]
        passwordField.tap()
        passwordField.typeText(password)
        app.buttons["auth.submit"].tap()
        let header = app.buttons["pet.selection"]
        XCTAssertTrue(header.waitForExistence(timeout: 20))
        // iOS may offer to store the synthetic credential after the first successful login.
        let dismissPassword = NSPredicate(format: "label IN %@", ["Ahora no", "Not Now"])
        let sheetAction = app.buttons.matching(dismissPassword).firstMatch
        if sheetAction.waitForExistence(timeout: 3) {
            sheetAction.tap()
            XCTAssertTrue(sheetAction.waitForNonExistence(timeout: 5))
        }
        let loaded = NSPredicate(format: "label CONTAINS %@ OR label CONTAINS %@", "Uma", "Nora")
        let petReady = XCTNSPredicateExpectation(predicate: loaded, object: header)
        XCTAssertEqual(XCTWaiter.wait(for: [petReady], timeout: 12), .completed)
        let target = header.label.contains("Nora") ? "Uma" : "Nora"
        app.buttons["pet.section.3"].tap()
        let nextMonth = app.buttons["calendar.nextMonth"]
        if !nextMonth.waitForExistence(timeout: 2) {
            app.buttons["pet.section.3"].tap()
        }
        XCTAssertTrue(nextMonth.waitForExistence(timeout: 6), app.debugDescription)
        header.tap()
        app.buttons[target].tap()
        XCTAssertTrue(header.label.contains(target))
        app.buttons["pet.settings"].tap()
        app.buttons["settings.language"].tap()
        app.buttons["English"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        app.buttons["settings.close"].tap()
        XCTAssertTrue(header.label.contains(target))
        XCTAssertTrue(nextMonth.waitForExistence(timeout: 6), "The calendar must remain visible after changing language")
        XCTAssertTrue(app.buttons["pet.section.1"].label.contains("Health"))
    }
}
