import Foundation

enum PawlyBackupError: LocalizedError {
    case notSignedIn, profileMissing, tooLarge, invalidFormat, accountMismatch
    var errorDescription: String? {
        switch self {
        case .notSignedIn: return "Inicia sesión para crear o restaurar una copia."
        case .profileMissing: return "No se ha encontrado el perfil de Pawly."
        case .tooLarge: return "La copia supera el límite de 25 MB."
        case .invalidFormat: return "El archivo no es una copia de Pawly compatible."
        case .accountMismatch: return "Esta copia pertenece a otra cuenta de Pawly."
        }
    }
}

enum PawlyBackupPolicy {
    static let format = "pawly-ios-backup"
    static let version = 2
    static let maximumBytes = 25 * 1024 * 1024
    static let profileFields: Set<String> = [
        "displayName", "bio", "photoUrl", "isPublic", "dogProfiles", "accountType",
        "professionalCategory", "professionalLocation", "professionalDescription",
        "professionalContactPublic", "professionalPublicContact"
    ]

    static func encode(_ payload: [String: Any]) throws -> Data {
        let data = try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys])
        guard data.count <= maximumBytes else { throw PawlyBackupError.tooLarge }
        return data
    }

    static func decode(_ data: Data, for uid: String) throws -> [String: Any] {
        guard data.count <= maximumBytes else { throw PawlyBackupError.tooLarge }
        guard let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              payload["format"] as? String == format, payload["version"] as? Int == version,
              let profile = payload["profile"] as? [String: Any], let pets = payload["pets"] as? [[String: Any]] else {
            throw PawlyBackupError.invalidFormat
        }
        guard payload["uid"] as? String == uid else { throw PawlyBackupError.accountMismatch }
        if let profiles = profile["dogProfiles"] { try validateIDs(profiles, key: "id") }
        try validateIDs(pets, key: "petId")
        for pet in pets {
            for group in ["privateCare", "shared"] where pet[group] != nil {
                guard let fields = pet[group] as? [String: Any] else { throw PawlyBackupError.invalidFormat }
                for key in ["passport", "tasks", "careLogs", "agenda", "diary", "expenses"] {
                    if let records = fields[key] { try validateIDs(records, key: "id", requireData: true) }
                }
            }
        }
        if let records = payload["exploreCollections"] { try validateIDs(records, key: "id", requireData: true) }
        return payload
    }

    static func validID(_ value: String) -> Bool {
        !value.isEmpty && value != "." && value != ".." && !value.contains("/") && value.utf8.count <= 1500
    }

    private static func validateIDs(_ raw: Any, key: String, requireData: Bool = false) throws {
        guard let records = raw as? [[String: Any]] else { throw PawlyBackupError.invalidFormat }
        var seen = Set<String>()
        for record in records {
            guard let id = record[key] as? String, validID(id), seen.insert(id).inserted,
                  !requireData || record["data"] is [String: Any] else { throw PawlyBackupError.invalidFormat }
        }
    }

    static func addingMissing<T, ID: Hashable>(_ imported: [T], to existing: [T], id: (T) -> ID) -> [T] {
        var seen = Set(existing.map(id))
        return existing + imported.filter { seen.insert(id($0)).inserted }
    }
}
