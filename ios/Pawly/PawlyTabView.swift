import SwiftUI

struct PawlyTabView: View {
    @EnvironmentObject private var session: PawlySession
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Inicio", systemImage: "house.fill") }
            CommunityView()
                .tabItem { Label("Comunidad", systemImage: "person.3.fill") }
            DiaryView()
                .tabItem { Label("Recuerdos", systemImage: "book.closed.fill") }
            AccountView()
                .tabItem { Label("Cuenta", systemImage: "person.crop.circle") }
        }
        .tint(PawlyColors.primary)
    }
}

struct HomeView: View {
    @EnvironmentObject private var session: PawlySession
    @State private var petName = "Tu compañero"
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Hola, (session.user?.displayName?.components(separatedBy: " ").first ?? "bienvenido")")
                                .font(.title2.bold()).foregroundStyle(PawlyColors.ink)
                            Text("Un día más de aventuras juntos")
                                .font(.subheadline).foregroundStyle(PawlyColors.muted)
                        }
                        Spacer()
                        Image(systemName: "sun.max.fill")
                            .font(.title2).foregroundStyle(Color.orange)
                            .padding(13).background(PawlyColors.peach, in: Circle())
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        ZStack(alignment: .bottomLeading) {
                            LinearGradient(colors: [PawlyColors.primary, Color(red: 0.35, green: 0.66, blue: 0.53)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            VStack(alignment: .leading, spacing: 8) {
                                Image(systemName: "pawprint.fill").font(.system(size: 34))
                                Text(petName).font(.largeTitle.bold())
                                Text("Tu mejor amigo merece lo mejor.")
                                    .font(.subheadline).opacity(0.9)
                            }
                            .foregroundStyle(.white).padding(22)
                        }
                        .frame(height: 180).clipShape(RoundedRectangle(cornerRadius: 25))
                        Text("Tu espacio Pawly está listo para empezar.")
                            .font(.subheadline).foregroundStyle(PawlyColors.muted)
                    }
                    Text("Tu día").font(.title3.bold()).foregroundStyle(PawlyColors.ink)
                    HStack(spacing: 12) {
                        quickCard("Paseo", detail: "Una aventura fuera", icon: "figure.walk", color: PawlyColors.mint)
                        quickCard("Salud", detail: "Cuida su bienestar", icon: "cross.case.fill", color: PawlyColors.peach)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Próximamente en iOS", systemImage: "sparkles")
                            .font(.headline).foregroundStyle(PawlyColors.primary)
                        Text("Estamos incorporando a esta versión el diario, la ficha veterinaria, los perfiles compartidos y las funciones sociales de Pawly.")
                            .font(.subheadline).foregroundStyle(PawlyColors.muted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .pawlyCard()
                }
                .padding(20)
            }
            .background(PawlyColors.canvas)
            .navigationBarHidden(true)
            .task { await loadPet() }
        }
    }

    private func quickCard(_ title: String, detail: String, icon: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Image(systemName: icon).font(.title2).foregroundStyle(PawlyColors.primary)
                .padding(11).background(color, in: Circle())
            Text(title).font(.headline).foregroundStyle(PawlyColors.ink)
            Text(detail).font(.caption).foregroundStyle(PawlyColors.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pawlyCard()
    }

    @MainActor private func loadPet() async {
        guard let uid = session.user?.uid else { return }
        do {
            let snapshot = try await Firestore.firestore().collection("sharedPets")
                .whereField("ownerUid", isEqualTo: uid).limit(to: 1).getDocuments()
            if let first = snapshot.documents.first {
                petName = first.data()["name"] as? String ?? "Tu compañero"
            }
        } catch { }
        isLoading = false
    }
}

struct CommunityView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView("Comunidad Pawly", systemImage: "person.3", description: Text("Aquí aparecerán publicaciones, perfiles y conversaciones. Estamos conectando las funciones sociales con Firebase."))
                .navigationTitle("Comunidad")
        }
    }
}

struct DiaryView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView("Tus recuerdos", systemImage: "book.closed", description: Text("El diario de aventuras y recuerdos se incorporará en la siguiente fase del port."))
                .navigationTitle("Recuerdos")
        }
    }
}

struct AccountView: View {
    @EnvironmentObject private var session: PawlySession
    @State private var errorMessage: String?
    var body: some View {
        NavigationStack {
            Form {
                Section("Cuenta") {
                    LabeledContent("Correo", value: session.user?.email ?? "—")
                    LabeledContent("Estado", value: session.user?.isEmailVerified == true ? "Verificado" : "Pendiente de verificación")
                }
                Section {
                    Button("Cerrar sesión", role: .destructive) {
                        do { try session.signOut() }
                        catch { errorMessage = error.localizedDescription }
                    }
                }
                if let errorMessage { Text(errorMessage).foregroundStyle(.red) }
            }
            .navigationTitle("Mi cuenta")
        }
    }
}
