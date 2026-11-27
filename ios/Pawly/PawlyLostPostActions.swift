import SwiftUI
import CoreLocation
import FirebaseAuth
import FirebaseFirestore

extension PawlyFeedPost {
    var publishedArea: String {
        for line in text.components(separatedBy: .newlines) {
            if line.lowercased().hasPrefix("zona:") {
                return String(line.dropFirst(5)).trimmingCharacters(in: .whitespaces)
            }
        }
        return ""
    }
}

struct PawlyLostPostActions: View {
    let post: PawlyFeedPost
    var onEdited: (PawlyFeedPost) -> Void
    @State private var showMap = false
    @State private var editing = false
    var body: some View {
        HStack {
            Button { showMap = true } label: { Label("Localizar la zona del aviso", systemImage: "map") }
            if Auth.auth().currentUser?.uid == post.authorId {
                Spacer()
                Button { editing = true } label: { Label("Editar aviso", systemImage: "pencil") }
            }
        }.font(.caption).buttonStyle(.borderless)
            .sheet(isPresented: $showMap) { PawlyPublishedArea(post: post) }
            .sheet(isPresented: $editing) { PawlyLostPostEditor(post: post, onEdited: onEdited) }
    }
}

private struct PawlyPublishedArea: View {
    @Environment(\.dismiss) private var dismiss
    let post: PawlyFeedPost
    @State private var point: CLLocationCoordinate2D?
    @State private var message: String?
    var body: some View {
        NavigationStack {
            VStack {
                if let point = post.publishedPoint ?? point { PawlyLostAreaMap(coordinate: point, name: post.publishedArea.isEmpty ? post.petName : post.publishedArea) }
                else if let message { Text(LocalizedStringKey(message)) } else { ProgressView() }
                if post.publishedPoint == nil, point == nil, !post.publishedArea.isEmpty {
                    Text(verbatim: post.publishedArea)
                    if let link = Self.mapsSearch(post.publishedArea) {
                        Link(destination: link) { Label("Buscar en el mapa", systemImage: "map") }
                    }
                }
            }.padding().navigationTitle("Ubicación aproximada del aviso")
                .toolbar { Button("Cerrar") { dismiss() } }.task { await locate() }
        }.presentationDetents([.medium, .large])
    }
    private static func mapsSearch(_ query: String) -> URL? {
        var link = URLComponents(); link.scheme = "https"; link.host = "maps.apple.com"; link.path = "/"
        link.queryItems = [URLQueryItem(name: "q", value: query)]
        return link.url
    }
    private func locate() async {
        if post.publishedPoint != nil { return }
        if let known = PawlyMapPoint.parse(post.publishedArea) { point = PawlyMapPoint.approximate(known); return }
        do {
            if !post.areaChanged, Auth.auth().currentUser?.isEmailVerified == true,
               let alert = try? await Firestore.firestore().collection("lostPetAlerts").document(post.id).getDocument(),
               let lat = alert.get("latCell") as? Int, let lon = alert.get("lonCell") as? Int,
               (-9000...9000).contains(lat), (-18000...18000).contains(lon) {
                point = CLLocationCoordinate2D(latitude: Double(lat)/100, longitude: Double(lon)/100); return
            }
            if !post.publishedArea.isEmpty, let coordinate = try await CLGeocoder().geocodeAddressString(post.publishedArea).first?.location?.coordinate {
                point = .init(latitude: (coordinate.latitude * 100).rounded()/100, longitude: (coordinate.longitude * 100).rounded()/100)
            } else { message = "No hay una ubicación disponible para este aviso." }
        } catch { message = "No se ha podido localizar la zona. Prueba con una dirección o municipio más concreto." }
    }
}

private struct PawlyLostPostEditor: View {
    @Environment(\.dismiss) private var dismiss
    let post: PawlyFeedPost
    let onEdited: (PawlyFeedPost) -> Void
    @StateObject private var attachment = PawlyAttachmentDraft(maximumDuration: 60)
    @State private var text: String
    @State private var retain = true
    @State private var saving = false
    @State private var error: String?
    init(post: PawlyFeedPost, onEdited: @escaping (PawlyFeedPost) -> Void) {
        self.post = post; self.onEdited = onEdited; _text = State(initialValue: post.text)
    }
    var body: some View {
        NavigationStack {
            Form {
                TextField("Datos del aviso", text: $text, axis: .vertical).lineLimit(4...12)
                Section("Foto o vídeo") {
                    if retain, !attachment.hasAttachment, !post.imageURL.isEmpty || !post.videoURL.isEmpty {
                        if !post.imageURL.isEmpty, let url = URL(string: post.imageURL) {
                            AsyncImage(url: url) { $0.resizable().scaledToFit() } placeholder: { ProgressView() }.frame(maxHeight: 180).pawlyPhoto(url)
                        }
                        if !post.videoURL.isEmpty, let url = URL(string: post.videoURL) { PawlyVideoPreview(url: url).frame(height: 160) }
                        Button("Quitar", role: .destructive) { retain = false }
                    }
                    PawlyAttachmentPicker(draft: attachment)
                }
                if saving { ProgressView() }
                if let error { Text(LocalizedStringKey(error)).foregroundStyle(.red) }
            }.navigationTitle("Editar aviso").interactiveDismissDisabled(saving).disabled(saving)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() }.disabled(saving) }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Guardar") { Task { await save() } }.disabled(saving || attachment.loading || attachment.error != nil
                            || text.count > 2000 || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
        }
    }
    @MainActor private func save() async {
        guard !saving, let user = Auth.auth().currentUser, user.uid == post.authorId else { return }
        saving = true; defer { saving = false }
        do {
            var updated = post
            updated.text = text.trimmingCharacters(in: .whitespacesAndNewlines)
            updated.areaChanged = post.areaChanged || updated.publishedArea != post.publishedArea
            var data: [String: Any] = ["text": updated.text, "lostAreaEdited": updated.areaChanged,
                "searchTokens": PawlyFeedFilter.tokens([updated.text, post.authorName, post.petName, post.category])]
            if attachment.hasAttachment || !retain {
                let media = try await attachment.upload(owner: user.uid, forPost: true)
                updated.imageURL = media.image; updated.videoURL = media.video
                data["imageUrl"] = media.image; data["videoUrl"] = media.video
                if media.image.isEmpty && media.video.isEmpty { data["lostMediaAssetId"] = FieldValue.delete() }
                else { data["lostMediaAssetId"] = PawlyCloudinaryUploader.indexID(for: media.image.isEmpty ? media.video : media.image) }
            }
            try await Firestore.firestore().collection("posts").document(post.id).updateData(data)
            onEdited(updated); dismiss()
        } catch { self.error = PawlyServiceMessage.key(for: error) }
    }
}
