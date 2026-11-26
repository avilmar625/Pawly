import SwiftUI
import MapKit
import CoreLocation
import FirebaseAuth
import FirebaseFirestore

struct PawlyCommunityPlace: Identifiable {
    var id: String
    var name: String
    var category: String
    var location: String
    var description: String
    var authorUID: String
    var createdAt: Date?
    var latitude: Double?
    var longitude: Double?
    var status: String
}

@MainActor
final class PawlyCommunityPlacesStore: ObservableObject {
    @Published private(set) var approved: [PawlyCommunityPlace] = []
    @Published private(set) var mine: [PawlyCommunityPlace] = []
    @Published var errorMessage: String?
    private let db = Firestore.firestore()
    private let categories = ["Parque", "Playa", "Restaurante", "Alojamiento", "Ruta", "Veterinario", "Otro"]

    func load() async {
        guard let uid = Auth.auth().currentUser?.uid else {
            errorMessage = "Inicia sesión para consultar los lugares de la comunidad."
            return
        }
        do {
            async let approvedSnapshot = db.collection("communityPlaces")
                .whereField("status", isEqualTo: "approved").limit(to: 100).getDocuments()
            async let ownSnapshot = db.collection("communityPlaces")
                .whereField("authorUid", isEqualTo: uid).limit(to: 100).getDocuments()
            let (approvedResult, ownResult) = try await (approvedSnapshot, ownSnapshot)
            approved = approvedResult.documents.compactMap(Self.mapPlace)
                .sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
            mine = ownResult.documents.compactMap(Self.mapPlace)
                .sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
            errorMessage = nil
        } catch {
            errorMessage = PawlyServiceMessage.key(for: error)
        }
    }

    func submit(
        name: String,
        category: String,
        location: String,
        description: String,
        coordinate: CLLocationCoordinate2D
    ) async throws {
        guard let user = Auth.auth().currentUser, user.isEmailVerified else {
            throw PawlyCommunityPlaceError.emailVerificationRequired
        }
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanLocation = location.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            cleanName.count >= 3, cleanName.count <= 80,
            cleanLocation.count >= 3, cleanLocation.count <= 120,
            cleanDescription.count >= 10, cleanDescription.count <= 500,
            categories.contains(category),
            (-90...90).contains(coordinate.latitude),
            (-180...180).contains(coordinate.longitude)
        else { throw PawlyCommunityPlaceError.invalidPlace }

        _ = try await PawlyBackendClient.postJSON(
            path: "/v1/community-places",
            body: [
                "submissionId": UUID().uuidString,
                "name": cleanName,
                "category": category,
                "location": cleanLocation,
                "description": cleanDescription,
                "latitude": coordinate.latitude,
                "longitude": coordinate.longitude
            ]
        )
        await load()
    }

    func updateOwn(
        _ place: PawlyCommunityPlace,
        name: String,
        category: String,
        location: String,
        description: String,
        coordinate: CLLocationCoordinate2D
    ) async throws {
        guard let user = Auth.auth().currentUser, user.isEmailVerified else {
            throw PawlyCommunityPlaceError.emailVerificationRequired
        }
        guard place.authorUID == user.uid else { throw PawlyCommunityPlaceError.notOwner }
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanLocation = location.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            cleanName.count >= 3, cleanName.count <= 80,
            cleanLocation.count >= 3, cleanLocation.count <= 120,
            cleanDescription.count >= 10, cleanDescription.count <= 500,
            categories.contains(category),
            (-90...90).contains(coordinate.latitude),
            (-180...180).contains(coordinate.longitude)
        else { throw PawlyCommunityPlaceError.invalidPlace }

        try await db.collection("communityPlaces").document(place.id).updateData([
            "name": cleanName,
            "category": category,
            "location": cleanLocation,
            "description": cleanDescription,
            "latitude": coordinate.latitude,
            "longitude": coordinate.longitude,
            "status": "pending"
        ])
        await load()
    }

    func deleteOwn(_ place: PawlyCommunityPlace) async throws {
        guard let user = Auth.auth().currentUser, user.isEmailVerified else {
            throw PawlyCommunityPlaceError.emailVerificationRequired
        }
        guard place.authorUID == user.uid else { throw PawlyCommunityPlaceError.notOwner }
        try await db.collection("communityPlaces").document(place.id).delete()
        await load()
    }

    private static func mapPlace(_ document: QueryDocumentSnapshot) -> PawlyCommunityPlace? {
        guard
            let name = document.get("name") as? String,
            let category = document.get("category") as? String,
            let location = document.get("location") as? String
        else { return nil }
        return PawlyCommunityPlace(
            id: document.documentID,
            name: name,
            category: category,
            location: location,
            description: document.get("description") as? String ?? "",
            authorUID: document.get("authorUid") as? String ?? "",
            createdAt: (document.get("createdAt") as? Timestamp)?.dateValue(),
            latitude: (document.get("latitude") as? NSNumber)?.doubleValue,
            longitude: (document.get("longitude") as? NSNumber)?.doubleValue,
            status: document.get("status") as? String ?? "pending"
        )
    }
}

enum PawlyCommunityPlaceError: LocalizedError {
    case emailVerificationRequired
    case invalidPlace
    case notOwner

    var errorDescription: String? {
        switch self {
        case .emailVerificationRequired:
            return "Verifica tu correo electrónico para proponer o editar lugares."
        case .invalidPlace:
            return "Revisa el nombre, la categoría, la ubicación y la descripción del lugar."
        case .notOwner:
            return "Solo puedes editar o eliminar tus propias propuestas."
        }
    }
}

struct PawlyCommunityPlacesView: View {
    @StateObject private var store = PawlyCommunityPlacesStore()
    @State private var showCreate = false
    @State private var editing: PawlyCommunityPlace?
    @State private var actionError: String?
    @State private var selectedPlace: PawlyCommunityPlace?

    var body: some View {
        List {
            Section {
                Text("Los lugares aprobados aparecen para toda la comunidad. Las propuestas nuevas pasan por revisión antes de mostrarse.")
                    .font(.caption).foregroundStyle(.secondary)
                Button { showCreate = true } label: {
                    Label("Proponer un lugar", systemImage: "plus.circle.fill")
                }
            }
            Section("Lugares de la comunidad") {
                if store.approved.isEmpty {
                    Text("Todavía no hay lugares aprobados. ¡Puedes proponer el primero!")
                        .foregroundStyle(.secondary)
                }
                ForEach(store.approved) { place in
                    PawlyCommunityPlaceRow(place: place) {
                        selectedPlace = place
                    }
                }
            }
            Section("Mis propuestas") {
                if store.mine.isEmpty {
                    Text("Aquí aparecerán tus propuestas pendientes o aprobadas.")
                        .foregroundStyle(.secondary)
                }
                ForEach(store.mine) { place in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(place.name).font(.headline)
                            Spacer()
                            Text(statusLabel(place.status))
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(place.status == "approved" ? PawlyColors.primary : PawlyColors.muted)
                        }
                        Text("\(Text(LocalizedStringKey(place.category))) · \(place.location)")
                            .font(.caption).foregroundStyle(.secondary)
                        Text(place.description).font(.subheadline).lineLimit(3)
                    }
                    .padding(.vertical, 3)
                    .swipeActions {
                        Button {
                            editing = place
                        } label: { Label("Editar", systemImage: "pencil") }
                            .tint(PawlyColors.primary)
                        Button(role: .destructive) {
                            Task {
                                do { try await store.deleteOwn(place) }
                                catch { actionError = PawlyServiceMessage.key(for: error) }
                            }
                        } label: { Label("Eliminar", systemImage: "trash") }
                    }
                }
            }
            if let error = store.errorMessage ?? actionError {
                Section { Text(LocalizedStringKey(error)).font(.caption).foregroundStyle(.red) }
            }
        }
        .navigationTitle("Lugares de la comunidad")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showCreate = true } label: { Image(systemName: "plus") }
            }
        }
        .task { await store.load() }
        .refreshable { await store.load() }
        .sheet(isPresented: $showCreate) {
            PawlyCommunityPlaceForm { name, category, location, description, coordinate in
                try await store.submit(name: name, category: category, location: location,
                                       description: description, coordinate: coordinate)
            }
        }
        .sheet(item: $editing) { place in
            PawlyCommunityPlaceForm(place: place) { name, category, location, description, coordinate in
                try await store.updateOwn(place, name: name, category: category, location: location,
                                          description: description, coordinate: coordinate)
            }
        }
        .sheet(item: $selectedPlace) { place in
            PawlyCommunityPlaceDetailView(place: place)
        }
    }

    private func statusLabel(_ status: String) -> LocalizedStringKey {
        switch status {
        case "approved": return "Aprobado"
        case "rejected": return "Rechazado"
        default: return "Pendiente"
        }
    }
}

private struct PawlyCommunityPlaceRow: View {
    let place: PawlyCommunityPlace
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 6) {
                Text(place.name).font(.headline).foregroundStyle(.primary)
                Text("\(Text(LocalizedStringKey(place.category))) · \(place.location)")
                    .font(.caption).foregroundStyle(.secondary)
                Text(place.description).font(.subheadline).foregroundStyle(.primary).lineLimit(3)
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

private struct PawlyCommunityPlaceDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let place: PawlyCommunityPlace

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(place.name).font(.title2.bold())
                    Text(LocalizedStringKey(place.category)).font(.caption.weight(.semibold)).foregroundStyle(PawlyColors.primary)
                    Text(place.description)
                    Label(place.location, systemImage: "mappin.and.ellipse")
                    if let latitude = place.latitude, let longitude = place.longitude {
                        Button {
                            let item = MKMapItem(placemark: MKPlacemark(
                                coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
                            ))
                            item.name = place.name
                            item.openInMaps()
                        } label: { Label("Abrir en Mapas", systemImage: "map") }
                    }
                }
            }
            .navigationTitle("Lugar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
    }
}

private struct PawlyCommunityPlaceForm: View {
    @Environment(\.dismiss) private var dismiss
    let existing: PawlyCommunityPlace?
    var onSave: (String, String, String, String, CLLocationCoordinate2D) async throws -> Void

    @State private var name = ""
    @State private var category = "Parque"
    @State private var location = ""
    @State private var description = ""
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var geocoding = false
    @State private var saving = false
    @State private var error: String?

    init(place: PawlyCommunityPlace? = nil,
         onSave: @escaping (String, String, String, String, CLLocationCoordinate2D) async throws -> Void) {
        self.existing = place
        self.onSave = onSave
        _name = State(initialValue: place?.name ?? "")
        _category = State(initialValue: place?.category ?? "Parque")
        _location = State(initialValue: place?.location ?? "")
        _description = State(initialValue: place?.description ?? "")
        if let latitude = place?.latitude, let longitude = place?.longitude {
            _coordinate = State(initialValue: CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Datos del lugar") {
                    TextField("Nombre", text: $name).onChange(of: name) { name = String($0.prefix(80)) }
                    Picker("Categoría", selection: $category) {
                        ForEach(["Parque", "Playa", "Restaurante", "Alojamiento", "Ruta", "Veterinario", "Otro"], id: \.self) {
                            Text(LocalizedStringKey($0))
                        }
                    }
                    TextField("Dirección o municipio", text: $location)
                        .onChange(of: location, perform: { _ in coordinate = nil })
                    TextField("Qué lo hace recomendable", text: $description, axis: .vertical)
                        .lineLimit(3...6)
                        .onChange(of: description) { description = String($0.prefix(500)) }
                    Button {
                        Task { await geocode() }
                    } label: {
                        HStack {
                            if geocoding { ProgressView() }
                            Label(coordinate == nil ? "Localizar dirección" : "Ubicación lista",
                                  systemImage: "mappin.and.ellipse")
                        }
                    }
                    .disabled(geocoding || location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if let coordinate {
                        Text(String(format: "Coordenadas: %.5f, %.5f", coordinate.latitude, coordinate.longitude))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                if let error { Section { Text(LocalizedStringKey(error)).foregroundStyle(.red) } }
                Section {
                    Text("La propuesta se enviará a revisión. Se requiere una ubicación concreta del lugar; no se utiliza ni se guarda tu posición personal.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section {
                    Button("Cancelar") { dismiss() }
                        .disabled(saving)
                    Button {
                        Task { await saveProposal() }
                    } label: {
                        HStack {
                            Spacer()
                            if saving { ProgressView() }
                            Text(saving ? "Enviando…" : "Enviar propuesta")
                            Spacer()
                        }
                    }
                    .disabled(saving || name.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 ||
                              location.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 ||
                              description.trimmingCharacters(in: .whitespacesAndNewlines).count < 10)
                }
            }
            .navigationTitle(existing == nil ? "Proponer un lugar" : "Editar propuesta")
        }
    }

    @MainActor private func saveProposal() async {
        guard let coordinate else {
            error = "Localiza primero la dirección para fijar el lugar en el mapa."
            return
        }
        saving = true
        defer { saving = false }
        do {
            try await onSave(name, category, location, description, coordinate)
            dismiss()
        } catch let saveError {
            self.error = saveError.localizedDescription
        }
    }

    @MainActor private func geocode() async {
        geocoding = true
        defer { geocoding = false }
        do {
            let results = try await CLGeocoder().geocodeAddressString(location)
            guard let result = results.first?.location?.coordinate else {
                error = "No se ha podido localizar la dirección. Añade una calle y un municipio."
                return
            }
            coordinate = result
            error = nil
        } catch let geocodingError {
            self.error = geocodingError.localizedDescription
        }
    }
}
