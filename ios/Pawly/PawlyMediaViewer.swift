import AVKit
import ImageIO
import SwiftUI

enum PawlyMediaItem: Hashable {
    case photo(URL), video(URL)
    var url: URL { switch self { case .photo(let url), .video(let url): return url } }
    static func photos(_ values: [String]) -> [Self] {
        values.compactMap { URL(string: $0) }.filter { $0.scheme == "https" }.map(Self.photo)
    }
}

extension View {
    func pawlyPhoto(_ url: URL?, gallery: [String] = []) -> some View {
        modifier(PawlyMediaLink(item: url.map(PawlyMediaItem.photo), items: PawlyMediaItem.photos(gallery)))
    }
}

private struct PawlyMediaLink: ViewModifier {
    let item: PawlyMediaItem?
    let items: [PawlyMediaItem]
    @State private var showing = false

    @ViewBuilder func body(content: Content) -> some View {
        if let item, item.url.scheme == "https" || item.url.isFileURL {
            Button { showing = true } label: { content.contentShape(Rectangle()) }
                .buttonStyle(.borderless).accessibilityLabel(item.isVideo ? "Reproducir vídeo" : "Abrir fotografía")
                .fullScreenCover(isPresented: $showing) {
                    PawlyMediaViewer(items: items.contains(item) ? items : [item], initial: item)
                }
        } else { content }
    }
}

private extension PawlyMediaItem {
    var isVideo: Bool { if case .video = self { return true }; return false }
}

struct PawlyVideoPreview: View {
    let url: URL
    var body: some View {
        ZStack {
            Color.black
            Image(systemName: "play.circle.fill").font(.system(size: 48)).foregroundStyle(.white)
        }.modifier(PawlyMediaLink(item: .video(url), items: []))
    }
}

private struct PawlyMediaViewer: View {
    let items: [PawlyMediaItem]
    @State private var index: Int
    @Environment(\.dismiss) private var dismiss
    init(items: [PawlyMediaItem], initial: PawlyMediaItem) {
        self.items = items
        _index = State(initialValue: items.firstIndex(of: initial) ?? 0)
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            TabView(selection: $index) {
                ForEach(items.indices, id: \.self) { i in
                    Group {
                        switch items[i] {
                        case .photo(let url):
                            if abs(index - i) <= 1 { PawlyFullPhoto(url: url) }
                        case .video(let url):
                            if i == index { PawlyFullVideo(url: url) }
                        }
                    }.tag(i)
                }
            }.tabViewStyle(.page(indexDisplayMode: items.count > 1 ? .always : .never))
            Button { dismiss() } label: {
                Image(systemName: "xmark.circle.fill").font(.title).foregroundStyle(.white, .black.opacity(0.7))
                    .frame(width: 48, height: 48)
            }.accessibilityLabel("Cerrar").padding(6)
        }.preferredColorScheme(.dark)
    }
}

private struct PawlyFullVideo: View {
    let url: URL
    @State private var player: AVPlayer?
    @State private var failed = false
    var body: some View {
        Group {
            if failed { Button("Reintentar") { start() } }
            else if let player, let item = player.currentItem {
                VideoPlayer(player: player)
                    .onReceive(item.publisher(for: \.status)) { status in
                        failed = status == .failed
                        if status == .readyToPlay { player.play() }
                    }
            } else { ProgressView() }
        }
        .onAppear { start() }
        .onDisappear { player?.pause(); player?.replaceCurrentItem(with: nil); player = nil }
    }

    private func start() {
        player?.pause()
        failed = false
        player = AVPlayer(url: url)
    }
}

private struct PawlyFullPhoto: View {
    let url: URL
    @State private var image: UIImage?
    @State private var failed = false
    @State private var attempt = 0
    var body: some View {
        Group {
            if let image { ZoomPhoto(image: image) }
            else if failed { Button("Reintentar") { attempt += 1 } }
            else { ProgressView() }
        }
        .task(id: attempt) {
            failed = false
            do {
                let file: URL
                if url.isFileURL { file = url }
                else {
                    let (download, response) = try await URLSession.shared.download(from: url)
                    guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode)
                    else { throw URLError(.badServerResponse) }
                    file = download
                }
                defer { if !url.isFileURL { try? FileManager.default.removeItem(at: file) } }
                let decoded = await Self.decode(file)
                try Task.checkCancellation()
                image = decoded
                failed = decoded == nil
            } catch { if !Task.isCancelled { failed = true } }
        }
    }

    nonisolated private static func decode(_ file: URL) async -> UIImage? {
        guard let source = CGImageSourceCreateWithURL(file as CFURL, nil),
              let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 3000
              ] as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }
}

private struct ZoomPhoto: UIViewRepresentable {
    let image: UIImage
    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator: NSObject, UIScrollViewDelegate {
        let imageView = UIImageView()
        func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }
        func scrollViewDidZoom(_ scrollView: UIScrollView) {
            scrollView.panGestureRecognizer.isEnabled = scrollView.zoomScale > 1.01
        }
    }
    func makeUIView(context: Context) -> UIScrollView {
        let view = UIScrollView()
        view.maximumZoomScale = 5
        view.contentInsetAdjustmentBehavior = .never
        view.showsHorizontalScrollIndicator = false
        view.showsVerticalScrollIndicator = false
        view.panGestureRecognizer.isEnabled = false
        view.delegate = context.coordinator
        let photo = context.coordinator.imageView
        photo.contentMode = .scaleAspectFit
        photo.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(photo)
        NSLayoutConstraint.activate([
            photo.leadingAnchor.constraint(equalTo: view.contentLayoutGuide.leadingAnchor),
            photo.topAnchor.constraint(equalTo: view.contentLayoutGuide.topAnchor),
            photo.trailingAnchor.constraint(equalTo: view.contentLayoutGuide.trailingAnchor),
            photo.bottomAnchor.constraint(equalTo: view.contentLayoutGuide.bottomAnchor),
            photo.widthAnchor.constraint(equalTo: view.frameLayoutGuide.widthAnchor),
            photo.heightAnchor.constraint(equalTo: view.frameLayoutGuide.heightAnchor)
        ])
        return view
    }
    func updateUIView(_ view: UIScrollView, context: Context) {
        if context.coordinator.imageView.image !== image {
            view.setZoomScale(1, animated: false)
            context.coordinator.imageView.image = image
        }
    }
}
