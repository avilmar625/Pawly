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
        guard !cleanName.isEmpty, cleanName.count <= 50,
              cleanUsername.range(of: "^[a-z0-9_.]{3,24}$", options: .regularExpression) != nil else {
            throw PawlyAuthError.invalidUsername
        }

        let result = try await Auth.auth().createUser(withEmail: cleanEmail, password: password)
        let uid = result.user.uid
        let db = Firestore.firestore()
        let usernameRef = db.collection("usernames").document(cleanUsername)
        let reservationRef = db.collection("usernameReservations").document(uid)
        let userRef = db.collection("users").document(uid)

        let existing = try await usernameRef.getDocument()
        guard !existing.exists else {
            try? await result.user.delete()
            try? Auth.auth().signOut()
            throw PawlyAuthError.usernameTaken
        }

        let batch = db.batch()
        batch.setData(["uid": uid], forDocument: usernameRef)
        batch.setData(["username": cleanUsername], forDocument: reservationRef)
        batch.setData([
            "uid": uid,
            "email": cleanEmail,
            "username": cleanUsername,
            "usernameLower": cleanUsername,
            "displayName": cleanName,
            "bio": "",
            "photoUrl": "",
            "isPublic": false,
            "followersCount": 0,
            "followingCount": 0,
            "accountType": "personal",
            "professionalCategory": "",
            "professionalLocation": "",
            "professionalDescription": "",
            "professionalContactPublic": false,
            "dogProfiles": [[String: Any]](),
            "createdAt": FieldValue.serverTimestamp(),
            "platform": "ios"
        ], forDocument: userRef)

        do {
            try await batch.commit()
        } catch {
            // A lost network response can happen after Firestore has committed.
            // Avoid deleting the Auth account when the profile is already present.
            let profileExists = (try? await userRef.getDocument().exists) ?? false
            if !profileExists {
                let usernameNowTaken = (try? await usernameRef.getDocument().exists) ?? false
                try? await result.user.delete()
                try? Auth.auth().signOut()
                if usernameNowTaken { throw PawlyAuthError.usernameTaken }
                throw PawlyAuthError.profileSetupFailed
            }
        }

        let change = result.user.createProfileChangeRequest()
        change.displayName = cleanName
        try? await change.commitChanges()
        // Verification email delivery is best-effort; it must not invalidate a
        // profile that Firebase has already saved successfully.
        try? await result.user.sendEmailVerification()
    }

    func resetPassword(email: String) async throws {
        try await Auth.auth().sendPasswordReset(
            withEmail: email.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    func requestAccountDeletion() async throws {
        guard let user = Auth.auth().currentUser else { throw PawlySessionError.notSignedIn }
        let requestRef = Firestore.firestore().collection("accountDeletionRequests").document(user.uid)
        let existing = try await requestRef.getDocument()
        if existing.exists {
            if existing.get("status") as? String == "pending" { return }
            throw PawlySessionError.deletionAlreadyRequested
        }
        try await requestRef.setData([
            "uid": user.uid,
            "email": user.email ?? "",
            "requestedAt": FieldValue.serverTimestamp(),
            "status": "pending",
            "source": "ios"
        ])
    }

    func signOut() throws {
        try Auth.auth().signOut()
    }
}

enum PawlyAuthError: LocalizedError {
    case invalidUsername
    case usernameTaken
    case profileSetupFailed

    var errorDescription: String? {
        switch self {
        case .invalidUsername:
            return "El usuario debe tener entre 3 y 24 caracteres: letras, números, punto o guion bajo."
        case .usernameTaken:
            return "Ese nombre de usuario ya está ocupado."
        case .profileSetupFailed:
            return "No se ha podido completar el perfil en Pawly. Comprueba la conexión y vuelve a crear la cuenta."
        }
    }
}

enum PawlySessionError: LocalizedError {
    case notSignedIn
    case deletionAlreadyRequested

    var errorDescription: String? {
        switch self {
        case .notSignedIn:
            return "Inicia sesión para solicitar la eliminación de la cuenta."
        case .deletionAlreadyRequested:
            return "Ya existe una solicitud de eliminación registrada para esta cuenta."
        }
    }
}
