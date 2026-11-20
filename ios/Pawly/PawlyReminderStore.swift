import Foundation
import UserNotifications
import Combine
import FirebaseAuth
import FirebaseFirestore

struct PawlyLocalReminder: Identifiable, Codable {
    var id: String
    var title: String
    var petName: String
    var date: Date
    var notes: String
    var repeatRule: String
    var completed: Bool
    var category: String

    private enum CodingKeys: String, CodingKey { case id, title, petName, date, notes, repeatRule, completed, category }
    init(id: String, title: String, petName: String, date: Date, notes: String = "", repeatRule: String = "Nunca", completed: Bool = false, category: String = "Personal") {
        self.id = id; self.title = title; self.petName = petName; self.date = date
        self.notes = notes; self.repeatRule = repeatRule; self.completed = completed; self.category = category
    }
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        title = try values.decode(String.self, forKey: .title)
        petName = try values.decode(String.self, forKey: .petName)
        date = try values.decode(Date.self, forKey: .date)
        notes = try values.decodeIfPresent(String.self, forKey: .notes) ?? ""
        repeatRule = try values.decodeIfPresent(String.self, forKey: .repeatRule) ?? "Nunca"
        completed = try values.decodeIfPresent(Bool.self, forKey: .completed) ?? false
        category = try values.decodeIfPresent(String.self, forKey: .category) ?? "Personal"
    }
}

@MainActor final class PawlyReminderStore: ObservableObject {
    static let shared = PawlyReminderStore()
    @Published private(set) var reminders: [PawlyLocalReminder] = []
    @Published var statusMessage: String?
    private let center = UNUserNotificationCenter.current()
    private let db = Firestore.firestore()
    private var agendaListener: ListenerRegistration?
    private var agendaPath: String?
    private var attemptedAgendaMigration: Set<String> = []
    private var activeUID: String?
    private var key: String { "pawly.localReminders.\(activeUID ?? "local")" }

    private init() { activeUID = Auth.auth().currentUser?.uid; loadStored(); rescheduleAll() }
    func loadForCurrentUser() {
        let uid = Auth.auth().currentUser?.uid
        guard uid != activeUID else { return }
        center.removeAllPendingNotificationRequests(); activeUID = uid; loadStored(); rescheduleAll()
    }
    func listenAgenda(ownerUID: String, petID: String, petName: String) {
        let path = "sharedPets/\(ownerUID)_\(petID)/agenda"
        guard path != agendaPath else { return }
        agendaListener?.remove(); agendaPath = path
        agendaListener = db.collection(path).order(by: "date").limit(to: 500).addSnapshotListener { [weak self] snapshot, error in
            Task { @MainActor in
                guard let self else { return }
                if let error { self.statusMessage = "No se pudo sincronizar la agenda: \(error.localizedDescription)"; return }
                guard let snapshot else { return }
                if snapshot.documents.isEmpty, !self.reminders.isEmpty,
                   !self.attemptedAgendaMigration.contains(path),
                   let uid = Auth.auth().currentUser?.uid {
                    self.attemptedAgendaMigration.insert(path)
                    let legacy = self.reminders
                    for reminder in legacy {
                        self.db.collection(path).document(reminder.id).setData([
                            "id": reminder.id, "title": reminder.title,
                            "date": Int64(reminder.date.timeIntervalSince1970 * 1000),
                            "category": reminder.category, "createdBy": uid,
                            "updatedAt": FieldValue.serverTimestamp()
                        ])
                    }
                    return
                }
                let localByID = Dictionary(uniqueKeysWithValues: self.reminders.map { ($0.id, $0) })
                self.reminders = snapshot.documents.compactMap { document in
                    guard let title = document.get("title") as? String,
                          let millis = document.get("date") as? NSNumber else { return nil }
                    let remote = PawlyLocalReminder(id: document.documentID, title: title, petName: petName,
                        date: Date(timeIntervalSince1970: millis.doubleValue / 1000),
                        category: document.get("category") as? String ?? "Personal")
                    guard let local = localByID[document.documentID] else { return remote }
                    var merged = local; merged.title = remote.title; merged.petName = remote.petName
                    merged.date = remote.date; merged.category = remote.category
                    return merged
                }
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
        return statusMessage ?? ""
    }
    func refreshAuthorizationStatus() { center.getNotificationSettings { [weak self] settings in Task { @MainActor in self?.statusMessage = settings.authorizationStatus == .authorized ? "Avisos locales activados." : "Activa las notificaciones en Ajustes de iOS." } } }
    func add(title: String, petName: String, date: Date, notes: String, repeatRule: String, category: String) {
        let item = PawlyLocalReminder(id: UUID().uuidString, title: title.trimmingCharacters(in: .whitespacesAndNewlines), petName: petName, date: date, notes: notes, repeatRule: repeatRule, category: category)
        reminders.append(item); persist(); schedule(item)
        guard let agendaPath, let uid = Auth.auth().currentUser?.uid else { return }
        let values: [String: Any] = ["id": item.id, "title": item.title, "date": Int64(date.timeIntervalSince1970 * 1000), "category": category, "createdBy": uid, "updatedAt": FieldValue.serverTimestamp()]
        db.collection(agendaPath).document(item.id).setData(values) { [weak self] error in
            if let error { Task { @MainActor in self?.statusMessage = "No se pudo sincronizar el aviso: \(error.localizedDescription)" } }
        }
    }
    func toggle(_ item: PawlyLocalReminder) {
        guard let index = reminders.firstIndex(where: { $0.id == item.id }) else { return }
        reminders[index].completed.toggle()
        if reminders[index].completed { center.removePendingNotificationRequests(withIdentifiers: [item.id]) } else { schedule(reminders[index]) }
        persist()
    }
    func remove(_ item: PawlyLocalReminder) {
        reminders.removeAll { $0.id == item.id }; center.removePendingNotificationRequests(withIdentifiers: [item.id]); persist()
        if let agendaPath { db.collection(agendaPath).document(item.id).delete { [weak self] error in if let error { Task { @MainActor in self?.statusMessage = "No se pudo borrar el aviso: \(error.localizedDescription)" } } } }
    }
    func cancelAllNotifications() { center.removeAllPendingNotificationRequests(); statusMessage = "Avisos pausados. Los recordatorios siguen guardados." }
    private func persist() { if let data = try? JSONEncoder().encode(reminders) { UserDefaults.standard.set(data, forKey: key) } }
    private func rescheduleAll() { center.removeAllPendingNotificationRequests(); reminders.filter { !$0.completed && $0.date > Date() }.forEach(schedule) }
    private func schedule(_ item: PawlyLocalReminder) {
        guard !item.completed, item.date > Date() else { return }
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
    @State private var showAdd = false
    var body: some View {
        List {
            Section("Agenda compartida") {
                if store.reminders.isEmpty { Text("Todavía no tienes avisos. Crea uno para vacunas, medicación, revisiones o cualquier cuidado.").font(.subheadline).foregroundStyle(.secondary) }
                ForEach(store.reminders) { item in
                    HStack(spacing: 10) {
                        Button { store.toggle(item) } label: { Image(systemName: item.completed ? "checkmark.circle.fill" : "circle").font(.title2).foregroundStyle(PawlyColors.primary) }.buttonStyle(.plain)
                        VStack(alignment: .leading, spacing: 3) { Text(item.title).strikethrough(item.completed).font(.headline); Text("\(item.category) · \(item.petName) · \(item.date.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary); if !item.notes.isEmpty { Text(item.notes).font(.caption).foregroundStyle(.secondary) } }
                        Spacer(); Button(role: .destructive) { store.remove(item) } label: { Image(systemName: "trash") }.buttonStyle(.borderless)
                    }.padding(.vertical, 4)
                }
            }
            Section { Button { showAdd = true } label: { Label("Crear recordatorio", systemImage: "plus.circle.fill") }; Button { Task { _ = await store.requestPermission() } } label: { Label("Activar avisos", systemImage: "bell.badge") } }
            if let message = store.statusMessage { Section { Text(message).font(.caption).foregroundStyle(.secondary) } }
        }
        .navigationTitle("Agenda")
        .sheet(isPresented: $showAdd) { PawlyReminderForm(petName: pets.selectedPet?.name ?? "Mi mascota") { title, date, notes, repeatRule, category in store.add(title: title, petName: pets.selectedPet?.name ?? "Mi mascota", date: date, notes: notes, repeatRule: repeatRule, category: category) } }
        .onAppear { store.loadForCurrentUser(); store.refreshAuthorizationStatus() }
        .task(id: pets.selectedPet?.id) {
            guard let pet = pets.selectedPet, let uid = Auth.auth().currentUser?.uid, Auth.auth().currentUser?.isEmailVerified == true else { return }
            do { try await pets.ensureSharedPet(pet); store.listenAgenda(ownerUID: pet.ownerUID ?? uid, petID: pet.id, petName: pet.name) }
            catch { store.statusMessage = "No se pudo abrir la agenda compartida: \(error.localizedDescription)" }
        }
        .onDisappear { store.stopListening() }
    }
}

struct PawlyReminderForm: View {
    @Environment(\.dismiss) private var dismiss
    let petName: String
    var onSave: (String, Date, String, String, String) -> Void
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
                LabeledContent("Mascota", value: petName)
            }.navigationTitle("Nuevo aviso").toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Guardar") { onSave(title, date, notes, repeatRule, category); dismiss() }.disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 || date <= Date()) }
            }
        }
    }
}
