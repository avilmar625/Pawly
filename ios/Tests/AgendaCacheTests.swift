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
        print("Separate agendas, remote deletion, local fields and legacy records passed")
    }
}
