import SwiftUI
import MapKit
import CoreLocation
import FirebaseAuth
import FirebaseFirestore

struct PawlyEventItem: Identifiable {
    var id: String
    var title: String
    var description: String
    var category: String
    var location: String
    var dateText: String
    var startAt: Date
    var timeZoneID: String
    var latitude: Double?
    var longitude: Double?
    var organizerName: String
    var organizerUID: String
    var interestedCount: Int
    var interestedByMe: Bool
    var photoURL: String = ""
    var videoURL: String = ""
}

@MainActor
final class PawlyEventsStore: ObservableObject {
    @Published private(set) var events: [PawlyEventItem] = []
    @Published var errorMessage: String?
    private let db = Firestore.firestore()
    private let categories = ["Quedada", "Ruta", "Taller", "Adopción"]

    @Published private(set) var more = false
    @Published private(set) var loading = false
    private var cursor: DocumentSnapshot?
    private var request = UUID()
    private var followed = Set<String>()
    func load(_ filter: PawlyEventFilter, next: Bool = false) async {
        guard let uid = Auth.auth().currentUser?.uid else {
            events = []; errorMessage = "Inicia sesión para consultar los eventos."; return
        }
        if next && (loading || !more) { return }
        let token = UUID(); request = token
        if !next { events = []; cursor = nil; more = false }
        loading = true
        defer { if request == token { loading = false } }
        do {
            if filter.following && !next {
                let docs = try await db.collection("users").document(uid).collection("following").getDocuments()
                guard request == token else { return }
                followed = Set(docs.documents.map(\.documentID))
            }
            var query: Query = db.collection("events")
            if filter.mine { query = query.whereField("organizerUid", isEqualTo: uid) }
            switch filter.order {
            case .recent: query = query.order(by: "createdAt", descending: true)
            case .participation: query = query.order(by: "interestedCount", descending: true)
            case .date, .distance:
                if let start = filter.bounds.0 { query = query.whereField("startAt", isGreaterThanOrEqualTo: Timestamp(date: start)) }
                if let end = filter.bounds.1 { query = query.whereField("startAt", isLessThan: Timestamp(date: end)) }
                query = query.order(by: "startAt", descending: filter.period == .past)
            }
            if next, let cursor { query = query.start(afterDocument: cursor) }
            let snapshot = try await query.limit(to: 30).getDocuments()
            guard request == token else { return }
            var values: [PawlyEventItem] = []
            for document in snapshot.documents {
                guard
                    let startAt = (document.get("startAt") as? Timestamp)?.dateValue(),
                    let title = document.get("title") as? String,
                    let description = document.get("description") as? String,
                    let location = document.get("location") as? String
                else { continue }

                if filter.following && !followed.contains(document.get("organizerUid") as? String ?? "") { continue }
                var interested = false
                if filter.interested {
                    interested = try await document.reference.collection("interested").document(uid).getDocument().exists
                    if !interested { continue }
                }

                values.append(PawlyEventItem(
                    id: document.documentID,
                    title: title,
                    description: description,
                    category: document.get("category") as? String ?? "Quedada",
                    location: location,
                    dateText: document.get("dateText") as? String ?? "",
                    startAt: startAt,
                    timeZoneID: document.get("timeZoneId") as? String ?? TimeZone.current.identifier,
                    latitude: (document.get("latitude") as? NSNumber)?.doubleValue,
                    longitude: (document.get("longitude") as? NSNumber)?.doubleValue,
                    organizerName: document.get("organizerName") as? String ?? "Comunidad Pawly",
                    organizerUID: document.get("organizerUid") as? String ?? "",
                    interestedCount: document.get("interestedCount") as? Int ?? 0,
                    interestedByMe: interested, photoURL: document.get("photoUrl") as? String ?? "",
                    videoURL: document.get("videoUrl") as? String ?? ""
                ))
            }
            guard request == token, !Task.isCancelled else { return }
            cursor = snapshot.documents.last
            more = snapshot.documents.count == 30
            let seen = Set(events.map(\.id))
            events += values.filter { !seen.contains($0.id) }
            errorMessage = nil
        } catch {
            if request == token { errorMessage = PawlyServiceMessage.key(for: error) }
        }
    }

    func save(
        editing: PawlyEventItem?,
        title: String,
        description: String,
        category: String,
        location: String,
        date: Date,
        coordinate: CLLocationCoordinate2D?,
        photoURL: String, videoURL: String
    ) async throws {
        guard let user = Auth.auth().currentUser, user.isEmailVerified else {
            throw PawlyEventError.emailVerificationRequired
        }
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanLocation = location.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanTitle.count >= 4, cleanTitle.count <= 90,
              cleanDescription.count >= 10, cleanDescription.count <= 1200,
              cleanLocation.count >= 3, cleanLocation.count <= 160,
              categories.contains(category), date > Date() else {
            throw PawlyEventError.invalidEvent
        }
        if let coordinate {
            guard (-90...90).contains(coordinate.latitude),
                  (-180...180).contains(coordinate.longitude) else {
                throw PawlyEventError.invalidCoordinate
            }
        }

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "es_ES")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "dd/MM/yyyy HH:mm"
        var data: [String: Any] = [
            "title": cleanTitle,
            "description": cleanDescription,
            "category": category,
            "location": cleanLocation,
            "dateText": formatter.string(from: date),
            "startAt": Timestamp(date: date),
            "timeZoneId": TimeZone.current.identifier
        ]
        if let coordinate {
            data["latitude"] = coordinate.latitude
            data["longitude"] = coordinate.longitude
        }
        if !photoURL.isEmpty {
            data["photoUrl"] = photoURL
            data["photoAssetId"] = PawlyCloudinaryUploader.indexID(for: photoURL)
        }
        if !videoURL.isEmpty {
            data["videoUrl"] = videoURL
            data["videoAssetId"] = PawlyCloudinaryUploader.indexID(for: videoURL)
        }
        if let editing {
            guard editing.organizerUID == user.uid else { throw PawlyEventError.invalidEvent }
            for key in ["latitude", "longitude", "photoUrl", "photoAssetId", "videoUrl", "videoAssetId"] where data[key] == nil {
                data[key] = FieldValue.delete()
            }
            try await db.collection("events").document(editing.id).updateData(data)
            return
        }
        let profile = try await db.collection("users").document(user.uid).getDocument()
        let displayName = (profile.get("displayName") as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? (profile.get("username") as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? "Usuario Pawly"

        data["organizerUid"] = user.uid
        data["organizerName"] = String(displayName.prefix(60))
        data["interestedCount"] = 0
        data["createdAt"] = FieldValue.serverTimestamp()
        _ = try await db.collection("events").addDocument(data: data)
    }

    func toggleInterest(_ event: PawlyEventItem) async throws {
        guard let user = Auth.auth().currentUser, user.isEmailVerified else {
            throw PawlyEventError.emailVerificationRequired
        }
        let eventRef = db.collection("events").document(event.id)
        let interestRef = eventRef.collection("interested").document(user.uid)

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            db.runTransaction({ transaction, errorPointer -> Any? in
                do {
                    let eventSnapshot = try transaction.getDocument(eventRef)
                    let interestSnapshot = try transaction.getDocument(interestRef)
                    guard eventSnapshot.exists else { throw PawlyEventError.eventNoLongerAvailable }
                    let count = eventSnapshot.get("interestedCount") as? Int ?? 0
                    if interestSnapshot.exists {
                        transaction.deleteDocument(interestRef)
                        transaction.updateData(["interestedCount": max(0, count - 1)], forDocument: eventRef)
                    } else {
                        transaction.setData([
                            "uid": user.uid,
                            "createdAt": FieldValue.serverTimestamp()
                        ], forDocument: interestRef)
                        transaction.updateData(["interestedCount": count + 1], forDocument: eventRef)
                    }
                    return nil
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
            }, completion: { _, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            })
        }
    }

    func interested(in event: PawlyEventItem) async throws -> Bool {
        guard let uid = Auth.auth().currentUser?.uid else { return false }
        return try await db.collection("events").document(event.id).collection("interested").document(uid).getDocument().exists
    }

    func loadAttendees(event: PawlyEventItem, pageToken: String = "") async throws -> (attendees: [PawlyIdentity], nextPageToken: String, total: Int?) {
        guard Auth.auth().currentUser?.uid == event.organizerUID else {
            throw PawlyEventError.attendeesOrganizerOnly
        }
        let response = try await PawlyBackendClient.postJSON(
            path: "/v1/events/attendees",
            body: ["eventId": event.id, "pageToken": pageToken]
        )
        let rows = response["attendees"] as? [[String: Any]] ?? []
        return (rows.compactMap(PawlyIdentity.init), response["nextPageToken"] as? String ?? "", response["total"] as? Int)
    }

    func report(_ event: PawlyEventItem, reason: String) async throws {
        guard let user = Auth.auth().currentUser, user.isEmailVerified else {
            throw PawlyEventError.emailVerificationRequired
        }
        let clean = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 500 else { throw PawlyEventError.invalidEvent }
        try await db.collection("reports").addDocument(data: [
            "reporterId": user.uid,
            "targetId": event.id,
            "targetType": "event",
            "reason": clean,
            "status": "open",
            "createdAt": FieldValue.serverTimestamp()
        ])
    }
}

enum PawlyEventError: LocalizedError {
    case emailVerificationRequired
    case invalidEvent
    case invalidCoordinate
    case eventNoLongerAvailable
    case attendeesOrganizerOnly

    var errorDescription: String? {
        switch self {
        case .emailVerificationRequired:
            return "Verifica tu correo electrónico para crear eventos o marcar interés."
        case .invalidEvent:
            return "Revisa el título, la descripción, el lugar, la categoría y la fecha del evento."
        case .invalidCoordinate:
            return "La ubicación seleccionada no es válida."
        case .eventNoLongerAvailable:
            return "Este evento ya no está disponible."
        case .attendeesOrganizerOnly:
            return "Solo la persona organizadora puede consultar la lista de asistentes."
        }
    }
}

enum PawlyEventOrder: String, CaseIterable { case date = "Fecha", recent = "Más recientes", participation = "Mayor participación", distance = "Distancia" }
enum PawlyEventPeriod: String, CaseIterable { case upcoming = "Próximos", past = "Pasados", range = "Intervalo de fechas", all = "Todas las fechas" }

struct PawlyEventFilter: Equatable {
    var period = PawlyEventPeriod.upcoming
    var order = PawlyEventOrder.date
    var from = Calendar.current.startOfDay(for: Date())
    var until = Calendar.current.startOfDay(for: Date())
    var category = "Todos"
    var zone = ""
    var radius = 0
    var mine = false
    var interested = false
    var following = false
    var bounds: (Date?, Date?) {
        switch period {
        case .upcoming: return (Date(), nil)
        case .past: return (nil, Date())
        case .all: return (nil, nil)
        case .range: return (Calendar.current.startOfDay(for: from), Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: until)))
        }
    }
    var active: Bool { self != PawlyEventFilter() }
}

struct PawlyEventsView: View {
    @StateObject private var store = PawlyEventsStore()
    @State private var filter = PawlyEventFilter()
    @State private var draft = PawlyEventFilter()
    @State private var filtersOpen = false
    @StateObject private var gps = PawlyLocationProvider()
    @State private var selectedEvent: PawlyEventItem?
    @State private var showCreate = false
    @State private var createMessage: String?
    @State private var created = false
    @State private var showReport = false

    @State private var query = ""
    private func distance(_ event: PawlyEventItem) -> Double? {
        guard let point = gps.location, let lat = event.latitude, let lon = event.longitude else { return nil }
        return point.distance(from: CLLocation(latitude: lat, longitude: lon)) / 1000
    }
    private func contains(_ text: String, _ term: String) -> Bool {
        term.isEmpty || text.range(of: term, options: [.caseInsensitive, .diacriticInsensitive]) != nil
    }
    private var visibleEvents: [PawlyEventItem] {
        let bounds = filter.bounds
        let matches = store.events.filter { event in
            (filter.category == "Todos" || event.category == filter.category)
                && (bounds.0.map { event.startAt >= $0 } ?? true) && (bounds.1.map { event.startAt < $0 } ?? true)
                && contains(event.location, filter.zone)
                && [event.title, event.description, event.location, event.organizerName].contains { contains($0, query) }
                && (filter.radius == 0 || (distance(event) ?? .infinity) <= Double(filter.radius))
        }
        if filter.order == .distance { return matches.sorted { (distance($0) ?? .infinity) < (distance($1) ?? .infinity) } }
        return matches
    }

    var body: some View {
        List {
            Button {
                draft = filter; filtersOpen = true
            } label: {
                Label(filter.active ? "Filtros activos" : "Filtrar", systemImage: "line.3.horizontal.decrease.circle")
            }
            Section("Eventos y quedadas") {
                if store.loading { ProgressView() }
                if visibleEvents.isEmpty && !store.loading {
                    PawlyEmptyState(
                        icon: "calendar",
                        title: "Todavía no hay eventos",
                        detail: "Propón una quedada, una ruta, un taller o una actividad de adopción."
                    )
                }
                ForEach(visibleEvents) { event in
                    Button { selectedEvent = event } label: {
                        VStack(alignment: .leading, spacing: 7) {
                            HStack {
                                Text(LocalizedStringKey(event.category))
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(PawlyColors.primary)
                                Spacer()
                                Text(event.startAt.formatted(date: .abbreviated, time: .shortened))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Text(event.title).font(.headline).foregroundStyle(.primary)
                            Text(event.description).font(.subheadline).foregroundStyle(.secondary).lineLimit(3)
                            Label(event.location, systemImage: "mappin.and.ellipse")
                                .font(.caption).foregroundStyle(.secondary)
                            HStack {
                                Label(event.organizerName, systemImage: "person")
                                if let km = distance(event) { Text(verbatim: Measurement(value: km, unit: UnitLength.kilometers).formatted(.measurement(width: .abbreviated, usage: .road))) }
                                Spacer()
                                Label("\(event.interestedCount)", systemImage: "person.2")
                            }
                            .font(.caption).foregroundStyle(PawlyColors.primary)
                        }
                        .padding(.vertical, 6).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            if store.more { Button("Cargar más") { Task { await store.load(filter, next: true) } }.disabled(store.loading) }
            if let message = store.errorMessage {
                Section { Text(LocalizedStringKey(message)).font(.caption).foregroundStyle(.red) }
            }

        }
        .searchable(text: $query, prompt: "Buscar eventos")
        .alert("Eventos y quedadas", isPresented: Binding(get: { createMessage != nil }, set: { if !$0 { createMessage = nil } })) {
            Button("Cerrar") { createMessage = nil }
        } message: { Text(LocalizedStringKey(createMessage ?? "")) }
        .navigationTitle("Eventos y quedadas")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showCreate = true } label: { Image(systemName: "plus") }.accessibilityLabel("Crear evento")
            }
        }
        .task(id: filter) { await store.load(filter) }
        .refreshable { await store.load(filter) }
        .sheet(isPresented: $showCreate, onDismiss: {
            if created { createMessage = "Evento publicado."; created = false; Task { await store.load(filter) } }
        }) {
            PawlyEventCreateSheet(editing: nil, store: store) { created = true }
        }
        .sheet(isPresented: $filtersOpen) {
            NavigationStack {
                Form {
                    Picker("Ordenar", selection: $draft.order) {
                        ForEach(PawlyEventOrder.allCases, id: \.self) { Text(LocalizedStringKey($0.rawValue)).tag($0) }
                    }
                    Picker("Fecha", selection: $draft.period) {
                        ForEach(PawlyEventPeriod.allCases, id: \.self) { Text(LocalizedStringKey($0.rawValue)).tag($0) }
                    }
                    if draft.period == .range {
                        DatePicker("Desde", selection: $draft.from, displayedComponents: .date)
                        DatePicker("Hasta", selection: $draft.until, in: draft.from..., displayedComponents: .date)
                    }
                    Picker("Tipo de evento", selection: $draft.category) {
                        ForEach(["Todos", "Quedada", "Ruta", "Taller", "Adopción"], id: \.self) { Text(LocalizedStringKey($0)).tag($0) }
                    }
                    TextField("Localidad o zona", text: $draft.zone)
                    Picker("Radio de búsqueda", selection: $draft.radius) {
                        Text("Sin límite").tag(0)
                        ForEach([5, 10, 25, 50, 100], id: \.self) { Text(verbatim: "\($0) km").tag($0) }
                    }
                    if draft.radius > 0 || draft.order == .distance {
                        Button { gps.request() } label: { Label("Usar mi ubicación", systemImage: "location") }
                        if let status = gps.message { Text(LocalizedStringKey(status)).font(.caption) }
                    }
                    Toggle("Organizados por mí", isOn: $draft.mine)
                    Toggle("Cuentas que sigo", isOn: $draft.following)
                    Toggle("Me interesa", isOn: $draft.interested)
                    Button("Restablecer") { draft = PawlyEventFilter() }
                }.navigationTitle("Filtrar").toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { filtersOpen = false } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Aplicar") { filter = draft; filtersOpen = false }
                            .disabled(((draft.radius > 0 || draft.order == .distance) && gps.location == nil)
                                      || (draft.period == .range && draft.until < draft.from))
                    }
                }
            }.onDisappear { gps.cancel() }
        }
        .sheet(item: $selectedEvent) { event in
            PawlyEventDetailsSheet(event: event, store: store) { Task { await store.load(filter) } }
        }
    }
}

private struct PawlyEventDetailsSheet: View {
    @Environment(\.dismiss) private var dismiss
    let event: PawlyEventItem
    @ObservedObject var store: PawlyEventsStore
    var onChange: () -> Void
    @State private var edited = false
    @State private var edit = false
    @State private var interest: Bool?
    @State private var changingInterest = false
    @State private var actualTotal: Int?
    @State private var attendees: [PawlyIdentity] = []
    @State private var nextPage = ""
    @State private var loadingAttendees = false
    @State private var message: String?
    @State private var showReport = false
    @State private var reportReason = "Contenido inapropiado"

    private var isOrganizer: Bool { Auth.auth().currentUser?.uid == event.organizerUID }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if let url = URL(string: event.photoURL), !event.photoURL.isEmpty {
                        AsyncImage(url: url) { image in image.resizable().scaledToFit() } placeholder: { ProgressView() }
                            .frame(maxHeight: 240).pawlyPhoto(url)
                    }
                    if let url = URL(string: event.videoURL), !event.videoURL.isEmpty { PawlyVideoPreview(url: url).frame(height: 200) }
                    Text(event.title).font(.title2.bold())
                    Text(event.description)
                    Label(event.category, systemImage: "calendar")
                    Label(event.location, systemImage: "mappin.and.ellipse")
                    Label(event.startAt.formatted(date: .complete, time: .shortened), systemImage: "clock")
                    Label("\(actualTotal ?? event.interestedCount) personas interesadas", systemImage: "person.2")
                    if let latitude = event.latitude, let longitude = event.longitude {
                        Button {
                            let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
                            let item = MKMapItem(placemark: MKPlacemark(coordinate: coordinate))
                            item.name = event.title
                            item.openInMaps()
                        } label: { Label("Abrir ubicación en Mapas", systemImage: "map") }
                    }
                }
                Section {
                    Button {
                        Task {
                            changingInterest = true; defer { changingInterest = false }
                            do { try await store.toggleInterest(event); onChange(); dismiss() }
                            catch { message = PawlyServiceMessage.key(for: error) }
                        }
                    } label: {
                        Label(interest == true ? "Ya no me interesa" : "Me interesa",
                              systemImage: interest == true ? "person.badge.minus" : "person.badge.plus")
                    }.disabled(interest == nil || changingInterest)
                }
                if isOrganizer {
                    Button("Editar evento") { edit = true }
                    Section("Personas interesadas") {
                        Button {
                            Task { await loadAttendees() }
                        } label: {
                            HStack {
                                if loadingAttendees { ProgressView() }
                                Text(attendees.isEmpty ? "Ver asistentes" : "Actualizar asistentes")
                            }
                        }.disabled(loadingAttendees)
                        if let actualTotal { LabeledContent("Perfiles visibles", value: "\(attendees.count) / \(actualTotal)") }
                        ForEach(attendees) { person in
                            if person.isPublic || person.id == Auth.auth().currentUser?.uid {
                                NavigationLink { PawlyPublicProfile(userID: person.id) } label: { PawlyIdentityRow(person: person) }
                            } else { PawlyIdentityRow(person: person) }
                        }
                        if !nextPage.isEmpty {
                            Button("Cargar más") { Task { await loadAttendees(pageToken: nextPage) } }
                        }
                    }
                } else {
                    Section {
                        Text("Por privacidad, solo la persona organizadora puede consultar la lista de asistentes.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                Section {
                    Button(role: .destructive) { showReport = true } label: {
                        Label("Denunciar evento", systemImage: "flag")
                    }
                }
                if let message { Section { Text(LocalizedStringKey(message)).font(.caption).foregroundStyle(.secondary) } }
            }
            .task { do { interest = try await store.interested(in: event) } catch { message = PawlyServiceMessage.key(for: error) } }
            .sheet(isPresented: $edit, onDismiss: { if edited { onChange(); dismiss() } }) {
                PawlyEventCreateSheet(editing: event, store: store) { edited = true }
            }
            .navigationTitle("Evento")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Cerrar") { dismiss() } } }
            .confirmationDialog("Denunciar evento", isPresented: $showReport, titleVisibility: .visible) {
                ForEach(["Contenido inapropiado", "Spam o fraude", "Información falsa", "Bienestar animal", "Otro"], id: \.self) { reason in
                    Button(LocalizedStringKey(reason)) {
                        Task {
                            do { try await store.report(event, reason: reason); message = "Denuncia enviada." }
                            catch { message = PawlyServiceMessage.key(for: error) }
                        }
                    }
                }
            }
        }
    }

    @MainActor private func loadAttendees(pageToken: String = "") async {
        loadingAttendees = true
        defer { loadingAttendees = false }
        do {
            let result = try await store.loadAttendees(event: event, pageToken: pageToken)
            if pageToken.isEmpty { attendees = result.attendees }
            else { let seen = Set(attendees.map(\.id)); attendees += result.attendees.filter { !seen.contains($0.id) } }
            if let total = result.total { actualTotal = total }
            nextPage = result.nextPageToken
            message = nil
        } catch { message = PawlyServiceMessage.key(for: error) }
    }
}

private struct PawlyEventCreateSheet: View {
    @Environment(\.dismiss) private var dismiss
    let editing: PawlyEventItem?
    let store: PawlyEventsStore
    var onSave: () -> Void
    @State private var keepMedia = true
    @State private var title = ""
    @State private var description = ""
    @State private var category = "Quedada"
    @State private var location = ""
    @State private var date = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var resolvedLocation = ""
    @State private var showLocationPicker = false
    @StateObject private var attachment = PawlyAttachmentDraft(maximumDuration: 60)
    @State private var saving = false
    @State private var error: String?

    init(editing: PawlyEventItem?, store: PawlyEventsStore, onSave: @escaping () -> Void) {
        self.editing = editing; self.store = store; self.onSave = onSave
        if let e = editing {
            _title = State(initialValue: e.title); _description = State(initialValue: e.description)
            _location = State(initialValue: e.location); _resolvedLocation = State(initialValue: e.location)
            _date = State(initialValue: e.startAt); _category = State(initialValue: e.category)
            if let lat = e.latitude, let lon = e.longitude { _coordinate = State(initialValue: .init(latitude: lat, longitude: lon)) }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Plan") {
                    TextField("Nombre del evento", text: $title)
                        .onChange(of: title) { title = String($0.prefix(90)) }
                    Picker("Tipo", selection: $category) {
                        ForEach(["Quedada", "Ruta", "Taller", "Adopción"], id: \.self) { Text(LocalizedStringKey($0)) }
                    }
                    TextField("Descripción", text: $description, axis: .vertical).lineLimit(3...7)
                    TextField("Lugar o dirección", text: $location)
                }
                Section("Foto o vídeo") {
                    if keepMedia, !attachment.hasAttachment, let event = editing, !event.photoURL.isEmpty || !event.videoURL.isEmpty {
                        if let url = URL(string: event.photoURL), !event.photoURL.isEmpty {
                            AsyncImage(url: url) { $0.resizable().scaledToFit() } placeholder: { ProgressView() }.frame(maxHeight: 160).pawlyPhoto(url)
                        } else if let url = URL(string: event.videoURL) { PawlyVideoPreview(url: url).frame(height: 160) }
                        Button("Quitar", role: .destructive) { keepMedia = false }
                    }
                    PawlyAttachmentPicker(draft: attachment)
                    if saving { ProgressView() }
                }
                Section("Fecha y hora") {
                    DatePicker("Comienza", selection: $date, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                }
                Section("Ubicación en el mapa (opcional)") {
                    Button { showLocationPicker=true } label: {
                        Label(coordinate == nil || location != resolvedLocation ? "Buscar dirección en el mapa" : "Cambiar ubicación en el mapa",systemImage:"mappin.and.ellipse")
                    }.buttonStyle(.borderless)
                    if let coordinate, location == resolvedLocation {
                        Map(coordinateRegion:.constant(MKCoordinateRegion(center:coordinate,latitudinalMeters:1200,longitudinalMeters:1200)))
                            .frame(height:155).clipShape(RoundedRectangle(cornerRadius:12)).allowsHitTesting(false)
                            .overlay { Image(systemName:"mappin.circle.fill").font(.title).foregroundStyle(PawlyColors.primary) }
                    }
                    if coordinate != nil {
                        Button(role: .destructive) {
                            coordinate = nil
                        } label: { Text("Quitar coordenadas") }
                    }
                    Text("Solo se guardan coordenadas del lugar del evento si lo localizas. No se comparte tu ubicación personal.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let error { Section { Text(LocalizedStringKey(error)).foregroundStyle(.red) } }
            }
            .disabled(saving).interactiveDismissDisabled(saving)
            .navigationTitle(editing == nil ? "Crear evento" : "Editar evento")
            .sheet(isPresented:$showLocationPicker) {
                PawlyMapPointPicker(query: location, point: location == resolvedLocation ? coordinate : nil, title: "Ubicación del evento"){name,point in
                    location=name;resolvedLocation=name;coordinate=point
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() }.disabled(saving) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Guardando…" : (editing == nil ? "Publicar" : "Guardar")) {
                        saving = true
                        Task {
                            defer { saving = false }
                            do {
                                guard let user = Auth.auth().currentUser, user.isEmailVerified else { throw PawlyEventError.emailVerificationRequired }
                                let media = try await attachment.upload(owner: user.uid, forPost: true)
                                let retain = !attachment.hasAttachment && keepMedia
                                try await store.save(editing: editing, title: title, description: description, category: category,
                                                     location: location, date: date, coordinate: location == resolvedLocation ? coordinate : nil,
                                                     photoURL: retain ? editing?.photoURL ?? "" : media.image,
                                                     videoURL: retain ? editing?.videoURL ?? "" : media.video)
                                onSave()
                                dismiss()
                            } catch { self.error = PawlyServiceMessage.key(for: error) }
                        }
                    }
                    .disabled(attachment.error != nil || attachment.loading || saving || description.count > 1200 || location.count > 160 || title.trimmingCharacters(in: .whitespacesAndNewlines).count < 4 ||
                              description.trimmingCharacters(in: .whitespacesAndNewlines).count < 10 ||
                              location.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 || date <= Date())
                }
            }
        }
    }

}
