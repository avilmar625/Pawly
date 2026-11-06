import Foundation
import UIKit

struct PawlyCloudinaryUploader {
    private var cloudName: String { Bundle.main.object(forInfoDictionaryKey:"CLOUDINARY_CLOUD_NAME") as? String ?? "" }
    private var imagePreset: String { Bundle.main.object(forInfoDictionaryKey:"CLOUDINARY_IMAGE_UPLOAD_PRESET") as? String ?? "" }
    private var videoPreset: String { Bundle.main.object(forInfoDictionaryKey:"CLOUDINARY_VIDEO_UPLOAD_PRESET") as? String ?? "" }

    func uploadImage(_ data: Data, folder: String, ownerUID: String) async throws -> String {
        guard let image=UIImage(data:data) else { throw PawlyMediaError.invalidImage }
        let longest=max(image.size.width*image.scale,image.size.height*image.scale)
        let factor=min(1,1600/max(longest,1))
        let target=CGSize(width:image.size.width*factor,height:image.size.height*factor)
        let format=UIGraphicsImageRendererFormat(); format.scale=1
        let resized=UIGraphicsImageRenderer(size:target,format:format).image { _ in image.draw(in:CGRect(origin:.zero,size:target)) }
        guard let jpeg=resized.jpegData(compressionQuality:0.82) else { throw PawlyMediaError.invalidImage }
        guard jpeg.count <= 8*1024*1024 else { throw PawlyMediaError.imageTooLarge }
        return try await upload(bytes:jpeg,filename:"pawly-image.jpg",mime:"image/jpeg",resource:"image",
                                preset:imagePreset,folder:folder,ownerUID:ownerUID)
    }
    func uploadVideo(_ data: Data, folder: String, ownerUID: String) async throws -> String {
        guard data.count <= 40*1024*1024 else { throw PawlyMediaError.videoTooLarge }
        return try await upload(bytes:data,filename:"pawly-video.mov",mime:"video/quicktime",resource:"video",
                                preset:videoPreset,folder:folder,ownerUID:ownerUID)
    }
    private func upload(bytes:Data,filename:String,mime:String,resource:String,preset:String,folder:String,ownerUID:String) async throws -> String {
        guard !cloudName.isEmpty,!preset.isEmpty else { throw PawlyMediaError.notConfigured }
        guard cloudName.range(of:"^[A-Za-z0-9_-]+$",options:.regularExpression) != nil else { throw PawlyMediaError.invalidConfiguration }
        let allowed = resource == "image" ? ["profile-photos","dog-photos","pet-photos","post-photos","message-photos"] : ["post-videos","message-videos"]
        guard allowed.contains(folder) else { throw PawlyMediaError.invalidFolder }
        let boundary="pawly-\(UUID().uuidString)"
        var body=Data()
        func field(_ name:String,_ value:String) {
            body.append("--\(boundary)\r\n".data(using:.utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using:.utf8)!)
            body.append("\(value)\r\n".data(using:.utf8)!)
        }
        field("upload_preset",preset)
        field("folder","pawly/\(folder)/\(ownerUID)")
        body.append("--\(boundary)\r\n".data(using:.utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".data(using:.utf8)!)
        body.append("Content-Type: \(mime)\r\n\r\n".data(using:.utf8)!)
        body.append(bytes)
        body.append("\r\n--\(boundary)--\r\n".data(using:.utf8)!)
        var request=URLRequest(url:URL(string:"https://api.cloudinary.com/v1_1/\(cloudName)/\(resource)/upload")!)
        request.httpMethod="POST"
        request.setValue("multipart/form-data; boundary=\(boundary)",forHTTPHeaderField:"Content-Type")
        request.httpBody=body
        request.timeoutInterval=360
        let (responseData,response)=try await URLSession.shared.data(for:request)
        guard let http=response as? HTTPURLResponse,(200..<300).contains(http.statusCode) else {
            let message=(try? JSONSerialization.jsonObject(with:responseData) as? [String:Any])?["error"] as? [String:Any]
            throw PawlyMediaError.uploadFailed(message?["message"] as? String ?? "Cloudinary rechazó el archivo.")
        }
        let json=try JSONSerialization.jsonObject(with:responseData) as? [String:Any]
        guard let url=json?["secure_url"] as? String,url.hasPrefix("https://") else { throw PawlyMediaError.invalidResponse }
        return url
    }
}
enum PawlyMediaError:LocalizedError {
    case notConfigured,invalidConfiguration,invalidFolder,invalidImage,imageTooLarge,videoTooLarge,invalidResponse
    case uploadFailed(String)
    var errorDescription:String? {
        switch self {
        case .notConfigured:return "Configura el Cloud name y los presets de Cloudinary en los ajustes de compilación de iOS."
        case .invalidConfiguration:return "El Cloud name de Cloudinary no es válido."
        case .invalidFolder:return "La carpeta de subida no está permitida."
        case .invalidImage:return "No se ha podido procesar la imagen."
        case .imageTooLarge:return "La imagen supera 8 MB después de comprimirla."
        case .videoTooLarge:return "El vídeo supera el límite de 40 MB."
        case .invalidResponse:return "Cloudinary no ha devuelto una URL HTTPS válida."
        case .uploadFailed(let message):return message
        }
    }
}
