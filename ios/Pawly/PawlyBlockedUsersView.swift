import SwiftUI

struct PawlyBlockedUsersView: View {
    @StateObject private var store = PawlyCommunityStore()
    @State private var blocked: [PawlyBlockedUser] = []
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        List {
            if loading {
                ProgressView("Cargando bloqueos…")
            } else if blocked.isEmpty {
                PawlyEmptyState(
                    icon: "hand.raised",
                    title: "No has bloqueado a nadie",
                    detail: "Las cuentas que bloquees aparecerán aquí y podrás desbloquearlas."
                )
            } else {
                ForEach(blocked) { user in
                    HStack(spacing: 12) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.title2)
                            .foregroundStyle(PawlyColors.muted)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(user.displayName).font(.headline)
                            if !user.username.isEmpty {
                                Text("@\(user.username)").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Button("Desbloquear") {
                            Task { await unblock(user) }
                        }
                        .font(.caption.weight(.semibold))
                        .buttonStyle(.bordered)
                    }
                    .padding(.vertical, 4)
                }
            }
            if let error {
                Text(error).font(.caption).foregroundStyle(.red)
            }
        }
        .navigationTitle("Usuarios bloqueados")
        .task { await load() }
        .refreshable { await load() }
    }

    @MainActor private func load() async {
        loading = true
        defer { loading = false }
        do {
            blocked = try await store.loadBlockedUsers()
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    @MainActor private func unblock(_ user: PawlyBlockedUser) async {
        do {
            try await store.unblockUser(user.id)
            blocked.removeAll { $0.id == user.id }
            error = nil
        } catch {
            self.error = "No se pudo desbloquear a esta persona: \(error.localizedDescription)"
        }
    }
}
