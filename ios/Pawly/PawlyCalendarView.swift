import SwiftUI

private enum AgendaKind: CaseIterable {
    case reminder, health, care, diary, routine
    var icon: String {
        switch self {
        case .reminder: return "bell.fill"
        case .health: return "cross.case.fill"
        case .care: return "pawprint.fill"
        case .diary: return "book.closed.fill"
        case .routine: return "checklist"
        }
    }
    var title: LocalizedStringKey {
        switch self {
        case .reminder: return "Avisos"
        case .health: return "Salud"
        case .care: return "Cuidados"
        case .diary: return "Diario"
        case .routine: return "Rutinas"
        }
    }
    var tint: Color {
        switch self {
        case .reminder: return .orange
        case .health: return .pink
        case .care: return .blue
        case .diary: return .purple
        case .routine: return PawlyColors.primary
        }
    }
}

private struct AgendaItem: Identifiable {
    let id: String
    let title: String
    let date: Date
    let kind: AgendaKind
    let details: [String]
    var reminder: PawlyLocalReminder? = nil
}

struct PawlyCalendarView: View {
    @EnvironmentObject private var pets: PawlyPetStore
    @ObservedObject private var reminders = PawlyReminderStore.shared
    @Environment(\.calendar) private var calendar
    @Environment(\.locale) private var locale
    @State private var passport: [PawlyPassportEntry] = []
    @State private var tasks: [PawlyCareTask] = []
    @State private var diary: [PawlyDiaryEntry] = []
    @State private var logs: [PawlyCareLogEntry] = []
    @State private var day = Date()
    @State private var selected: AgendaItem?
    @State private var error: String?

    var body: some View {
        Group {
            if let pet = pets.selectedPet {
                let cells = monthDays
                let groups = monthItems(days: cells, pet: pet)
                List {
                    Section {
                        HStack {
                            monthButton(-1)
                            Spacer()
                            DatePicker("Elegir mes", selection: $day, displayedComponents: .date)
                                .labelsHidden().datePickerStyle(.compact)
                            Spacer()
                            monthButton(1)
                        }
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 7)) {
                            ForEach(0..<7, id: \.self) { offset in
                                Text(calendar.veryShortStandaloneWeekdaySymbols[(calendar.firstWeekday - 1 + offset) % 7])
                                    .font(.caption2).foregroundStyle(.secondary)
                            }
                            let blanks = ((cells.first.map { calendar.component(.weekday, from: $0) } ?? calendar.firstWeekday) - calendar.firstWeekday + 7) % 7
                            ForEach(0..<blanks, id: \.self) { _ in Color.clear.frame(height: 1) }
                            ForEach(cells, id: \.self) { date in
                                let events = groups[date] ?? []
                                Button { day = date } label: {
                                    VStack(spacing: 3) {
                                        Text(date, format: .dateTime.day())
                                        HStack(spacing: 2) {
                                            ForEach(AgendaKind.allCases, id: \.self) { kind in
                                                if events.contains(where: { $0.kind == kind }) { Circle().fill(kind.tint).frame(width: 4, height: 4) }
                                            }
                                        }.frame(height: 5)
                                    }
                                    .frame(maxWidth: .infinity, minHeight: 36)
                                    .background(calendar.isDate(date, inSameDayAs: day) ? PawlyColors.mint : .clear, in: RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(Text(date, format: .dateTime.weekday().month().day()))
                                .accessibilityValue(Text("\(events.count) registros"))
                                .accessibilityAddTraits(calendar.isDate(day, inSameDayAs: date) ? .isSelected : [])
                            }
                        }
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 80), alignment: .leading)], alignment: .leading) {
                            ForEach(AgendaKind.allCases, id: \.self) { kind in
                                Label { Text(kind.title) } icon: { Image(systemName: kind.icon).foregroundStyle(kind.tint) }.font(.caption2)
                            }
                        }
                    } header: { Text(day, format: .dateTime.month(.wide).year()) }
                    Section {
                        let items = groups[calendar.startOfDay(for: day)] ?? []
                        if items.isEmpty { Text("No hay actividades para este día.").foregroundStyle(.secondary) }
                        ForEach(items) { item in
                            Button { selected = item } label: {
                                Label { Text(verbatim: item.title) } icon: { Image(systemName: item.kind.icon).foregroundStyle(item.kind.tint) }
                            }
                        }
                    } header: { Text(day, format: .dateTime.weekday(.wide).day().month(.wide)) }
                    if let error { Text(error).foregroundStyle(.red) }
                }
                .refreshable { await load(pet) }
                .task(id: pet) { await load(pet) }
                .sheet(item: $selected) { item in
                    NavigationStack {
                        Form {
                            LabeledContent("Mascota", value: pet.name)
                            LabeledContent("Fecha") { Text(item.date, format: .dateTime.day().month().year()) }
                            ForEach(Array(item.details.filter { !$0.isEmpty }.enumerated()), id: \.offset) { _, detail in Text(verbatim: detail) }
                            if let reminder = item.reminder {
                                NavigationLink("Editar") {
                                    PawlyReminderForm(petName: pet.name, existing: reminder) { title, date, notes, rule, category in
                                        var updated = reminder
                                        updated.title = title; updated.date = date; updated.notes = notes
                                        updated.repeatRule = rule; updated.category = category
                                        try await reminders.save(updated, pet: pet)
                                        selected = nil
                                    }
                                }
                                Button("Eliminar recordatorio", role: .destructive) {
                                    Task {
                                        do { try await reminders.remove(id: reminder.id, pet: pet); selected = nil }
                                        catch { selected = nil; self.error = error.localizedDescription }
                                    }
                                }
                            }
                        }
                        .navigationTitle(item.title)
                        .toolbar { Button("Cerrar") { selected = nil } }
                    }
                }
            } else { PawlyEmptyState(icon: "calendar", title: "Añade una mascota", detail: "Su calendario de cuidados aparecerá aquí.") }
        }
        .navigationTitle("Agenda").navigationBarTitleDisplayMode(.inline)
        .toolbar { NavigationLink { PawlyRemindersView() } label: { Label("Recordatorios", systemImage: "bell.badge") } }
    }

    private func monthButton(_ step: Int) -> some View {
        Button {
            if let month = calendar.dateInterval(of: .month, for: day)?.start {
                day = calendar.date(byAdding: .month, value: step, to: month) ?? day
            }
        } label: { Image(systemName: step < 0 ? "chevron.left" : "chevron.right").frame(width: 44, height: 44) }
        .buttonStyle(.borderless).accessibilityLabel(step < 0 ? "Mes anterior" : "Mes siguiente")
    }

    private var monthDays: [Date] {
        guard let month = calendar.dateInterval(of: .month, for: day), let range = calendar.range(of: .day, in: .month, for: day) else { return [] }
        return range.compactMap { calendar.date(byAdding: .day, value: $0 - 1, to: month.start) }
    }

    private func monthItems(days: [Date], pet: PawlyPet) -> [Date: [AgendaItem]] {
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = calendar.timeZone
        parser.dateFormat = "yyyy-MM-dd"
        var items: [AgendaItem] = []
        for entry in passport {
            let dates = Set([entry.date, entry.nextDoseDate].filter { !$0.isEmpty })
            for raw in dates {
                if let date = parser.date(from: raw) {
                    items.append(AgendaItem(id: "health-\(entry.id)-\(raw)", title: entry.title, date: date, kind: .health,
                        details: [entry.type, entry.detail, entry.product, entry.dose, entry.clinic]))
                }
            }
        }
        for entry in diary {
            if let date = entry.date ?? entry.updatedAt {
                items.append(AgendaItem(id: "diary-" + entry.id, title: entry.title, date: date, kind: .diary, details: [entry.category, entry.text]))
            }
        }
        for log in logs {
            if let date = log.occurredAt {
                items.append(AgendaItem(id: "care-" + log.id, title: log.taskTitle, date: date, kind: .care,
                    details: [log.action, log.actorName, log.note, log.description, log.walker, log.mapURL]))
            }
        }
        let active = reminders.items(for: pet).filter { !$0.completed }
        for date in days {
            let weekday = (calendar.component(.weekday, from: date) + 5) % 7 + 1
            for task in tasks where !task.completed {
                let isDue = task.frequency == "Diaria" || (task.frequency == "Semanal" && task.daysOfWeek?.contains(weekday) == true)
                if isDue {
                    items.append(AgendaItem(id: "task-" + task.id, title: task.title, date: date, kind: .routine,
                        details: [task.action, task.frequency, task.time, task.responsible, task.dose]))
                }
            }
            for item in active where PawlyAgendaCache.occurs(item, on: date, calendar: calendar) {
                let time = calendar.dateComponents([.hour, .minute], from: item.date)
                let start = calendar.date(bySettingHour: time.hour ?? 0, minute: time.minute ?? 0, second: 0, of: date) ?? date
                items.append(AgendaItem(id: "alarm-" + item.id, title: item.title, date: start, kind: .reminder,
                    details: [item.category, item.notes, start.formatted(.dateTime.hour().minute().locale(locale)), item.repeatRule], reminder: item))
            }
        }
        return Dictionary(grouping: items.sorted { $0.date < $1.date }, by: { calendar.startOfDay(for: $0.date) })
    }

    @MainActor private func load(_ pet: PawlyPet) async {
        do {
            async let p = pets.loadPassport(pet)
            async let t = pets.loadTasks(pet)
            async let d = pets.loadDiary(pet)
            async let c = pets.loadCareLogs(pet, limit: 100)
            let result = try await (p, t, d, c)
            guard !Task.isCancelled, pets.selectedPet == pet else { return }
            (passport, tasks, diary, logs) = result
            error = nil
        } catch { if !Task.isCancelled { self.error = error.localizedDescription } }
    }
}
