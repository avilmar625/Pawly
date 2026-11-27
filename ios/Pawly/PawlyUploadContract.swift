import Foundation

enum PawlyMediaError: LocalizedError {
    case notConfigured, invalidConfiguration, invalidFolder, invalidImage, unsupportedVideo
    case imageTooLarge, videoTooLong, videoTooLarge, invalidResponse, indexFailed, uploadFailed(Int)

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "Falta configurar Cloudinary o el preset de subida."
        case .invalidConfiguration: return "El nombre de Cloudinary no es válido."
        case .invalidFolder: return "La carpeta de subida no está permitida."
        case .invalidImage: return "No se ha podido leer la imagen seleccionada."
        case .unsupportedVideo: return "Selecciona un vídeo MOV, MP4, WebM o 3GP."
        case .imageTooLarge: return "La imagen comprimida supera los 8 MB."
        case .videoTooLong: return "El vídeo debe durar como máximo 60 segundos."
        case .videoTooLarge: return "El vídeo supera el límite de 40 MB."
        case .invalidResponse: return "Cloudinary ha devuelto un recurso fuera de la carpeta esperada."
        case .indexFailed: return "El archivo ha llegado a Cloudinary, pero no se ha podido registrar su propietario."
        case .uploadFailed(let code): return "Cloudinary ha rechazado la subida (HTTP \(code)). Comprueba el preset y el formato."
        }
    }
}

struct PawlyUploadContract {
    let endpoint: URL
    private let cloudName: String
    let folder: String
    let preset: String
    let resource: String

    init(cloud: String, preset: String, resource: String, folder: String, owner: String) throws {
        let cloud = cloud.trimmingCharacters(in: .whitespacesAndNewlines)
        self.cloudName = cloud
        self.preset = preset.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cloud.isEmpty, !self.preset.isEmpty, !self.preset.contains("$(") else { throw PawlyMediaError.notConfigured }
        guard cloud.range(of: #"^[a-zA-Z0-9_-]+$"#, options: .regularExpression) != nil,
              let endpoint = URL(string: "https://api.cloudinary.com/v1_1/\(cloud)/\(resource)/upload") else {
            throw PawlyMediaError.invalidConfiguration
        }
        let imageFolders = ["profile-photos", "dog-photos", "pet-photos", "post-photos", "message-photos", "diary-photos"]
        let videoFolders = ["post-videos", "message-videos"]
        guard (resource == "image" && imageFolders.contains(folder)) || (resource == "video" && videoFolders.contains(folder)),
              !owner.isEmpty, !owner.contains("/"), !owner.contains("\r"), !owner.contains("\n") else {
            throw PawlyMediaError.invalidFolder
        }
        self.endpoint = endpoint
        self.resource = resource
        self.folder = "pawly/\(folder)/\(owner)"
    }

    // Match Android's unsigned presets. Cloudinary allocates the public ID.
    func request(data: Data, filename: String, mime: String) -> URLRequest {
        let boundary = "pawly-\(UUID().uuidString)"
        var body = Data()
        for (key, value) in [("upload_preset", preset), ("folder", folder)] {
            body.append(contentsOf: "--\(boundary)\r\nContent-Disposition: form-data; name=\"\(key)\"\r\n\r\n\(value)\r\n".utf8)
        }
        body.append(contentsOf: "--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\nContent-Type: \(mime)\r\n\r\n".utf8)
        body.append(data)
        body.append(contentsOf: "\r\n--\(boundary)--\r\n".utf8)
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        request.timeoutInterval = 360
        return request
    }

    struct Asset: Decodable {
        let public_id: String
        let secure_url: String
        let resource_type: String
    }

    func asset(from data: Data) throws -> Asset {
        guard let asset = try? JSONDecoder().decode(Asset.self, from: data),
              asset.resource_type == resource, asset.public_id.hasPrefix(folder + "/"),
              asset.public_id.count > folder.count + 1, asset.public_id.count <= 255,
              let url = URL(string: asset.secure_url), url.scheme == "https",
              url.host?.lowercased() == "res.cloudinary.com", url.port == nil, url.user == nil, url.password == nil,
              url.path.hasPrefix("/" + cloudName + "/" + resource + "/upload/"),
              url.path.hasSuffix("/" + asset.public_id) || url.deletingPathExtension().path.hasSuffix("/" + asset.public_id) else {
            throw PawlyMediaError.invalidResponse
        }
        return asset
    }

    static func videoFormat(_ data: Data) throws -> (ext: String, mime: String) {
        guard data.count <= 40 * 1024 * 1024 else { throw PawlyMediaError.videoTooLarge }
        if data.starts(with: [0x1a, 0x45, 0xdf, 0xa3]) { return ("webm", "video/webm") }
        guard data.count >= 12, String(decoding: data.dropFirst(4).prefix(4), as: UTF8.self) == "ftyp" else {
            throw PawlyMediaError.unsupportedVideo
        }
        let brand = String(decoding: data.dropFirst(8).prefix(4), as: UTF8.self)
        if brand == "qt  " { return ("mov", "video/quicktime") }
        if brand.hasPrefix("3gp") { return ("3gp", "video/3gpp") }
        return ("mp4", "video/mp4")
    }
}

enum PawlyVideoPoster {
    static func url(for video: URL) -> URL? {
        guard var components = URLComponents(url: video, resolvingAgainstBaseURL: false),
              components.scheme == "https", components.host == "res.cloudinary.com",
              components.user == nil, components.password == nil, components.query == nil else { return nil }
        var pieces = components.percentEncodedPath.split(separator: "/").map(String.init)
        guard pieces.count >= 4, pieces[1] == "video", pieces[2] == "upload",
              !pieces[3].hasPrefix("s--"), let file = pieces.last else { return nil }
        let base = (file as NSString).deletingPathExtension
        pieces[pieces.count - 1] = base + ".jpg"
        pieces.insert("c_limit,w_640,q_auto", at: 3)
        components.percentEncodedPath = "/" + pieces.joined(separator: "/")
        return components.url
    }
}
