import Foundation
import Combine

struct PawlyPushDestination: Identifiable, Equatable {
    let id: String
    let type: String
    let relatedID: String
    let sourceEventID: String
}

/// Holds a push destination until the signed-in SwiftUI tab shell can consume it.
@MainActor
final class PawlyPushRouter: ObservableObject {
    static let shared = PawlyPushRouter()
    @Published private(set) var destination: PawlyPushDestination?

    private init() {}

    func open(userInfo: [AnyHashable: Any]) {
        let nested = userInfo["data"] as? [String: Any] ?? [:]
        func value(_ key: String) -> String {
            (userInfo[key] as? String ?? nested[key] as? String ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }

        let type = value("type").uppercased()
        guard ["LIKE", "COMMENT", "FOLLOW", "LOST_PET", "MESSAGE"].contains(type) else { return }
        destination = PawlyPushDestination(
            id: UUID().uuidString,
            type: type,
            relatedID: value("relatedId"),
            sourceEventID: value("sourceEventId")
        )
    }

    func consume(_ id: String) {
        guard destination?.id == id else { return }
        destination = nil
    }
}
