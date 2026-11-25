import Foundation

@main enum AgendaCacheTests {
    static func main() throws {
        let a = "sharedPets/account_first/agenda"
        let b = "sharedPets/account_second/agenda"
        let legacy = PawlyLocalReminder(id: "local", title: "Vet", petName: "Uma", date: Date())
        var other = legacy
        other.petScope = b
        other.synced = true
        var current = legacy
        current.petScope = a
        current.synced = true
        current.notes = "Bring passport"
        var remote = legacy
        remote.title = "Check-up"
        let updated = PawlyAgendaCache.merge(local: [other, current], remote: [remote], scope: a, name: "Uma")
        precondition(updated.count == 2 && updated.last?.title == "Check-up" && updated.last?.notes == "Bring passport")
        let empty = PawlyAgendaCache.merge(local: updated + [PawlyLocalReminder(id: "offline", title: "Walk", petName: "Uma", date: Date())],
                                          remote: [], scope: a, name: "Uma")
        precondition(empty.count == 2 && empty.contains { $0.petScope == b } && empty.contains { $0.id == "offline" })
        let old = Data(#"{"id":"old","title":"Walk","petName":"Uma","date":0}"#.utf8)
        let decoded = try JSONDecoder().decode(PawlyLocalReminder.self, from: old)
        precondition(decoded.petScope == nil && decoded.repeatRule == "Nunca")
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = calendar.date(from: DateComponents(year: 2026, month: 11, day: 5, hour: 12))!
        var delayed = PawlyLocalReminder(id: "common", title: "Care", petName: "Uma", date: now.addingTimeInterval(7 * 86400), repeatRule: "Cada día")
        var active = delayed
        active.date = now.addingTimeInterval(-4 * 86400)
        active.petScope = a
        delayed.petScope = b
        let scheduled = PawlyNotificationSchedule.requests(for: [delayed, active], after: now, calendar: calendar, limit: 4)
        precondition(scheduled.count == 4 && scheduled.first?.repeats == true)
        precondition(scheduled.first?.date == now.addingTimeInterval(86400))
        precondition(scheduled.dropFirst().allSatisfy { !$0.repeats && $0.date >= delayed.date })
        precondition(Set(scheduled.map(\.identifier)).count == 4)
        var monthly = active
        monthly.repeatRule = "Cada mes"
        monthly.date = calendar.date(from: DateComponents(year: 2026, month: 10, day: 31, hour: 10))!
        precondition(calendar.component(.month, from: PawlyNotificationSchedule.next(monthly, after: now, calendar: calendar)!) == 12)
        monthly.completed = true
        precondition(PawlyNotificationSchedule.next(monthly, after: now, calendar: calendar) == nil)
        print("Separate agendas, remote deletion, local fields and legacy records passed")
    }
}
