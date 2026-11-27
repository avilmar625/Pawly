import CoreLocation
import Foundation

enum PawlyMapPoint {
    static func parse(_ query: String) -> CLLocationCoordinate2D? {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts: [String]
        if text.contains(";") {
            parts = text.components(separatedBy: ";").map { $0.replacingOccurrences(of: ",", with: ".") }
        } else if text.contains(",") {
            parts = text.components(separatedBy: ",")
        } else {
            parts = text.split(whereSeparator: \.isWhitespace).map(String.init)
        }
        guard parts.count == 2,
              let lat = Double(parts[0].trimmingCharacters(in: .whitespaces)),
              let lon = Double(parts[1].trimmingCharacters(in: .whitespaces)),
              lat.isFinite, lon.isFinite, (-90...90).contains(lat), (-180...180).contains(lon) else { return nil }
        return .init(latitude: lat, longitude: lon)
    }

    static func shareURL(_ point: CLLocationCoordinate2D, name: String) -> URL? {
        var url = URLComponents(string: "https://maps.apple.com/")
        url?.queryItems = [URLQueryItem(name:"ll",value:label(point,approximate:false).replacingOccurrences(of:" ",with:"")),URLQueryItem(name:"q",value:String(name.prefix(120)))]
        return url?.url
    }
    static func sharedURL(in text:String) -> URL? {
        guard let line=text.components(separatedBy:.newlines).last, let url=URL(string:line), url.scheme=="https", url.host=="maps.apple.com", url.user==nil, url.password==nil,
              URLComponents(url:url,resolvingAgainstBaseURL:false)?.queryItems?.contains(where:{$0.name=="ll"}) == true else { return nil }
        return url
    }

    static func approximate(_ point: CLLocationCoordinate2D) -> CLLocationCoordinate2D {
        .init(latitude: (point.latitude * 100).rounded()/100, longitude: (point.longitude * 100).rounded()/100)
    }

    static func label(_ point: CLLocationCoordinate2D, approximate: Bool) -> String {
        String(format: approximate ? "%.2f, %.2f" : "%.5f, %.5f", locale: Locale(identifier: "en_US_POSIX"),
               point.latitude, point.longitude)
    }
}

// Android and older iOS releases store the public area on a "Zona:" line.
enum PawlyLostNoticeText {
    static func area(in text: String) -> String {
        guard let line = text.components(separatedBy: "\n").first(where: isAreaLine) else { return "" }
        return String(line.trimmingCharacters(in: .whitespaces).dropFirst(5)).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func replacingArea(in text: String, with label: String) -> String {
        var lines = text.components(separatedBy: "\n")
        let label = label.components(separatedBy: .newlines).joined(separator: " ").trimmingCharacters(in: .whitespaces)
        let index = lines.firstIndex(where: isAreaLine)
        lines.removeAll(where: isAreaLine)
        if !label.isEmpty {
            lines.insert("Zona: " + label, at: min(index ?? 1, lines.count))
        }
        return lines.joined(separator: "\n")
    }

    private static func isAreaLine(_ line: String) -> Bool {
        line.trimmingCharacters(in: .whitespaces).lowercased().hasPrefix("zona:")
    }
}

enum PawlyLostPinEdit: Equatable {
    case keep, clear, set(Int, Int)

    var latitude: Int? {
        if case let .set(latitude, _) = self { return latitude }
        return nil
    }

    var longitude: Int? {
        if case let .set(_, longitude) = self { return longitude }
        return nil
    }

    func matches(_ lat: Int?, _ lon: Int?, invalidated: Bool) -> Bool {
        self != .keep && lat == latitude && lon == longitude && invalidated == (self == .clear)
    }
}

enum PawlyLostEditError: LocalizedError {
    case changed
    var errorDescription: String? { "Otra persona o dispositivo ha modificado el aviso. Tu borrador sigue aquí." }
}
