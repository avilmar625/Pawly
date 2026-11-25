// iOS parity build checkpoint for the expanded native feature set.
import SwiftUI
import FirebaseCore
#if DEBUG && targetEnvironment(simulator)
import FirebaseAuth
import FirebaseFirestore
#endif

@main
struct PawlyApp: App {
    @UIApplicationDelegateAdaptor(PawlyAppDelegate.self) private var appDelegate
    @AppStorage("pawly.darkMode") private var darkMode = false
    @AppStorage("pawly.accent") private var accent = "forest"
    @AppStorage("pawly.language") private var language = "es"
    @StateObject private var session: PawlySession

    init() {
        #if DEBUG && targetEnvironment(simulator)
        if Self.usesLocalEmulators {
            let options = FirebaseOptions(googleAppID: "1:100000000000:ios:0123456789abcdef", gcmSenderID: "100000000000")
            options.projectID = "demo-pawly"
            // Firebase Installations validates the shape before Auth selects its loopback endpoint.
            options.apiKey = "A" + String(repeating: "0", count: 38)
            FirebaseApp.configure(options: options)
            Auth.auth().useEmulator(withHost: "127.0.0.1", port: 9099)
            Firestore.firestore().useEmulator(withHost: "127.0.0.1", port: 8080)
        }
        #endif
        if Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil,
           FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        _session = StateObject(wrappedValue: PawlySession())
    }

    #if DEBUG && targetEnvironment(simulator)
    static var usesLocalEmulators: Bool {
        ProcessInfo.processInfo.arguments.contains("-PawlyLocalEmulators")
    }
    #endif

    private var calendar: Calendar {
        var calendar = Calendar.current
        calendar.locale = Locale(identifier: language)
        return calendar
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if !session.firebaseConfigured {
                    FirebaseSetupView()
                } else if session.isLoading {
                    LoadingView()
                } else if session.user != nil {
                    PawlyTabView()
                        .environmentObject(session)
                } else {
                    AuthenticationView()
                        .environmentObject(session)
                }
            }
            .environment(\.locale, Locale(identifier: language))
            .environment(\.calendar, calendar)
            .preferredColorScheme(darkMode ? .dark : .light)
            .tint(PawlyColors.accentColor(accent))
            .environment(\.colorScheme, darkMode ? .dark : .light)
        }
    }
}

struct LoadingView: View {
    var body: some View {
        ZStack {
            PawlyColors.canvas.ignoresSafeArea()
            VStack(spacing: 14) {
                PawlyLogo()
                ProgressView().tint(PawlyColors.primary)
            }
        }
    }
}

struct FirebaseSetupView: View {
    var body: some View {
        ZStack {
            PawlyColors.canvas.ignoresSafeArea()
            VStack(spacing: 18) {
                PawlyLogo()
                Text("Conecta Pawly con Firebase")
                    .font(.title2.bold()).foregroundStyle(PawlyColors.ink)
                Text("Añade GoogleService-Info.plist al proyecto iOS desde Firebase Console para activar el inicio de sesión y la sincronización.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(PawlyColors.muted)
                Label("Configuración pendiente", systemImage: "wrench.and.screwdriver")
                    .font(.footnote.weight(.semibold)).foregroundStyle(PawlyColors.primary)
            }
            .padding(28)
            .pawlyCard()
            .padding(24)
        }
    }
}
