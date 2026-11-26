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
}

struct PawlyEventAttendee: Identifiable {
    var id: String
    var displayName: String
    var username: String
    var photoURL: String
}

@MainActor
final class PawlyEventsStore: ObservableObject {
    @Published private(set) var events: [PawlyEventItem] = []
    @Published var errorMessage: String?
    private let db = Firestore.firestore()
    private let categories = ["Quedada", "Ruta", "Taller", "Adopción"]

    func load() async {
        guard let uid = Auth.auth().currentUser?.uid else {
            errorMessage = "Inicia sesión para consultar los eventos."
            return
        }
        do {
            let snapshot = try await db.collection("events")
                .order(by: "createdAt", descending: true)
                .limit(to: 60)
                .getDocuments()

            var values: [PawlyEventItem] = []
            for document in snapshot.documents {
                guard
                    let startAt = (document.get("startAt") as? Timestamp)?.dateValue(),
                    let title = document.get("title") as? String,
                    let description = document.get("description") as? String,
                    let location = document.get("location") as? String
                else { continue }

                let interest = try await document.reference.collection("interested")
                    .document(uid).getDocument()

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
                    interestedByMe: interest.exists, photoURL: document.get("photoUrl") as? String ?? ""
                ))
            }
            events = values.sorted { $0.startAt < $1.startAt }
            errorMessage = nil
        } catch {
            errorMessage = PawlyServiceMessage.key(for: error)
        }
    }

    func create(
        title: String,
        description: String,
        category: String,
        location: String,
        date: Date,
        coordinate: CLLocationCoordinate2D?,
        photoURL: String
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

        let profile = try await db.collection("users").document(user.uid).getDocument()
        let displayName = (profile.get("displayName") as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? (profile.get("username") as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? "Usuario Pawly"

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
            "timeZoneId": TimeZone.current.identifier,
            "organizerUid": user.uid,
            "organizerName": String(displayName.prefix(60)),
            "interestedCount": 0,
            "createdAt": FieldValue.serverTimestamp()
        ]
        if let coordinate {
            data["latitude"] = coordinate.latitude
            data["longitude"] = coordinate.longitude
        }
        if !photoURL.isEmpty {
            data["photoUrl"] = photoURL
            data["photoAssetId"] = PawlyCloudinaryUploader.indexID(for: photoURL)
        }
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
        await load()
    }

    func loadAttendees(event: PawlyEventItem, pageToken: String = "") async throws -> (attendees: [PawlyEventAttendee], nextPageToken: String) {
        guard Auth.auth().currentUser?.uid == event.organizerUID else {
            throw PawlyEventError.attendeesOrganizerOnly
        }
        let response = try await PawlyBackendClient.postJSON(
            path: "/v1/events/attendees",
            body: ["eventId": event.id, "pageToken": pageToken]
        )
        let rows = response["attendees"] as? [[String: Any]] ?? []
        let people = rows.compactMap { row -> PawlyEventAttendee? in
            guard let id = row["uid"] as? String, !id.isEmpty else { return nil }
            return PawlyEventAttendee(
                id: id,
                displayName: row["displayName"] as? String ?? "Usuario Pawly",
                username: row["username"] as? String ?? "",
                photoURL: row["photoUrl"] as? String ?? ""
            )
        }
        return (people, response["nextPageToken"] as? String ?? "")
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

struct PawlyEventsView: View {
    @StateObject private var store = PawlyEventsStore()
    @State private var selectedCategory = "Todos"
    @State private var selectedEvent: PawlyEventItem?
    @State private var showCreate = false
    @State private var createMessage: String?
    @State private var created = false
    @State private var showReport = false

    @State private var query = ""
    @State private var days = 0
    @State private var ascending = true

    private var visibleEvents: [PawlyEventItem] {
        let cutoff = Calendar.current.date(byAdding: .day, value: days, to: Date()) ?? Date()
        return store.events.filter { event in
            (selectedCategory == "Todos" || event.category == selectedCategory)
                && (days == 0 || event.startAt <= cutoff)
                && (query.isEmpty || [event.title, event.description, event.location].contains { $0.localizedCaseInsensitiveContains(query) })
        }.sorted { ascending ? $0.startAt < $1.startAt : $0.startAt > $1.startAt }
    }

    var body: some View {
        List {
            Section {
                Menu {
                    Picker("Tipo de evento", selection: $selectedCategory) {
                        ForEach(["Todos", "Quedada", "Ruta", "Taller", "Adopción"], id: \.self) { Text(LocalizedStringKey($0)).tag($0) }
                    }
                    Picker("Fecha", selection: $days) {
                        Text("Todas las fechas").tag(0)
                        Text("Próximos 7 días").tag(7)
                        Text("Próximos 30 días").tag(30)
                    }
                    Toggle("Más próximos primero", isOn: $ascending)
                } label: { Label("Filtrar", systemImage: "line.3.horizontal.decrease.circle") }
            }
            Section("Próximos planes") {
                if visibleEvents.isEmpty {
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
                                Text(event.category.uppercased())
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
                                Spacer()
                                Label("\(event.interestedCount)", systemImage: "person.2")
                            }
                            .font(.caption).foregroundStyle(PawlyColors.primary)
                        }
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                }
            }
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
                Button { showCreate = true } label: { Image(systemName: "plus") }
            }
        }
        .task { await store.load() }
        .refreshable { await store.load() }
        .sheet(isPresented: $showCreate, onDismiss: {
            if created { createMessage = "Evento publicado."; created = false; Task { await store.load() } }
        }) {
            PawlyEventCreateSheet { title, description, category, location, date, coordinate, photoURL in
                try await store.create(title: title, description: description, category: category,
                                       location: location, date: date, coordinate: coordinate, photoURL: photoURL)
                created = true
            }
        }
        .sheet(item: $selectedEvent) { event in
            PawlyEventDetailsSheet(event: event, store: store)
        }
    }
}

private struct PawlyEventDetailsSheet: View {
    @Environment(\.dismiss) private var dismiss
    let event: PawlyEventItem
    @ObservedObject var store: PawlyEventsStore
    @State private var attendees: [PawlyEventAttendee] = []
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
                    Text(event.title).font(.title2.bold())
                    Text(event.description)
                    Label(event.category, systemImage: "calendar")
                    Label(event.location, systemImage: "mappin.and.ellipse")
                    Label(event.startAt.formatted(date: .complete, time: .shortened), systemImage: "clock")
                    Label("\(event.interestedCount) personas interesadas", systemImage: "person.2")
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
                            do { try await store.toggleInterest(event); dismiss() }
                            catch { message = PawlyServiceMessage.key(for: error) }
                        }
                    } label: {
                        Label(event.interestedByMe ? "Ya no me interesa" : "Me interesa",
                              systemImage: event.interestedByMe ? "person.badge.minus" : "person.badge.plus")
                    }
                }
                if isOrganizer {
                    Section("Personas interesadas") {
                        Button {
                            Task { await loadAttendees() }
                        } label: {
                            HStack {
                                if loadingAttendees { ProgressView() }
                                Text(attendees.isEmpty ? "Ver asistentes" : "Actualizar asistentes")
                            }
                        }.disabled(loadingAttendees)
                        ForEach(attendees) { person in
                            NavigationLink { PawlyPublicProfile(userID: person.id) } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(person.displayName)
                                if !person.username.isEmpty {
                                    Text("@\(person.username)").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            }
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
            else { attendees += result.attendees }
            nextPage = result.nextPageToken
            message = nil
        } catch { message = PawlyServiceMessage.key(for: error) }
    }
}

private struct PawlyEventCreateSheet: View {
    @Environment(\.dismiss) private var dismiss
    var onSave: (String, String, String, String, Date, CLLocationCoordinate2D?, String) async throws -> Void
    @State private var title = ""
    @State private var description = ""
    @State private var category = "Quedada"
    @State private var location = ""
    @State private var date = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var resolvedLocation = ""
    @State private var showLocationPicker = false
    @StateObject private var attachment = PawlyAttachmentDraft()
    @State private var saving = false
    @State private var error: String?

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
                Section("Fotografía") { PawlyAttachmentPicker(draft: attachment, allowsVideo: false) }
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
            .navigationTitle("Crear evento")
            .sheet(isPresented:$showLocationPicker) {
                PawlyEventLocationPicker(initialQuery:location){name,point in
                    location=name;resolvedLocation=name;coordinate=point
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() }.disabled(saving) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Guardando…" : "Publicar") {
                        saving = true
                        Task {
                            defer { saving = false }
                            do {
                                guard let user = Auth.auth().currentUser, user.isEmailVerified else { throw PawlyEventError.emailVerificationRequired }
                                let media = try await attachment.upload(owner: user.uid, forPost: true)
                                try await onSave(title, description, category, location, date,
                                                 location == resolvedLocation ? coordinate : nil, media.image)
                                dismiss()
                            } catch { self.error = PawlyServiceMessage.key(for: error) }
                        }
                    }
                    .disabled(attachment.loading || saving || description.count > 1200 || location.count > 160 || title.trimmingCharacters(in: .whitespacesAndNewlines).count < 4 ||
                              description.trimmingCharacters(in: .whitespacesAndNewlines).count < 10 ||
                              location.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 || date <= Date())
                }
            }
        }
    }

}

private struct PawlyEventLocationPicker:View {
    @Environment(\.dismiss) private var dismiss
    @State private var query:String
    @State private var results:[MKMapItem]=[]
    @State private var selected:MKMapItem?
    @State private var region=MKCoordinateRegion()
    @State private var busy=false
    @State private var message:String?
    let onSelect:(String,CLLocationCoordinate2D)->Void

    init(initialQuery:String,onSelect:@escaping(String,CLLocationCoordinate2D)->Void) {
        _query=State(initialValue:initialQuery);self.onSelect=onSelect
    }
    var body:some View {
        NavigationStack {
            List {
                Section("Buscar lugar o dirección") {
                    TextField("Lugar, calle o localidad",text:$query).onSubmit{Task{await search()}}
                    Button {Task{await search()}}label:{Label("Buscar en el mapa",systemImage:"magnifyingglass")}
                        .disabled(busy || query.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty)
                    if busy { ProgressView() }
                    if let message {Text(LocalizedStringKey(message)).font(.caption).foregroundStyle(.secondary)}
                }
                if let selected {
                    Section("Ajusta el punto del evento") {
                        Map(coordinateRegion:$region)
                            .frame(height:230)
                            .overlay { Image(systemName:"mappin.circle.fill").font(.largeTitle).foregroundStyle(PawlyColors.primary).allowsHitTesting(false) }
                        Text("Desplaza el mapa para ajustar el punto central. Solo se guardará la ubicación del evento.").font(.caption)
                        Button("Usar este punto") {
                            let name=selected.placemark.title ?? selected.name ?? query
                            onSelect(name,region.center);dismiss()
                        }.buttonStyle(.borderedProminent)
                    }
                }
                Section("Resultados") {
                    ForEach(Array(results.enumerated()),id:\.offset){_,item in
                        Button {
                            selected=item
                            region=MKCoordinateRegion(center:item.placemark.coordinate,latitudinalMeters:1200,longitudinalMeters:1200)
                        } label: {
                            VStack(alignment:.leading){Text(item.name ?? "Lugar");Text(item.placemark.title ?? "").font(.caption).foregroundStyle(.secondary)}
                        }.buttonStyle(.borderless)
                    }
                }
            }.navigationTitle("Ubicación del evento")
            .toolbar{ToolbarItem(placement:.cancellationAction){Button("Cancelar"){dismiss()}}}
            .task {if !query.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty {await search()}}
        }
    }
    @MainActor private func search() async {
        guard !busy,!query.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty else{return}
        busy=true;message=nil;selected=nil;results=[];defer{busy=false}
        let request=MKLocalSearch.Request();request.naturalLanguageQuery=query
        do {
            results=try await MKLocalSearch(request:request).start().mapItems
            if let first=results.first {
                selected=first
                region=MKCoordinateRegion(center:first.placemark.coordinate,latitudinalMeters:1200,longitudinalMeters:1200)
            } else {message="No se encontraron lugares. Prueba una dirección más concreta."}
        } catch { message=PawlyServiceMessage.key(for: error) }
    }
}
