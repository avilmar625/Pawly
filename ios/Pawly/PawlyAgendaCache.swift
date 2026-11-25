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
