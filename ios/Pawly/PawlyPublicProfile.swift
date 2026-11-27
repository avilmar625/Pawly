import SwiftUI
import FirebaseAuth
import FirebaseFirestore

enum PawlyRelationship: String {
    case followers, following
    var title: LocalizedStringKey { self == .followers ? "Seguidores" : "Siguiendo" }
}

struct PawlyRelationshipList: View {
    let relation: PawlyRelationship
    @StateObject private var store = PawlyCommunityStore()
    @State private var users: [PawlyPublicUser] = []
    @State private var cursor: DocumentSnapshot?
    @State private var more = true
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        List {
            ForEach(users) { user in
                NavigationLink { PawlyPublicProfile(userID: user.id) } label: {
                    VStack(alignment: .leading) {
                        Text(verbatim: user.displayName)
                        Text(verbatim: "@" + user.username).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            if loading { ProgressView() }
            else if let error { Text(LocalizedStringKey(error)); Button("Reintentar") { Task { await nextPage() } } }
            else if more { Button("Cargar más") { Task { await nextPage() } } }
            else if users.isEmpty { Text("No hay perfiles públicos disponibles.") }
        }
        .navigationTitle(relation.title)
        .task { if users.isEmpty && more { await nextPage() } }
    }

    @MainActor private func nextPage() async {
        guard !loading, let uid = store.uid else { return }
        loading = true
        defer { loading = false }
        do {
            let size = 20
            var query: Query = Firestore.firestore().collection("users").document(uid).collection(relation.rawValue)
                .order(by: "createdAt", descending: true).limit(to: size)
            if let cursor { query = query.start(afterDocument: cursor) }
            let page = try await query.getDocuments()
            var result: [PawlyPublicUser] = []
            for doc in page.documents {
                do { if let user = try await store.publicUser(doc.documentID) { result.append(user) } }
                catch let denied as NSError where denied.domain == FirestoreErrorDomain && denied.code == FirestoreErrorCode.permissionDenied.rawValue { continue }
            }
            try Task.checkCancellation()
            let previous = Set(users.map(\.id))
            users.append(contentsOf: result.filter { !previous.contains($0.id) })
            cursor = page.documents.last
            more = page.documents.count == size
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
