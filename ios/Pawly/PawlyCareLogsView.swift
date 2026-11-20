import SwiftUI
import PhotosUI
import FirebaseAuth

struct PawlyCareLogsView: View {
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
                                            .clipShape(RoundedRectangle(cornerRadius: 10))
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
                    PawlyCareLogForm(petName: pet.name) { title, action, note, distance, description, walker, photos in
                        var photoURLs: [String] = []
                        if let uid = Auth.auth().currentUser?.uid {
                            for item in photos {
                                guard let data = try await item.loadTransferable(type: Data.self) else { continue }
                                let url = try await PawlyCloudinaryUploader().uploadImage(
                                    data, folder: "pet-photos", ownerUID: uid
                                )
                                photoURLs.append(url)
                            }
                        }
                        try await pets.logCareAction(
                            pet: pet,
                            taskTitle: title,
                            action: action,
                            note: note,
                            distanceKm: distance,
                            description: description,
                            walker: walker,
                            photoURLs: photoURLs
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
    let petName: String
    let initialAction: String
    var onSave: (String, String, String, Double?, String, String, [PhotosPickerItem]) async throws -> Void

    @State private var title: String
    @State private var action: String

    init(
        petName: String,
        initialAction: String = "Paseo",
        onSave: @escaping (String, String, String, Double?, String, String, [PhotosPickerItem]) async throws -> Void
    ) {
        self.petName = petName
        self.initialAction = initialAction
        self.onSave = onSave
        _title = State(initialValue: initialAction)
        _action = State(initialValue: initialAction)
    }
    @State private var note = ""
    @State private var distance = ""
    @State private var description = ""
    @State private var walker = ""
    @State private var photos: [PhotosPickerItem] = []
    @State private var busy = false
    @State private var error: String?

    private let actions = ["Paseo", "Comida", "Medicación", "Higiene", "Otro"]

    var body: some View {
        NavigationStack {
            Form {
                Section("Acción realizada") {
                    TextField("Qué se ha hecho", text: $title)
                    Picker("Tipo", selection: $action) {
                        ForEach(actions, id: \.self) { Text($0) }
                    }
                    TextField("Cuidador o responsable (opcional)", text: $walker)
                }
                if action == "Paseo" {
                    Section("Paseo") {
                        TextField("Distancia en km (opcional)", text: $distance)
                            .keyboardType(.decimalPad)
                    }
                }
                Section("Detalles") {
                    TextField("Notas", text: $note, axis: .vertical).lineLimit(2...5)
                    TextField("Descripción (opcional)", text: $description, axis: .vertical).lineLimit(2...5)
                    PhotosPicker(selection: $photos, maxSelectionCount: 4, matching: .images) {
                        Label(photos.isEmpty ? "Añadir fotografías" : "\(photos.count) fotografías", systemImage: "photo")
                    }
                }
                Section {
                    LabeledContent("Mascota", value: petName)
                    Text("El registro se compartirá con las personas que tengan permiso de edición en esta mascota.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Registrar cuidado")
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
                            do {
                                try await onSave(
                                    title.trimmingCharacters(in: .whitespacesAndNewlines),
                                    action,
                                    note.trimmingCharacters(in: .whitespacesAndNewlines),
                                    parsedDistance,
                                    description.trimmingCharacters(in: .whitespacesAndNewlines),
                                    walker.trimmingCharacters(in: .whitespacesAndNewlines),
                                    photos
                                )
                                dismiss()
                            } catch {
                                self.error = error.localizedDescription
                            }
                        }
                    }
                    .disabled(busy || title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
