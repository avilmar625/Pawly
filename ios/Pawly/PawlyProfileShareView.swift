import SwiftUI
import FirebaseFirestore

private enum PawlyRecipientList: String, CaseIterable {
    case chats = "Conversaciones", following = "Siguiendo", followers = "Seguidores", contacts = "Contactos"
}
private struct PawlyRecipient: Identifiable {
    let id: String
    let name: String
    let username: String
    var chat: String?
}

struct PawlyProfileShareView: View {
    let profileID: String
    @StateObject private var store = PawlyCommunityStore()
    @State private var profileText: String?
    @State private var profileError: String?
    @State private var visibility: ListenerRegistration?
    @State private var group = PawlyRecipientList.chats
    @State private var query = ""
    @State private var recipients: [PawlyRecipient] = []
    @State private var selected: [String: PawlyRecipient] = [:]
    @State private var sent = Set<String>()
    @State private var messageIDs: [String: String] = [:]
    @State private var cursor: DocumentSnapshot?
    @State private var more = false
    @State private var loading = false
    @State private var sending = false
    @State private var request = UUID()
    @State private var failure: String?
    private let db = Firestore.firestore()
    private var search: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        List {
            if let profileText {
                Section {
                    Text(verbatim: profileText)
                    ShareLink(item: profileText) { Label("Compartir fuera de Pawly", systemImage: "square.and.arrow.up") }
                }
                Picker("Destinatarios", selection: $group) {
                    ForEach(PawlyRecipientList.allCases, id: \.self) { Text(LocalizedStringKey($0.rawValue)).tag($0) }
                }.onChange(of: group) { _ in query = "" }
                if group == .contacts && search.isEmpty {
                    PawlyContactDiscoveryView { username in query = "@" + username }
                } else {
                    ForEach(recipients) { user in
                        Button {
                            if selected.removeValue(forKey: user.id) == nil { selected[user.id] = user }
                        } label: {
                            HStack {
                                PawlyChatAvatar(userID: user.id, name: user.name)
                                VStack(alignment: .leading) {
                                    Text(verbatim: user.name)
                                    if !user.username.isEmpty { Text(verbatim: "@" + user.username).font(.caption).foregroundStyle(.secondary) }
                                }
                                Spacer()
                                Image(systemName: sent.contains(user.id) ? "paperplane.fill" :
                                      selected[user.id] == nil ? "circle" : "checkmark.circle.fill")
                            }
                        }.disabled(sent.contains(user.id)).accessibilityAddTraits(selected[user.id] == nil ? [] : .isSelected)
                    }
                    if loading { ProgressView() }
                    else if more { Button("Cargar más") { Task { await load(next: true) } } }
                    else if recipients.isEmpty && failure == nil { Text("No hay destinatarios disponibles.") }
                }
                if !selected.isEmpty {
                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView() }; Text("Enviar"); Text(verbatim: String(selected.count)) }
                    }
                }
                if !sent.isEmpty { HStack { Label("Enviado", systemImage: "checkmark"); Text(verbatim: String(sent.count)) } }
                if let failure { Text(LocalizedStringKey(failure)).foregroundStyle(.secondary) }
            } else if let profileError { Text(LocalizedStringKey(profileError)) }
            else { ProgressView() }
        }.disabled(sending).navigationTitle("Compartir perfil")
            .searchable(text: $query, prompt: "Buscar por nombre o usuario")
            .onAppear { observeProfile() }
            .onDisappear { visibility?.remove(); request = UUID() }
            .task(id: [group.rawValue, search, profileText ?? ""]) {
                guard profileText != nil else { return }
                await load(next: false)
            }
    }

    private func observeProfile() {
        visibility?.remove()
        visibility = db.collection("users").document(profileID).addSnapshotListener(includeMetadataChanges: true) { document, error in
            if let error { profileText = nil; profileError = PawlyServiceMessage.key(for: error); return }
            guard let document, document.exists, document.get("isPublic") as? Bool == true,
                  let username = document.get("username") as? String, !username.isEmpty else {
                profileText = nil; profileError = "Solo se pueden compartir perfiles públicos."; return
            }
            guard !document.metadata.isFromCache else { return }
            let name = document.get("displayName") as? String ?? username
            profileText = name + " · @" + username + " · Pawly"; profileError = nil
        }
    }

    @MainActor private func load(next: Bool) async {
        guard let me = store.uid, !next || !loading else { return }
        let id = UUID(); request = id
        let term = search, list = group
        if !next { recipients = []; cursor = nil; more = false }
        loading = true; failure = nil
        defer { if request == id { loading = false } }
        guard list != .contacts || !term.isEmpty else { return }
        do {
            if !term.isEmpty { try await Task.sleep(nanoseconds: 300_000_000) }
            try Task.checkCancellation()
            let owner = db.collection("users").document(me)
            async let a = owner.collection("blocked").getDocuments()
            async let b = owner.collection("blockedBy").getDocuments()
            let blocks = try await (a, b)
            let hidden = Set((blocks.0.documents + blocks.1.documents).map(\.documentID)).union([me])
            var rows: [PawlyRecipient] = []
            var last: DocumentSnapshot?
            var hasMore = false
            if !term.isEmpty {
                let username = term.trimmingCharacters(in: CharacterSet(charactersIn: "@")).lowercased()
                guard username.count >= 2 else { return }
                let users = db.collection("users").whereField("isPublic", isEqualTo: true)
                var ranges = [("usernameLower", username)]
                if !term.hasPrefix("@") {
                    ranges += Set([term, term.capitalized, term.lowercased()]).sorted().map { ("displayName", $0) }
                }
                for (key, prefix) in ranges {
                    try Task.checkCancellation()
                    let docs = try await users.order(by: key).start(at: [prefix]).end(at: [prefix + "\u{f8ff}"]).limit(to: 20).getDocuments()
                    for doc in docs.documents where !hidden.contains(doc.documentID) {
                        if let person = recipient(doc) { rows.append(person) }
                    }
                }
            } else {
                let base: Query
                if list == .chats { base = db.collection("conversations").whereField("participants", arrayContains: me).order(by: "updatedAt", descending: true) }
                else { base = owner.collection(list == .following ? "following" : "followers").order(by: FieldPath.documentID()) }
                var page = base.limit(to: 25)
                if next, let cursor { page = page.start(afterDocument: cursor) }
                let docs = try await page.getDocuments()
                last = docs.documents.last; hasMore = docs.documents.count == 25
                for doc in docs.documents {
                    try Task.checkCancellation()
                    if list == .chats {
                        guard let other = (doc.get("participants") as? [String])?.first(where: { $0 != me }), !hidden.contains(other) else { continue }
                        let name = (doc.get("participantNames") as? [String: String])?[other] ?? "Pawly"
                        rows.append(PawlyRecipient(id: other, name: name, username: "", chat: doc.documentID))
                    } else if !hidden.contains(doc.documentID) {
                        do {
                            let profile = try await db.collection("users").document(doc.documentID).getDocument()
                            if let person = recipient(profile) { rows.append(person) }
                        } catch let issue as NSError where issue.domain == FirestoreErrorDomain && issue.code == FirestoreErrorCode.permissionDenied.rawValue { continue }
                    }
                }
            }
            guard request == id, !Task.isCancelled else { return }
            var seen = Set(recipients.map(\.id))
            recipients += rows.filter { seen.insert($0.id).inserted }
            cursor = last; more = hasMore
        } catch { if request == id && !Task.isCancelled { failure = PawlyServiceMessage.key(for: error) } }
    }

    private func recipient(_ doc: DocumentSnapshot) -> PawlyRecipient? {
        guard doc.get("isPublic") as? Bool == true, let username = doc.get("username") as? String else { return nil }
        return PawlyRecipient(id: doc.documentID, name: doc.get("displayName") as? String ?? username, username: username)
    }

    @MainActor private func send() async {
        guard !sending, let profileText else { return }
        sending = true; defer { sending = false }
        failure = nil
        for person in Array(selected.values) {
            do {
                let chatID: String
                if let chat = person.chat { chatID = chat } else { chatID = try await store.startConversation(userID: person.id) }
                let id = messageIDs[person.id] ?? UUID().uuidString
                messageIDs[person.id] = id
                try await store.sendMessage(conversationID: chatID, text: profileText, sharedProfileUID: profileID, messageID: id)
                sent.insert(person.id); selected.removeValue(forKey: person.id)
            } catch { failure = PawlyServiceMessage.key(for: error) }
        }
    }
}
