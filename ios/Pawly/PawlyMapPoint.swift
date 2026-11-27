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

    static func approximate(_ point: CLLocationCoordinate2D) -> CLLocationCoordinate2D {
        .init(latitude: (point.latitude * 100).rounded()/100, longitude: (point.longitude * 100).rounded()/100)
    }

    static func label(_ point: CLLocationCoordinate2D, approximate: Bool) -> String {
        String(format: approximate ? "%.2f, %.2f" : "%.5f, %.5f", locale: Locale(identifier: "en_US_POSIX"),
               point.latitude, point.longitude)
    }
}
