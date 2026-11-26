import FirebaseFirestore
import FirebaseAuth
import SwiftUI

enum PawlyFeedOrder: String, CaseIterable {
    case recent = "Más recientes", oldest = "Más antiguos", liked = "Más Me gusta", discussed = "Más comentadas"
    var field: String {
        switch self { case .recent, .oldest: return "createdAt"; case .liked: return "likeCount"; case .discussed: return "commentCount" }
    }
}

struct PawlyFeedFilter: Hashable {
    var query = ""
    var category = ""
    var order = PawlyFeedOrder.recent
    var media = ""
    var following = false
    static func words(_ text: String) -> [String] {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .lowercased().components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
    }
    static func tokens(_ fields: [String]) -> [String] {
        Array(Set(words(fields.joined(separator: " ")).map { String($0.prefix(64)) })).sorted()
    }
}

struct PawlyFeedBrowser: View {
    let filter: PawlyFeedFilter
    @ObservedObject var store: PawlyCommunityStore
    let comment: (PawlyFeedPost) -> Void
    @State private var posts = [PawlyFeedPost]()
    @State private var cursor: DocumentSnapshot?
    @State private var followed = Set<String>()
    @State private var author: String?
    @State private var more = false
    @State private var busy = false
    @State private var error: String?
    @State private var request = UUID()
    @State private var refresh = 0

    var body: some View {
        Group {
            ForEach(posts.filter { !store.hiddenFeedAuthors.contains($0.authorId) }) { post in
                FeedPostCard(post: post, onLike: { Task {
                    do {
                        try await store.toggleLike(post)
                        if let i = posts.firstIndex(where: { $0.id == post.id }) {
                            posts[i].likedByMe.toggle()
                            posts[i].likeCount += posts[i].likedByMe ? 1 : -1
                        }
                    } catch { self.error = PawlyServiceMessage.key(for: error) }
                } }, onComment: { comment(post) }, onEdited: { updated in
                    if let i = posts.firstIndex(where: { $0.id == updated.id }) { posts[i] = updated }
                })
            }
            if busy { ProgressView() }
            if let error { Text(LocalizedStringKey(error)); Button("Reintentar") { refresh += 1 } }
            else if more && !busy { Button("Cargar más") { Task { await page(request) } } }
            else if !busy && posts.isEmpty { Text("No hay publicaciones de este tipo.") }
        }
        .task(id: refresh) {
            let id = UUID()
            request = id; posts = []; cursor = nil; more = false; busy = true; error = nil
            do {
                try await Task.sleep(nanoseconds: 300_000_000)
                guard let uid = store.uid else { throw PawlyCommunityError.notSignedIn }
                let db = Firestore.firestore()
                if filter.following {
                    let docs = try await db.collection("users").document(uid).collection("following").getDocuments()
                    followed = Set(docs.documents.map(\.documentID))
                }
                if filter.query.hasPrefix("@") {
                    let handle = String(filter.query.dropFirst()).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !handle.isEmpty, !handle.contains("/") else { busy = false; return }
                    let result = try await db.collection("usernames").document(handle).getDocument()
                    author = result.get("uid") as? String
                    if author == nil { busy = false; return }
                }
                try Task.checkCancellation()
                busy = false
                await page(id)
            } catch { if !Task.isCancelled { self.error = PawlyServiceMessage.key(for: error); busy = false } }
        }
        .onDisappear { request = UUID() }
    }

    @MainActor private func page(_ id: UUID) async {
        guard !busy, request == id, let uid = store.uid else { return }
        busy = true
        defer { if request == id { busy = false } }
        do {
            var q: Query = Firestore.firestore().collection("posts")
            let words = PawlyFeedFilter.words(filter.query)
            if let author { q = q.whereField("authorId", isEqualTo: author) }
            else if let term = words.max(by: { $0.count < $1.count }) {
                q = q.whereField("searchTokens", arrayContains: String(term.prefix(64)))
            }
            q = q.order(by: filter.order.field, descending: filter.order != .oldest).limit(to: 40)
            if let cursor { q = q.start(afterDocument: cursor) }
            let result = try await q.getDocuments()
            guard request == id, !Task.isCancelled, store.uid == uid else { return }
            let existing = Set(posts.map(\.id))
            let matching = result.documents.map { PawlyCommunityStore.feedPost($0, uid: uid) }.filter { p in
                let content = [p.text, p.authorName, p.petName, p.category].map { PawlyFeedFilter.words($0).joined(separator: " ") }
                return !existing.contains(p.id) && (filter.category.isEmpty || filter.category == p.category)
                    && (!filter.following || followed.contains(p.authorId))
                    && (filter.media != "image" || !p.imageURL.isEmpty)
                    && (filter.media != "video" || !p.videoURL.isEmpty)
                    && (author != nil || words.isEmpty || content.contains { $0.contains(words.joined(separator: " ")) })
            }
            posts += matching
            cursor = result.documents.last; more = result.documents.count == 40
            error = nil
        } catch { if request == id { self.error = PawlyServiceMessage.key(for: error) } }
    }
}
