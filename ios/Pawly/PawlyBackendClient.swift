import Foundation
import FirebaseAuth

enum PawlyBackendClient {
    static func postJSON(path: String, body: [String: Any]) async throws -> [String: Any] {
        guard
            let user = Auth.auth().currentUser,
            let rawBaseURL = Bundle.main.object(forInfoDictionaryKey: "PAWLY_BACKEND_URL") as? String,
            let baseURL = URL(string: rawBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)),
            baseURL.scheme?.lowercased() == "https",
            let url = URL(string: path, relativeTo: baseURL)
        else {
            throw PawlyBackendError.notConfigured
        }

        let token = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            user.getIDToken { value, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let value {
                    continuation.resume(returning: value)
                } else {
                    continuation.resume(throwing: PawlyBackendError.missingToken)
                }
            }
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.timeoutInterval = 25

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw PawlyBackendError.invalidResponse
        }
        let result = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard (200..<300).contains(http.statusCode) else {
            let message = result["error"] as? String ?? "El servicio de Pawly ha respondido con un error."
            throw PawlyBackendError.rejected(message)
        }
        return result
    }

    static func dispatchLostPetAlert(alertID: String) async throws -> Int {
        guard !alertID.isEmpty, alertID.count <= 128 else { throw PawlyBackendError.invalidAlert }
        var pageToken = ""
        var offset = 0
        var delivered = 0

        for _ in 0..<2_000 {
            var payload: [String: Any] = ["alertId": alertID, "offset": offset]
            if !pageToken.isEmpty { payload["pageToken"] = pageToken }
            let result = try await postJSON(path: "/v1/lost-pet-alerts", body: payload)
            delivered += result["delivered"] as? Int ?? 0

            if result["complete"] as? Bool == true {
                return delivered
            }

            pageToken = result["pageToken"] as? String ?? ""
            offset = result["nextOffset"] as? Int ?? 0
        }
        throw PawlyBackendError.paginationLimit
    }
}

private enum PawlyBackendError: LocalizedError {
    case notConfigured
    case missingToken
    case invalidResponse
    case invalidAlert
    case paginationLimit
    case rejected(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Configura PAWLY_BACKEND_URL para utilizar el servicio de avisos de Pawly."
        case .missingToken:
            return "Firebase no ha generado un token de acceso válido."
        case .invalidResponse:
            return "El servicio de Pawly ha devuelto una respuesta no válida."
        case .invalidAlert:
            return "El identificador del aviso no es válido."
        case .paginationLimit:
            return "El servicio ha alcanzado el límite de páginas de avisos."
        case .rejected(let message):
            return message
        }
    }
}
