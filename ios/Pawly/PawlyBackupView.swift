import SwiftUI
import UniformTypeIdentifiers
import FirebaseAuth
import FirebaseFirestore

@MainActor
enum PawlyBackupManager {
    static func exportBackup() async throws -> URL {
        guard let uid = Auth.auth().currentUser?.uid else { throw PawlyBackupError.notSignedIn }
        let db = Firestore.firestore()
        let userRef = db.collection("users").document(uid)
        let userSnapshot = try await userRef.getDocument()
        guard let profile = userSnapshot.data() else { throw PawlyBackupError.profileMissing }

        let rawPets = profile["dogProfiles"] as? [[String: Any]] ?? []
        var pets: [[String: Any]] = []
        for rawPet in rawPets {
            guard let petID = rawPet["id"] as? String, PawlyBackupPolicy.validID(petID) else { throw PawlyBackupError.invalidFormat }
            let privateRoot = userRef.collection("petCare").document(petID)
            let privateCare: [String: Any] = [
                "passport": try await exportCollection(privateRoot.collection("passport")),
                "tasks": try await exportCollection(privateRoot.collection("tasks")),
                "careLogs": try await exportCollection(privateRoot.collection("careLogs"))
            ]

            let sharedRoot = db.collection("sharedPets").document("\(uid)_\(petID)")
            let sharedSnapshot = try await sharedRoot.getDocument()
            var shared: [String: Any]?
            if sharedSnapshot.exists, (sharedSnapshot.get("ownerUid") as? String) == uid {
                let profileDoc = try await sharedRoot.collection("profile").document("current").getDocument()
                shared = [
                    "root": jsonSafe(sharedSnapshot.data() ?? [:]),
                    "profile": jsonSafe(profileDoc.data() ?? [:]),
                    "passport": try await exportCollection(sharedRoot.collection("passport")),
                    "tasks": try await exportCollection(sharedRoot.collection("tasks")),
                    "careLogs": try await exportCollection(sharedRoot.collection("careLogs")),
                    "agenda": try await exportCollection(sharedRoot.collection("agenda")),
                    "diary": try await exportCollection(sharedRoot.collection("diary")),
                    "expenses": try await exportCollection(sharedRoot.collection("expenses"))
                ]
            }

            var item: [String: Any] = [
                "profile": jsonSafe(rawPet),
                "privateCare": privateCare
            ]
            if let shared { item["shared"] = shared }
            item["petId"] = petID
            pets.append(item)
        }

        let exploreCollections = try await exportCollection(userRef.collection("exploreCollections"))
        let privateContact = try await userRef.collection("privateData").document("professionalContact").getDocument()
        let privateHome = try await userRef.collection("privateData").document("home").getDocument()
        let reminderData = PawlyReminderStore.shared.exportBackupData()?.base64EncodedString() ?? ""
        let savedPlacesData = try JSONEncoder().encode(try await PawlySavedPlacesStore.snapshot(for: uid))

        let envelope: [String: Any] = [
            "format": PawlyBackupPolicy.format,
            "version": PawlyBackupPolicy.version,
            "exportedAt": Int64(Date().timeIntervalSince1970 * 1000),
            "uid": uid,
            "profile": jsonSafe(profile.filter { PawlyBackupPolicy.profileFields.contains($0.key) }),
            "privateProfessionalContact": privateContact.get("value") as? String ?? "",
            "privateHome": privateHome.exists ? jsonSafe(privateHome.data() ?? [:]) : [:],
            "pets": pets,
            "exploreCollections": exploreCollections.filter { $0["id"] as? String != "ios-favourites" },
            "localRemindersBase64": reminderData,
            "localSavedPlacesBase64": savedPlacesData.base64EncodedString()
        ]

        let data = try PawlyBackupPolicy.encode(envelope)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd-HHmm"
        let file = FileManager.default.temporaryDirectory
            .appendingPathComponent("Pawly-backup-\(formatter.string(from: Date())).json")
        try data.write(to: file, options: .atomic)
        return file
    }

    static func restoreBackup(from data: Data) async throws -> PawlyBackupReport {
        guard let uid = Auth.auth().currentUser?.uid else { throw PawlyBackupError.notSignedIn }
        let payload = try PawlyBackupPolicy.decode(data, for: uid)
        let reminders = try localValues(PawlyLocalReminder.self, key: "localRemindersBase64", in: payload)
        guard reminders.count <= 500, reminders.allSatisfy({ PawlyBackupPolicy.validID($0.id) }) else { throw PawlyBackupError.invalidFormat }
        let localPlaces = try localValues(PawlySavedPlace.self, key: "localSavedPlacesBase64", in: payload)
        let collections = payload["exploreCollections"] as? [[String: Any]] ?? []
        let legacy = collections.first { $0["id"] as? String == "ios-favourites" }?["data"] as? [String: Any]
        let oldPlaces = try PawlySavedPlace.decodeEntries(legacy?["entries"] as? [String] ?? [])
        let savedPlaces = PawlyBackupPolicy.addingMissing(oldPlaces, to: localPlaces) { $0.id }
        guard savedPlaces.count <= 100 else { throw PawlyBackupError.invalidFormat }

        let db = Firestore.firestore()
        let userRef = db.collection("users").document(uid)
        if let raw = payload["profile"] as? [String: Any], let source = restoreValue(raw) as? [String: Any] {
            _ = try await db.runTransaction { transaction, failure -> Any? in
                do {
                    let current = try transaction.getDocument(userRef)
                    guard let fields = current.data() else { throw PawlyBackupError.profileMissing }
                    var changes = source.filter { PawlyBackupPolicy.profileFields.contains($0.key) && fields[$0.key] == nil }
                    if let imported = source["dogProfiles"] as? [[String: Any]] {
                        let existing = fields["dogProfiles"] as? [[String: Any]] ?? []
                        guard fields["dogProfiles"] == nil || fields["dogProfiles"] is [[String: Any]] else {
                            throw PawlyBackupError.invalidFormat
                        }
                        let merged = PawlyBackupPolicy.addingMissing(imported, to: existing) { $0["id"] as? String }
                        if merged.count > existing.count { changes["dogProfiles"] = merged }
                    }
                    if !changes.isEmpty { transaction.updateData(changes, forDocument: userRef) }
                } catch { failure?.pointee = error as NSError }
                return nil
            }
        }
        if let contact = payload["privateProfessionalContact"] as? String, !contact.isEmpty {
            _ = try await insertMissing([(userRef.collection("privateData").document("professionalContact"), ["value": String(contact.prefix(120))])])
        }
        if let home = payload["privateHome"] as? [String: Any], !home.isEmpty,
           var fields = restoreValue(home) as? [String: Any] {
            fields["updatedAt"] = FieldValue.serverTimestamp()
            _ = try await insertMissing([(userRef.collection("privateData").document("home"), fields)])
        }

        var restoredDocuments = 0
        var skippedCareLogs = 0
        let petItems = payload["pets"] as? [[String: Any]] ?? []
        for item in petItems {
            guard let petID = item["petId"] as? String, !petID.isEmpty else { continue }
            let privateRoot = userRef.collection("petCare").document(petID)
            if let privateCare = item["privateCare"] as? [String: Any] {
                restoredDocuments += try await restoreCollection(privateCare["passport"], to: privateRoot.collection("passport"))
                restoredDocuments += try await restoreCollection(privateCare["tasks"], to: privateRoot.collection("tasks"))
                // Care logs are immutable audit records whose rules require a new
                // server timestamp and the original acting user. They are exported
                // for safekeeping but not forged or re-created by another account.
                skippedCareLogs += (privateCare["careLogs"] as? [[String: Any]])?.count ?? 0
            }

            guard let shared = item["shared"] as? [String: Any] else { continue }
            let sharedRoot = db.collection("sharedPets").document("\(uid)_\(petID)")
            let rootSnapshot = try await sharedRoot.getDocument()
            guard rootSnapshot.exists, (rootSnapshot.get("ownerUid") as? String) == uid else { continue }

            restoredDocuments += try await restoreCollection(shared["passport"], to: sharedRoot.collection("passport"))
            restoredDocuments += try await restoreCollection(shared["tasks"], to: sharedRoot.collection("tasks"))
            restoredDocuments += try await restoreAgenda(shared["agenda"], to: sharedRoot.collection("agenda"), uid: uid)
            restoredDocuments += try await restoreDiary(shared["diary"], to: sharedRoot.collection("diary"))
            restoredDocuments += try await restoreExpenses(shared["expenses"], to: sharedRoot.collection("expenses"), uid: uid)
            skippedCareLogs += (shared["careLogs"] as? [[String: Any]])?.count ?? 0

            if let rawProfile = shared["profile"] as? [String: Any] {
                let profile = restoreValue(rawProfile) as? [String: Any] ?? [:]
                if let name = profile["name"] as? String, !name.isEmpty {
                    _ = try await insertMissing([(sharedRoot.collection("profile").document("current"), [
                        "petId": petID,
                        "name": String(name.prefix(40)),
                        "photoUrl": profile["photoUrl"] ?? NSNull(),
                        "updatedAt": FieldValue.serverTimestamp()
                    ])])
                }
            }
        }

        restoredDocuments += try await restoreExploreCollections(
            collections.filter { $0["id"] as? String != "ios-favourites" },
            to: userRef.collection("exploreCollections")
        )

        guard Auth.auth().currentUser?.uid == uid else { throw PawlyBackupError.accountMismatch }
        if !reminders.isEmpty { try PawlyReminderStore.shared.importBackupData(JSONEncoder().encode(reminders)) }
        if !savedPlaces.isEmpty {
            try await PawlySavedPlacesStore.restore(savedPlaces, for: uid)
        }

        return PawlyBackupReport(
            petCount: petItems.count,
            restoredDocuments: restoredDocuments,
            skippedCareLogs: skippedCareLogs
        )
    }

    private static func exportCollection(_ collection: CollectionReference) async throws -> [[String: Any]] {
        let snapshot = try await collection.getDocuments()
        return snapshot.documents.map { document in
            [
                "id": document.documentID,
                "data": jsonSafe(document.data())
            ]
        }
    }

    private static func restoreCollection(_ raw: Any?, to collection: CollectionReference) async throws -> Int {
        let records = raw as? [[String: Any]] ?? []
        var writes: [(DocumentReference, [String: Any])] = []
        for record in records {
            guard let id = record["id"] as? String,
                  let rawFields = record["data"] as? [String: Any] else { continue }
            let fields = restoreValue(rawFields) as? [String: Any] ?? [:]
            guard !fields.isEmpty else { continue }
            writes.append((collection.document(id), fields))
        }
        return try await insertMissing(writes)
    }

    private static func restoreAgenda(_ raw: Any?, to collection: CollectionReference, uid: String) async throws -> Int {
        let records = raw as? [[String: Any]] ?? []
        var writes: [(DocumentReference, [String: Any])] = []
        for record in records {
            guard let id = record["id"] as? String,
                  var fields = restoreValue(record["data"] as? [String: Any] ?? [:]) as? [String: Any],
                  let title = fields["title"] as? String,
                  let date = fields["date"] else { continue }
            fields["id"] = id
            fields["title"] = String(title.prefix(100))
            fields["date"] = date
            guard fields["createdBy"] as? String == uid else { continue }
            fields["updatedAt"] = FieldValue.serverTimestamp()
            writes.append((collection.document(id), fields))
        }
        return try await insertMissing(writes)
    }

    private static func restoreDiary(_ raw: Any?, to collection: CollectionReference) async throws -> Int {
        let records = raw as? [[String: Any]] ?? []
        var writes: [(DocumentReference, [String: Any])] = []
        for record in records {
            guard let id = record["id"] as? String,
                  let fields = restoreValue(record["data"] as? [String: Any] ?? [:]) as? [String: Any],
                  let text = fields["text"] as? String,
                  let position = fields["position"] else { continue }
            writes.append((collection.document(id), [
                "id": id, "text": String(text.prefix(12_000)),
                "position": position, "updatedAt": FieldValue.serverTimestamp()
            ]))
        }
        return try await insertMissing(writes)
    }

    private static func restoreExploreCollections(_ raw: Any?, to collection: CollectionReference) async throws -> Int {
        let records = raw as? [[String: Any]] ?? []
        var writes: [(DocumentReference, [String: Any])] = []
        for record in records {
            guard let id = record["id"] as? String,
                  let rawData = record["data"] as? [String: Any],
                  let fields = restoreValue(rawData) as? [String: Any],
                  let name = fields["name"] as? String,
                  let entries = fields["entries"] as? [String]
            else { continue }
            let cleanName = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(32))
            guard !cleanName.isEmpty else { continue }
            writes.append((collection.document(id), [
                "name": cleanName,
                "description": String((fields["description"] as? String ?? "").prefix(120)),
                "entries": Array(entries.prefix(100)),
                "updatedAt": FieldValue.serverTimestamp()
            ]))
        }
        return try await insertMissing(writes)
    }

    private static func restoreExpenses(_ raw: Any?, to collection: CollectionReference, uid: String) async throws -> Int {
        let records = raw as? [[String: Any]] ?? []
        var writes: [(DocumentReference, [String: Any])] = []
        for record in records {
            guard let id = record["id"] as? String,
                  let fields = restoreValue(record["data"] as? [String: Any] ?? [:]) as? [String: Any],
                  let kind = fields["kind"] as? String,
                  let title = fields["title"] as? String,
                  let amount = fields["amount"],
                  let date = fields["date"], fields["createdBy"] as? String == uid else { continue }
            writes.append((collection.document(id), [
                "id": id, "kind": kind, "title": String(title.prefix(80)),
                "detail": String((fields["detail"] as? String ?? "").prefix(500)),
                "amount": amount, "date": date, "createdBy": uid,
                "updatedAt": FieldValue.serverTimestamp()
            ]))
        }
        return try await insertMissing(writes)
    }

    private static func localValues<T: Decodable>(_ type: T.Type, key: String, in payload: [String: Any]) throws -> [T] {
        guard let raw = payload[key] else { return [] }
        guard let encoded = raw as? String else { throw PawlyBackupError.invalidFormat }
        if encoded.isEmpty { return [] }
        guard let data = Data(base64Encoded: encoded) else { throw PawlyBackupError.invalidFormat }
        return try JSONDecoder().decode([T].self, from: data)
    }

    private static func insertMissing(_ writes: [(DocumentReference, [String: Any])]) async throws -> Int {
        var count = 0
        // Read the whole group before queuing writes. Firestore retries if a device creates a record concurrently.
        for start in stride(from: 0, to: writes.count, by: 20) {
            let group = Array(writes[start..<min(start + 20, writes.count)])
            let result = try await Firestore.firestore().runTransaction { transaction, failure -> Any? in
                do {
                    let missing = try group.filter { try !transaction.getDocument($0.0).exists }
                    for (ref, data) in missing { transaction.setData(data, forDocument: ref) }
                    return missing.count
                } catch { failure?.pointee = error as NSError; return nil }
            }
            count += result as? Int ?? 0
        }
        return count
    }

    private static func jsonSafe(_ value: Any) -> Any {
        if let timestamp = value as? Timestamp {
            return ["__pawlyTimestampMillis": Int64(timestamp.dateValue().timeIntervalSince1970 * 1000)]
        }
        if let date = value as? Date {
            return ["__pawlyDateMillis": Int64(date.timeIntervalSince1970 * 1000)]
        }
        if let dictionary = value as? [String: Any] {
            return dictionary.mapValues { jsonSafe($0) }
        }
        if let array = value as? [Any] {
            return array.map { jsonSafe($0) }
        }
        if value is NSNull { return NSNull() }
        if let string = value as? String { return string }
        if let number = value as? NSNumber { return number }
        return String(describing: value)
    }

    private static func restoreValue(_ value: Any) -> Any {
        if let dictionary = value as? [String: Any] {
            if let millis = dictionary["__pawlyTimestampMillis"] as? NSNumber {
                return Timestamp(date: Date(timeIntervalSince1970: millis.doubleValue / 1000))
            }
            if let millis = dictionary["__pawlyDateMillis"] as? NSNumber {
                return Date(timeIntervalSince1970: millis.doubleValue / 1000)
            }
            return dictionary.mapValues { restoreValue($0) }
        }
        if let array = value as? [Any] { return array.map { restoreValue($0) } }
        return value
    }
}

struct PawlyBackupReport {
    let petCount: Int
    let restoredDocuments: Int
    let skippedCareLogs: Int
}

struct PawlyBackupView: View {
    @State private var exportURL: URL?
    @State private var importing = false
    @State private var busy = false
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        Form {
            Section("Copia de seguridad") {
                Text("Exporta los datos de tu cuenta y de las mascotas que administras. El archivo incluye perfiles, pasaporte, rutinas, agenda, diario, gastos, favoritos y recordatorios locales.")
                    .font(.subheadline).foregroundStyle(.secondary)
                Button {
                    Task { await createBackup() }
                } label: {
                    HStack {
                        if busy { ProgressView() }
                        Label("Crear copia de seguridad", systemImage: "square.and.arrow.up")
                    }
                }.disabled(busy)
                if let exportURL {
                    ShareLink(item: exportURL) {
                        Label("Compartir o guardar el archivo", systemImage: "square.and.arrow.up.on.square")
                    }
                }
            }
            Section("Restaurar") {
                Button {
                    importing = true
                } label: {
                    Label("Seleccionar archivo de copia", systemImage: "square.and.arrow.down")
                }.disabled(busy)
                Text("La restauración solo acepta copias de esta misma cuenta y combina los datos recuperables sin borrar registros que ya existan.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Section("Qué debes saber") {
                Text("Las fotografías y vídeos se conservan como enlaces de Cloudinary, no se descargan dentro del archivo. Los registros de actividad de cuidados se incluyen para consulta, pero no se recrean porque las reglas de seguridad exigen conservar la autoría original.")
                    .font(.caption).foregroundStyle(.secondary)
                Text("La copia no incluye credenciales, contraseñas ni el archivo de configuración de Firebase.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if let message {
                Section { Text(LocalizedStringKey(message)).foregroundStyle(isError ? .red : PawlyColors.primary) }
            }
        }
        .navigationTitle("Copias de seguridad")
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                Task { await restoreBackup(url) }
            case .failure(let error):
                message = PawlyServiceMessage.key(for: error)
                isError = true
            }
        }
    }

    @MainActor private func createBackup() async {
        busy = true
        defer { busy = false }
        do {
            exportURL = try await PawlyBackupManager.exportBackup()
            message = "Copia creada correctamente. Guárdala en un lugar seguro."
            isError = false
        } catch {
            message = PawlyServiceMessage.key(for: error)
            isError = true
        }
    }

    @MainActor private func restoreBackup(_ url: URL) async {
        busy = true
        defer { busy = false }
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        do {
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize
            guard let size, size <= PawlyBackupPolicy.maximumBytes else { throw PawlyBackupError.tooLarge }
            let data = try Data(contentsOf: url, options: .mappedIfSafe)
            let report = try await PawlyBackupManager.restoreBackup(from: data)
            message = "Restauración finalizada: \(report.petCount) mascotas revisadas y \(report.restoredDocuments) registros incorporados. \(report.skippedCareLogs) registros de actividad no se han recreado por seguridad."
            isError = false
        } catch {
            message = PawlyServiceMessage.key(for: error)
            isError = true
        }
    }
}
