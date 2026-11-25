import Foundation

/// Remembers confirmed HTTP responses while Firestore indexing, saving a form or retrying its attachments.
actor PawlyUploadCache {
    static let shared = PawlyUploadCache()
    private struct Entry {
        let asset: PawlyUploadContract.Asset
        let created: Date
        var indexed: Bool
    }
    private var entries: [String: Entry] = [:]
    private var pending: [String: Task<String, Error>] = [:]

    func resolve(key: String, upload: @escaping () async throws -> PawlyUploadContract.Asset,
                 index: @escaping (PawlyUploadContract.Asset) async throws -> Void) async throws -> String {
        if let task = pending[key] { return try await task.value }
        let cutoff = Date().addingTimeInterval(-900)
        entries = entries.filter { $0.value.created > cutoff }
        if let existing = entries[key], existing.indexed { return existing.asset.secure_url }
        let task = Task {
            let asset: PawlyUploadContract.Asset
            if let existing = entries[key] {
                asset = existing.asset
            } else {
                asset = try await upload()
                if entries.count >= 128, let oldest = entries.min(by: { $0.value.created < $1.value.created }) {
                    entries.removeValue(forKey: oldest.key)
                }
                entries[key] = Entry(asset: asset, created: Date(), indexed: false)
            }
            try await index(asset)
            entries[key]?.indexed = true
            return asset.secure_url
        }
        pending[key] = task
        defer { pending[key] = nil }
        return try await task.value
    }
}
