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
    private var registering = false
    private var authListener: AuthStateDidChangeListenerHandle?

    init() {
        firebaseConfigured = FirebaseApp.app() != nil
        guard firebaseConfigured else {
            isLoading = false
            return
        }
        authListener = Auth.auth().addStateDidChangeListener { [weak self] _, _ in
            Task { @MainActor in
                guard let self, !self.registering else { return }
                self.user = Auth.auth().currentUser
                self.isLoading = false
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

        guard !registering else { throw PawlyAuthError.profileSetupFailed }
        registering = true
        var completed = false
        var authenticatedUID: String?
        defer {
            if !completed, let authenticatedUID, Auth.auth().currentUser?.uid == authenticatedUID {
                try? Auth.auth().signOut()
            }
            registering = false
            user = Auth.auth().currentUser
        }
        let db = Firestore.firestore()
        let result: AuthDataResult
        let created: Bool
        do {
            result = try await Auth.auth().createUser(withEmail: cleanEmail, password: password)
            created = true
        } catch let conflict as NSError where conflict.domain == AuthErrorDomain && conflict.code == AuthErrorCode.emailAlreadyInUse.rawValue {
            // Resume interrupted setup only after proving ownership with the supplied password.
            result = try await Auth.auth().signIn(withEmail: cleanEmail, password: password)
            authenticatedUID = result.user.uid
            let profile = try await db.collection("users").document(result.user.uid).getDocument(source: .server)
            guard !profile.exists else { throw conflict }
            created = false
        }
        let uid = result.user.uid
        authenticatedUID = uid
        let usernameRef = db.collection("usernames").document(cleanUsername)
        let reservationRef = db.collection("usernameReservations").document(uid)
        let userRef = db.collection("users").document(uid)

        let existing = try await usernameRef.getDocument(source: .server)
        guard !existing.exists else {
            if created { try await result.user.delete() }
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
            // Roll back only after a definitive write rejection and a server-confirmed absence.
            let writeError = error as NSError
            let rejected = writeError.domain == FirestoreErrorDomain &&
                [FirestoreErrorCode.permissionDenied.rawValue, FirestoreErrorCode.alreadyExists.rawValue].contains(writeError.code)
            let profile = try await userRef.getDocument(source: .server)
            if !profile.exists {
                if rejected, created {
                    let reservation = try await usernameRef.getDocument(source: .server)
                    try await result.user.delete()
                    if reservation.exists { throw PawlyAuthError.usernameTaken }
                }
                throw PawlyAuthError.profileSetupFailed
            }
        }

        let change = result.user.createProfileChangeRequest()
        change.displayName = cleanName
        try? await change.commitChanges()
        // Verification email delivery is best-effort; it must not invalidate a
        // profile that Firebase has already saved successfully.
        try? await result.user.sendEmailVerification()
        completed = true
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
