import Foundation
import ImageIO
import UniformTypeIdentifiers
import FirebaseFirestore

struct PawlyCloudinaryUploader {
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
        func setting(_ key: String) -> String { Bundle.main.object(forInfoDictionaryKey: key) as? String ?? "" }
        return try PawlyUploadContract(cloud: setting("CLOUDINARY_CLOUD_NAME"),
                                       preset: setting("CLOUDINARY_\(resource.uppercased())_UPLOAD_PRESET"),
                                       resource: resource, folder: folder, owner: owner)
    }

    private func upload(_ contract: PawlyUploadContract, data: Data, filename: String, mime: String, owner: String) async throws -> String {
        let request = contract.request(data: data, filename: filename, mime: mime)
        let (responseData, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw PawlyMediaError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else { throw PawlyMediaError.uploadFailed(http.statusCode) }
        let asset = try contract.asset(from: responseData)
        let index = Firestore.firestore().collection("cloudinaryAssets").document()
        do {
            try await index.setData(["uid": owner, "publicId": asset.public_id, "resourceType": contract.resource,
                                     "secureUrl": asset.secure_url, "createdAt": FieldValue.serverTimestamp()])
        } catch {
            throw PawlyMediaError.indexFailed
        }
        return asset.secure_url
    }
}
