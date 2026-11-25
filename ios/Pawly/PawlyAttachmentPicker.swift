import AVFoundation
import CoreTransferable
import PhotosUI
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
    private var data: Data?
    private var kind: PawlyCameraKind = .photo
    private var uploadedURL: String?
    private var uploadedScope: String?
    private var preparation: Task<Void, Never>?
    var hasAttachment: Bool { data != nil }

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
                let preview = try await Self.preview(raw, kind: newKind)
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

    nonisolated private static func preview(_ bytes: Data, kind: PawlyCameraKind) async throws -> UIImage {
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
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: file))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 420, height: 300)
        let frame = try await generator.image(at: .zero)
        return UIImage(cgImage: frame.image)
    }

    func upload(owner: String, forPost: Bool) async throws -> (image: String, video: String) {
        guard let data else { return ("", "") }
        let prefix = forPost ? "post" : "message"
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
    @State private var item: PhotosPickerItem?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                PhotosPicker(selection: $item, matching: .any(of: [.images, .videos])) {
                    Label("Elegir de la galería", systemImage: "photo.on.rectangle")
                }
                PawlyCameraButton { draft.capture($0, kind: .photo) }
                PawlyCameraButton(kind: .video, label: "Grabar vídeo") { draft.capture($0, kind: .video) }
                if draft.loading { ProgressView() }
                if draft.hasAttachment || draft.loading {
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

struct DiaryPhotoThumbnail: View {
    let item: PhotosPickerItem
    @State private var thumbnail: UIImage?
    var body: some View {
        Group {
            if let thumbnail { Image(uiImage: thumbnail).resizable().scaledToFill() }
            else { Image(systemName: "photo") }
        }
        .frame(width: 64, height: 64).clipped()
        .task(id: item) {
            guard let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else { return }
            let preview = await image.byPreparingThumbnail(ofSize: CGSize(width: 128, height: 128))
            if !Task.isCancelled { thumbnail = preview }
        }
    }
}
