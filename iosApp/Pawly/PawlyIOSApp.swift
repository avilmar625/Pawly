import SwiftUI
import FirebaseCore

@main
struct PawlyIOSApp: App {
    init() {
        // Firebase is activated after GoogleService-Info.plist is added to the Xcode target.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
    }

    var body: some Scene {
        WindowGroup {
            PawlyRootView()
                .tint(PawlyStyle.forest)
        }
    }
}
