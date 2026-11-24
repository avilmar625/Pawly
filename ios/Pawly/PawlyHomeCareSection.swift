import SwiftUI
import FirebaseAuth
import PhotosUI

private struct PawlyHomeUpcomingItem {
    let title: String
    let category: String
    let date: Date
}

struct PawlyHomeCareSection: View {
    @EnvironmentObject private var pets: PawlyPetStore
    @StateObject private var reminders = PawlyReminderStore.shared
    let pet: PawlyPet
    var openHealth: (() -> Void)? = nil

    @State private var tasks: [PawlyCareTask] = []
    @State private var passport: [PawlyPassportEntry] = []
    @State private var diaryEntries: [PawlyDiaryEntry] = []
    @State private var careLogs: [PawlyCareLogEntry] = []
    @State private var isLoading = true
    @State private var pendingTaskIDs: Set<String> = []
    @State private var confirmationTask: PawlyCareTask?
    @State private var duplicateMedicationLog: PawlyCareLogEntry?
    @State private var showHealth = false
    @State private var showWeightEntry = false
    @State private var showDiary = false
    @State private var showCareHistory = false
    @State private var showQuickCareLog = false
    @State private var quickCareAction = "Paseo"
    @State private var error: String?

    private var scope: String {
        "\(Auth.auth().currentUser?.uid ?? "guest").\(pet.ownerUID ?? "owner").\(pet.id)"
    }
    private var latestWeight: Double? {
        let sorted = passport.filter { $0.type.localizedCaseInsensitiveContains("peso") }.sorted {
            $0.date == $1.date ? $0.createdAtOrder > $1.createdAtOrder : $0.date > $1.date
        }
        for entry in sorted {
            for value in [entry.title, entry.detail] {
                let raw = value.lowercased().replacingOccurrences(of:",",with:".")
                if let range = raw.range(of: #"\d{1,3}(?:\.\d{1,2})?(?=\s*kg\b)"#, options:.regularExpression),
                   let number = Double(raw[range]), (0.1...150).contains(number) { return number }
                if let number = Double(raw.trimmingCharacters(in:.whitespacesAndNewlines)), (0.1...150).contains(number) { return number }
            }
        }
        return nil
    }
    private var today: Date { Calendar.current.startOfDay(for: Date()) }
    private var todayKey: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = Calendar.current.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
    private var dueTasks: [PawlyCareTask] {
        tasks.filter {
            isDueToday($0) && ($0.frequency != "Puntual" || !$0.completed || $0.completedOn == todayKey)
        }.sorted {
            let firstTime = $0.time.isEmpty ? "99:99" : $0.time
            let secondTime = $1.time.isEmpty ? "99:99" : $1.time
            return firstTime == secondTime ? $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending : firstTime < secondTime
        }
    }
    private var completedCount: Int { dueTasks.filter(isCompleteToday).count }
    private var progress: Double {
        guard !dueTasks.isEmpty else { return 0 }
        return Double(completedCount) / Double(dueTasks.count)
    }
    private var nextUpcoming: PawlyHomeUpcomingItem? {
        var candidates: [PawlyHomeUpcomingItem] = []
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = Calendar.current.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        for entry in passport where !entry.type.localizedCaseInsensitiveContains("peso") {
            guard let date = formatter.date(from: String(entry.date.prefix(10))),
                  Calendar.current.startOfDay(for: date) >= today else { continue }
            candidates.append(PawlyHomeUpcomingItem(
                title: entry.title.isEmpty ? entry.type : entry.title,
                category: entry.type,
                date: date
            ))
        }
        for reminder in reminders.reminders where
            reminder.petName.caseInsensitiveCompare(pet.name) == .orderedSame &&
            !reminder.completed && reminder.date > Date() {
            candidates.append(PawlyHomeUpcomingItem(
                title: reminder.title,
                category: reminder.category,
                date: reminder.date
            ))
        }
        return candidates.min { $0.date < $1.date }
    }
    private var anniversaryEntry: PawlyDiaryEntry? {
        let calendar = Calendar.current
        return diaryEntries
            .filter { entry in
                guard let date = entry.date ?? entry.updatedAt else { return false }
                return date < today &&
                    calendar.component(.month, from: date) == calendar.component(.month, from: today) &&
                    calendar.component(.day, from: date) == calendar.component(.day, from: today)
            }
            .max { left, right in
                (left.date ?? left.updatedAt ?? .distantPast) < (right.date ?? right.updatedAt ?? .distantPast)
            }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            VStack(alignment: .leading, spacing: 9) {
                HStack {
                    Label("Hoy para \(pet.name)", systemImage: "pawprint.fill")
                        .font(.headline)
                        .foregroundStyle(.white)
                    Spacer()
                    if isLoading { ProgressView().tint(.white) }
                }
                HStack(alignment: .lastTextBaseline, spacing: 6) {
                    Text("\(completedCount)/\(dueTasks.count)")
                        .font(.system(size: 29, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Text("cuidados completados")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.88))
                }
                ProgressView(value: progress)
                    .tint(.white)
                    .background(.white.opacity(0.2), in: Capsule())
                Text(progressMessage)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.88))
            }
            .padding(16)
            .background(
                LinearGradient(
                    colors: [PawlyColors.primary, PawlyColors.primary.opacity(0.76)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ),
                in: RoundedRectangle(cornerRadius: 22, style: .continuous)
            )

            PawlyPersistentDisclosureGroup(
                title: "Lo de hoy",
                subtitle: dueTasks.isEmpty ? "Rutinas programadas para hoy" : "\(completedCount) de \(dueTasks.count) completadas",
                icon: "checklist",
                storageKey: "pawly.home.\(scope).today",
                defaultExpanded: true
            ) {
                if dueTasks.isEmpty {
                    Text(tasks.isEmpty
                         ? "Todavía no hay rutinas configuradas. Añádelas en Salud para ver aquí los cuidados reales."
                         : "No hay cuidados programados para hoy.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    VStack(spacing: 0) {
                        ForEach(dueTasks) { task in
                            taskRow(task)
                            if task.id != dueTasks.last?.id {
                                Divider().padding(.leading, 42)
                            }
                        }
                    }
                }
                HStack {
                    Spacer()
                    Button("Ver Salud") { if let openHealth { openHealth() } else { showHealth = true } }
                        .font(.caption.weight(.semibold))
                }
            }
            .id("pawly.home.\(scope).today")

            HStack(spacing: 8) {
                quickAction("Paseo", icon: "figure.walk") {
                    quickCareAction = "Paseo"
                    showQuickCareLog = true
                }
                quickAction("Peso", icon: "scalemass") {
                    showWeightEntry = true
                }
                quickAction("Foto", icon: "camera.fill") {
                    let uid = Auth.auth().currentUser?.uid ?? "guest"
                    UserDefaults.standard.set(true, forKey: "pawly.diary.\(uid).\(pet.id).newMemory")
                    showDiary = true
                }
                quickAction("Comida", icon: "fork.knife") {
                    if let task = dueTasks.first(where: { $0.action == "Comida" }) {
                        requestToggle(task, checked: !isCompleteToday(task))
                    } else {
                        quickCareAction = "Comida"
                        showQuickCareLog = true
                    }
                }
            }

            if let upcoming = nextUpcoming {
                Button { showHealth = true } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "cross.case.fill")
                            .font(.title3)
                            .foregroundStyle(PawlyColors.primary)
                            .frame(width: 44, height: 44)
                            .background(PawlyColors.raisedSurface, in: RoundedRectangle(cornerRadius: 14))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("PRÓXIMAMENTE")
                                .font(.caption2.weight(.bold))
                                .tracking(1)
                                .foregroundStyle(PawlyColors.primary)
                            Text(upcoming.title)
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(PawlyColors.ink)
                                .lineLimit(1)
                            Text("\(upcoming.category) · \(upcoming.date.formatted(date: .abbreviated, time: upcoming.date == today ? .shortened : .omitted))")
                                .font(.caption)
                                .foregroundStyle(PawlyColors.muted)
                        }
                        Spacer(minLength: 4)
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(PawlyColors.primary)
                    }
                    .padding(13)
                    .background(PawlyColors.mint, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
            }

            if let memory = anniversaryEntry {
                Button { showDiary = true } label: {
                    HStack(spacing: 12) {
                        if let firstPhoto = memory.photos.first, let url = URL(string: firstPhoto) {
                            AsyncImage(url: url) { image in
                                image.resizable().scaledToFill()
                            } placeholder: {
                                PawlyAnniversaryPlaceholder()
                            }
                            .frame(width: 70, height: 70)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        } else {
                            PawlyAnniversaryPlaceholder()
                                .frame(width: 70, height: 70)
                        }
                        VStack(alignment: .leading, spacing: 3) {
                            Text("UN DÍA COMO HOY")
                                .font(.caption2.weight(.bold))
                                .tracking(0.8)
                                .foregroundStyle(PawlyColors.primary)
                            Text(memory.title.isEmpty ? "Un recuerdo especial" : memory.title)
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(PawlyColors.ink)
                                .lineLimit(2)
                            Text((memory.date ?? memory.updatedAt ?? Date()).formatted(date: .abbreviated, time: .omitted) + " · Diario")
                                .font(.caption)
                                .foregroundStyle(PawlyColors.muted)
                        }
                        Spacer(minLength: 4)
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(PawlyColors.primary)
                    }
                    .padding(12)
                    .background(PawlyColors.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(PawlyColors.ink.opacity(0.06)))
                }
                .buttonStyle(.plain)
            }

            if let latestLog = careLogs.first {
                PawlyPersistentDisclosureGroup(
                    title: "Último cuidado registrado",
                    subtitle: latestLog.taskTitle,
                    icon: "clock.arrow.circlepath",
                    storageKey: "pawly.home.\(scope).lastCare",
                    defaultExpanded: false
                ) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(latestLog.action) · \(latestLog.actorName)")
                            .font(.subheadline.weight(.semibold))
                        if let date = latestLog.occurredAt {
                            Text(date.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if !latestLog.note.isEmpty {
                            Text(latestLog.note).font(.caption).foregroundStyle(.secondary)
                        }
                        if !latestLog.description.isEmpty {
                            Text(latestLog.description).font(.caption).foregroundStyle(.secondary)
                        }
                        if !latestLog.walker.isEmpty {
                            Label(latestLog.walker, systemImage: "figure.walk")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        if let distance = latestLog.distanceKm {
                            Label(String(format: "%.2f km", distance), systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        if !latestLog.mapURL.isEmpty, let url = URL(string: latestLog.mapURL) {
                            Link(destination: url) {
                                Label("Ver mapa", systemImage: "map")
                                    .font(.caption.weight(.semibold))
                            }
                        }
                        if !latestLog.photoURLs.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 7) {
                                    ForEach(latestLog.photoURLs.prefix(4), id: \.self) { url in
                                        AsyncImage(url: URL(string: url)) { image in
                                            image.resizable().scaledToFill()
                                        } placeholder: {
                                            PawlyAnniversaryPlaceholder()
                                        }
                                        .frame(width: 58, height: 58)
                                        .clipShape(RoundedRectangle(cornerRadius: 9))
                                    }
                                }
                            }
                        }
                        Button("Abrir historial de cuidados") { showCareHistory = true }
                            .font(.caption.weight(.semibold))
                    }
                }
                .id("pawly.home.\(scope).lastCare")
            }

            if let error {
                Text(error).font(.caption).foregroundStyle(.red)
            }
        }
        .task(id: scope) { await load() }
        .sheet(isPresented: $showHealth) { CareView().environmentObject(pets) }
        .sheet(isPresented: $showWeightEntry) {
            PassportEntrySheet(initialType: "Peso", latestWeight: latestWeight) { entry in
                try await pets.savePassport(entry, pet: pet)
                await load()
            }
        }
        .sheet(isPresented: $showDiary) { DiaryView(openPhotoPickerOnEnter: true).environmentObject(pets) }
        .sheet(isPresented: $showCareHistory) { PawlyCareLogsView().environmentObject(pets) }
        .sheet(isPresented: $showQuickCareLog) {
            PawlyCareLogForm(petName: pet.name, initialAction: quickCareAction) { title, action, note, distance, description, walker, photos in
                var photoURLs: [String] = []
                if let uid = Auth.auth().currentUser?.uid {
                    for item in photos {
                        guard let data = try await item.loadTransferable(type: Data.self) else { continue }
                        let url = try await PawlyCloudinaryUploader().uploadImage(data, folder: "pet-photos", ownerUID: uid)
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
                await load()
            }
        }
        .alert(
            "Puede que esta medicación ya se haya administrado",
            isPresented: Binding(
                get: { confirmationTask != nil },
                set: { if !$0 { confirmationTask = nil; duplicateMedicationLog = nil } }
            )
        ) {
            Button("Registrar igualmente", role: .destructive) {
                if let task = confirmationTask {
                    Task { await update(task, checked: true) }
                }
                confirmationTask = nil
                duplicateMedicationLog = nil
            }
            Button("Cancelar", role: .cancel) {
                confirmationTask = nil
                duplicateMedicationLog = nil
            }
        } message: {
            if let duplicateMedicationLog {
                Text("\(duplicateMedicationLog.actorName) ya registró esta medicación hoy. Comprueba la pauta antes de duplicar el registro.")
            } else {
                Text("Comprueba la pauta antes de duplicar el registro.")
            }
        }
    }

    private func quickAction(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(PawlyColors.primary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(PawlyColors.surface, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(PawlyColors.ink.opacity(0.07)))
                Text(title)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(PawlyColors.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var progressMessage: String {
        if dueTasks.isEmpty {
            return tasks.isEmpty ? "Aún no has creado rutinas para hoy." : "No hay cuidados programados para hoy."
        }
        if completedCount == dueTasks.count { return "¡Todo listo! Qué buen equipo." }
        if dueTasks.count - completedCount == 1 { return "Solo queda un cuidado por completar." }
        return "Aún quedan \(dueTasks.count - completedCount) cuidados por completar."
    }

    @ViewBuilder private func taskRow(_ task: PawlyCareTask) -> some View {
        let complete = isCompleteToday(task)
        Button {
            requestToggle(task, checked: !complete)
        } label: {
            HStack(spacing: 10) {
                Image(systemName: complete ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(PawlyColors.primary)
                VStack(alignment: .leading, spacing: 3) {
                    Text(task.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(PawlyColors.ink)
                        .strikethrough(complete)
                    let detail = [
                        task.responsible.isEmpty ? nil : "Responsable: \(task.responsible)",
                        task.time.isEmpty ? nil : task.time,
                        task.dose.isEmpty ? nil : task.dose
                    ].compactMap { $0 }.joined(separator: " · ")
                    Text(detail.isEmpty ? "\(task.action) · \(task.frequency)" : detail)
                        .font(.caption)
                        .foregroundStyle(PawlyColors.muted)
                        .multilineTextAlignment(.leading)
                }
                Spacer()
                if pendingTaskIDs.contains(task.id) { ProgressView() }
            }
            .padding(.vertical, 9)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(pendingTaskIDs.contains(task.id))
    }

    @MainActor private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let loadedTasks = pets.loadTasks(pet)
            async let loadedPassport = pets.loadPassport(pet)
            async let loadedDiary = pets.loadDiary(pet)
            async let loadedLogs = pets.loadCareLogs(pet, limit: 50)
            tasks = try await loadedTasks
            passport = (try? await loadedPassport) ?? []
            diaryEntries = (try? await loadedDiary) ?? []
            careLogs = (try? await loadedLogs) ?? []
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func isDueToday(_ task: PawlyCareTask) -> Bool {
        guard task.frequency == "Semanal",
              let days = task.daysOfWeek, !days.isEmpty else { return true }
        let weekday = Calendar.current.component(.weekday, from: Date())
        let isoWeekday = weekday == 1 ? 7 : weekday - 1
        return days.contains(isoWeekday)
    }

    private func isCompleteToday(_ task: PawlyCareTask) -> Bool {
        guard task.completed else { return false }
        if task.frequency == "Puntual" { return true }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        guard let completedDate = formatter.date(from: task.completedOn) else { return false }
        if task.frequency == "Semanal", task.daysOfWeek?.isEmpty ?? true {
            var isoCalendar = Calendar(identifier: .iso8601)
            isoCalendar.timeZone = Calendar.current.timeZone
            return isoCalendar.component(.weekOfYear, from: completedDate) == isoCalendar.component(.weekOfYear, from: today) &&
                isoCalendar.component(.yearForWeekOfYear, from: completedDate) == isoCalendar.component(.yearForWeekOfYear, from: today)
        }
        return Calendar.current.isDate(completedDate, inSameDayAs: today)
    }

    private func requestToggle(_ task: PawlyCareTask, checked: Bool) {
        if checked && task.action.localizedCaseInsensitiveCompare("Medicación") == .orderedSame,
           let currentUID = Auth.auth().currentUser?.uid,
           let duplicate = careLogs
            .filter({
                !$0.actorUID.isEmpty && $0.actorUID != "local" && $0.actorUID != currentUID &&
                $0.action.localizedCaseInsensitiveCompare("Medicación") == .orderedSame &&
                $0.taskTitle.trimmingCharacters(in: .whitespacesAndNewlines).localizedCaseInsensitiveCompare(task.title.trimmingCharacters(in: .whitespacesAndNewlines)) == .orderedSame
            })
            .filter({ log in
                guard let date = log.occurredAt else { return false }
                return Calendar.current.isDate(date, inSameDayAs: Date())
            })
            .max(by: { ($0.occurredAt ?? .distantPast) < ($1.occurredAt ?? .distantPast) }) {
            duplicateMedicationLog = duplicate
            confirmationTask = task
            return
        }
        Task { await update(task, checked: checked) }
    }

    @MainActor private func update(_ task: PawlyCareTask, checked: Bool) async {
        guard !pendingTaskIDs.contains(task.id) else { return }
        pendingTaskIDs.insert(task.id)
        defer { pendingTaskIDs.remove(task.id) }
        var updated = task
        updated.completed = checked
        updated.completedOn = checked ? todayKey : ""
        do {
            try await pets.saveTask(updated, pet: pet)
            if checked {
                try await pets.logCareAction(pet: pet, taskTitle: task.title, action: task.action)
            }
            await load()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct PawlyAnniversaryPlaceholder: View {
    var body: some View {
        Image(systemName: "pawprint.fill")
            .font(.title2)
            .foregroundStyle(PawlyColors.primary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(PawlyColors.mint)
    }
}
