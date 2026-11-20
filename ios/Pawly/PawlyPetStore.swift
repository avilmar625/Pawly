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
    var id: String; var text: String; var title: String; var category: String; var photos: [String]; var date: Date?; var position: Int; var updatedAt: Date?
}
struct PawlyPetInvitation:Identifiable {
    var id:String;var sharedPetID:String;var petName:String;var ownerName:String;var ownerUID:String;var role:String
}

@MainActor
final class PawlyPetStore: ObservableObject {
    @Published private(set) var pets: [PawlyPet] = []
    @Published var selectedPetID: String? {
        didSet { if let selectedPetID { UserDefaults.standard.set(selectedPetID, forKey: "pawly.selectedPetID") } }
    }
    @Published var isLoading = false
    @Published var errorMessage: String?
    private let db = Firestore.firestore()
    private var uid: String? { Auth.auth().currentUser?.uid }
    private var petListener: ListenerRegistration?
    private var applyingRemoteSelection = false
    var selectedPet: PawlyPet? { pets.first(where: { $0.id == selectedPetID }) ?? pets.first }

    init() { selectedPetID = UserDefaults.standard.string(forKey: "pawly.selectedPetID") }

    deinit { petListener?.remove() }

    func startListening() {
        guard let uid else { return }
        petListener?.remove()
        petListener = db.collection("users").document(uid).addSnapshotListener { [weak self] snapshot, error in
            guard let self, let snapshot, error == nil else { return }
            Task { @MainActor in
                let currentShared = self.pets.filter { $0.ownerUID != nil }
                let owned = self.decodeOwnedPets(snapshot.data()?["dogProfiles"] as? [[String: Any]] ?? [])
                self.pets = owned + currentShared
                if !self.applyingRemoteSelection,
                   let selectedName = try? await self.db.collection("users").document(uid)
                    .collection("privateData").document("home").getDocument().get("dogName") as? String,
                   let matching = self.pets.first(where: { $0.name == selectedName }) {
                    self.applyingRemoteSelection = true
                    self.selectedPetID = matching.id
                    self.applyingRemoteSelection = false
                } else if !self.pets.contains(where: { $0.id == self.selectedPetID }) {
                    self.selectedPetID = self.pets.first?.id
                }
            }
        }
    }

    func stopListening() {
        petListener?.remove()
        petListener = nil
    }

    private func decodeOwnedPets(_ raw: [[String: Any]]) -> [PawlyPet] {
        raw.compactMap { item -> PawlyPet? in
            guard let id = item["id"] as? String, let name = item["name"] as? String else { return nil }
            return PawlyPet(id:id,name:name,breed:item["breed"] as? String ?? "",
                age:item["age"] as? String ?? "",description:item["description"] as? String ?? "",
                photoURL:item["photoUrl"] as? String ?? "",species:item["species"] as? String ?? "Perro")
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
            let home = try? await db.collection("users").document(uid).collection("privateData").document("home").getDocument()
            let selectedName = home?.get("dogName") as? String
            let remoteSelection = selectedName.flatMap { name in pets.first(where: { $0.name == name }) }
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
            current.filter { ($0["id"] as? String) != pet.id } + [encode(pet)]
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
            photoURL: photoURL, species: species)
        if let owner = original.ownerUID {
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
            pets = pets.map { $0.id == original.id && $0.ownerUID == owner ? updated : $0 }
        } else {
            try await updateOwnedPetProfiles { current in
                guard let index = current.firstIndex(where: { ($0["id"] as? String) == original.id }) else {
                    throw PawlyStoreError.petNotFound
                }
                var value = current[index]
                value.merge(encode(updated)) { _, new in new }
                current[index] = value
                return current
            }
            let owned = pets.filter { $0.ownerUID == nil }.map { $0.id == original.id ? updated : $0 }
            pets = owned + pets.filter { $0.ownerUID != nil }
        }
    }

    private func encode(_ pet: PawlyPet) -> [String: Any] {
        ["id": pet.id, "name": pet.name, "breed": pet.breed, "age": pet.age, "description": pet.description,
         "photoUrl": pet.photoURL, "species": pet.species, "traits": [String]()]
    }
    func deletePet(_ pet: PawlyPet) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        guard pet.ownerUID == nil else { throw PawlyStoreError.noSharedAccess }
        try await updateOwnedPetProfiles { current in current.filter { ($0["id"] as? String) != pet.id } }
        let updatedOwned = pets.filter { $0.ownerUID == nil && $0.id != pet.id }
        pets = updatedOwned + pets.filter { $0.ownerUID != nil }
        if selectedPetID == pet.id, let next = pets.first { persistSelectedPet(next) }
    }

    private func passportRoot(_ pet:PawlyPet) throws -> CollectionReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        if let owner=pet.ownerUID { return db.collection("sharedPets").document("\(owner)_\(pet.id)").collection("passport") }
        return db.collection("users").document(uid).collection("petCare").document(pet.id).collection("passport")
    }
    private func tasksRoot(_ pet:PawlyPet) throws -> CollectionReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        if let owner=pet.ownerUID { return db.collection("sharedPets").document("\(owner)_\(pet.id)").collection("tasks") }
        return db.collection("users").document(uid).collection("petCare").document(pet.id).collection("tasks")
    }
    func loadPassport(_ pet:PawlyPet) async throws -> [PawlyPassportEntry] {
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
                reminderTime: d.get("reminderTime") as? String ?? "09:00"
            )
        }.sorted { $0.date > $1.date }
    }
    func savePassport(_ e: PawlyPassportEntry, pet: PawlyPet) async throws {
        guard !e.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              e.title.count <= 120, e.detail.count <= 1200, e.type.count <= 40, e.date.count <= 40,
              e.product.count <= 120, e.dose.count <= 120, e.frequency.count <= 120, e.clinic.count <= 120,
              e.nextDoseDate.isEmpty || e.nextDoseDate.range(of: "^\\d{4}-\\d{2}-\\d{2}$", options: .regularExpression) != nil,
              ["PENDIENTE", "REALIZADO", "VENCIDO"].contains(e.status),
              e.reminderTime.range(of: "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$", options: .regularExpression) != nil
        else { throw PawlyStoreError.invalidEntry }
        let ref = passportRoot(pet).document(e.id)
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
    func deletePassport(_ id: String, pet: PawlyPet) async throws { try await passportRoot(pet).document(id).delete() }

    func loadTasks(_ pet: PawlyPet) async throws -> [PawlyCareTask] {
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
    func deleteTask(_ id: String, pet: PawlyPet) async throws { try await tasksRoot(pet).document(id).delete() }

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
        let root=try sharedRoot(pet)
        let key=username.trimmingCharacters(in:.whitespacesAndNewlines).replacingOccurrences(of:"@",with:"").lowercased()
        guard key.range(of:"^[a-z0-9_.]{3,24}$",options:.regularExpression) != nil else { throw PawlyStoreError.invalidUsername }
        let usernameDoc=try await db.collection("usernames").document(key).getDocument()
        guard let invitee=usernameDoc.get("uid") as? String,invitee != uid else { throw PawlyStoreError.invalidInvitee }
        let member=try await root.collection("members").document(invitee).getDocument()
        guard !member.exists else { throw PawlyStoreError.alreadyMember }
        let pending=try await db.collection("petInvitations").whereField("sharedPetId",isEqualTo:root.documentID)
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
                photos:payload?["photos"] as? [String] ?? [],date:date,position:d.get("position") as? Int ?? 0,updatedAt:timestamp)
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
    case notSignedIn, invalidPetName, invalidEntry, invalidTask, invalidDiary, diaryLimit, verifyEmail, noSharedAccess, invalidUsername, invalidInvitee, alreadyMember, invitationPending, petNotFound
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
        case .invalidUsername:return "Escribe un nombre de usuario válido (3–24 caracteres)."
        case .invalidInvitee:return "No se ha encontrado ese usuario o no puedes invitarte a ti mismo."
        case .alreadyMember:return "Esta persona ya tiene acceso al perfil."
        case .invitationPending:return "Ya hay una invitación pendiente para esta persona."
        }
    }
}
