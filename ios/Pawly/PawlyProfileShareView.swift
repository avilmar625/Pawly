import SwiftUI
import FirebaseFirestore

struct PawlyProfileShareView: View {
    let profileID: String
    @StateObject private var store = PawlyCommunityStore()
    @State private var profileText: String?
    @State private var profileError: String?
    @State private var visibility: ListenerRegistration?
    @State private var selected: [String: PawlyIdentity] = [:]
    @State private var sent = Set<String>()
    @State private var messageIDs: [String: String] = [:]
    @State private var sending = false
    @State private var failure: String?
    private let db = Firestore.firestore()

    var body: some View {
        List {
            if let profileText {
                Section {
                    Text(verbatim: profileText)
                    ShareLink(item: profileText) { Label("Compartir fuera de Pawly", systemImage: "square.and.arrow.up") }
                }
                PawlyRecipientPicker(includeChats: true, selected: Set(selected.keys), sent: sent) { person in
                    if selected.removeValue(forKey: person.id) == nil { selected[person.id] = person }
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
            .onAppear { observeProfile() }
            .onDisappear { visibility?.remove() }
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

struct PawlyRecipientPicker: View {
    var includeChats = false
    var selected = Set<String>()
    var sent = Set<String>()
    var choose: (PawlyIdentity) -> Void
    @StateObject private var store = PawlyCommunityStore()
    @State private var query = ""
    @State private var people: [PawlyIdentity] = []
    @State private var cursor = ""
    @State private var loading = false
    @State private var contacts = false
    @State private var failure: String?
    @State private var request = UUID()

    var body: some View {
        Section {
            TextField("Buscar por nombre o usuario", text: $query).textInputAutocapitalization(.never).autocorrectionDisabled()
            DisclosureGroup("Contactos", isExpanded: $contacts) {
                if contacts { PawlyContactDiscoveryView { query = "@" + $0; contacts = false } }
            }
            ForEach(people) { person in
                Button { choose(person) } label: {
                    HStack {
                        if person.chat != nil && person.username.isEmpty {
                            PawlyChatAvatar(userID:person.id,name:person.name)
                            Text(verbatim:person.name)
                        } else { PawlyIdentityRow(person: person) }
                        Spacer()
                        if selected.contains(person.id) || sent.contains(person.id) { Image(systemName: "checkmark.circle.fill") }
                    }
                }.buttonStyle(.borderless).disabled(sent.contains(person.id))
            }
            if loading { ProgressView() }
            if !cursor.isEmpty && !loading { Button("Cargar más") { Task { await load(next: true) } } }
            if let failure { Text(LocalizedStringKey(failure)); Button("Reintentar") { Task { await load(next: false) } } }
        }.task(id: query) { await load(next: false) }
    }

    @MainActor private func load(next: Bool) async {
        guard let me = store.uid else { return }
        let token = UUID(); request = token
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        loading = true; failure = nil
        defer { if request == token { loading = false } }
        if !next { people = []; cursor = "" }
        do {
            var rows: [PawlyIdentity] = []
            var more = ""
            if term.isEmpty {
                if includeChats && !next {
                    let db = Firestore.firestore()
                    let mine = db.collection("users").document(me)
                    async let a = mine.collection("blocked").getDocuments()
                    async let b = mine.collection("blockedBy").getDocuments()
                    let blocks = try await (a,b)
                    let hidden = Set((blocks.0.documents + blocks.1.documents).map(\.documentID))
                    let chats = try await db.collection("conversations").whereField("participants", arrayContains: me)
                        .order(by: "updatedAt", descending: true).limit(to: 25).getDocuments()
                    for chat in chats.documents {
                        guard let id = (chat.get("participants") as? [String])?.first(where: { $0 != me }), !hidden.contains(id) else { continue }
                        rows.append(.init(id: id, name: (chat.get("participantNames") as? [String:String])?[id] ?? "Pawly",
                            username: "", chat: chat.documentID))
                    }
                    if request == token && !Task.isCancelled { people = rows }
                }
                let page = try await PawlyIdentity.page(.following, cursor: cursor)
                rows += page.0.filter { $0.isPublic }; more = page.1
            } else {
                try await Task.sleep(nanoseconds: 300_000_000)
                let names = try await store.searchUsers(term)
                rows = names.map { .init(id: $0.id, name: $0.displayName, username: $0.username, photo: $0.photoURL) }
                if !term.hasPrefix("@") && term.count >= 2 {
                    let db=Firestore.firestore()
                    let base=db.collection("users").whereField("isPublic", isEqualTo:true).order(by:"displayName")
                    for prefix in Set([term, term.capitalized]) {
                        let matches=try await base.start(at:[prefix]).end(at:[prefix+"\u{f8ff}"]).limit(to:12).getDocuments()
                        var seen=Set(rows.map(\.id))
                        for doc in matches.documents where doc.documentID != me && seen.insert(doc.documentID).inserted {
                            try Task.checkCancellation()
                            do {
                                if let user=try await store.publicUser(doc.documentID) {
                                    rows.append(.init(id:user.id,name:user.displayName,username:user.username,photo:user.photoURL))
                                }
                            } catch let problem as NSError where problem.domain == FirestoreErrorDomain && problem.code == FirestoreErrorCode.permissionDenied.rawValue { continue }
                        }
                    }
                }
            }
            guard request == token && !Task.isCancelled else { return }
            var seen = Set(people.map(\.id))
            people += rows.filter { seen.insert($0.id).inserted }; cursor = more
        } catch { if request == token && !Task.isCancelled { failure = PawlyServiceMessage.key(for: error) } }
    }
}
