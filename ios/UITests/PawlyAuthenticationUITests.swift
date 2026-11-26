import XCTest

final class PawlyAuthenticationUITests: XCTestCase {
    func testPasswordSurvivesVisibilityAndModeChanges() {
        let app = XCUIApplication()
        app.launchArguments = ["-PawlyLocalEmulators", "-PawlyResetTestSession"]
        app.launch()
        let password = app.secureTextFields["auth.password"]
        XCTAssertTrue(password.waitForExistence(timeout: 12))
        password.tap()
        password.typeText("pawly-fixture")
        app.buttons["auth.showPassword"].tap()
        XCTAssertEqual(app.textFields["auth.password"].value as? String, "pawly-fixture")
        XCTAssertFalse(app.buttons["auth.submit"].isEnabled)
        app.swipeUp()
        app.buttons["auth.switchMode"].tap()
        XCTAssertTrue(app.textFields["auth.person"].exists)
        app.buttons["auth.showPassword"].tap()
        XCTAssertTrue(app.secureTextFields["auth.password"].exists)
        app.buttons["auth.showPassword"].tap()
        XCTAssertEqual(app.textFields["auth.password"].value as? String, "pawly-fixture")
        XCTAssertFalse(app.buttons["auth.submit"].isEnabled)
    }
}
