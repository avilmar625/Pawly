import SwiftUI
import UIKit
import Foundation
import UserNotifications
import Combine
import FirebaseAuth
import FirebaseFirestore

@MainActor final class PawlyReminderStore: ObservableObject {
    static let shared = PawlyReminderStore()
    @Published private(set) var reminders: [PawlyLocalReminder] = []
    @Published var statusMessage: String?
    private let center = UNUserNotificationCenter.current()
    private let db = Firestore.firestore()
    private var agendaListener: ListenerRegistration?
    private var agendaPath: String?
    @Published private(set) var systemAllowsNotifications = false
    private var activeUID: String?
    private var key: String { "pawly.localReminders.\(activeUID ?? "local")" }

    private init() { activeUID = Auth.auth().currentUser?.uid; loadStored(); rescheduleAll() }
    func loadForCurrentUser() {
        let uid = Auth.auth().currentUser?.uid
        guard uid != activeUID else { return }
        stopListening(); center.removeAllPendingNotificationRequests(); activeUID = uid; loadStored(); rescheduleAll()
    }
    func exportBackupData() -> Data? {
        try? JSONEncoder().encode(reminders)
    }

    func importBackupData(_ data: Data) throws {
        let values = try JSONDecoder().decode([PawlyLocalReminder].self, from: data)
        guard values.count <= 500 else { throw PawlyReminderBackupError.tooMany }
        loadForCurrentUser()
        reminders = PawlyBackupPolicy.addingMissing(values, to: reminders) { [$0.petScope ?? $0.petName, $0.id] }
        persist()
        rescheduleAll()
    }
    func listenAgenda(ownerUID: String, petID: String, petName: String) {
        let path = "sharedPets/\(ownerUID)_\(petID)/agenda"
        guard path != agendaPath else { return }
        agendaListener?.remove(); agendaPath = path
        agendaListener = db.collection(path).order(by: "date").addSnapshotListener(includeMetadataChanges: true) { [weak self] snapshot, error in
            Task { @MainActor in
                guard let self, self.agendaPath == path, self.activeUID == Auth.auth().currentUser?.uid else { return }
                if let error { self.statusMessage = error.localizedDescription; return }
                guard let snapshot, !snapshot.metadata.isFromCache, !snapshot.metadata.hasPendingWrites else { return }
                let remote = snapshot.documents.compactMap { document -> PawlyLocalReminder? in
                    guard let title = document.get("title") as? String,
                          let millis = document.get("date") as? NSNumber else { return nil }
                    return PawlyLocalReminder(id: document.documentID, title: title, petName: petName,
                        date: Date(timeIntervalSince1970: millis.doubleValue / 1000),
                        category: document.get("category") as? String ?? "Personal")
                }
                self.reminders = PawlyAgendaCache.merge(local: self.reminders, remote: remote, scope: path, name: petName)
                self.persist(); self.rescheduleAll(); self.statusMessage = "Agenda sincronizada con la mascota."
            }
        }
    }
    func stopListening() { agendaListener?.remove(); agendaListener = nil; agendaPath = nil }
    private func loadStored() {
        if let data = UserDefaults.standard.data(forKey: key), let values = try? JSONDecoder().decode([PawlyLocalReminder].self, from: data) { reminders = values }
        else { reminders = [] }
    }
    func requestPermission() async -> String {
        do { let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge]); statusMessage = granted ? "Notificaciones activadas en este dispositivo." : "Permiso denegado. Actívalo en Ajustes de iOS."; if granted { rescheduleAll(); PawlyAppDelegate.registerForRemoteNotifications() } }
        catch { statusMessage = error.localizedDescription }
        refreshAuthorizationStatus()
        return statusMessage ?? ""
    }
    func refreshAuthorizationStatus() {
        Task {
            let settings = await center.notificationSettings()
            systemAllowsNotifications = [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus)
        }
    }

    func scope(for pet: PawlyPet) -> String? {
        guard let uid = Auth.auth().currentUser?.uid else { return nil }
        return "sharedPets/\(pet.ownerUID ?? uid)_\(pet.id)/agenda"
    }

    func items(for pet: PawlyPet?) -> [PawlyLocalReminder] {
        guard let pet, let path = scope(for: pet) else { return [] }
        return reminders.filter { $0.belongs(to: path, name: pet.name) }.sorted { $0.date < $1.date }
    }

    func save(_ reminder: PawlyLocalReminder, pet: PawlyPet) async throws {
        loadForCurrentUser()
        guard let path = scope(for: pet), let uid = activeUID else { throw PawlyStoreError.notSignedIn }
        var item = reminder
        item.petScope = path
        if Auth.auth().currentUser?.isEmailVerified == true {
            let ref = db.collection(path).document(item.id)
            let mutable: [String: Any] = ["title": item.title, "date": Int64(item.date.timeIntervalSince1970 * 1000),
                                          "category": item.category, "updatedAt": FieldValue.serverTimestamp()]
            let id = item.id
            _ = try await db.runTransaction { transaction, failure -> Any? in
                do {
                    if try transaction.getDocument(ref).exists { transaction.updateData(mutable, forDocument: ref) }
                    else { transaction.setData(mutable.merging(["id": id, "createdBy": uid]) { original, _ in original }, forDocument: ref) }
                } catch { failure?.pointee = error as NSError }
                return nil
            }
            item.synced = true
        }
        guard activeUID == uid, Auth.auth().currentUser?.uid == uid else { return }
        reminders.removeAll { $0.id == item.id && $0.belongs(to: path, name: pet.name) }
        reminders.append(item)
        persist()
        rescheduleAll()
    }

    func toggle(_ item: PawlyLocalReminder) {
        guard let index = reminders.firstIndex(where: { $0.id == item.id && $0.petScope == item.petScope }) else { return }
        reminders[index].completed.toggle()
        persist()
        rescheduleAll()
    }

    func remove(id: String, pet: PawlyPet) async throws {
        guard let path = scope(for: pet), let uid = activeUID else { return }
        let local = items(for: pet).first { $0.id == id }
        if Auth.auth().currentUser?.isEmailVerified == true && local?.synced != false {
            try await db.collection(path).document(id).delete()
        }
        guard activeUID == uid, Auth.auth().currentUser?.uid == uid else { return }
        reminders.removeAll { $0.id == id && $0.belongs(to: path, name: pet.name) }
        persist()
        rescheduleAll()
    }
    func cancelAllNotifications() { center.removeAllPendingNotificationRequests(); statusMessage = "Avisos pausados. Los recordatorios siguen guardados." }
    private func persist() { if let data = try? JSONEncoder().encode(reminders) { UserDefaults.standard.set(data, forKey: key) } }
    private func rescheduleAll() { center.removeAllPendingNotificationRequests(); reminders.filter { !$0.completed && $0.date > Date() }.forEach(schedule) }
    private func schedule(_ item: PawlyLocalReminder) {
        guard UserDefaults.standard.object(forKey: "pawly.notificationsEnabled") as? Bool != false, !item.completed, item.date > Date() else { return }
        let content = UNMutableNotificationContent(); content.title = item.title
        content.body = item.notes.isEmpty ? "\(item.petName) · \(item.category)" : "\(item.petName): \(item.notes)"; content.sound = .default
        var components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: item.date)
        let repeats = item.repeatRule != "Nunca"
        if repeats { switch item.repeatRule {
            case "Cada día": components = Calendar.current.dateComponents([.hour, .minute], from: item.date)
            case "Cada semana": components = Calendar.current.dateComponents([.weekday, .hour, .minute], from: item.date)
            case "Cada mes": components = Calendar.current.dateComponents([.day, .hour, .minute], from: item.date)
            case "Cada año": components = Calendar.current.dateComponents([.month, .day, .hour, .minute], from: item.date)
            default: break
        } }
        center.add(UNNotificationRequest(identifier: item.id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: repeats))) { [weak self] error in
            if let error { Task { @MainActor in self?.statusMessage = error.localizedDescription } }
        }
    }
}

struct PawlyRemindersView: View {
    @StateObject private var store = PawlyReminderStore.shared
    @EnvironmentObject private var pets: PawlyPetStore
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("pawly.notificationsEnabled") private var noticesEnabled = true
    @State private var editing: PawlyLocalReminder?
    @State private var showAdd = false
    @State private var permissionMessage:String?
    var body: some View {
        List {
            Section("Agenda compartida") {
                if store.items(for: pets.selectedPet).isEmpty { Text("Todavía no tienes avisos. Crea uno para vacunas, medicación, revisiones o cualquier cuidado.").font(.subheadline).foregroundStyle(.secondary) }
                ForEach(store.items(for: pets.selectedPet)) { item in
                    HStack(spacing: 10) {
                        Button { store.toggle(item) } label: { Image(systemName: item.completed ? "checkmark.circle.fill" : "circle").font(.title2).foregroundStyle(PawlyColors.primary) }.buttonStyle(.plain)
                        VStack(alignment: .leading, spacing: 3) { Text(item.title).strikethrough(item.completed).font(.headline); Text("\(item.category) · \(item.petName) · \(item.date.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary); if !item.notes.isEmpty { Text(item.notes).font(.caption).foregroundStyle(.secondary) } }
                        Spacer(); Button { editing = item } label: { Label("Editar", systemImage: "pencil").labelStyle(.iconOnly) }.buttonStyle(.borderless)
                        Button(role: .destructive) {
                            guard let pet = pets.selectedPet else { return }
                            Task { do { try await store.remove(id: item.id, pet: pet) } catch { store.statusMessage = error.localizedDescription } }
                        } label: { Image(systemName: "trash") }.buttonStyle(.borderless)
                    }.padding(.vertical, 4)
                }
            }
            Section { Button { showAdd = true } label: { Label("Crear recordatorio", systemImage: "plus.circle.fill") }.buttonStyle(.borderless)
                if noticesEnabled && store.systemAllowsNotifications {
                    Label("Avisos activados", systemImage: "checkmark.circle")
                    Button("Pausar avisos de Pawly") {
                        noticesEnabled = false
                        store.cancelAllNotifications()
                        PawlyAppDelegate.disableRemoteNotifications()
                    }
                } else {
                    Button { noticesEnabled = true; Task { permissionMessage = await store.requestPermission() } } label: {
                        Label("Activar avisos", systemImage: "bell.badge")
                    }.buttonStyle(.borderless)
                }
                Button { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } } label: { Label("Abrir ajustes de notificaciones",systemImage:"gear") }.buttonStyle(.borderless) }
            if let permissionMessage { Section { Text(permissionMessage).font(.caption).foregroundStyle(.secondary) } }
            if let message = store.statusMessage { Section { Text(message).font(.caption).foregroundStyle(.secondary) } }
        }
        .navigationTitle("Agenda")
        .sheet(isPresented: $showAdd) { if let pet = pets.selectedPet { form(for: pet) } }
        .sheet(item: $editing) { item in if let pet = pets.selectedPet { form(for: pet, reminder: item) } }
        .onAppear { store.loadForCurrentUser(); store.refreshAuthorizationStatus() }
        .onChange(of: scenePhase) { if $0 == .active { store.refreshAuthorizationStatus() } }
    }

    private func form(for pet: PawlyPet, reminder: PawlyLocalReminder? = nil) -> some View {
        PawlyReminderForm(petName: pet.name, existing: reminder) { title, date, notes, repeatRule, category in
            try await store.save(PawlyLocalReminder(id: reminder?.id ?? UUID().uuidString, title: title, petName: pet.name,
                date: date, notes: notes, repeatRule: repeatRule, completed: reminder?.completed ?? false, category: category), pet: pet)
        }
    }
}

struct PawlyReminderForm: View {
    @Environment(\.dismiss) private var dismiss
    let petName: String
    var onSave: (String, Date, String, String, String) async throws -> Void
    @State private var busy = false
    @State private var error: String?
    init(petName: String, existing: PawlyLocalReminder? = nil, onSave: @escaping (String, Date, String, String, String) async throws -> Void) {
        self.petName = petName
        self.onSave = onSave
        if let existing {
            _title = State(initialValue: existing.title)
            _date = State(initialValue: existing.date)
            _notes = State(initialValue: existing.notes)
            _repeatRule = State(initialValue: existing.repeatRule)
            _category = State(initialValue: existing.category)
        }
    }
    @State private var title = ""
    @State private var date = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
    @State private var notes = ""
    @State private var repeatRule = "Nunca"
    @State private var category = "Personal"
    var body: some View {
        NavigationStack {
            Form {
                TextField("Qué hay que recordar", text: $title)
                DatePicker("Fecha y hora", selection: $date, in: Date()...)
                Picker("Categoría", selection: $category) { ForEach(["Personal", "Veterinario", "Cuidado", "Paseo", "Viaje"], id: \.self) { Text($0) } }
                TextField("Notas (opcional)", text: $notes, axis: .vertical).lineLimit(2...4)
                Picker("Repetición", selection: $repeatRule) { ForEach(["Nunca", "Cada día", "Cada semana", "Cada mes", "Cada año"], id: \.self) { Text($0) } }
                if let error { Text(error).foregroundStyle(.red) }
                LabeledContent("Mascota", value: petName)
            }.disabled(busy).interactiveDismissDisabled(busy).navigationTitle("Recordatorio").toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() }.disabled(busy) }
                ToolbarItem(placement: .confirmationAction) { Button("Guardar") {
                    busy = true
                    Task {
                        defer { busy = false }
                        do { try await onSave(title.trimmingCharacters(in: .whitespacesAndNewlines), date, notes, repeatRule, category); dismiss() }
                        catch { self.error = error.localizedDescription }
                    }
                }.disabled(busy || title.count > 100 || title.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 || date <= Date()) }
            }
        }
    }
}

enum PawlyReminderBackupError: LocalizedError {
    case tooMany
    var errorDescription: String? { "La copia contiene demasiados recordatorios locales." }
}
