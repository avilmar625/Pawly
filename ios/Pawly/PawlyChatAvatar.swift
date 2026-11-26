import SwiftUI
import FirebaseAuth
import FirebaseFirestore

struct PawlyChatAvatar: View {
    let userID: String
    let name: String
    var size: CGFloat = 40
    @State private var photo: URL?

    var body: some View {
        ZStack {
            Circle().fill(PawlyColors.mint)
            Text(verbatim: name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased())
                .font(.system(size: size * 0.38, weight: .semibold)).foregroundStyle(PawlyColors.primary)
            if let photo {
                AsyncImage(url: photo) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() }
                }
            }
        }
        .frame(width: size, height: size).clipShape(Circle()).accessibilityHidden(true)
        .task(id: userID) {
            photo = nil
            guard !userID.isEmpty, let owner = Auth.auth().currentUser?.uid else { return }
            let document = Firestore.firestore().collection("users").document(userID)
            let updates = AsyncStream<URL?> { continuation in
                let listener = document.addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                    guard error == nil, let snapshot, Auth.auth().currentUser?.uid == owner else {
                        continuation.yield(nil)
                        continuation.finish()
                        return
                    }
                    guard !snapshot.metadata.isFromCache else { return }
                    let data = snapshot.data()
                    let url = (data?["photoUrl"] as? String).flatMap(URL.init(string:))
                    continuation.yield(data?["isPublic"] as? Bool == true && url?.scheme == "https" ? url : nil)
                }
                continuation.onTermination = { _ in listener.remove() }
            }
            for await url in updates {
                guard !Task.isCancelled else { return }
                photo = url
            }
        }
    }
}
