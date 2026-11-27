import Foundation

@main struct MapPointTests {
    static func main() {
        for text in ["41.3874, 2.1686", "41,3874; 2,1686", " 41.3874 2.1686 "] {
            guard let coordinate = PawlyMapPoint.parse(text) else { fatalError("Rejected coordinates: \(text)") }
            let cell = PawlyMapPoint.approximate(coordinate)
            precondition(cell.latitude == 41.39 && cell.longitude == 2.17)
            precondition(PawlyMapPoint.label(cell, approximate: true) == "41.39, 2.17")
        }
        for text in ["91,2", "-20,181", "nan, 2", "Calle Mayor 12", "", "12,34,56"] {
            precondition(PawlyMapPoint.parse(text) == nil)
        }
        let link = PawlyMapPoint.shareURL(PawlyMapPoint.parse("41.38,2.16")!, name:"Café & Parc")!
        precondition(PawlyMapPoint.sharedURL(in: "Paseo\n" + link.absoluteString) == link)
        precondition(PawlyMapPoint.sharedURL(in:"https://maps.apple.com.ejemplo.com/?ll=41,2") == nil)
        let negative = PawlyMapPoint.approximate(PawlyMapPoint.parse("-33.8688, -70.6693")!)
        precondition(negative.latitude == -33.87 && negative.longitude == -70.67)
        precondition(PawlyMapPoint.parse("0, 0") != nil)
        let legacy = "Mascota perdida\n  zOnA: Barrio anterior\nContacto: Ana\n\nSeñas"
        let moved = PawlyLostNoticeText.replacingArea(in: legacy, with: "Zona nueva")
        precondition(moved == "Mascota perdida\nZona: Zona nueva\nContacto: Ana\n\nSeñas")
        precondition(PawlyLostNoticeText.area(in: moved) == "Zona nueva")
        precondition(PawlyLostNoticeText.replacingArea(in: moved, with: "") == "Mascota perdida\nContacto: Ana\n\nSeñas")
        precondition(PawlyLostNoticeText.replacingArea(in: "Aviso sin zona", with: "Puerto") == "Aviso sin zona\nZona: Puerto")
        precondition(PawlyLostPinEdit.set(0, 0).matches(0, 0, invalidated: false))
        precondition(PawlyLostPinEdit.clear.matches(nil, nil, invalidated: true))
        precondition(!PawlyLostPinEdit.keep.matches(nil, nil, invalidated: false))
        print("Map coordinates, privacy rounding and share links passed")
    }
}
