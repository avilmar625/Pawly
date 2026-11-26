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
        guard let uid = Auth.auth().currentUser?.uid else { stop(); return }
        let path = "sharedPets/\(pet.ownerUID ?? uid)_\(pet.id)/expenses"
        guard path != activePath || listener == nil else { return }
        stop()
        entries = []
        activePath = path
        do {
            try await pets.ensureSharedPet(pet)
            guard !Task.isCancelled, activePath == path, Auth.auth().currentUser?.uid == uid else { return }
            listener = db.collection(path).order(by: "date", descending: true).limit(to: 500)
                .addSnapshotListener { [weak self] snapshot, error in
                    Task { @MainActor in
                        guard let self, self.activePath == path, Auth.auth().currentUser?.uid == uid else { return }
                        if let error {
                            self.errorMessage = PawlyServiceMessage.key(for: error)
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
            errorMessage = PawlyServiceMessage.key(for: error)
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
        let fields: [String: Any] = [
            "kind": entry.kind,
            "title": entry.title.trimmingCharacters(in: .whitespacesAndNewlines),
            "detail": entry.detail.trimmingCharacters(in: .whitespacesAndNewlines),
            "amount": entry.amount,
            "date": Int64(entry.date.timeIntervalSince1970 * 1000),
            "updatedAt": FieldValue.serverTimestamp()
        ]
        _ = try await db.runTransaction { transaction, failure -> Any? in
            do {
                if try transaction.getDocument(ref).exists { transaction.updateData(fields, forDocument: ref) }
                else { transaction.setData(fields.merging(["createdBy": uid, "id": entry.id]) { existing, _ in existing }, forDocument: ref) }
            } catch { failure?.pointee = error as NSError }
            return nil
        }
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
    @Environment(\.locale) private var locale
    @EnvironmentObject private var pets: PawlyPetStore
    @StateObject private var store = PawlyExpensesStore()
    @State private var selectedMonth = Calendar.current.dateInterval(of: .month, for: Date())?.start ?? Date()
    @State private var showAdd = false
    @State private var categoryFilter = "Todo"
    @State private var showMonthPicker = false

    private let kinds = ["Alimentación", "Higiene", "Gasto veterinario", "Accesorios", "Otros"]
    private var monthEntries: [PawlyExpense] {
        store.entries.filter { Calendar.current.isDate($0.date, equalTo: selectedMonth, toGranularity: .month) }
    }
    private var filteredEntries: [PawlyExpense] {
        monthEntries.filter { categoryFilter == "Todo" || $0.kind == categoryFilter }
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
                            Button { moveMonth(-1) } label: { Image(systemName: "chevron.left").frame(width:44,height:44) }.buttonStyle(.borderless).accessibilityLabel("Mes anterior")
                            Spacer()
                            Button { showMonthPicker.toggle() } label: {
                                Label(selectedMonth.formatted(.dateTime.month(.wide).year().locale(locale)), systemImage: "calendar")
                                    .font(.headline)
                            }.buttonStyle(.borderless)
                            Spacer()
                            Button { moveMonth(1) } label: { Image(systemName: "chevron.right").frame(width:44,height:44) }.buttonStyle(.borderless).accessibilityLabel("Mes siguiente")
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
                        Picker("Categoría", selection: $categoryFilter) {
                            ForEach(["Todo"] + kinds, id: \.self) { Text(LocalizedStringKey($0)).tag($0) }
                        }
                        if filteredEntries.isEmpty {
                            Text("No hay movimientos registrados.").foregroundStyle(.secondary)
                        }
                        ForEach(filteredEntries) { entry in
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
                        Section { Text(LocalizedStringKey(error)).font(.caption).foregroundStyle(.red) }
                    }
                }
                .navigationTitle("Gastos de \(pet.name)")
                .sheet(isPresented: $showMonthPicker) {
                    NavigationStack {
                        Form {
                            DatePicker("Elige una fecha del mes", selection: $selectedMonth, displayedComponents: .date)
                            Button("Mes actual") { selectedMonth = Date(); showMonthPicker = false }
                        }
                        .navigationTitle("Elegir mes")
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Aceptar") { selectedMonth = Calendar.current.dateInterval(of: .month, for: selectedMonth)?.start ?? selectedMonth; showMonthPicker = false } } }
                    }.presentationDetents([.medium])
                }
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showAdd = true } label: { Image(systemName: "plus") } } }
                .sheet(isPresented: $showAdd) {
                    PawlyExpenseForm { entry in
                        Task {
                            do { try await store.add(entry, pet: pet); showAdd = false }
                            catch { store.errorMessage = PawlyServiceMessage.key(for: error) }
                        }
                    }
                }
                .task(id: pet) { await store.listen(pet: pet, pets: pets) }
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
                    ForEach(["Alimentación", "Higiene", "Gasto veterinario", "Accesorios", "Otros"], id: \.self) { Text(LocalizedStringKey($0)) }
                }
                TextField("Concepto", text: $title)
                TextField("Importe (€)", text: $amount).keyboardType(.decimalPad)
                DatePicker("Fecha", selection: $date, displayedComponents: .date)
                TextField("Detalles (opcional)", text: $detail, axis: .vertical).lineLimit(2...4)
                if let error { Text(LocalizedStringKey(error)).foregroundStyle(.red) }
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
