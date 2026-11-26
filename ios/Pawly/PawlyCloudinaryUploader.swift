import Foundation
import CryptoKit
import FirebaseAuth
import ImageIO
import UniformTypeIdentifiers
import FirebaseFirestore

struct PawlyCloudinaryUploader {
    private let setting: (String) -> String
    private let send: (URLRequest) async throws -> (Data, URLResponse)

    init(setting: @escaping (String) -> String = { Bundle.main.object(forInfoDictionaryKey: $0) as? String ?? "" },
         send: @escaping (URLRequest) async throws -> (Data, URLResponse) = { try await URLSession.shared.data(for: $0) }) {
        self.setting = setting
        self.send = send
    }

    static func indexID(for url: String) -> String {
        SHA256.hash(data: Data(url.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    func uploadImage(_ data: Data, folder: String, ownerUID: String) async throws -> String {
        let contract = try configuration(resource: "image", folder: folder, owner: ownerUID)
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 1600
              ] as CFDictionary) else { throw PawlyMediaError.invalidImage }
        let bytes = NSMutableData()
        guard let output = CGImageDestinationCreateWithData(bytes, UTType.jpeg.identifier as CFString, 1, nil) else {
            throw PawlyMediaError.invalidImage
        }
        CGImageDestinationAddImage(output, thumbnail, [kCGImageDestinationLossyCompressionQuality: 0.82] as CFDictionary)
        guard CGImageDestinationFinalize(output) else { throw PawlyMediaError.invalidImage }
        guard bytes.length <= 8 * 1024 * 1024 else { throw PawlyMediaError.imageTooLarge }
        return try await upload(contract, data: bytes as Data, filename: "photo.jpg", mime: "image/jpeg", owner: ownerUID)
    }

    func uploadVideo(_ data: Data, folder: String, ownerUID: String) async throws -> String {
        let contract = try configuration(resource: "video", folder: folder, owner: ownerUID)
        let format = try PawlyUploadContract.videoFormat(data)
        return try await upload(contract, data: data, filename: "video.\(format.ext)", mime: format.mime, owner: ownerUID)
    }

    private func configuration(resource: String, folder: String, owner: String) throws -> PawlyUploadContract {
        return try PawlyUploadContract(cloud: setting("CLOUDINARY_CLOUD_NAME"),
                                       preset: setting("CLOUDINARY_\(resource.uppercased())_UPLOAD_PRESET"),
                                       resource: resource, folder: folder, owner: owner)
    }

    private func upload(_ contract: PawlyUploadContract, data: Data, filename: String, mime: String, owner: String) async throws -> String {
        guard Auth.auth().currentUser?.uid == owner else { throw PawlyStoreError.notSignedIn }
        let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        let key = "\(contract.endpoint)/\(contract.folder)/\(contract.preset)/\(digest)"
        return try await PawlyUploadCache.shared.resolve(key: key) {
            let request = contract.request(data: data, filename: filename, mime: mime)
            let (responseData, response) = try await send(request)
            guard let http = response as? HTTPURLResponse else { throw PawlyMediaError.invalidResponse }
            guard (200..<300).contains(http.statusCode) else { throw PawlyMediaError.uploadFailed(http.statusCode) }
            return try contract.asset(from: responseData)
        } index: { asset in
            guard Auth.auth().currentUser?.uid == owner else { throw PawlyStoreError.notSignedIn }
            let recordKey = Self.indexID(for: asset.secure_url)
            let ref = Firestore.firestore().collection("cloudinaryAssets").document(recordKey)
            do {
                try await ref.setData(["uid": owner, "publicId": asset.public_id, "resourceType": contract.resource,
                                       "secureUrl": asset.secure_url, "createdAt": FieldValue.serverTimestamp()])
            } catch {
                // The owner index is create-only. A prior write may have committed before losing its response.
                guard let existing = try? await ref.getDocument(), existing.get("uid") as? String == owner,
                      existing.get("publicId") as? String == asset.public_id,
                      existing.get("secureUrl") as? String == asset.secure_url else { throw PawlyMediaError.indexFailed }
            }
        }
    }
}
