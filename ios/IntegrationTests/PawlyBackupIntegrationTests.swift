import XCTest
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
@testable import Pawly

final class PawlyBackupIntegrationTests: XCTestCase {
    @MainActor func testBackupRestoresMissingRecordsAndProtectsNewerProfile() async throws {
        // Refuse to write unless the host has explicitly selected the local demo project.
        guard FirebaseApp.app()?.options.projectID == "demo-pawly", PawlyApp.usesLocalEmulators else {
            throw XCTSkip("Run this test using the Pawly scheme and local Auth and Firestore emulators")
        }
        let session = PawlySession()
        try Auth.auth().signOut()
        let handle = "test" + UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12).lowercased()
        try await session.register(email: handle + "@example.test", password: "fixture-pawly-123", displayName: "Fixture", username: handle)
        let uid = try XCTUnwrap(Auth.auth().currentUser?.uid)
        defer { try? Auth.auth().signOut() }
        let user = Firestore.firestore().collection("users").document(uid)
        let item = user.collection("exploreCollections").document("fixture")
        try await item.setData(["name": "Fixture", "description": "", "entries": ["walk"], "updatedAt": FieldValue.serverTimestamp()])
        let place = PawlySavedPlace(id: "demo", name: "Park", subtitle: "", latitude: 0, longitude: 0, phone: nil)
        try await PawlySavedPlacesStore.restore([place], for: uid)
        let file = try await PawlyBackupManager.exportBackup()
        defer { try? FileManager.default.removeItem(at: file) }
        let data = try Data(contentsOf: file)
        let envelope = try PawlyBackupPolicy.decode(data, for: uid)
        XCTAssertEqual(envelope["version"] as? Int, 2)
        XCTAssertNil((envelope["profile"] as? [String: Any])?["email"])
        try await item.delete()
        try await user.updateData(["bio": "Changed after backup"])
        for _ in 0..<2 { _ = try await PawlyBackupManager.restoreBackup(from: data) }
        let restored = try await item.getDocument(source: .server)
        let profile = try await user.getDocument(source: .server)
        let places = try await PawlySavedPlacesStore.snapshot(for: uid)
        XCTAssertEqual(restored.get("entries") as? [String], ["walk"])
        XCTAssertEqual(profile.get("bio") as? String, "Changed after backup")
        XCTAssertEqual(places.map(\.id), ["demo"])
    }
}
