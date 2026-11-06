import Foundation
import Combine
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
        _ = try await Auth.auth().signIn(withEmail: email.trimmingCharacters(in: .whitespacesAndNewlines), password: password)
    }

    func register(email: String, password: String, displayName: String) async throws {
        let result = try await Auth.auth().createUser(withEmail: email.trimmingCharacters(in: .whitespacesAndNewlines), password: password)
        let cleanName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let change = result.user.createProfileChangeRequest()
        change.displayName = cleanName
        try await change.commitChanges()
        try await Firestore.firestore().collection("users").document(result.user.uid).setData([
            "uid": result.user.uid,
            "email": result.user.email ?? "",
            "displayName": cleanName,
            "createdAt": FieldValue.serverTimestamp(),
            "platform": "ios"
        ], merge: true)
        try await result.user.sendEmailVerification()
    }

    func resetPassword(email: String) async throws {
        try await Auth.auth().sendPasswordReset(withEmail: email.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    func signOut() throws {
        try Auth.auth().signOut()
    }
}
