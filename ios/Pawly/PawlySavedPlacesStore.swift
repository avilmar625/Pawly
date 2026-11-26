import Foundation
import Combine
import FirebaseAuth
import FirebaseFirestore

struct PawlySavedPlace: Identifiable, Codable {
    let id: String
    let name: String
    let subtitle: String
    let latitude: Double
    let longitude: Double
    let phone: String?

    static func cacheKey(_ uid: String) -> String { "pawly.savedPlaces.v3." + uid }

    static func cached(for uid: String) -> [Self] {
        guard let data = UserDefaults.standard.data(forKey: cacheKey(uid)) else { return [] }
        return (try? JSONDecoder().decode([Self].self, from: data)) ?? []
    }

    static func decodeEntries(_ entries: [String]) throws -> [Self] {
        try entries.map { try JSONDecoder().decode(Self.self, from: Data($0.utf8)) }
    }
}

@MainActor
final class PawlySavedPlacesStore: ObservableObject {
    @Published private(set) var places: [PawlySavedPlace] = []
    @Published private(set) var busy = false
    @Published var message: String?

    private static func privateDocument(_ uid: String) -> DocumentReference {
        Firestore.firestore().collection("users").document(uid).collection("privateData").document("mapFavourites")
    }

    private static func legacyDocument(_ uid: String) -> DocumentReference {
        Firestore.firestore().collection("users").document(uid).collection("exploreCollections").document("ios-favourites")
    }

    // A present but empty private document is authoritative after the last deletion.
    static func snapshot(for uid: String) async throws -> [PawlySavedPlace] {
        let current = try await privateDocument(uid).getDocument(source: .server)
        if current.exists {
            return try PawlySavedPlace.decodeEntries(current.get("entries") as? [String] ?? [])
        }
        let legacy = try await legacyDocument(uid).getDocument(source: .server)
        let old = try PawlySavedPlace.decodeEntries(legacy.get("entries") as? [String] ?? [])
        return PawlyBackupPolicy.addingMissing(PawlySavedPlace.cached(for: uid), to: old) { $0.id }
    }

    func load() async {
        guard let uid = Auth.auth().currentUser?.uid else { places = []; return }
        guard !busy else { return }
        busy = true
        defer { busy = false }
        places = PawlySavedPlace.cached(for: uid)
        do { try accept(try await Self.snapshot(for: uid), for: uid) }
        catch { if Auth.auth().currentUser?.uid == uid { message = PawlyServiceMessage.key(for: error) } }
    }

    func toggle(_ place: PawlySavedPlace) {
        let remove = places.contains { $0.id == place.id }
        change(place, remove: remove)
    }

    func remove(_ place: PawlySavedPlace) { change(place, remove: true) }

    private func change(_ place: PawlySavedPlace, remove: Bool) {
        guard !busy, let uid = Auth.auth().currentUser?.uid else { return }
        busy = true
        Task {
            defer { busy = false }
            do {
                let updated = try await Self.mutate(uid: uid) { existing in
                    remove ? existing.filter { $0.id != place.id } :
                        PawlyBackupPolicy.addingMissing([place], to: existing) { $0.id }
                }
                try accept(updated, for: uid)
            } catch { if Auth.auth().currentUser?.uid == uid { message = PawlyServiceMessage.key(for: error) } }
        }
    }

    private func accept(_ updated: [PawlySavedPlace], for uid: String) throws {
        guard Auth.auth().currentUser?.uid == uid else { throw PawlyBackupError.accountMismatch }
        try Self.cache(updated, for: uid)
        places = updated
        message = nil
    }

    static func restore(_ imported: [PawlySavedPlace], for uid: String) async throws {
        let updated = try await mutate(uid: uid) { PawlyBackupPolicy.addingMissing(imported, to: $0) { $0.id } }
        try cache(updated, for: uid)
    }

    private static func cache(_ values: [PawlySavedPlace], for uid: String) throws {
        UserDefaults.standard.set(try JSONEncoder().encode(values), forKey: PawlySavedPlace.cacheKey(uid))
    }

    private static func mutate(uid: String, change: @escaping ([PawlySavedPlace]) -> [PawlySavedPlace]) async throws -> [PawlySavedPlace] {
        guard Auth.auth().currentUser?.uid == uid else { throw PawlyBackupError.accountMismatch }
        let document = privateDocument(uid)
        let legacy = legacyDocument(uid)
        let cached = PawlySavedPlace.cached(for: uid)
        let result = try await Firestore.firestore().runTransaction { transaction, failure -> Any? in
            do {
                let current = try transaction.getDocument(document)
                var places = try PawlySavedPlace.decodeEntries(current.get("entries") as? [String] ?? [])
                if !current.exists {
                    let old = try transaction.getDocument(legacy)
                    let migrated = try PawlySavedPlace.decodeEntries(old.get("entries") as? [String] ?? [])
                    places = PawlyBackupPolicy.addingMissing(cached, to: migrated) { $0.id }
                }
                let updated = change(places)
                guard updated.count <= 100 else { throw PawlyExploreError.tooManySavedPlaces }
                let encoded = try updated.map { String(decoding: try JSONEncoder().encode($0), as: UTF8.self) }
                transaction.setData(["entries": encoded, "updatedAt": FieldValue.serverTimestamp()], forDocument: document)
                return encoded
            } catch { failure?.pointee = error as NSError; return nil }
        }
        guard let entries = result as? [String] else { throw PawlyBackupError.invalidFormat }
        return try PawlySavedPlace.decodeEntries(entries)
    }
}

enum PawlyExploreError: LocalizedError {
    case tooManySavedPlaces
    var errorDescription: String? { "Puedes guardar hasta 100 lugares." }
}
