import SwiftUI
import FirebaseAuth
import FirebaseFirestore

struct PawlyExpense: Identifiable, Codable, Hashable {
    var id: String
    var kind: String
    var title: String
    var detail: String
    var amount: Double
    var date: Date
}

@MainActor
final class PawlyExpensesStore: ObservableObject {
    @Published private(set) var entries: [PawlyExpense] = []
    @Published var errorMessage: String?
    private let db = Firestore.firestore()
    private var listener: ListenerRegistration?
    private var activePath: String?

    func listen(pet: PawlyPet, pets: PawlyPetStore) async {
        do {
            try await pets.ensureSharedPet(pet)
            guard let uid = Auth.auth().currentUser?.uid else { return }
            let ownerUID = pet.ownerUID ?? uid
            let path = "sharedPets/\(ownerUID)_\(pet.id)/expenses"
            guard activePath != path else { return }
            listener?.remove()
            activePath = path
            listener = db.collection(path).order(by: "date", descending: true).limit(to: 500)
                .addSnapshotListener { [weak self] snapshot, error in
                    Task { @MainActor in
                        guard let self else { return }
                        if let error {
                            self.errorMessage = error.localizedDescription
                            return
                        }
                        self.entries = snapshot?.documents.compactMap { document in
                            guard
                                let kind = document.get("kind") as? String,
                                let title = document.get("title") as? String,
                                let amount = document.get("amount") as? NSNumber,
                                let date = document.get("date") as? NSNumber
                            else { return nil }
                            return PawlyExpense(
                                id: document.documentID,
                                kind: kind,
                                title: title,
                                detail: document.get("detail") as? String ?? "",
                                amount: amount.doubleValue,
                                date: Date(timeIntervalSince1970: date.doubleValue / 1000)
                            )
                        } ?? []
                        self.errorMessage = nil
                    }
                }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func stop() {
        listener?.remove()
        listener = nil
        activePath = nil
    }

    func add(_ entry: PawlyExpense, pet: PawlyPet) async throws {
        guard let uid = Auth.auth().currentUser?.uid else { throw PawlyStoreError.notSignedIn }
        guard
            !entry.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            entry.title.count <= 80,
            entry.detail.count <= 500,
            entry.amount.isFinite, entry.amount > 0, entry.amount <= 100_000,
            ["Alimentación", "Higiene", "Gasto veterinario", "Accesorios", "Otros"].contains(entry.kind)
        else { throw PawlyExpenseError.invalidEntry }
        let ownerUID = pet.ownerUID ?? uid
        let ref = db.collection("sharedPets").document("\(ownerUID)_\(pet.id)")
            .collection("expenses").document(entry.id)
        try await ref.setData([
            "id": entry.id,
            "kind": entry.kind,
            "title": entry.title.trimmingCharacters(in: .whitespacesAndNewlines),
            "detail": entry.detail.trimmingCharacters(in: .whitespacesAndNewlines),
            "amount": entry.amount,
            "date": Int64(entry.date.timeIntervalSince1970 * 1000),
            "createdBy": uid,
            "updatedAt": FieldValue.serverTimestamp()
        ])
    }

    func remove(_ entry: PawlyExpense, pet: PawlyPet) async throws {
        guard let uid = Auth.auth().currentUser?.uid else { throw PawlyStoreError.notSignedIn }
        let ownerUID = pet.ownerUID ?? uid
        try await db.collection("sharedPets").document("\(ownerUID)_\(pet.id)")
            .collection("expenses").document(entry.id).delete()
    }
}

enum PawlyExpenseError: LocalizedError {
    case invalidEntry
    var errorDescription: String? {
        "Introduce un concepto válido y un importe superior a cero."
    }
}

struct PawlyExpensesView: View {
    @EnvironmentObject private var pets: PawlyPetStore
    @StateObject private var store = PawlyExpensesStore()
    @State private var selectedMonth = Calendar.current.dateInterval(of: .month, for: Date())?.start ?? Date()
    @State private var showAdd = false

    private let kinds = ["Alimentación", "Higiene", "Gasto veterinario", "Accesorios", "Otros"]
    private var monthEntries: [PawlyExpense] {
        store.entries.filter { Calendar.current.isDate($0.date, equalTo: selectedMonth, toGranularity: .month) }
    }
    private var monthlyTotal: Double { monthEntries.reduce(0) { $0 + $1.amount } }
    private var totals: [(String, Double)] {
        kinds.compactMap { kind in
            let total = monthEntries.filter { $0.kind == kind }.reduce(0) { $0 + $1.amount }
            return total > 0 ? (kind, total) : nil
        }.sorted { $0.1 > $1.1 }
    }

    var body: some View {
        Group {
            if let pet = pets.selectedPet {
                List {
                    Section {
                        HStack {
                            Button { moveMonth(-1) } label: { Image(systemName: "chevron.left") }
                            Spacer()
                            Text(selectedMonth.formatted(.dateTime.month(.wide).year())).font(.headline)
                            Spacer()
                            Button { moveMonth(1) } label: { Image(systemName: "chevron.right") }
                        }
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Total del mes").font(.caption).foregroundStyle(.secondary)
                                Text(monthlyTotal, format: .currency(code: Locale.current.currency?.identifier ?? "EUR"))
                                    .font(.title2.bold()).foregroundStyle(PawlyColors.primary)
                            }
                            Spacer()
                            Button { showAdd = true } label: {
                                Label("Añadir gasto", systemImage: "plus.circle.fill")
                            }
                        }
                    }
                    Section("Por categoría") {
                        if totals.isEmpty {
                            Text("Todavía no hay gastos en este mes.").foregroundStyle(.secondary)
                        } else {
                            ForEach(totals, id: \.0) { category, total in
                                VStack(alignment: .leading, spacing: 5) {
                                    HStack {
                                        Text(category)
                                        Spacer()
                                        Text(total, format: .currency(code: Locale.current.currency?.identifier ?? "EUR"))
                                            .fontWeight(.semibold)
                                    }
                                    ProgressView(value: total, total: max(monthlyTotal, 0.01))
                                        .tint(PawlyColors.primary)
                                }
                            }
                        }
                    }
                    Section("Movimientos") {
                        if monthEntries.isEmpty {
                            Text("No hay movimientos registrados.").foregroundStyle(.secondary)
                        }
                        ForEach(monthEntries) { entry in
                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: icon(for: entry.kind))
                                    .foregroundStyle(PawlyColors.primary)
                                    .frame(width: 36, height: 36)
                                    .background(PawlyColors.mint, in: RoundedRectangle(cornerRadius: 10))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(entry.title).font(.headline)
                                    Text("\(entry.kind) · \(entry.date.formatted(date: .abbreviated, time: .omitted))")
                                        .font(.caption).foregroundStyle(.secondary)
                                    if !entry.detail.isEmpty {
                                        Text(entry.detail).font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Text(entry.amount, format: .currency(code: Locale.current.currency?.identifier ?? "EUR"))
                                    .font(.subheadline.weight(.semibold))
                            }
                            .swipeActions {
                                Button(role: .destructive) {
                                    Task { try? await store.remove(entry, pet: pet) }
                                } label: { Label("Eliminar", systemImage: "trash") }
                            }
                        }
                    }
                    if let error = store.errorMessage {
                        Section { Text(error).font(.caption).foregroundStyle(.red) }
                    }
                }
                .navigationTitle("Gastos de \(pet.name)")
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showAdd = true } label: { Image(systemName: "plus") } } }
                .sheet(isPresented: $showAdd) {
                    PawlyExpenseForm { entry in
                        Task {
                            do { try await store.add(entry, pet: pet); showAdd = false }
                            catch { store.errorMessage = error.localizedDescription }
                        }
                    }
                }
                .task(id: pet.id) { await store.listen(pet: pet, pets: pets) }
                .onDisappear { store.stop() }
            } else {
                PawlyEmptyState(icon: "receipt", title: "Añade una mascota", detail: "Aquí podrás registrar y consultar sus gastos.")
            }
        }
    }

    private func moveMonth(_ amount: Int) {
        selectedMonth = Calendar.current.date(byAdding: .month, value: amount, to: selectedMonth) ?? selectedMonth
    }

    private func icon(for kind: String) -> String {
        switch kind {
        case "Alimentación": return "fork.knife"
        case "Higiene": return "shower"
        case "Gasto veterinario": return "cross.case"
        case "Accesorios": return "tag"
        default: return "receipt"
        }
    }
}

private struct PawlyExpenseForm: View {
    @Environment(\.dismiss) private var dismiss
    var onSave: (PawlyExpense) -> Void
    @State private var kind = "Alimentación"
    @State private var title = ""
    @State private var detail = ""
    @State private var amount = ""
    @State private var date = Date()
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Picker("Categoría", selection: $kind) {
                    ForEach(["Alimentación", "Higiene", "Gasto veterinario", "Accesorios", "Otros"], id: \.self) { Text($0) }
                }
                TextField("Concepto", text: $title)
                TextField("Importe (€)", text: $amount).keyboardType(.decimalPad)
                DatePicker("Fecha", selection: $date, displayedComponents: .date)
                TextField("Detalles (opcional)", text: $detail, axis: .vertical).lineLimit(2...4)
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Nuevo gasto")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") {
                        let raw = amount.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
                        guard let value = Double(raw), value > 0, value <= 100_000 else {
                            error = "Introduce un importe válido."
                            return
                        }
                        onSave(PawlyExpense(id: UUID().uuidString, kind: kind, title: title, detail: detail,
                                            amount: value, date: date))
                        dismiss()
                    }
                    .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

private struct PawlyCalendarEvent: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let date: Date
    let symbol: String
    let tint: Color
}

struct PawlyCalendarView: View {
    @EnvironmentObject private var pets: PawlyPetStore
    @StateObject private var reminders = PawlyReminderStore.shared
    @State private var passport: [PawlyPassportEntry] = []
    @State private var tasks: [PawlyCareTask] = []
    @State private var diary: [PawlyDiaryEntry] = []
    @State private var month = Calendar.current.dateInterval(of: .month, for: Date())?.start ?? Date()
    @State private var selectedDate = Calendar.current.startOfDay(for: Date())
    @State private var error: String?

    private let calendar = Calendar.current
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)

    var body: some View {
        Group {
            if let pet = pets.selectedPet {
                List {
                    Section {
                        HStack {
                            Button { changeMonth(-1) } label: { Image(systemName: "chevron.left") }
                            Spacer()
                            Text(month.formatted(.dateTime.month(.wide).year())).font(.headline)
                            Spacer()
                            Button { changeMonth(1) } label: { Image(systemName: "chevron.right") }
                        }
                        LazyVGrid(columns: columns, spacing: 8) {
                            ForEach(weekdaySymbols, id: \.self) { Text($0).font(.caption2.weight(.semibold)).foregroundStyle(.secondary).frame(maxWidth: .infinity) }
                            ForEach(Array(monthCells.enumerated()), id: \.offset) { _, day in
                                if let day {
                                    let count = events.filter { calendar.isDate($0.date, inSameDayAs: day) }.count
                                    Button { selectedDate = calendar.startOfDay(for: day) } label: {
                                        VStack(spacing: 3) {
                                            Text("\(calendar.component(.day, from: day))")
                                                .font(.subheadline.weight(calendar.isDate(day, inSameDayAs: selectedDate) ? .bold : .regular))
                                            Circle().fill(count > 0 ? PawlyColors.primary : .clear).frame(width: 5, height: 5)
                                        }
                                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                                        .background(calendar.isDate(day, inSameDayAs: selectedDate) ? PawlyColors.mint : .clear,
                                                    in: RoundedRectangle(cornerRadius: 9))
                                    }.buttonStyle(.plain)
                                } else {
                                    Color.clear.frame(height: 34)
                                }
                            }
                        }
                    }
                    Section(selectedDate.formatted(date: .complete, time: .omitted)) {
                        let todaysEvents = events.filter { calendar.isDate($0.date, inSameDayAs: selectedDate) }
                        if todaysEvents.isEmpty {
                            Text("No hay actividades para este día.").foregroundStyle(.secondary)
                        } else {
                            ForEach(todaysEvents) { event in
                                HStack(spacing: 11) {
                                    Image(systemName: event.symbol).foregroundStyle(event.tint).frame(width: 28)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(event.title).font(.subheadline.weight(.semibold))
                                        Text(event.subtitle).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(event.date.formatted(date: .omitted, time: .shortened))
                                        .font(.caption2).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                    if let error { Section { Text(error).font(.caption).foregroundStyle(.red) } }
                }
                .navigationTitle("Calendario")
                .toolbar { ToolbarItem(placement: .topBarTrailing) { PetPicker() } }
                .task(id: pet.id) { await load(pet) }
                .onDisappear { reminders.stopListening() }
            } else {
                PawlyEmptyState(icon: "calendar", title: "Añade una mascota", detail: "Su calendario de cuidados aparecerá aquí.")
            }
        }
    }

    private var weekdaySymbols: [String] {
        let formatter = DateFormatter()
        let symbols = formatter.shortStandaloneWeekdaySymbols
            ?? formatter.shortWeekdaySymbols
            ?? ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
        guard !symbols.isEmpty else { return [] }
        let start = (calendar.firstWeekday - 1 + symbols.count) % symbols.count
        return Array(symbols.dropFirst(start)) + Array(symbols.prefix(start))
    }

    private var monthCells: [Date?] {
        guard let interval = calendar.dateInterval(of: .month, for: month) else { return [] }
        let dayCount = calendar.range(of: .day, in: .month, for: month)?.count ?? 30
        let weekday = calendar.component(.weekday, from: interval.start)
        let leading = (weekday - calendar.firstWeekday + 7) % 7
        return Array(repeating: nil, count: leading) +
            (0..<dayCount).compactMap { calendar.date(byAdding: .day, value: $0, to: interval.start) }.map { Optional($0) }
    }

    private var events: [PawlyCalendarEvent] {
        var result = reminders.reminders.filter { !$0.completed }.map {
            PawlyCalendarEvent(id: "reminder-\($0.id)", title: $0.title,
                               subtitle: "\($0.petName) · \($0.category)", date: $0.date,
                               symbol: "bell.fill", tint: PawlyColors.primary)
        }
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = "yyyy-MM-dd"
        for entry in passport where !entry.nextDoseDate.isEmpty {
            if let date = parser.date(from: entry.nextDoseDate) {
                result.append(PawlyCalendarEvent(id: "passport-\(entry.id)", title: entry.title,
                    subtitle: "Próxima dosis · \(entry.type)", date: date,
                    symbol: "cross.case.fill", tint: PawlyColors.primary))
            }
        }
        for entry in diary {
            if let date = entry.date {
                result.append(PawlyCalendarEvent(id: "diary-\(entry.id)", title: entry.title,
                    subtitle: "Recuerdo · \(entry.category)", date: date,
                    symbol: "book.closed.fill", tint: PawlyColors.muted))
            }
        }
        let isoWeekday = calendar.component(.weekday, from: selectedDate) == 1 ? 7 :
            calendar.component(.weekday, from: selectedDate) - 1
        for task in tasks where !task.completed {
            let matches: Bool
            switch task.frequency {
            case "Diaria": matches = true
            case "Semanal":
                let selectedDays = task.daysOfWeek ?? []
                matches = selectedDays.isEmpty || selectedDays.contains(isoWeekday)
            case "Mensual": matches = calendar.component(.day, from: selectedDate) == calendar.component(.day, from: Date())
            default: matches = false
            }
            if matches {
                result.append(PawlyCalendarEvent(id: "task-\(task.id)-\(selectedDate.timeIntervalSince1970)",
                    title: task.title, subtitle: "Rutina · \(task.action)", date: selectedDate,
                    symbol: "checklist", tint: PawlyColors.primary))
            }
        }
        return result.sorted { $0.date < $1.date }
    }

    @MainActor private func load(_ pet: PawlyPet) async {
        do {
            try await pets.ensureSharedPet(pet)
            async let p = pets.loadPassport(pet)
            async let t = pets.loadTasks(pet)
            async let d = pets.loadDiary(pet)
            passport = try await p
            tasks = try await t
            diary = try await d
            if let uid = Auth.auth().currentUser?.uid {
                reminders.loadForCurrentUser()
                reminders.listenAgenda(ownerUID: pet.ownerUID ?? uid, petID: pet.id, petName: pet.name)
            }
            error = nil
        } catch let loadError {
            self.error = loadError.localizedDescription
        }
    }

    private func changeMonth(_ amount: Int) {
        month = calendar.date(byAdding: .month, value: amount, to: month) ?? month
    }
}
