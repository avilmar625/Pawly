import SwiftUI
import PhotosUI
import FirebaseAuth

struct PawlyCareLogsView: View {
    init(startWithForm: Bool = false) { _showAdd = State(initialValue: startWithForm) }
    @EnvironmentObject private var pets: PawlyPetStore
    @State private var logs: [PawlyCareLogEntry] = []
    @State private var showAdd = false
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Group {
            if let pet = pets.selectedPet {
                List {
                    if logs.isEmpty && !busy {
                        PawlyEmptyState(
                            icon: "checklist",
                            title: "Aún no hay acciones registradas",
                            detail: "Anota paseos, comidas, medicación, higiene y otros cuidados."
                        )
                    }
                    ForEach(logs) { entry in
                        VStack(alignment: .leading, spacing: 9) {
                            HStack(alignment: .top) {
                                Image(systemName: icon(for: entry.action))
                                    .font(.title3)
                                    .foregroundStyle(PawlyColors.primary)
                                    .frame(width: 40, height: 40)
                                    .background(PawlyColors.mint, in: RoundedRectangle(cornerRadius: 12))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(entry.taskTitle).font(.headline)
                                    Text("\(entry.action) · \(entry.actorName)")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if let date = entry.occurredAt {
                                    Text(date.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption2).foregroundStyle(.secondary)
                                }
                            }
                            if !entry.note.isEmpty { Text(entry.note).font(.subheadline) }
                            if !entry.description.isEmpty { Text(entry.description).font(.caption).foregroundStyle(.secondary) }
                            if !entry.walker.isEmpty { Label(entry.walker, systemImage: "figure.walk").font(.caption).foregroundStyle(.secondary) }
                            if let distance = entry.distanceKm {
                                Label(String(format: "%.2f km", distance), systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            if !entry.mapURL.isEmpty, let url=URL(string:entry.mapURL) {
                                Link(destination:url){Label("Ver mapa",systemImage:"map").font(.caption)}
                            }
                            if !entry.videoURL.isEmpty, let clip = URL(string: entry.videoURL) { PawlyVideoPreview(url: clip).frame(height: 170) }
                                if !entry.photoURLs.isEmpty {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(entry.photoURLs, id: \.self) { url in
                                            AsyncImage(url: URL(string: url)) { image in
                                                image.resizable().scaledToFill()
                                            } placeholder: {
                                                RoundedRectangle(cornerRadius: 10).fill(PawlyColors.mint)
                                            }
                                            .frame(width: 130, height: 100)
                                            .clipShape(RoundedRectangle(cornerRadius: 10)).pawlyPhoto(URL(string: url), gallery: entry.photoURLs)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 5)
                    }
                    if let error { Text(error).font(.caption).foregroundStyle(.red) }
                }
                .navigationTitle("Historial de cuidados")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) { PetPicker() }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { showAdd = true } label: { Image(systemName: "plus.circle.fill") }
                            .disabled(busy)
                    }
                }
                .refreshable { await load(pet) }
                .task(id: pet.id) { await load(pet) }
                .sheet(isPresented: $showAdd) {
                    PawlyCareLogForm(pet: pet) { selected, title, action, note, distance, description, walker, photos, mapURL, clip in
                        var photoURLs: [String] = []
                        if let uid = Auth.auth().currentUser?.uid {
                            for data in photos {
                                let url = try await PawlyCloudinaryUploader().uploadImage(
                                    data, folder: "pet-photos", ownerUID: uid
                                )
                                photoURLs.append(url)
                            }
                        }
                        try await pets.logCareAction(
                            pet: selected,
                            taskTitle: title,
                            action: action,
                            note: note,
                            distanceKm: distance,
                            description: description,
                            walker: walker,
                            photoURLs: photoURLs,
                            mapURL: mapURL, videoURL: clip
                        )
                        await load(pet)
                    }
                }
            } else {
                PawlyEmptyState(
                    icon: "pawprint",
                    title: "Selecciona una mascota",
                    detail: "El historial registra las acciones realizadas para cada mascota."
                )
            }
        }
    }

    @MainActor private func load(_ pet: PawlyPet) async {
        busy = true
        defer { busy = false }
        do {
            try await pets.ensureSharedPet(pet)
            logs = try await pets.loadCareLogs(pet)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func icon(for action: String) -> String {
        switch action {
        case "Paseo": return "figure.walk"
        case "Comida": return "fork.knife"
        case "Medicación": return "pills"
        case "Higiene": return "shower"
        default: return "pawprint"
        }
    }
}

struct PawlyCareLogForm: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var pets: PawlyPetStore
    @State private var chosenID: String
    @StateObject private var video = PawlyAttachmentDraft(maximumDuration: 60)
    @State private var mapOpen = false
    let fixedAction: Bool
    var onSave: (PawlyPet, String, String, String, Double?, String, String, [Data], String, String) async throws -> Void

    @State private var title: String
    @State private var action: String

    init(
        pet: PawlyPet,
        initialAction: String? = nil,
        onSave: @escaping (PawlyPet, String, String, String, Double?, String, String, [Data], String, String) async throws -> Void
    ) {
        _chosenID = State(initialValue: pet.id)
        self.fixedAction = initialAction != nil
        self.onSave = onSave
        _title = State(initialValue: initialAction ?? "Paseo")
        _action = State(initialValue: initialAction ?? "Paseo")
    }
    @State private var note = ""
    @State private var distance = ""
    @State private var description = ""
    @State private var walker = ""
    @State private var photos: [PhotosPickerItem] = []
    @State private var cameraPhotos: [Data] = []
    @State private var mapURL = ""
    @State private var busy = false
    @State private var error: String?

    private let actions = ["Paseo", "Comida", "Medicación", "Higiene", "Otro"]

    var body: some View {
        NavigationStack {
            Form {
                Section(action == "Paseo" ? "Vuestro paseo" : "Acción realizada") {
                    if !fixedAction {
                        Picker("Tipo", selection: $action) {
                        ForEach(actions, id: \.self) { Text(LocalizedStringKey($0)) }
                        }.onChange(of: action) { next in if actions.contains(title) { title = next } }
                    }
                    TextField(action == "Paseo" ? "Nombre del paseo" : "Qué se ha hecho", text: $title)
                    TextField(action == "Paseo" ? "Quién ha paseado (opcional)" : "Cuidador o responsable (opcional)", text: $walker)
                }
                if action == "Paseo" {
                    Section("Paseo") {
                        TextField("Distancia en km (opcional)", text: $distance)
                            .keyboardType(.decimalPad)
                        Button { mapOpen = true } label: { Label("Seleccionar zona del paseo", systemImage: "map") }
                        TextField("Enlace HTTPS al mapa (opcional)",text:$mapURL)
                            .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                    }
                }
                if action == "Paseo" { Section("Vídeo") { PawlyAttachmentPicker(draft: video, videoOnly: true) } }
                Section("Detalles") {
                    TextField("Notas", text: $note, axis: .vertical).lineLimit(2...5)
                    TextField("Descripción (opcional)", text: $description, axis: .vertical).lineLimit(2...5)
                    PhotosPicker(selection: $photos, maxSelectionCount: max(1,4-cameraPhotos.count), matching: .images) {
                        Label("Elegir de la galería",systemImage:"photo")
                    }.disabled(cameraPhotos.count >= 4)
                    PawlyCameraButton { data in if photos.count+cameraPhotos.count < 4 { cameraPhotos.append(data) } }
                        .disabled(photos.count+cameraPhotos.count >= 4)
                    if photos.count+cameraPhotos.count > 0 {
                        PawlyPhotoSelection(gallery: $photos, camera: $cameraPhotos)
                        LabeledContent("Fotografías seleccionadas",value:"\(photos.count+cameraPhotos.count)/4")
                        Button("Quitar selección",role:.destructive){photos=[];cameraPhotos=[]}.buttonStyle(.borderless)
                    }
                }
                Section {
                    Picker("Mascota", selection: $chosenID) {
                        ForEach(pets.pets) { pet in Text(verbatim: pet.name).tag(pet.id) }
                    }
                    Text("El registro se compartirá con las personas que tengan permiso de edición en esta mascota.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .sheet(isPresented: $mapOpen) {
                PawlyMapPointPicker(query: "", point: URLComponents(string: mapURL)?.queryItems?.first(where: { $0.name == "ll" })?.value.flatMap(PawlyMapPoint.parse),
                                    title: "Seleccionar zona del paseo") { name, point in
                    var url = URLComponents(); url.scheme = "https"; url.host = "maps.apple.com"; url.path = "/"
                    url.queryItems = [URLQueryItem(name: "ll", value: PawlyMapPoint.label(point, approximate: false)), URLQueryItem(name: "q", value: name)]
                    mapURL = url.url?.absoluteString ?? ""
                }
            }
            .navigationTitle(action == "Paseo" ? "Registrar paseo" : "Registrar cuidado")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }.disabled(busy)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Guardando…" : "Guardar") {
                        Task {
                            busy = true
                            defer { busy = false }
                            let raw = distance.trimmingCharacters(in: .whitespacesAndNewlines)
                                .replacingOccurrences(of: ",", with: ".")
                            let parsedDistance = raw.isEmpty ? nil : Double(raw)
                            if !raw.isEmpty && (parsedDistance == nil || parsedDistance! < 0.01 || parsedDistance! > 1000) {
                                error = "La distancia debe estar entre 0,01 y 1000 km."
                                return
                            }
                            let cleanMap = action == "Paseo" ? mapURL.trimmingCharacters(in:.whitespacesAndNewlines) : ""
                            if !cleanMap.isEmpty && (URL(string:cleanMap)?.scheme?.lowercased() != "https" || URL(string:cleanMap)?.host == nil) {
                                error="Introduce un enlace válido que comience por https://.";return
                            }
                            do {
                                var imageData=cameraPhotos
                                for item in photos {
                                    guard let data=try await item.loadTransferable(type:Data.self) else {throw PawlyMediaError.invalidImage}
                                    imageData.append(data)
                                }
                                guard let selected = pets.pets.first(where: { $0.id == chosenID }), let uid = Auth.auth().currentUser?.uid else { throw PawlyStoreError.notSignedIn }
                                let clip = action == "Paseo" ? try await video.upload(owner: uid, prefix: "pet").video : ""
                                try await onSave(selected,
                                    title.trimmingCharacters(in: .whitespacesAndNewlines),
                                    action,
                                    note.trimmingCharacters(in: .whitespacesAndNewlines),
                                    parsedDistance,
                                    description.trimmingCharacters(in: .whitespacesAndNewlines),
                                    walker.trimmingCharacters(in: .whitespacesAndNewlines),
                                    Array(imageData.prefix(4)),
                                    cleanMap, clip
                                )
                                dismiss()
                            } catch {
                                self.error = error.localizedDescription
                            }
                        }
                    }
                    .disabled(busy || video.loading || video.error != nil || title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
