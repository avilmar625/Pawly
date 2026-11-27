import Foundation

@main enum UploadContractTests {
    static func main() throws {
        let contract = try PawlyUploadContract(cloud: " cloud ", preset: " images ", resource: "image", folder: "post-photos", owner: "owner")
        let body = String(decoding: contract.request(data: Data([0]), filename: "a.jpg", mime: "image/jpeg").httpBody!, as: UTF8.self)
        precondition(body.contains("name=\"folder\"\r\n\r\npawly/post-photos/owner"))
        precondition(!body.contains("name=\"public_id\"") && body.contains("name=\"upload_preset\"\r\n\r\nimages\r\n"))
        func response(_ owner: String, type: String = "image", url: String = "https://res.cloudinary.com/cloud/image/upload/v1/pawly/post-photos/owner/asset.jpg") -> Data {
            try! JSONSerialization.data(withJSONObject: ["public_id": "pawly/post-photos/\(owner)/asset", "resource_type": type, "secure_url": url])
        }
        let asset = try contract.asset(from: response("owner"))
        precondition(asset.public_id == "pawly/post-photos/owner/asset")
        for data in [response("owner2"), response("owner", type: "video"), response("owner", url: "http://example.org"),
                     response("owner", url: "https://example.org/cloud/image/upload/pawly/post-photos/owner/asset.jpg"),
                     response("owner", url: "https://res.cloudinary.com/other/image/upload/pawly/post-photos/owner/asset.jpg"), Data()] {
            precondition((try? contract.asset(from: data)) == nil)
        }
        for folder in ["diary-videos", "pet-videos"] {
            let video = try PawlyUploadContract(cloud: "cloud", preset: "videos", resource: "video", folder: folder, owner: "owner")
            precondition(video.folder == "pawly/" + folder + "/owner")
            precondition((try? PawlyUploadContract(cloud: "cloud", preset: "images", resource: "image", folder: folder, owner: "owner")) == nil)
        }
        let movie = URL(string: "https://res.cloudinary.com/cloud/video/upload/v1/pawly/post-videos/a/clip.mp4")!
        precondition(PawlyVideoPoster.url(for: movie)?.path == "/cloud/video/upload/c_limit,w_640,q_auto/v1/pawly/post-videos/a/clip.jpg")
        precondition(PawlyVideoPoster.url(for: URL(string: "https://res.cloudinary.com/cloud/video/upload/s--private--/clip.mp4")!) == nil)
        for (brand, ext) in [("qt  ", "mov"), ("isom", "mp4"), ("3gp4", "3gp")] {
            let format = try PawlyUploadContract.videoFormat(Data(("0000ftyp" + brand).utf8))
            precondition(format.ext == ext)
        }
        precondition((try? PawlyUploadContract.videoFormat(Data())) == nil)
        precondition((try? PawlyUploadContract(cloud: "test", preset: "", resource: "image", folder: "post-photos", owner: "id")) == nil)
        print("Cloudinary request, response ownership and video types passed")
    }
}
