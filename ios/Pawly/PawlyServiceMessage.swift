import Foundation
import FirebaseAuth
import FirebaseFirestore

enum PawlyServiceMessage {
    /// Return a display key so an on-screen error follows changes in the SwiftUI locale.
    static func key(for error: Error) -> String {
        let issue = error as NSError
        if issue.domain == AuthErrorDomain {
            switch AuthErrorCode(rawValue: issue.code) {
            case .invalidCredential, .wrongPassword, .userNotFound: return "El correo o la contraseña no son correctos."
            case .invalidEmail: return "Comprueba el formato del correo electrónico."
            case .emailAlreadyInUse: return "Este correo ya tiene una cuenta. Inicia sesión."
            case .weakPassword: return "Elige una contraseña más segura."
            case .tooManyRequests: return "Demasiados intentos. Vuelve a intentarlo más tarde."
            case .networkError: return "Comprueba la conexión y vuelve a intentarlo."
            default: break
            }
        }
        if issue.domain == FirestoreErrorDomain {
            switch issue.code {
            case FirestoreErrorCode.permissionDenied.rawValue, FirestoreErrorCode.unauthenticated.rawValue: return "Tu cuenta no tiene permiso para esta acción."
            case FirestoreErrorCode.unavailable.rawValue, FirestoreErrorCode.deadlineExceeded.rawValue: return "Comprueba la conexión y vuelve a intentarlo."
            default: break
            }
        }
        if issue.domain == NSURLErrorDomain, issue.code != NSURLErrorCancelled {
            return "Comprueba la conexión y vuelve a intentarlo."
        }
        return error.localizedDescription
    }
}
