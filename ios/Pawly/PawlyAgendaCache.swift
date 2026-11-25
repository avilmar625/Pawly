import Foundation

struct PawlyLocalReminder: Identifiable, Codable {
    var id: String
    var title: String
    var petName: String
    var date: Date
    var notes: String
    var repeatRule: String
    var completed: Bool
    var category: String

    var petScope: String?
    var synced: Bool?

    func belongs(to scope: String, name: String) -> Bool {
        petScope.map { $0 == scope } ?? (petName == name)
    }

    private enum CodingKeys: String, CodingKey { case id, title, petName, date, notes, repeatRule, completed, category, petScope, synced }
    init(id: String, title: String, petName: String, date: Date, notes: String = "", repeatRule: String = "Nunca", completed: Bool = false, category: String = "Personal") {
        self.id = id; self.title = title; self.petName = petName; self.date = date
        self.notes = notes; self.repeatRule = repeatRule; self.completed = completed; self.category = category
    }
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        petScope = try values.decodeIfPresent(String.self, forKey: .petScope)
        synced = try values.decodeIfPresent(Bool.self, forKey: .synced)
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


enum PawlyAgendaCache {
    static func occurs(_ reminder: PawlyLocalReminder, on day: Date, calendar: Calendar) -> Bool {
        let first = calendar.startOfDay(for: reminder.date)
        guard day >= first else { return false }
        switch reminder.repeatRule {
        case "Cada día": return true
        case "Cada semana": return calendar.component(.weekday, from: day) == calendar.component(.weekday, from: first)
        case "Cada mes": return calendar.component(.day, from: day) == calendar.component(.day, from: first)
        case "Cada año": return calendar.dateComponents([.month, .day], from: day) == calendar.dateComponents([.month, .day], from: first)
        default: return calendar.isDate(day, inSameDayAs: first)
        }
    }

    static func merge(local: [PawlyLocalReminder], remote: [PawlyLocalReminder], scope: String, name: String) -> [PawlyLocalReminder] {
        let samePet = local.filter { $0.belongs(to: scope, name: name) }
        let ids = Set(remote.map(\.id))
        let retained = local.filter {
            !$0.belongs(to: scope, name: name) || ($0.synced != true && !ids.contains($0.id))
        }
        return retained + remote.map { record in
            var item = record
            item.petScope = scope
            item.synced = true
            if let previous = samePet.first(where: { $0.id == record.id }) {
                item.notes = previous.notes
                item.repeatRule = previous.repeatRule
                item.completed = previous.completed
            }
            return item
        }
    }
}

struct PawlyNotificationOccurrence {
    let reminder: PawlyLocalReminder
    let date: Date
    let components: DateComponents
    let repeats: Bool

    var identifier: String {
        let scope = reminder.petScope ?? reminder.petName
        let key = "\(scope.utf8.count):\(scope):\(reminder.id.utf8.count):\(reminder.id)"
        return repeats ? key : key + ":\(Int64(date.timeIntervalSince1970))"
    }
}

enum PawlyNotificationSchedule {
    private static func pattern(_ item: PawlyLocalReminder, calendar: Calendar) -> DateComponents? {
        let units: Set<Calendar.Component>
        switch item.repeatRule {
        case "Cada día": units = [.hour, .minute]
        case "Cada semana": units = [.weekday, .hour, .minute]
        case "Cada mes": units = [.day, .hour, .minute]
        case "Cada año": units = [.month, .day, .hour, .minute]
        default: return nil
        }
        return calendar.dateComponents(units, from: item.date)
    }

    static func next(_ item: PawlyLocalReminder, after now: Date, calendar: Calendar) -> Date? {
        guard !item.completed else { return nil }
        guard let start = calendar.dateInterval(of: .minute, for: item.date)?.start else { return nil }
        if start > now { return start }
        guard let pattern = pattern(item, calendar: calendar) else { return nil }
        return calendar.nextDate(after: now, matching: pattern, matchingPolicy: .strict)
    }

    static func requests(for items: [PawlyLocalReminder], after now: Date, calendar: Calendar, limit: Int = 64) -> [PawlyNotificationOccurrence] {
        var candidates = items.compactMap { item -> PawlyNotificationOccurrence? in
            guard let date = next(item, after: now, calendar: calendar) else { return nil }
            if let pattern = pattern(item, calendar: calendar),
               calendar.nextDate(after: now, matching: pattern, matchingPolicy: .strict) == date {
                return PawlyNotificationOccurrence(reminder: item, date: date, components: pattern, repeats: true)
            }
            return dated(item, date, calendar)
        }
        var result: [PawlyNotificationOccurrence] = []
        while result.count < limit, let index = candidates.indices.min(by: { candidates[$0].date < candidates[$1].date }) {
            let event = candidates.remove(at: index)
            result.append(event)
            if !event.repeats, let next = next(event.reminder, after: event.date, calendar: calendar) {
                candidates.append(dated(event.reminder, next, calendar))
            }
        }
        return result
    }

    private static func dated(_ item: PawlyLocalReminder, _ date: Date, _ calendar: Calendar) -> PawlyNotificationOccurrence {
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        return PawlyNotificationOccurrence(reminder: item, date: date, components: components, repeats: false)
    }
}
