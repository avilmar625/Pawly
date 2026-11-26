import XCTest
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
@testable import Pawly

final class PawlyChatIntegrationTests: XCTestCase {
    // The emulator accepts a local fixture credential for setup. The store always uses the signed-in SDK.
    private func seed(_ documents: [(String, [String: Any])]) async throws {
        let base = "projects/demo-pawly/databases/(default)/documents"
        let url = try XCTUnwrap(URL(string: "http://127.0.0.1:8080/v1/" + base + ":commit"))
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer owner", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let writes = documents.map { path, fields in ["update": ["name": base + "/" + path, "fields": fields]] }
        request.httpBody = try JSONSerialization.data(withJSONObject: ["writes": writes])
        let (_, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
    }

    @MainActor func testOlderChatMessagesAndLiveReplacementAtWindowLimit() async throws {
        guard FirebaseApp.app()?.options.projectID == "demo-pawly", PawlyApp.usesLocalEmulators else {
            throw XCTSkip("Run with local Auth and Firestore emulators")
        }
        let uid = try await Auth.auth().signInAnonymously().user.uid
        defer { try? Auth.auth().signOut() }
        let id = UUID().uuidString
        let path = "conversations/" + id
        func message(_ n: Int) -> (String, [String: Any]) {
            (path + "/messages/m" + String(n), [
                "createdAt": ["timestampValue": "2026-01-01T00:00:0" + String(n) + "Z"],
                "senderId": ["stringValue": uid], "text": ["stringValue": String(n)]
            ])
        }
        try await seed([(path, ["participants": ["arrayValue": ["values": [
            ["stringValue": uid], ["stringValue": "other-fixture"]
        ]]]])] + (1...3).map(message))
        let store = PawlyCommunityStore()
        defer { store.stopMessages() }
        let first = expectation(description: "Only the two latest messages are shown")
        let replaced = expectation(description: "A fourth message replaces the oldest visible one")
        store.listenMessages(conversationID: id, limit: 2) { result in
            switch result {
            case .success(let (page, more)) where more:
                switch page.map(\.id) {
                case ["m2", "m3"]: first.fulfill()
                case ["m3", "m4"]: replaced.fulfill()
                default: break
                }
            case .failure(let error): XCTFail(error.localizedDescription)
            default: break
            }
        }
        await fulfillment(of: [first], timeout: 15)
        try await seed([message(4)])
        await fulfillment(of: [replaced], timeout: 15)
        let expanded = expectation(description: "The whole history is returned in display order")
        store.listenMessages(conversationID: id, limit: 4) { result in
            switch result {
            case .success(let (page, more)) where page.map(\.id) == ["m1", "m2", "m3", "m4"]:
                XCTAssertFalse(more)
                expanded.fulfill()
            case .failure(let error): XCTFail(error.localizedDescription)
            default: break
            }
        }
        await fulfillment(of: [expanded], timeout: 15)
    }
}
