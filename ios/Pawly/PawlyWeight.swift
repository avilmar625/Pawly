import Foundation

enum PawlyWeight {
    static func kilograms(title: String, detail: String) -> Double? {
        let unit = #"(?<![\d.,-])\d{1,3}(?:[.,]\d{1,2})?(?=\s*kg\b)"#
        let standalone = #"^\s*(?:peso\s*[:=]?\s*)?(\d{1,3}(?:[.,]\d{1,2})?)\s*$"#
        for field in [title, detail] {
            let text = field.lowercased()
            let matched: Substring?
            if let range = text.range(of: unit, options: .regularExpression) {
                matched = text[range]
            } else if text.range(of: standalone, options: .regularExpression) != nil {
                matched = text.drop { !$0.isNumber }.prefix { $0.isNumber || $0 == "," || $0 == "." }
            } else { continue }
            if let matched, let value = Double(matched.replacingOccurrences(of: ",", with: ".")),
               (0.1...150).contains(value) { return value }
        }
        return nil
    }
}
