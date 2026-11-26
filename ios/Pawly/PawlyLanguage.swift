import Foundation

enum PawlyLanguage: String, CaseIterable, Identifiable {
    case ca, de, en, es, eu, fr, pt
    var id: String { rawValue }
    static func text(_ key: String, language: String) -> String {
        guard let path = Bundle.main.path(forResource: language, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return key }
        return bundle.localizedString(forKey: key, value: key, table: nil)
    }

    var nativeName: String {
        switch self {
        case .ca: return "Català"
        case .de: return "Deutsch"
        case .en: return "English"
        case .es: return "Español"
        case .eu: return "Euskara"
        case .fr: return "Français"
        case .pt: return "Português"
        }
    }
}
