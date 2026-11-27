import AVFoundation
import CoreTransferable
import PhotosUI
import ImageIO
import SwiftUI

private struct PickedMovie: Transferable {
    let data: Data
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .movie) { received in
            let size = try received.file.resourceValues(forKeys: [.fileSizeKey]).fileSize
            guard let size, size <= 40 * 1024 * 1024 else { throw PawlyMediaError.videoTooLarge }
            return PickedMovie(data: try Data(contentsOf: received.file, options: .mappedIfSafe))
        }
    }
}

@MainActor final class PawlyAttachmentDraft: ObservableObject {
    @Published private(set) var thumbnail: UIImage?
    @Published private(set) var loading = false
    @Published private(set) var error: String?
    private let maximumDuration: Double?
    init(maximumDuration: Double? = nil) { self.maximumDuration = maximumDuration }
    private var data: Data?
    private var kind: PawlyCameraKind = .photo
    private var uploadedURL: String?
    private var uploadedScope: String?
    private var preparation: Task<Void, Never>?
    var hasAttachment: Bool { data != nil }
    func posterPhoto() async -> UIImage? {
        if kind == .video { return thumbnail }
        guard let data, let image = UIImage(data: data) else { return nil }
        return await image.byPreparingThumbnail(ofSize: CGSize(width: 1200, height: 1200))
    }

    func select(_ item: PhotosPickerItem) {
        let kind: PawlyCameraKind = item.supportedContentTypes.contains { $0.conforms(to: .movie) } ? .video : .photo
        prepare(kind) {
            if kind == .video {
                guard let movie = try await item.loadTransferable(type: PickedMovie.self) else { throw PawlyMediaError.unsupportedVideo }
                return movie.data
            }
            guard let bytes = try await item.loadTransferable(type: Data.self) else { throw PawlyMediaError.invalidImage }
            return bytes
        }
    }

    func capture(_ bytes: Data, kind: PawlyCameraKind) {
        prepare(kind) { bytes }
    }

    func clear() {
        preparation?.cancel()
        preparation = nil
        data = nil
        thumbnail = nil
        uploadedURL = nil
        uploadedScope = nil
        error = nil
        loading = false
    }

    private func prepare(_ newKind: PawlyCameraKind, bytes: @escaping () async throws -> Data) {
        clear()
        loading = true
        preparation = Task {
            do {
                let raw = try await bytes()
                try Task.checkCancellation()
                let preview = try await Self.preview(raw, kind: newKind, maximumDuration: maximumDuration)
                try Task.checkCancellation()
                data = raw
                kind = newKind
                thumbnail = preview
            } catch {
                guard !Task.isCancelled else { return }
                self.error = error.localizedDescription
            }
            loading = false
        }
    }

    nonisolated private static func preview(_ bytes: Data, kind: PawlyCameraKind, maximumDuration: Double?) async throws -> UIImage {
        if kind == .photo {
            guard let image = UIImage(data: bytes), let preview = await image.byPreparingThumbnail(ofSize: CGSize(width: 420, height: 300)) else {
                throw PawlyMediaError.invalidImage
            }
            return preview
        }
        let format = try PawlyUploadContract.videoFormat(bytes)
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathExtension(format.ext)
        try bytes.write(to: file, options: .atomic)
        defer { try? FileManager.default.removeItem(at: file) }
        let asset = AVURLAsset(url: file)
        if let maximumDuration {
            let seconds = try await asset.load(.duration).seconds
            guard seconds.isFinite, seconds > 0, seconds <= maximumDuration else { throw PawlyMediaError.videoTooLong }
        }
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 420, height: 300)
        let frame = try await generator.image(at: .zero)
        return UIImage(cgImage: frame.image)
    }

    func upload(owner: String, forPost: Bool) async throws -> (image: String, video: String) {
        try await upload(owner: owner, prefix: forPost ? "post" : "message")
    }
    func upload(owner: String, prefix: String) async throws -> (image: String, video: String) {
        guard let data else { return ("", "") }
        let scope = "\(owner)/\(prefix)"
        let url: String
        if let uploadedURL, uploadedScope == scope {
            url = uploadedURL
        } else {
            let uploader = PawlyCloudinaryUploader()
            if kind == .photo { url = try await uploader.uploadImage(data, folder: prefix + "-photos", ownerUID: owner) }
            else { url = try await uploader.uploadVideo(data, folder: prefix + "-videos", ownerUID: owner) }
            uploadedURL = url
            uploadedScope = scope
        }
        return kind == .photo ? (url, "") : ("", url)
    }
}

struct PawlyAttachmentPicker: View {
    @ObservedObject var draft: PawlyAttachmentDraft
    var allowsVideo = true
    var videoOnly = false
    @State private var item: PhotosPickerItem?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                PhotosPicker(selection: $item, matching: videoOnly ? .videos : (allowsVideo ? .any(of: [.images, .videos]) : .images)) {
                    Label(videoOnly ? "Elegir vídeo" : "Elegir de la galería", systemImage: videoOnly ? "film" : "photo.on.rectangle")
                }
                if !videoOnly { PawlyCameraButton { draft.capture($0, kind: .photo) } }
                if allowsVideo { PawlyCameraButton(kind: .video, label: "Grabar vídeo") { draft.capture($0, kind: .video) } }
                if draft.loading { ProgressView() }
                if draft.hasAttachment || draft.loading || draft.error != nil {
                    Spacer()
                    Button(role: .destructive) { draft.clear() } label: { Label("Quitar", systemImage: "trash") }
                }
            }
            .labelStyle(.iconOnly).buttonStyle(.borderless).controlSize(.large)
            if let image = draft.thumbnail {
                Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 110).accessibilityLabel("Archivo adjunto")
            }
            if let error = draft.error { Text(LocalizedStringKey(error)).font(.caption).foregroundStyle(.red) }
        }
        .onChange(of: item) { selection in
            if let selection { draft.select(selection); item = nil }
        }
    }
}

struct PawlyPhotoSelection: View {
    @Binding var gallery: [PhotosPickerItem]
    @Binding var camera: [Data]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                ForEach(gallery, id: \.self) { item in
                    thumbnail { try await item.loadTransferable(type: Data.self) } remove: {
                        gallery.removeAll { $0 == item }
                    }.id(item)
                }
                ForEach(Array(camera.enumerated()), id: \.offset) { index, bytes in
                    thumbnail { bytes } remove: { if camera.indices.contains(index) { camera.remove(at: index) } }
                        .id(bytes)
                }
            }
        }
    }

    private func thumbnail(load: @escaping () async throws -> Data?, remove: @escaping () -> Void) -> some View {
        ZStack(alignment: .topTrailing) {
            PawlyPhotoThumbnail(load: load)
            Button(action: remove) {
                Image(systemName: "xmark.circle.fill").symbolRenderingMode(.palette).foregroundStyle(.white, .black.opacity(0.72))
                    .frame(width: 44, height: 44, alignment: .topTrailing)
            }.buttonStyle(.borderless).accessibilityLabel("Quitar foto")
        }
    }
}

private struct PawlyPhotoThumbnail: View {
    let load: () async throws -> Data?
    @State private var preview: UIImage?

    var body: some View {
        Group {
            if let preview { Image(uiImage: preview).resizable().scaledToFill() }
            else { Image(systemName: "photo") }
        }
        .frame(width: 72, height: 72).clipped()
        .task {
            guard let bytes = try? await load(), !Task.isCancelled else { return }
            let thumbnail = await Self.image(from: bytes)
            if !Task.isCancelled { preview = thumbnail }
        }
    }

    nonisolated private static func image(from data: Data) async -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 144
              ] as CFDictionary) else { return nil }
        return UIImage(cgImage: image)
    }
}
