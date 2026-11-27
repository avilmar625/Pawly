import Foundation
import Combine
import FirebaseAuth
import FirebaseFirestore

struct PawlyPet: Identifiable, Hashable {
    var id: String
    var name: String
    var breed: String
    var age: String
    var description: String
    var photoURL: String
    var species: String
    var ownerUID: String? = nil
    var isShared: Bool = false
}
struct PawlyPassportEntry: Identifiable {
    var id: String
    var type: String
    var title: String
    var detail: String
    var date: String
    var product: String = ""
    var dose: String = ""
    var frequency: String = ""
    var clinic: String = ""
    var nextDoseDate: String = ""
    var status: String = "REALIZADO"
    var reminderEnabled: Bool = false
    var reminderTime: String = "09:00"
    var createdAtOrder: Int = 0
}
struct PawlyCareTask: Identifiable {
    var id: String
    var title: String
    var responsible: String
    var frequency: String
    var completed: Bool
    var dose: String = ""
    var time: String = ""
    var completedOn: String = ""
    var action: String = "Otro"
    var daysOfWeek: [Int]? = nil
}
struct PawlyDiaryEntry: Identifiable {
    var id: String; var text: String; var title: String; var category: String; var photos: [String]; var date: Date?; var position: Int; var updatedAt: Date?; var videoURLs: [String] = []
}
struct PawlyCareLogEntry: Identifiable {
    var id: String
    var taskTitle: String
    var action: String
    var actorUID: String
    var actorName: String
    var occurredAt: Date?
    var note: String
    var distanceKm: Double?
    var mapURL: String
    var description: String
    var walker: String
    var photoURLs: [String]
    var videoURL = ""
}
struct PawlyPetInvitation:Identifiable {
    var id:String;var sharedPetID:String;var petName:String;var ownerName:String;var ownerUID:String;var role:String
}

@MainActor
final class PawlyPetStore: ObservableObject {
    @Published private(set) var pets: [PawlyPet] = []
    @Published var selectedPetID: String? {
        didSet {
            if let uid { UserDefaults.standard.set(selectedPetID, forKey: Self.selectionKey(uid)) }
        }
    }
    @Published var isLoading = false
    @Published var errorMessage: String?
    private let db = Firestore.firestore()
    private var uid: String? { Auth.auth().currentUser?.uid }
    private var petListener: ListenerRegistration?
    private var homeListener: ListenerRegistration?
    private var invitationListener: ListenerRegistration?
    private var sharedProfileListeners: [String: ListenerRegistration] = [:]
    private var isListening = false
    private var applyingRemoteSelection = false
    var selectedPet: PawlyPet? { pets.first(where: { $0.id == selectedPetID }) ?? pets.first }

    private static func selectionKey(_ uid: String) -> String { "pawly.selectedPetID." + uid }

    init() {
        if let uid = Auth.auth().currentUser?.uid {
            selectedPetID = UserDefaults.standard.string(forKey: Self.selectionKey(uid))
        }
    }

    deinit {
        petListener?.remove()
        homeListener?.remove()
        invitationListener?.remove()
        sharedProfileListeners.values.forEach { $0.remove() }
    }

    func startListening() {
        guard let uid else { return }
        isListening = true

        petListener?.remove()
        petListener = db.collection("users").document(uid).addSnapshotListener { [weak self] snapshot, error in
            guard let self, let snapshot, error == nil else { return }
            Task { @MainActor in
                let currentShared = self.pets.filter { $0.ownerUID != nil }
                let owned = self.decodeOwnedPets(snapshot.data()?["dogProfiles"] as? [[String: Any]] ?? [])
                self.pets = owned + currentShared
                self.syncSharedProfileListeners(uid: uid)
            }
        }

        homeListener?.remove()
        homeListener = db.collection("users").document(uid)
            .collection("privateData").document("home")
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self, let snapshot, error == nil else { return }
                Task { @MainActor in self.applyRemoteSelection(snapshot: snapshot) }
            }

        invitationListener?.remove()
        if Auth.auth().currentUser?.isEmailVerified == true {
            invitationListener = db.collection("petInvitations")
                .whereField("inviteeUid", isEqualTo: uid)
                .whereField("status", isEqualTo: "accepted")
                .addSnapshotListener { [weak self] _, error in
                    guard let self, error == nil else { return }
                    Task { @MainActor in
                        await self.loadPets()
                        self.syncSharedProfileListeners(uid: uid)
                    }
                }
        }
        syncSharedProfileListeners(uid: uid)
    }

    private func applyRemoteSelection(snapshot: DocumentSnapshot?) {
        guard !applyingRemoteSelection, let snapshot, snapshot.exists else { return }
        let selectedID = snapshot.get("selectedPetId") as? String
        let selectedName = snapshot.get("dogName") as? String
        let matching = selectedID.flatMap { id in pets.first(where: { $0.id == id }) }
            ?? selectedName.flatMap { name in pets.first(where: { $0.name == name }) }
        if let matching {
            applyingRemoteSelection = true
            selectedPetID = matching.id
            applyingRemoteSelection = false
        } else if !pets.contains(where: { $0.id == selectedPetID }) {
            selectedPetID = pets.first?.id
        }
    }

    private func syncSharedProfileListeners(uid: String) {
        guard isListening else { return }
        let sharedPets = pets.filter { $0.ownerUID != nil || $0.isShared }
        let desiredKeys = Set(sharedPets.map { "\($0.ownerUID ?? uid)_\($0.id)" })

        for key in Array(sharedProfileListeners.keys) where !desiredKeys.contains(key) {
            sharedProfileListeners[key]?.remove()
            sharedProfileListeners[key] = nil
        }

        for pet in sharedPets {
            let ownerUID = pet.ownerUID ?? uid
            let key = "\(ownerUID)_\(pet.id)"
            guard sharedProfileListeners[key] == nil else { continue }
            sharedProfileListeners[key] = db.collection("sharedPets").document(key)
                .collection("profile").document("current")
                .addSnapshotListener { [weak self] snapshot, error in
                    guard let self, let snapshot, error == nil, snapshot.exists else { return }
                    Task { @MainActor in
                        guard let index = self.pets.firstIndex(where: {
                            $0.id == pet.id && $0.ownerUID == pet.ownerUID
                        }) else { return }
                        let current = self.pets[index]
                        self.pets[index] = PawlyPet(
                            id: current.id,
                            name: snapshot.get("name") as? String ?? current.name,
                            breed: current.breed,
                            age: current.age,
                            description: current.description,
                            photoURL: snapshot.get("photoUrl") as? String ?? current.photoURL,
                            species: current.species,
                            ownerUID: current.ownerUID,
                            isShared: current.isShared
                        )
                    }
                }
        }
    }

    func stopListening() {
        isListening = false
        petListener?.remove()
        petListener = nil
        homeListener?.remove()
        homeListener = nil
        invitationListener?.remove()
        invitationListener = nil
        sharedProfileListeners.values.forEach { $0.remove() }
        sharedProfileListeners.removeAll()
    }

    private func decodeOwnedPets(_ raw: [[String: Any]]) -> [PawlyPet] {
        raw.compactMap { item -> PawlyPet? in
            guard let id = item["id"] as? String, let name = item["name"] as? String else { return nil }
            return PawlyPet(id:id,name:name,breed:item["breed"] as? String ?? "",
                age:item["age"] as? String ?? "",description:item["description"] as? String ?? "",
                photoURL:item["photoUrl"] as? String ?? "",species:item["species"] as? String ?? "Perro",
                isShared:item["isShared"] as? Bool ?? false)
        }
    }

    private func updateOwnedPetProfiles(
        _ transform: @escaping ([[String: Any]]) throws -> [[String: Any]]
    ) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        let userRef = db.collection("users").document(uid)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            db.runTransaction({ transaction, errorPointer -> Any? in
                do {
                    let snapshot = try transaction.getDocument(userRef)
                    let current = snapshot.data()?["dogProfiles"] as? [[String: Any]] ?? []
                    transaction.updateData(["dogProfiles": try transform(current)], forDocument: userRef)
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

    private func persistSelectedPet(_ pet: PawlyPet) {
        guard let uid, !applyingRemoteSelection else { return }
        selectedPetID = pet.id
        let names = pets.map { $0.name }
        db.collection("users").document(uid).collection("privateData").document("home").setData([
            "dogName": pet.name,
            "dogProfiles": names,
            "selectedPetId": pet.id,
            "updatedAt": FieldValue.serverTimestamp()
        ], merge: true) { [weak self] error in
            if let error { Task { @MainActor in self?.errorMessage = "No se pudo sincronizar la mascota seleccionada: \(error.localizedDescription)" } }
        }
    }

    func loadPets() async {
        guard let uid else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let snapshot = try await db.collection("users").document(uid).getDocument()
            let ownedPets = decodeOwnedPets(snapshot.data()?["dogProfiles"] as? [[String: Any]] ?? [])
            var sharedPets:[PawlyPet]=[]
            if Auth.auth().currentUser?.isEmailVerified == true {
                let accepted=try await db.collection("petInvitations").whereField("inviteeUid",isEqualTo:uid)
                    .whereField("status",isEqualTo:"accepted").limit(to:30).getDocuments()
                for invitation in accepted.documents {
                    guard let sharedID=invitation.get("sharedPetId") as? String else { continue }
                    let root=try await db.collection("sharedPets").document(sharedID).getDocument()
                    guard root.exists,let owner=root.get("ownerUid") as? String,let petID=root.get("petId") as? String else { continue }
                    let profile=try await root.reference.collection("profile").document("current").getDocument()
                    let name=profile.get("name") as? String ?? root.get("name") as? String ?? "Mascota compartida"
                    sharedPets.append(PawlyPet(id:petID,name:name,breed:"",age:"",description:"Perfil compartido",
                        photoURL:profile.get("photoUrl") as? String ?? "",species:"Mascota",ownerUID:owner))
                }
            }
            pets=ownedPets+sharedPets
            if isListening {
                syncSharedProfileListeners(uid: uid)
            }

            // Older builds may already have created the owner's sharedPet root
            // without setting the isShared marker in dogProfiles. Detect that
            // state on sign-in and migrate private care data before new writes.
            for ownedPet in ownedPets where !ownedPet.isShared {
                let root = db.collection("sharedPets").document("\(uid)_\(ownedPet.id)")
                let rootSnapshot = try await root.getDocument()
                guard rootSnapshot.exists,
                      rootSnapshot.get("ownerUid") as? String == uid else { continue }
                let ownerMembership = try await root.collection("members").document(uid).getDocument()
                guard ownerMembership.exists else { continue }
                try await enableSharedCareSync(for: ownedPet)
            }

            let home = try? await db.collection("users").document(uid).collection("privateData").document("home").getDocument()
            let selectedID = home?.get("selectedPetId") as? String
            let selectedName = home?.get("dogName") as? String
            let remoteSelection = selectedID.flatMap { id in pets.first(where: { $0.id == id }) }
                ?? selectedName.flatMap { name in pets.first(where: { $0.name == name }) }
            if let remoteSelection {
                applyingRemoteSelection = true
                selectedPetID = remoteSelection.id
                applyingRemoteSelection = false
            } else if !pets.contains(where: { $0.id == selectedPetID }) {
                selectedPetID = pets.first?.id
            }
            errorMessage = nil
        } catch { errorMessage = error.localizedDescription }
    }
    func select(_ pet: PawlyPet) { persistSelectedPet(pet) }

    func savePet(name: String, breed: String, age: String, species: String, description: String, photoURL: String = "") async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 40 else { throw PawlyStoreError.invalidPetName }
        let pet = PawlyPet(id: UUID().uuidString, name: clean, breed: breed.trimmingCharacters(in: .whitespacesAndNewlines),
                           age: age.trimmingCharacters(in: .whitespacesAndNewlines), description: description.trimmingCharacters(in: .whitespacesAndNewlines),
                           photoURL: photoURL, species: species)
        try await updateOwnedPetProfiles { current in
            current.filter { ($0["id"] as? String) != pet.id } + [self.encode(pet)]
        }
        let updatedOwned = pets.filter { $0.ownerUID == nil && $0.id != pet.id } + [pet]
        pets = updatedOwned + pets.filter { $0.ownerUID != nil }
        persistSelectedPet(pet)
    }
    func updatePet(_ original: PawlyPet, name: String, breed: String, age: String, species: String, description: String, photoURL: String) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 40 else { throw PawlyStoreError.invalidPetName }
        let updated = PawlyPet(id: original.id, name: clean,
            breed: breed.trimmingCharacters(in: .whitespacesAndNewlines),
            age: age.trimmingCharacters(in: .whitespacesAndNewlines),
            description: description.trimmingCharacters(in: .whitespacesAndNewlines),
            photoURL: photoURL, species: species, ownerUID: original.ownerUID, isShared: original.isShared)
        if let owner = original.ownerUID ?? (original.isShared ? uid : nil) {
            let root = db.collection("sharedPets").document("\(owner)_\(original.id)")
            let member = try await root.collection("members").document(uid).getDocument()
            guard member.get("role") as? String == "owner" || member.get("role") as? String == "editor" else {
                throw PawlyStoreError.noSharedAccess
            }
            let role = member.get("role") as? String ?? ""
            let profile = root.collection("profile").document("current")
            let batch = db.batch()
            if role == "owner" {
                batch.updateData(["name": clean, "updatedAt": FieldValue.serverTimestamp()], forDocument: root)
            }
            batch.setData([
                "petId": updated.id,
                "name": clean,
                "photoUrl": photoURL.isEmpty ? (NSNull() as Any) : (photoURL as Any),
                "updatedAt": FieldValue.serverTimestamp()
            ], forDocument: profile, merge: true)
            try await batch.commit()
            if owner == uid {
                try await updateOwnedPetProfiles { current in
                    var updatedProfiles = current
                    guard let index = updatedProfiles.firstIndex(where: { ($0["id"] as? String) == original.id }) else {
                        throw PawlyStoreError.petNotFound
                    }
                    var value = updatedProfiles[index]
                    value.merge(self.encode(updated)) { _, new in new }
                    updatedProfiles[index] = value
                    return updatedProfiles
                }
            }
            pets = pets.map { $0.id == original.id && ($0.ownerUID == owner || ($0.ownerUID == nil && owner == uid)) ? updated : $0 }
        } else {
            try await updateOwnedPetProfiles { current in
                var updatedProfiles = current
                guard let index = updatedProfiles.firstIndex(where: { ($0["id"] as? String) == original.id }) else {
                    throw PawlyStoreError.petNotFound
                }
                var value = updatedProfiles[index]
                value.merge(self.encode(updated)) { _, new in new }
                updatedProfiles[index] = value
                return updatedProfiles
            }
            let owned = pets.filter { $0.ownerUID == nil }.map { $0.id == original.id ? updated : $0 }
            pets = owned + pets.filter { $0.ownerUID != nil }
        }
    }

    private func encode(_ pet: PawlyPet) -> [String: Any] {
        [
            "id": pet.id,
            "name": pet.name,
            "breed": pet.breed,
            "age": pet.age,
            "description": pet.description,
            "photoUrl": pet.photoURL,
            "species": pet.species,
            "isShared": pet.isShared,
            "traits": [String]()
        ]
    }
    func deletePet(_ pet: PawlyPet) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        guard pet.ownerUID == nil else { throw PawlyStoreError.noSharedAccess }
        guard !pet.isShared else { throw PawlyStoreError.sharedPetCannotDelete }
        try await updateOwnedPetProfiles { current in current.filter { ($0["id"] as? String) != pet.id } }
        let updatedOwned = pets.filter { $0.ownerUID == nil && $0.id != pet.id }
        pets = updatedOwned + pets.filter { $0.ownerUID != nil }
        if selectedPetID == pet.id, let next = pets.first { persistSelectedPet(next) }
    }

    private func passportRoot(_ pet:PawlyPet) throws -> CollectionReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        if let owner = pet.ownerUID ?? (pet.isShared ? uid : nil) {
            return db.collection("sharedPets").document("\(owner)_\(pet.id)").collection("passport")
        }
        return db.collection("users").document(uid).collection("petCare").document(pet.id).collection("passport")
    }
    private func tasksRoot(_ pet:PawlyPet) throws -> CollectionReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        if let owner = pet.ownerUID ?? (pet.isShared ? uid : nil) {
            return db.collection("sharedPets").document("\(owner)_\(pet.id)").collection("tasks")
        }
        return db.collection("users").document(uid).collection("petCare").document(pet.id).collection("tasks")
    }
    private func careLogsRoot(_ pet: PawlyPet) throws -> CollectionReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        if let owner = pet.ownerUID ?? (pet.isShared ? uid : nil) {
            return db.collection("sharedPets").document("\(owner)_\(pet.id)").collection("careLogs")
        }
        return db.collection("users").document(uid).collection("petCare").document(pet.id).collection("careLogs")
    }

    private func migrateLegacyCollection(
        from source: CollectionReference,
        to destination: CollectionReference,
        maximumDocuments: Int = 450
    ) async throws {
        let sourceSnapshot = try await source.limit(to: maximumDocuments + 1).getDocuments()
        guard sourceSnapshot.documents.count <= maximumDocuments else {
            throw PawlyStoreError.tooManyLegacyCareRecords
        }
        guard !sourceSnapshot.documents.isEmpty else { return }

        // Check every destination ID rather than sampling the first 451 records.
        // This guarantees that an older shared record is never overwritten just
        // because it happened to be outside a limited destination query.
        var missing: [QueryDocumentSnapshot] = []
        for document in sourceSnapshot.documents {
            let destinationSnapshot = try await destination.document(document.documentID).getDocument()
            if !destinationSnapshot.exists { missing.append(document) }
        }
        guard !missing.isEmpty else { return }
        let batch = db.batch()
        for document in missing {
            batch.setData(document.data(), forDocument: destination.document(document.documentID))
        }
        try await batch.commit()
    }

    private func enableSharedCareSync(for pet: PawlyPet) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        let root = try sharedRoot(pet)
        try await migrateLegacyCollection(
            from: db.collection("users").document(uid).collection("petCare").document(pet.id).collection("passport"),
            to: root.collection("passport")
        )
        try await migrateLegacyCollection(
            from: db.collection("users").document(uid).collection("petCare").document(pet.id).collection("tasks"),
            to: root.collection("tasks")
        )
        try await updateOwnedPetProfiles { current in
            var updatedProfiles = current
            guard let index = updatedProfiles.firstIndex(where: { ($0["id"] as? String) == pet.id }) else {
                throw PawlyStoreError.petNotFound
            }
            updatedProfiles[index]["isShared"] = true
            return updatedProfiles
        }
        pets = pets.map { value in
            guard value.id == pet.id && value.ownerUID == nil else { return value }
            return PawlyPet(
                id: value.id, name: value.name, breed: value.breed, age: value.age,
                description: value.description, photoURL: value.photoURL,
                species: value.species, ownerUID: nil, isShared: true
            )
        }
    }

    func loadCareLogs(_ pet: PawlyPet, limit: Int = 100) async throws -> [PawlyCareLogEntry] {
        guard (1...500).contains(limit) else { throw PawlyStoreError.invalidTask }
        try await ensureSharedPet(pet)
        let snapshot = try await careLogsRoot(pet).order(by: "occurredAt", descending: true).limit(to: limit).getDocuments()
        var documents = snapshot.documents
        if pet.isShared && pet.ownerUID == nil, let uid {
            // Historical audit rows cannot be copied to the shared collection
            // without forging their actor/timestamp. Keep showing the owner's
            // original private history alongside new shared log entries.
            let legacy = try await db.collection("users").document(uid).collection("petCare")
                .document(pet.id).collection("careLogs")
                .order(by: "occurredAt", descending: true).limit(to: limit).getDocuments()
            let sharedIDs = Set(documents.map(\.documentID))
            documents.append(contentsOf: legacy.documents.filter { !sharedIDs.contains($0.documentID) })
            documents.sort {
                let a = ($0.get("occurredAt") as? Timestamp)?.dateValue() ?? .distantPast
                let b = ($1.get("occurredAt") as? Timestamp)?.dateValue() ?? .distantPast
                return a > b
            }
            documents = Array(documents.prefix(limit))
        }
        return documents.compactMap { document in
            guard let title = document.get("taskTitle") as? String else { return nil }
            return PawlyCareLogEntry(
                id: document.documentID,
                taskTitle: title,
                action: document.get("action") as? String ?? "Otro",
                actorUID: document.get("actorUid") as? String ?? "",
                actorName: document.get("actorName") as? String ?? "Cuidador",
                occurredAt: (document.get("occurredAt") as? Timestamp)?.dateValue(),
                note: document.get("note") as? String ?? "",
                distanceKm: (document.get("distanceKm") as? NSNumber)?.doubleValue,
                mapURL: document.get("mapUrl") as? String ?? "",
                description: document.get("description") as? String ?? "",
                walker: document.get("walker") as? String ?? "",
                photoURLs: document.get("photoUrls") as? [String] ?? [], videoURL: document.get("videoUrl") as? String ?? ""
            )
        }
    }

    func logCareAction(
        pet: PawlyPet,
        taskTitle: String,
        action: String,
        note: String = "",
        distanceKm: Double? = nil,
        description: String = "",
        walker: String = "",
        photoURLs: [String] = [],
        mapURL: String = "", videoURL: String = ""
    ) async throws {
        try await ensureSharedPet(pet)
        guard let user = Auth.auth().currentUser else { throw PawlyStoreError.notSignedIn }
        let allowedActions = ["Paseo", "Comida", "Medicación", "Higiene", "Otro"]
        let cleanTitle = taskTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanWalker = walker.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            !cleanTitle.isEmpty, cleanTitle.count <= 120,
            allowedActions.contains(action),
            cleanNote.count <= 500,
            cleanDescription.count <= 600,
            cleanWalker.count <= 80,
            distanceKm == nil || (distanceKm!.isFinite && distanceKm! >= 0.01 && distanceKm! <= 1000),
            photoURLs.count <= 4,
            photoURLs.allSatisfy({ $0.hasPrefix("https://") && $0.count <= 2048 }),
            mapURL.count <= 2048,
            mapURL.isEmpty || (URL(string: mapURL)?.scheme?.lowercased() == "https")
        else { throw PawlyStoreError.invalidTask }

        let actorProfile = try await db.collection("users").document(user.uid).getDocument()
        let actorName = (actorProfile.get("displayName") as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? (actorProfile.get("username") as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? "Cuidador"
        let reference = try careLogsRoot(pet).document()
        var data: [String: Any] = [
            "id": reference.documentID,
            "taskTitle": cleanTitle,
            "action": action,
            "actorUid": user.uid,
            "actorName": String(actorName.prefix(80)),
            "occurredAt": FieldValue.serverTimestamp(),
            "note": cleanNote,
            "description": cleanDescription,
            "walker": cleanWalker,
            "photoUrls": photoURLs,
            "mapUrl": mapURL
        ]
        if !videoURL.isEmpty { data["videoUrl"] = videoURL; data["videoAssetId"] = PawlyCloudinaryUploader.indexID(for: videoURL) }
        if let distanceKm { data["distanceKm"] = distanceKm }
        try await reference.setData(data)
    }

    func deleteCareLog(_ id: String, pet: PawlyPet) async throws {
        try await careLogsRoot(pet).document(id).delete()
    }

    func loadPassport(_ pet:PawlyPet) async throws -> [PawlyPassportEntry] {
        try await ensureSharedPet(pet)
        let snapshot = try await passportRoot(pet).getDocuments()
        return snapshot.documents.compactMap { d in
            guard let title = d.get("title") as? String else { return nil }
            return PawlyPassportEntry(
                id: d.documentID,
                type: d.get("type") as? String ?? "Otro",
                title: title,
                detail: d.get("detail") as? String ?? "",
                date: d.get("date") as? String ?? "",
                product: d.get("product") as? String ?? "",
                dose: d.get("dose") as? String ?? "",
                frequency: d.get("frequency") as? String ?? "",
                clinic: d.get("clinic") as? String ?? "",
                nextDoseDate: d.get("nextDoseDate") as? String ?? "",
                status: d.get("status") as? String ?? "REALIZADO",
                reminderEnabled: d.get("reminderEnabled") as? Bool ?? false,
                reminderTime: d.get("reminderTime") as? String ?? "09:00",
                createdAtOrder: d.get("createdAtOrder") as? Int ?? 0
            )
        }.sorted {
            if $0.date == $1.date { return $0.createdAtOrder > $1.createdAtOrder }
            return $0.date > $1.date
        }
    }
    func savePassport(_ e: PawlyPassportEntry, pet: PawlyPet) async throws {
        try await ensureSharedPet(pet)
        guard !e.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              e.title.count <= 120, e.detail.count <= 1200, e.type.count <= 40, e.date.count <= 40,
              e.product.count <= 120, e.dose.count <= 120, e.frequency.count <= 120, e.clinic.count <= 120,
              e.nextDoseDate.isEmpty || e.nextDoseDate.range(of: "^\\d{4}-\\d{2}-\\d{2}$", options: .regularExpression) != nil,
              ["PENDIENTE", "REALIZADO", "VENCIDO"].contains(e.status),
              e.reminderTime.range(of: "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$", options: .regularExpression) != nil
        else { throw PawlyStoreError.invalidEntry }
        let ref = try passportRoot(pet).document(e.id)
        let existing = try await ref.getDocument()
        let order = existing.get("createdAtOrder") as? Int ?? Int(Date().timeIntervalSince1970 * 1_000)
        try await ref.setData([
            "id":e.id,"type":e.type,"title":e.title,"detail":e.detail,"date":e.date,
            "product":e.product,"dose":e.dose,"frequency":e.frequency,"clinic":e.clinic,
            "nextDoseDate":e.nextDoseDate,"status":e.status,
            "reminderEnabled":e.reminderEnabled,"reminderTime":e.reminderTime,
            "createdAtOrder":order
        ])
    }
    func deletePassport(_ id: String, pet: PawlyPet) async throws { try await ensureSharedPet(pet); try await passportRoot(pet).document(id).delete() }

    func loadTasks(_ pet: PawlyPet) async throws -> [PawlyCareTask] {
        try await ensureSharedPet(pet)
        let snapshot = try await tasksRoot(pet).getDocuments()
        return snapshot.documents.compactMap { d in
            guard let title = d.get("title") as? String else { return nil }
            return PawlyCareTask(
                id: d.documentID,
                title: title,
                responsible: d.get("responsible") as? String ?? "",
                frequency: d.get("frequency") as? String ?? "",
                completed: d.get("completed") as? Bool ?? false,
                dose: d.get("dose") as? String ?? "",
                time: d.get("time") as? String ?? "",
                completedOn: d.get("completedOn") as? String ?? "",
                action: d.get("action") as? String ?? "Otro",
                daysOfWeek: d.get("daysOfWeek") as? [Int]
            )
        }
    }
    func saveTask(_ t: PawlyCareTask, pet: PawlyPet) async throws {
        try await ensureSharedPet(pet)
        let allowedActions = ["Paseo", "Comida", "Medicación", "Higiene", "Otro"]
        guard !t.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              t.title.count <= 120,
              t.responsible.count <= 80,
              t.frequency.count <= 80,
              t.dose.count <= 80,
              t.time.count <= 5,
              t.completedOn.count <= 10,
              allowedActions.contains(t.action),
              (t.daysOfWeek == nil || (t.frequency == "Semanal" && (t.daysOfWeek?.count ?? 0) <= 7 && Set(t.daysOfWeek ?? []).isSubset(of: Set(1...7))))
        else { throw PawlyStoreError.invalidTask }

        var data: [String: Any] = [
            "id": t.id,
            "title": t.title,
            "responsible": t.responsible,
            "frequency": t.frequency,
            "completed": t.completed,
            "dose": t.dose,
            "time": t.time,
            "completedOn": t.completedOn,
            "action": t.action
        ]
        if let days = t.daysOfWeek { data["daysOfWeek"] = days }
        try await tasksRoot(pet).document(t.id).setData(data)
    }
    func deleteTask(_ id: String, pet: PawlyPet) async throws { try await ensureSharedPet(pet); try await tasksRoot(pet).document(id).delete() }

    private func sharedRoot(_ pet: PawlyPet) throws -> DocumentReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        return db.collection("sharedPets").document("\(pet.ownerUID ?? uid)_\(pet.id)")
    }
    func ensureSharedPet(_ pet: PawlyPet) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        guard Auth.auth().currentUser?.isEmailVerified == true else { throw PawlyStoreError.verifyEmail }
        let root = try sharedRoot(pet)
        if pet.ownerUID != nil {
            let member=try await root.collection("members").document(uid).getDocument()
            guard member.exists else { throw PawlyStoreError.noSharedAccess }
            return
        }
        let existing = try await root.getDocument()
        if !existing.exists {
            let profile = try await db.collection("users").document(uid).getDocument()
            let name = profile.get("displayName") as? String ?? profile.get("username") as? String ?? "Propietario"
            let username = profile.get("username") as? String ?? ""
            let batch = db.batch()
            batch.setData(["id":root.documentID,"petId":pet.id,"name":pet.name,"ownerUid":uid,
                           "createdAt":FieldValue.serverTimestamp(),"updatedAt":FieldValue.serverTimestamp()], forDocument:root)
            batch.setData(["uid":uid,"role":"owner","displayName":name,"username":username,
                           "joinedAt":FieldValue.serverTimestamp()], forDocument:root.collection("members").document(uid))
            batch.setData(["petId":pet.id,"name":pet.name,"photoUrl":pet.photoURL.isEmpty ? NSNull() : pet.photoURL as Any,
                           "updatedAt":FieldValue.serverTimestamp()], forDocument:root.collection("profile").document("current"))
            try await batch.commit()
        } else {
            let member = try await root.collection("members").document(uid).getDocument()
            if !member.exists { throw PawlyStoreError.noSharedAccess }
        }
    }
    func invite(_ username:String,role:String,pet:PawlyPet) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        guard pet.ownerUID == nil else { throw PawlyStoreError.noSharedAccess }
        guard Auth.auth().currentUser?.isEmailVerified == true else { throw PawlyStoreError.verifyEmail }
        guard role == "editor" || role == "reader" else { throw PawlyStoreError.invalidTask }
        try await ensureSharedPet(pet)
        try await enableSharedCareSync(for: pet)
        let root=try sharedRoot(pet)
        let key=username.trimmingCharacters(in:.whitespacesAndNewlines).replacingOccurrences(of:"@",with:"").lowercased()
        guard key.range(of:"^[a-z0-9_.]{3,24}$",options:.regularExpression) != nil else { throw PawlyStoreError.invalidUsername }
        let usernameDoc=try await db.collection("usernames").document(key).getDocument()
        guard let invitee=usernameDoc.get("uid") as? String,invitee != uid else { throw PawlyStoreError.invalidInvitee }
        let member=try await root.collection("members").document(invitee).getDocument()
        guard !member.exists else { throw PawlyStoreError.alreadyMember }
        let pending=try await db.collection("petInvitations").whereField("sharedPetId",isEqualTo:root.documentID)
            .whereField("ownerUid",isEqualTo:uid)
            .whereField("inviteeUid",isEqualTo:invitee).whereField("status",isEqualTo:"pending").limit(to:1).getDocuments()
        guard pending.isEmpty else { throw PawlyStoreError.invitationPending }
        let owner=try await db.collection("users").document(uid).getDocument()
        try await db.collection("petInvitations").document(UUID().uuidString).setData([
            "sharedPetId":root.documentID,"petName":pet.name,"ownerUid":uid,
            "ownerName":owner.get("displayName") as? String ?? owner.get("username") as? String ?? "Usuario Pawly",
            "inviteeUid":invitee,"inviteeUsername":key,"role":role,"status":"pending",
            "createdAt":FieldValue.serverTimestamp()
        ])
    }
    func pendingInvitations() async throws -> [PawlyPetInvitation] {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        let docs=try await db.collection("petInvitations").whereField("inviteeUid",isEqualTo:uid)
            .whereField("status",isEqualTo:"pending").limit(to:30).getDocuments()
        return docs.documents.map { d in
            PawlyPetInvitation(id:d.documentID,sharedPetID:d.get("sharedPetId") as? String ?? "",
                petName:d.get("petName") as? String ?? "Mascota",ownerName:d.get("ownerName") as? String ?? "Usuario Pawly",
                ownerUID:d.get("ownerUid") as? String ?? "",role:d.get("role") as? String ?? "editor")
        }
    }
    func respondToInvitation(_ invitation:PawlyPetInvitation,accept:Bool) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        guard Auth.auth().currentUser?.isEmailVerified == true else { throw PawlyStoreError.verifyEmail }
        let invitationRef=db.collection("petInvitations").document(invitation.id)
        let profile=try await db.collection("users").document(uid).getDocument()
        let memberRef=db.collection("sharedPets").document(invitation.sharedPetID).collection("members").document(uid)
        let batch=db.batch()
        if accept {
            batch.setData(["uid":uid,"role":invitation.role,"invitationId":invitation.id,
                "displayName":profile.get("displayName") as? String ?? profile.get("username") as? String ?? "Cuidador",
                "username":profile.get("username") as? String ?? "",
                "joinedAt":FieldValue.serverTimestamp()],forDocument:memberRef)
        }
        batch.updateData(["status":accept ? "accepted":"declined","respondedAt":FieldValue.serverTimestamp()],forDocument:invitationRef)
        try await batch.commit()
    }

    func loadDiary(_ pet: PawlyPet) async throws -> [PawlyDiaryEntry] {
        try await ensureSharedPet(pet)
        let root = try sharedRoot(pet)
        let snapshot = try await root.collection("diary").order(by:"position").limit(to:500).getDocuments()
        return snapshot.documents.compactMap { d in
            guard let text = d.get("text") as? String else { return nil }
            let payload = (try? JSONSerialization.jsonObject(with: Data(text.utf8))) as? [String: Any]
            let timestamp = (d.get("updatedAt") as? Timestamp)?.dateValue()
            let date = (payload?["date"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1000) }
            return PawlyDiaryEntry(id:d.documentID,text:payload?["text"] as? String ?? text,
                title:payload?["title"] as? String ?? text,category:payload?["category"] as? String ?? "Otros",
                photos:payload?["photos"] as? [String] ?? [],date:date,position:d.get("position") as? Int ?? 0,updatedAt:timestamp,videoURLs:payload?["videoUrls"] as? [String] ?? [payload?["videoUrl"] as? String].compactMap { $0 }.filter { !$0.isEmpty })
        }.sorted { ($0.date ?? $0.updatedAt ?? .distantPast) > ($1.date ?? $1.updatedAt ?? .distantPast) }
    }
    func addDiaryEntry(_ text: String, pet: PawlyPet) async throws {
        let clean = text.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 12000 else { throw PawlyStoreError.invalidDiary }
        try await ensureSharedPet(pet)
        let root = try sharedRoot(pet)
        let collection = root.collection("diary")
        let last = try await collection.order(by:"position",descending:true).limit(to:1).getDocuments()
        let next = (last.documents.first?.get("position") as? Int ?? -1) + 1
        guard next < 500 else { throw PawlyStoreError.diaryLimit }
        let ref = collection.document("entry_\(UUID().uuidString)")
        try await ref.setData(["id":ref.documentID,"text":clean,"position":next,"updatedAt":FieldValue.serverTimestamp()])
    }
    func deleteDiaryEntry(_ id:String, pet:PawlyPet) async throws {
        try await ensureSharedPet(pet)
        try await sharedRoot(pet).collection("diary").document(id).delete()
    }
}

enum PawlyStoreError: LocalizedError {
    case notSignedIn, invalidPetName, invalidEntry, invalidTask, invalidDiary, diaryLimit, verifyEmail, noSharedAccess, invalidUsername, invalidInvitee, alreadyMember, invitationPending, petNotFound, tooManyLegacyCareRecords, sharedPetCannotDelete
    var errorDescription:String? {
        switch self {
        case .notSignedIn:return "Inicia sesión para continuar."
        case .invalidPetName:return "El nombre de la mascota es obligatorio y no puede superar 40 caracteres."
        case .invalidEntry:return "Revisa los datos del registro sanitario."
        case .invalidTask:return "Revisa los datos de la rutina."
        case .invalidDiary:return "El recuerdo debe tener entre 1 y 12 000 caracteres."
        case .diaryLimit:return "Has alcanzado el límite de 500 recuerdos."
        case .verifyEmail:return "Verifica tu correo electrónico para activar la sincronización del diario."
        case .noSharedAccess:return "No tienes acceso a este perfil compartido."
        case .petNotFound:return "No se ha encontrado el perfil de mascota que intentas editar."
        case .tooManyLegacyCareRecords:return "Hay demasiados registros antiguos para migrarlos en una sola operación. Se han conservado sin cambios; contacta con soporte."
        case .sharedPetCannotDelete:return "Esta mascota ya está compartida. Retira primero el acceso de las demás personas antes de eliminar el perfil."
        case .invalidUsername:return "Escribe un nombre de usuario válido (3–24 caracteres)."
        case .invalidInvitee:return "No se ha encontrado ese usuario o no puedes invitarte a ti mismo."
        case .alreadyMember:return "Esta persona ya tiene acceso al perfil."
        case .invitationPending:return "Ya hay una invitación pendiente para esta persona."
        }
    }
}
