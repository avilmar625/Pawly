import Foundation

@main enum BackupPolicyTests {
    static func main() throws {
        let profile = ["id": "pet", "name": "Lua"]
        let envelope: [String: Any] = ["format": PawlyBackupPolicy.format, "version": 2, "uid": "fixture",
            "pets": [["petId": "pet", "privateCare": ["passport": [["id": "one", "data": ["title": "Weight"]]]]]],
            "profile": ["dogProfiles": [profile]]]
        let data = try PawlyBackupPolicy.encode(envelope)
        let decoded = try PawlyBackupPolicy.decode(data, for: "fixture")
        precondition((decoded["pets"] as? [[String: Any]])?.count == 1)
        precondition((try? PawlyBackupPolicy.decode(data, for: "another")) == nil)
        var broken = envelope
        broken["pets"] = [["petId": "pet", "shared": ["agenda": [["id": "", "data": [:]]]]]]
        let invalid = try PawlyBackupPolicy.encode(broken)
        precondition((try? PawlyBackupPolicy.decode(invalid, for: "fixture")) == nil)
        let current = [["id": "pet", "name": "New name"], ["id": "current", "name": "Roc"]]
        let imported = [profile, ["id": "missing", "name": "Milú"]]
        let merged = PawlyBackupPolicy.addingMissing(imported, to: current) { $0["id"] }
        precondition(merged == current + [imported[1]])
        precondition(PawlyBackupPolicy.addingMissing(imported, to: merged) { $0["id"] } == merged)
        print("Backup v2 fixture, validation, account boundaries and idempotent merge passed")
    }
}
