import SwiftUI
import FirebaseAuth
import FirebaseFirestore

enum PawlyRelationship: String {
    case followers, following
    var title: LocalizedStringKey { self == .followers ? "Seguidores" : "Siguiendo" }
}

struct PawlyIdentity: Identifiable {
    let id: String
    let name: String
    let username: String
    let photo: String
    let isPublic: Bool
    var chat: String? = nil

    init(id: String, name: String, username: String, photo: String = "", isPublic: Bool = true, chat: String? = nil) {
        self.id = id; self.name = name; self.username = username
        self.photo = photo; self.isPublic = isPublic; self.chat = chat
    }

    init?(_ row: [String: Any]) {
        guard let uid = row["uid"] as? String, !uid.isEmpty else { return nil }
        self.init(id: uid, name: row["displayName"] as? String ?? "Pawly", username: row["username"] as? String ?? "",
                  photo: row["photoUrl"] as? String ?? "", isPublic: row["isPublic"] as? Bool ?? false)
    }

    static func page(_ relation: PawlyRelationship, cursor: String) async throws -> ([Self], String, Int?) {
        let response = try await PawlyBackendClient.postJSON(path: "/v1/me/relationships",
            body: ["relation": relation.rawValue, "pageToken": cursor])
        return ((response["people"] as? [[String: Any]] ?? []).compactMap(Self.init),
                response["nextPageToken"] as? String ?? "", response["total"] as? Int)
    }
}

struct PawlyIdentityRow: View {
    let person: PawlyIdentity
    var body: some View {
        HStack {
            ZStack {
                Circle().fill(PawlyColors.mint)
                Image(systemName: "person.fill").foregroundStyle(PawlyColors.primary)
                if let url = URL(string: person.photo), url.scheme == "https" {
                    AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: { Color.clear }
                }
            }.frame(width: 40, height: 40).clipShape(Circle()).accessibilityHidden(true)
            VStack(alignment: .leading) {
                Text(verbatim: person.name)
                if !person.username.isEmpty { Text(verbatim: "@" + person.username).font(.caption).foregroundStyle(.secondary) }
            }
            if !person.isPublic { Image(systemName: "lock.fill").font(.caption).accessibilityLabel("Perfil privado") }
        }
    }
}

struct PawlyRelationshipList: View {
    let relation: PawlyRelationship
    @State private var users: [PawlyIdentity] = []
    @State private var cursor = ""
    @State private var more = true
    @State private var total: Int?
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        List {
            if let total { LabeledContent("Perfiles visibles", value: "\(users.count) / \(total)") }
            ForEach(users) { user in
                if user.isPublic { NavigationLink { PawlyPublicProfile(userID: user.id) } label: { PawlyIdentityRow(person: user) } }
                else { PawlyIdentityRow(person: user) }
            }
            if loading { ProgressView() }
            else if let error { Text(LocalizedStringKey(error)); Button("Reintentar") { Task { await nextPage() } } }
            else if more { Button("Cargar más") { Task { await nextPage() } } }
            else if users.isEmpty { Text("No hay destinatarios disponibles.") }
        }.navigationTitle(relation.title)
         .task { if users.isEmpty && more { await nextPage() } }
    }

    @MainActor private func nextPage() async {
        guard !loading else { return }
        loading = true; defer { loading = false }
        do {
            let (page, next, count) = try await PawlyIdentity.page(relation, cursor: cursor)
            try Task.checkCancellation()
            let previous = Set(users.map(\.id))
            users += page.filter { !previous.contains($0.id) }
            cursor = next; more = !next.isEmpty
            if let count { total = count }
            error = nil
        } catch { if !Task.isCancelled { self.error = PawlyServiceMessage.key(for: error) } }
    }
}

struct PawlyPublicProfile: View {
    let userID: String
    @StateObject private var store = PawlyCommunityStore()
    @State private var person: PawlyPublicUser?
    @State private var working = false
    @State private var message: String?
    @State private var report = false
    @State private var block = false
    @State private var chat: PawlyChat?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            if let user = person {
                HStack {
                    if let url = URL(string: user.photoURL), !user.photoURL.isEmpty {
                        AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: { PawlyAvatarPlaceholder() }
                            .frame(width: 48, height: 48).clipShape(Circle()).pawlyPhoto(url)
                    }
                    VStack(alignment: .leading) { Text(verbatim: user.displayName).font(.headline); Text(verbatim: "@" + user.username) }
                }
                if !user.bio.isEmpty { Text(verbatim: user.bio) }
                NavigationLink { PawlyProfileShareView(profileID: user.id) } label: { Label("Compartir perfil", systemImage: "square.and.arrow.up") }
                if user.id != store.uid {
                    Button(user.following ? "Dejar de seguir" : "Seguir") { act {
                        try await store.setFollowing(user, follow: !user.following)
                        person = try await store.publicUser(userID)
                    } }
                    Button("Mensaje") { act {
                        let id = try await store.startConversation(username: user.username)
                        chat = PawlyChat(id: id, otherUserID: user.id, otherUserName: user.displayName, lastMessage: "")
                    } }
                    Button("Denunciar") { report = true }
                    Button("Bloquear", role: .destructive) { block = true }
                }
            } else if message == nil { ProgressView() }
            if let message { Text(LocalizedStringKey(message)).foregroundStyle(.secondary) }
        }
        .disabled(working).navigationTitle("Perfil público")
        .task(id: userID) {
            do { person = try await store.publicUser(userID); if person == nil { message = "Perfil no disponible." } }
            catch { message = "No se ha podido abrir este perfil." }
        }
        .sheet(item: $chat) { ChatView(store: store, chat: $0) }
        .confirmationDialog("¿Bloquear esta cuenta?", isPresented: $block) {
            Button("Bloquear", role: .destructive) { if let person { act { try await store.blockUser(person); dismiss() } } }
        }
        .confirmationDialog("Motivo de la denuncia", isPresented: $report) {
            ForEach(["Acoso o comportamiento abusivo", "Spam o publicidad", "Contenido inapropiado", "Suplantación de identidad"], id: \.self) { reason in
                Button(LocalizedStringKey(reason)) { if let person { act { try await store.reportUser(person, reason: reason); message = "Denuncia enviada para revisión." } } }
            }
        }
    }

    private func act(_ action: @escaping @MainActor () async throws -> Void) {
        guard !working else { return }
        working = true
        Task { @MainActor in
            defer { working = false }
            do { try await action() } catch { message = PawlyServiceMessage.key(for: error) }
        }
    }
}
