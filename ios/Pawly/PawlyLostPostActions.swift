import SwiftUI
import CoreLocation
import FirebaseAuth
import FirebaseFirestore

extension PawlyFeedPost {
    var publishedArea: String { PawlyLostNoticeText.area(in: text) }
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
               alert.get("status") as? String == "active",
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
    @State private var post: PawlyFeedPost
    let onEdited: (PawlyFeedPost) -> Void
    @StateObject private var attachment = PawlyAttachmentDraft(maximumDuration: 60)
    @State private var text: String
    @State private var choosingArea = false
    @State private var selectedArea: String?
    @State private var selectedPoint: CLLocationCoordinate2D?
    @State private var removeArea = false
    @State private var conflict = false
    @State private var retain = true
    private var area: String { PawlyLostNoticeText.area(in: text) }
    private var effectivePoint: CLLocationCoordinate2D? {
        if area == selectedArea, let selectedPoint { return selectedPoint }
        return !removeArea && area == post.publishedArea ? post.publishedPoint : nil
    }
    private var pinEdit: PawlyLostPinEdit {
        if area == selectedArea, let point = selectedPoint {
            return .set(Int((point.latitude * 100).rounded()), Int((point.longitude * 100).rounded()))
        }
        return removeArea || area != post.publishedArea ? .clear : .keep
    }
    @State private var saving = false
    @State private var error: String?
    init(post: PawlyFeedPost, onEdited: @escaping (PawlyFeedPost) -> Void) {
        _post = State(initialValue: post); self.onEdited = onEdited; _text = State(initialValue: post.text)
    }
    var body: some View {
        NavigationStack {
            Form {
                TextField("Datos del aviso", text: $text, axis: .vertical).lineLimit(4...12)
                Section("Ubicación aproximada del aviso") {
                    if let point = effectivePoint {
                        PawlyLostAreaMap(coordinate: point, name: area).id(PawlyMapPoint.label(point, approximate: true))
                    } else if !area.isEmpty {
                        Text(verbatim: area)
                    }
                    Button { choosingArea = true } label: { Label("Cambiar ubicación en el mapa", systemImage: "mappin.and.ellipse") }
                    if !removeArea && (!area.isEmpty || effectivePoint != nil || !post.areaChanged) {
                        Button("Retirar zona del aviso", role: .destructive) {
                            selectedArea = nil; selectedPoint = nil; removeArea = true
                            text = PawlyLostNoticeText.replacingArea(in: text, with: "")
                        }
                    }
                }
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
                if conflict {
                    Button("Descartar borrador y cargar la última versión") { Task { await reload() } }
                }
                if saving { ProgressView() }
                if let error { Text(LocalizedStringKey(error)).foregroundStyle(.red) }
            }.navigationTitle("Editar aviso").interactiveDismissDisabled(saving).disabled(saving)
                .sheet(isPresented: $choosingArea) {
                    PawlyMapPointPicker(query: area, point: effectivePoint, title: "Ubicación aproximada del aviso", approximate: true) { label, point in
                        selectedArea = label; selectedPoint = point; removeArea = false
                        text = PawlyLostNoticeText.replacingArea(in: text, with: label)
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() }.disabled(saving) }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Guardar") { Task { await save() } }.disabled(conflict || saving || attachment.loading || attachment.error != nil
                            || text.count > 2000 || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
        }
    }
    @MainActor private func save() async {
        guard !saving, let user = Auth.auth().currentUser, user.uid == post.authorId else { return }
        saving = true; defer { saving = false }
        do {
            let media = attachment.hasAttachment || !retain ? try await attachment.upload(owner: user.uid, forPost: true) : nil
            let updated = try await PawlyCommunityStore().editLostPost(post, text: text, pin: pinEdit, media: media)
            onEdited(updated); dismiss()
        } catch {
            self.error = PawlyServiceMessage.key(for: error)
            conflict = (error as NSError).domain == (PawlyLostEditError.changed as NSError).domain
        }
    }

    @MainActor private func reload() async {
        guard !saving else { return }
        saving = true; defer { saving = false }
        do {
            guard let current = try await PawlyCommunityStore().loadPost(post.id) else { throw PawlyCommunityError.invalidPost }
            post = current; text = current.text; attachment.clear(); retain = true
            selectedArea = nil; selectedPoint = nil; removeArea = false; conflict = false; error = nil
            onEdited(current)
        } catch { self.error = PawlyServiceMessage.key(for: error) }
    }
}
