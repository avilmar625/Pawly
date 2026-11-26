import XCTest
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import UIKit
@testable import Pawly

final class PawlyMediaIntegrationTests: XCTestCase {
    @MainActor func testPreparedImageAndVideoReceiveSeparateOwnerIndexes() async throws {
        guard FirebaseApp.app()?.options.projectID == "demo-pawly", PawlyApp.usesLocalEmulators else {
            throw XCTSkip("This test requires local Firebase emulators")
        }
        let uid = try await Auth.auth().signInAnonymously().user.uid
        defer { try? Auth.auth().signOut() }
        let uploader = PawlyCloudinaryUploader(setting: { _ in "fixture" }) { request in
            let endpoint = try XCTUnwrap(request.url)
            let kind = endpoint.pathComponents[3]
            let image = kind == "image"
            let path = "pawly/" + (image ? "post-photos/" : "message-videos/") + uid + "/fixture"
            let url = "https://res.cloudinary.com/fixture/" + kind + "/upload/v1/" + path + (image ? ".jpg" : ".mp4")
            let body = try XCTUnwrap(request.httpBody)
            let content = String(decoding: body, as: UTF8.self)
            XCTAssertTrue(content.contains(image ? "image/jpeg" : "video/mp4"))
            XCTAssertTrue(content.contains(String(path.dropLast("/fixture".count))))
            XCTAssertFalse(content.contains("name=\"public_id\""))
            let response = try JSONSerialization.data(withJSONObject: ["public_id": path, "resource_type": kind, "secure_url": url])
            return (response, try XCTUnwrap(HTTPURLResponse(url: endpoint, statusCode: 200, httpVersion: nil, headerFields: nil)))
        }
        let sourceImage = UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24)).pngData { context in
            UIColor.green.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 24, height: 24))
        }
        let image = try await uploader.uploadImage(sourceImage, folder: "post-photos", ownerUID: uid)
        let video = try await uploader.uploadVideo(Data("0000ftypisom".utf8), folder: "message-videos", ownerUID: uid)
        for (url, kind) in [(image, "image"), (video, "video")] {
            let index = Firestore.firestore().collection("cloudinaryAssets").document(PawlyCloudinaryUploader.indexID(for: url))
            let saved = try await index.getDocument(source: .server)
            XCTAssertEqual(saved.get("uid") as? String, uid)
            XCTAssertEqual(saved.get("resourceType") as? String, kind)
            XCTAssertEqual(saved.get("secureUrl") as? String, url)
        }
    }
}
