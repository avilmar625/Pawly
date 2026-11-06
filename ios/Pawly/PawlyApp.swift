import SwiftUI
import FirebaseCore

@main
struct PawlyApp: App {
    @StateObject private var session = PawlySession()

    init() {
        if Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil {
            FirebaseApp.configure()
        }
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
            .preferredColorScheme(.light)
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
                    .font(.footnote.semibold()).foregroundStyle(PawlyColors.primary)
            }
            .padding(28)
            .pawlyCard()
            .padding(24)
        }
    }
}
