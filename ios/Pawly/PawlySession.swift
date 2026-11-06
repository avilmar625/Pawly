import Foundation
import Combine
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore

@MainActor
final class PawlySession: ObservableObject {
    @Published private(set) var user: User?
    @Published private(set) var isLoading = true
    let firebaseConfigured: Bool
    private var authListener: AuthStateDidChangeListenerHandle?

    init() {
        firebaseConfigured = FirebaseApp.app() != nil
        guard firebaseConfigured else {
            isLoading = false
            return
        }
        authListener = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                self?.user = user
                self?.isLoading = false
            }
        }
    }

    deinit {
        if let authListener { Auth.auth().removeStateDidChangeListener(authListener) }
    }

    func signIn(email: String, password: String) async throws {
        _ = try await Auth.auth().signIn(
            withEmail: email.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password
        )
    }

    func register(email: String, password: String, displayName: String, username: String) async throws {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "@", with: "")
            .lowercased()
        guard cleanUsername.range(of: "^[a-z0-9_.]{3,24}$", options: .regularExpression) != nil else {
            throw PawlyAuthError.invalidUsername
        }

        let result = try await Auth.auth().createUser(withEmail: cleanEmail, password: password)
        let uid = result.user.uid
        let db = Firestore.firestore()
        let usernameRef = db.collection("usernames").document(cleanUsername)
        let userRef = db.collection("users").document(uid)

        do {
            let existing = try await usernameRef.getDocument()
            guard !existing.exists else { throw PawlyAuthError.usernameTaken }
            try await usernameRef.setData(["uid": uid])
            try await userRef.setData([
                "uid": uid,
                "email": cleanEmail,
                "username": cleanUsername,
                "usernameLower": cleanUsername,
                "displayName": cleanName,
                "bio": "",
                "photoUrl": "",
                "isPublic": true,
                "followersCount": 0,
                "followingCount": 0,
                "createdAt": FieldValue.serverTimestamp(),
                "platform": "ios"
            ])
            let change = result.user.createProfileChangeRequest()
            change.displayName = cleanName
            try await change.commitChanges()
            try await result.user.sendEmailVerification()
        } catch {
            try? await usernameRef.delete()
            try? await result.user.delete()
            throw error
        }
    }

    func resetPassword(email: String) async throws {
        try await Auth.auth().sendPasswordReset(
            withEmail: email.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    func signOut() throws {
        try Auth.auth().signOut()
    }
}

enum PawlyAuthError: LocalizedError {
    case invalidUsername
    case usernameTaken

    var errorDescription: String? {
        switch self {
        case .invalidUsername:
            return "El usuario debe tener entre 3 y 24 caracteres: letras, números, punto o guion bajo."
        case .usernameTaken:
            return "Ese nombre de usuario ya está ocupado."
        }
    }
}
