import Foundation
import UIKit
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import GoogleSignIn

enum PawlyGoogleSignIn {
    static func handle(_ url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    @MainActor
    static func signIn() async throws {
        let configuredID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String
        let clientID = [configuredID, FirebaseApp.app()?.options.clientID].compactMap { $0 }.first { !$0.isEmpty && !$0.contains("$(") }
        guard let clientID else {
            throw PawlyGoogleError.missingClientID
        }
        guard let presenter = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: \.isKeyWindow)?.rootViewController else {
            throw PawlyGoogleError.noPresenter
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let idToken = result.user.idToken?.tokenString else {
            throw PawlyGoogleError.missingToken
        }

        let credential = GoogleAuthProvider.credential(
            withIDToken: idToken,
            accessToken: result.user.accessToken.tokenString
        )
        let authResult = try await Auth.auth().signIn(with: credential)
        let user = authResult.user
        let db = Firestore.firestore()
        let userRef = db.collection("users").document(user.uid)
        let existingProfile = try await userRef.getDocument()
        guard !existingProfile.exists else { return }

        let rawSeed = (user.email?.components(separatedBy: "@").first ?? "pawly")
            .lowercased()
            .replacingOccurrences(of: "[^a-z0-9_.]", with: "", options: .regularExpression)
        let seed = rawSeed.isEmpty ? "pawly" : rawSeed
        let suffix = String(user.uid.lowercased().filter { $0.isLetter || $0.isNumber }.suffix(8))
        let username = String(seed.prefix(15)) + "_" + suffix
        guard username.range(of: "^[a-z0-9_.]{3,24}$", options: .regularExpression) != nil else {
            throw PawlyGoogleError.invalidGeneratedUsername
        }

        let usernameRef = db.collection("usernames").document(username)
        let reservationRef = db.collection("usernameReservations").document(user.uid)
        let existingUsername = try await usernameRef.getDocument()
        guard !existingUsername.exists else {
            throw PawlyGoogleError.usernameCollision
        }

        // These three records must be committed together: Firestore rules require
        // the username reservation to exist when the username and user are created.
        let displayName = (user.displayName ?? seed).trimmingCharacters(in: .whitespacesAndNewlines)
        let batch = db.batch()
        batch.setData(["uid": user.uid], forDocument: usernameRef)
        batch.setData(["username": username], forDocument: reservationRef)
        batch.setData([
            "uid": user.uid,
            "email": user.email ?? "",
            "username": username,
            "usernameLower": username,
            "displayName": String(displayName.prefix(50)),
            "bio": "",
            "photoUrl": user.photoURL?.absoluteString ?? "",
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
        try await batch.commit()
    }
}

enum PawlyGoogleError: LocalizedError {
    case missingClientID
    case noPresenter
    case missingToken
    case invalidGeneratedUsername
    case usernameCollision

    var errorDescription: String? {
        switch self {
        case .missingClientID:
            return "No se encontró el CLIENT_ID de Google. Revisa GoogleService-Info.plist y GOOGLE_CLIENT_ID en Config.local.xcconfig."
        case .noPresenter:
            return "No se ha podido abrir la ventana de Google."
        case .missingToken:
            return "Google no ha devuelto un token válido."
        case .invalidGeneratedUsername:
            return "No se ha podido generar un nombre de usuario válido para tu cuenta."
        case .usernameCollision:
            return "El nombre de usuario automático ya está ocupado. Inténtalo de nuevo."
        }
    }
}
