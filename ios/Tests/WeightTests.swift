import Foundation
@main enum WeightTests {
    static func main() {
        precondition(PawlyWeight.kilograms(title: "Peso", detail: "12,75 kg") == 12.75)
        precondition(PawlyWeight.kilograms(title: "Peso: 15,2", detail: "revisión") == 15.2)
        precondition(PawlyWeight.kilograms(title: "20", detail: "medición 2") == 20)
        for invalid in ["1000 kg", "0 kg", "-1 kg", "2026-11-25", "dosis 12 ml", "170 kg"] {
            precondition(PawlyWeight.kilograms(title: invalid, detail: "") == nil)
        }
        print("Legacy and current kilogram records, limits and unrelated numbers passed")
    }
}
