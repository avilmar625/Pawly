import Foundation

enum PawlyLanguage: String, CaseIterable, Identifiable {
    case ca, de, en, es, eu, fr, pt
    var id: String { rawValue }
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
