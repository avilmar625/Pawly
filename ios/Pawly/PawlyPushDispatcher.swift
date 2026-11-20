import Foundation
import FirebaseAuth

enum PawlyPushDispatcher {
    static func send(
        type: String,
        recipientUID: String,
        relatedID: String = "",
        sourceEventID: String = ""
    ) async {
        guard
            let user = Auth.auth().currentUser,
            !recipientUID.isEmpty,
            let rawURL = Bundle.main.object(forInfoDictionaryKey: "PAWLY_BACKEND_URL") as? String,
            let baseURL = URL(string: rawURL.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))),
            ["https"].contains(baseURL.scheme?.lowercased() ?? ""),
            let url = URL(string: "/v1/activity-notifications", relativeTo: baseURL)
        else { return }

        do {
            let token = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
                user.getIDToken { value, error in
                    if let error { continuation.resume(throwing: error) }
                    else if let value { continuation.resume(returning: value) }
                    else { continuation.resume(throwing: PawlyPushError.missingToken) }
                }
            }
            let body: [String: String] = [
                "type": type,
                "recipientUid": recipientUID,
                "relatedId": relatedID,
                "sourceEventId": sourceEventID
            ]
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.timeoutInterval = 20

            let (_, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw PawlyPushError.requestRejected
            }
        } catch {
            // A committed Firestore notification remains available in Pawly even
            // when the optional push service is not yet deployed or is unavailable.
        }
    }
}

private enum PawlyPushError: Error {
    case missingToken
    case requestRejected
}
