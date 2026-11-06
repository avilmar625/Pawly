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
}
struct PawlyPassportEntry: Identifiable {
    var id: String; var type: String; var title: String; var detail: String; var date: String
}
struct PawlyCareTask: Identifiable {
    var id: String; var title: String; var responsible: String; var frequency: String; var completed: Bool
}
struct PawlyDiaryEntry: Identifiable {
    var id: String; var text: String; var position: Int; var updatedAt: Date?
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
    var selectedPet: PawlyPet? { pets.first(where: { $0.id == selectedPetID }) ?? pets.first }

    init() { selectedPetID = UserDefaults.standard.string(forKey: "pawly.selectedPetID") }

    func loadPets() async {
        guard let uid else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let snapshot = try await db.collection("users").document(uid).getDocument()
            let raw = snapshot.data()?["dogProfiles"] as? [[String: Any]] ?? []
            pets = raw.compactMap { item in
                guard let id = item["id"] as? String, let name = item["name"] as? String else { return nil }
                return PawlyPet(id: id, name: name, breed: item["breed"] as? String ?? "",
                                age: item["age"] as? String ?? "", description: item["description"] as? String ?? "",
                                photoURL: item["photoUrl"] as? String ?? "", species: item["species"] as? String ?? "Perro")
            }
            if !pets.contains(where: { $0.id == selectedPetID }) { selectedPetID = pets.first?.id }
            errorMessage = nil
        } catch { errorMessage = error.localizedDescription }
    }
    func select(_ pet: PawlyPet) { selectedPetID = pet.id }

    func savePet(name: String, breed: String, age: String, species: String, description: String) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 40 else { throw PawlyStoreError.invalidPetName }
        let pet = PawlyPet(id: UUID().uuidString, name: clean, breed: breed.trimmingCharacters(in: .whitespacesAndNewlines),
                           age: age.trimmingCharacters(in: .whitespacesAndNewlines), description: description.trimmingCharacters(in: .whitespacesAndNewlines),
                           photoURL: "", species: species)
        var updated = pets; updated.append(pet)
        try await db.collection("users").document(uid).updateData(["dogProfiles": updated.map(encode)])
        pets = updated; selectedPetID = pet.id
    }
    private func encode(_ pet: PawlyPet) -> [String: Any] {
        ["id": pet.id, "name": pet.name, "breed": pet.breed, "age": pet.age, "description": pet.description,
         "photoUrl": pet.photoURL, "species": pet.species, "traits": [String]()]
    }
    func deletePet(_ pet: PawlyPet) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        let updated = pets.filter { $0.id != pet.id }
        try await db.collection("users").document(uid).updateData(["dogProfiles": updated.map(encode)])
        pets = updated
        if selectedPetID == pet.id { selectedPetID = updated.first?.id }
    }

    private func passportRoot(_ id: String) throws -> CollectionReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        return db.collection("users").document(uid).collection("petCare").document(id).collection("passport")
    }
    private func tasksRoot(_ id: String) throws -> CollectionReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        return db.collection("users").document(uid).collection("petCare").document(id).collection("tasks")
    }
    func loadPassport(_ petID: String) async throws -> [PawlyPassportEntry] {
        let snapshot = try await passportRoot(petID).getDocuments()
        return snapshot.documents.compactMap { d in
            guard let title = d.get("title") as? String else { return nil }
            return PawlyPassportEntry(id: d.documentID, type: d.get("type") as? String ?? "Otro",
                                      title: title, detail: d.get("detail") as? String ?? "", date: d.get("date") as? String ?? "")
        }.sorted { $0.date > $1.date }
    }
    func savePassport(_ e: PawlyPassportEntry, petID: String) async throws {
        guard !e.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, e.title.count <= 120,
              e.detail.count <= 1200, e.type.count <= 40, e.date.count <= 40 else { throw PawlyStoreError.invalidEntry }
        try await passportRoot(petID).document(e.id).setData(["id":e.id,"type":e.type,"title":e.title,"detail":e.detail,"date":e.date])
    }
    func deletePassport(_ id: String, petID: String) async throws { try await passportRoot(petID).document(id).delete() }

    func loadTasks(_ petID: String) async throws -> [PawlyCareTask] {
        let snapshot = try await tasksRoot(petID).getDocuments()
        return snapshot.documents.compactMap { d in
            guard let title = d.get("title") as? String else { return nil }
            return PawlyCareTask(id: d.documentID, title: title, responsible: d.get("responsible") as? String ?? "",
                                 frequency: d.get("frequency") as? String ?? "", completed: d.get("completed") as? Bool ?? false)
        }
    }
    func saveTask(_ t: PawlyCareTask, petID: String) async throws {
        guard !t.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, t.title.count <= 120,
              t.responsible.count <= 80, t.frequency.count <= 80 else { throw PawlyStoreError.invalidTask }
        try await tasksRoot(petID).document(t.id).setData(["id":t.id,"title":t.title,"responsible":t.responsible,"frequency":t.frequency,"completed":t.completed])
    }
    func deleteTask(_ id: String, petID: String) async throws { try await tasksRoot(petID).document(id).delete() }

    private func sharedRoot(_ pet: PawlyPet) throws -> DocumentReference {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        return db.collection("sharedPets").document("\(uid)_\(pet.id)")
    }
    func ensureSharedPet(_ pet: PawlyPet) async throws {
        guard let uid else { throw PawlyStoreError.notSignedIn }
        guard Auth.auth().currentUser?.isEmailVerified == true else { throw PawlyStoreError.verifyEmail }
        let root = try sharedRoot(pet)
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
            try await batch.commit()
        } else {
            let member = try await root.collection("members").document(uid).getDocument()
            if !member.exists { throw PawlyStoreError.noSharedAccess }
        }
    }
    func loadDiary(_ pet: PawlyPet) async throws -> [PawlyDiaryEntry] {
        try await ensureSharedPet(pet)
        let root = try sharedRoot(pet)
        let snapshot = try await root.collection("diary").order(by:"position").limit(to:200).getDocuments()
        return snapshot.documents.compactMap { d in
            guard let text = d.get("text") as? String else { return nil }
            return PawlyDiaryEntry(id:d.documentID,text:text,position:d.get("position") as? Int ?? 0,
                                   updatedAt:(d.get("updatedAt") as? Timestamp)?.dateValue())
        }
    }
    func addDiaryEntry(_ text: String, pet: PawlyPet) async throws {
        let clean = text.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 1000 else { throw PawlyStoreError.invalidDiary }
        try await ensureSharedPet(pet)
        let root = try sharedRoot(pet)
        let collection = root.collection("diary")
        let last = try await collection.order(by:"position",descending:true).limit(to:1).getDocuments()
        let next = (last.documents.first?.get("position") as? Int ?? -1) + 1
        guard next < 200 else { throw PawlyStoreError.diaryLimit }
        let ref = collection.document("entry_\(UUID().uuidString)")
        try await ref.setData(["id":ref.documentID,"text":clean,"position":next,"updatedAt":FieldValue.serverTimestamp()])
    }
    func deleteDiaryEntry(_ id:String, pet:PawlyPet) async throws {
        try await ensureSharedPet(pet)
        try await sharedRoot(pet).collection("diary").document(id).delete()
    }
}

enum PawlyStoreError: LocalizedError {
    case notSignedIn, invalidPetName, invalidEntry, invalidTask, invalidDiary, diaryLimit, verifyEmail, noSharedAccess
    var errorDescription:String? {
        switch self {
        case .notSignedIn:return "Inicia sesión para continuar."
        case .invalidPetName:return "El nombre de la mascota es obligatorio y no puede superar 40 caracteres."
        case .invalidEntry:return "Revisa los datos del registro sanitario."
        case .invalidTask:return "Revisa los datos de la rutina."
        case .invalidDiary:return "El recuerdo debe tener entre 1 y 1000 caracteres."
        case .diaryLimit:return "Has alcanzado el límite de 200 recuerdos."
        case .verifyEmail:return "Verifica tu correo electrónico para activar la sincronización del diario."
        case .noSharedAccess:return "No tienes acceso a este perfil compartido."
        }
    }
}
