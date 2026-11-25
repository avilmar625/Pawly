import SwiftUI
import UIKit
import AVFoundation
import UniformTypeIdentifiers

enum PawlyCameraKind {
    case photo, video
}

struct PawlyCameraButton: View {
    var kind: PawlyCameraKind = .photo
    var label: String = "Hacer foto"
    var requestCapture: Binding<Bool> = .constant(false)
    var onCapture: (Data) -> Void
    @State private var requesting = false
    @State private var showing = false
    @State private var failure: String?

    var body: some View {
        Button { beginCapture()
        } label: {
            Label(LocalizedStringKey(label), systemImage:kind == .photo ? "camera" : "video")
        }
        .buttonStyle(.borderless)
        .disabled(requesting)
        .onChange(of:requestCapture.wrappedValue){requested in
            if requested { requestCapture.wrappedValue=false;beginCapture() }
        }
        .onAppear { if requestCapture.wrappedValue { requestCapture.wrappedValue=false;beginCapture() } }
        .fullScreenCover(isPresented:$showing) {
            PawlyCameraPicker(kind:kind) { result in
                showing = false
                switch result {
                case .success(let data): if let data { onCapture(data) }
                case .failure(let error): failure = error.localizedDescription
                }
            }.ignoresSafeArea()
        }
        .alert("Cámara",isPresented:Binding(get:{failure != nil},set:{if !$0 { failure=nil }})) {
            Button("Abrir ajustes") {
                if let url=URL(string:UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }
            Button("Cerrar",role:.cancel) { failure=nil }
        } message: { Text(failure ?? "") }
    }
    private func beginCapture() {
            let mediaType = kind == .photo ? UTType.image.identifier : UTType.movie.identifier
            guard UIImagePickerController.isSourceTypeAvailable(.camera),
                  UIImagePickerController.availableMediaTypes(for:.camera)?.contains(mediaType) == true else {
                failure = "La cámara no está disponible en este dispositivo. Puedes utilizar la galería."
                return
            }
            guard !requesting else { return }
            requesting = true
            Task { @MainActor in
                defer { requesting = false }
                guard await AVCaptureDevice.requestAccess(for: .video) else {
                    failure = "Permite el acceso a la cámara en Ajustes de iOS."
                    return
                }
                if kind == .video {
                    guard await AVCaptureDevice.requestAccess(for: .audio) else {
                        failure = "Permite el acceso al micrófono en Ajustes de iOS para grabar vídeo."
                        return
                    }
                }
                showing = true
            }
    }
}

private struct PawlyCameraPicker: UIViewControllerRepresentable {
    let kind: PawlyCameraKind
    let completion: (Result<Data?,Error>) -> Void

    func makeUIViewController(context:Context) -> UIImagePickerController {
        let picker=UIImagePickerController()
        picker.sourceType = .camera
        picker.mediaTypes = [kind == .photo ? UTType.image.identifier : UTType.movie.identifier]
        picker.delegate = context.coordinator
        if kind == .video {
            picker.videoQuality = .typeMedium
            picker.videoMaximumDuration = 60
            picker.cameraCaptureMode = .video
        } else { picker.cameraCaptureMode = .photo }
        return picker
    }
    func updateUIViewController(_ controller:UIImagePickerController,context:Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(completion:completion) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let completion: (Result<Data?,Error>) -> Void
        init(completion:@escaping (Result<Data?,Error>) -> Void) { self.completion=completion }
        func imagePickerControllerDidCancel(_ picker:UIImagePickerController) { completion(.success(nil)) }
        func imagePickerController(_ picker:UIImagePickerController,didFinishPickingMediaWithInfo info:[UIImagePickerController.InfoKey:Any]) {
            do {
                if let image=info[.originalImage] as? UIImage, let data=image.jpegData(compressionQuality:0.9) {
                    completion(.success(data))
                } else if let url=info[.mediaURL] as? URL {
                    let size=try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
                    guard size <= 40*1024*1024 else { throw PawlyMediaError.videoTooLarge }
                    completion(.success(try Data(contentsOf:url, options:.mappedIfSafe)))
                } else { throw PawlyMediaError.invalidImage }
            } catch { completion(.failure(error)) }
        }
    }
}
