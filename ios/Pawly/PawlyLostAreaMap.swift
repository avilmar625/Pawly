import MapKit
import SwiftUI

struct PawlyLostAreaMap: View {
    let coordinate: CLLocationCoordinate2D
    let name: String
    @State private var region: MKCoordinateRegion

    init(coordinate: CLLocationCoordinate2D, name: String) {
        self.coordinate = coordinate
        self.name = name
        _region = State(initialValue: MKCoordinateRegion(center: coordinate, latitudinalMeters: 2600, longitudinalMeters: 2600))
    }

    private struct Pin: Identifiable {
        let id = "area"
        let coordinate: CLLocationCoordinate2D
    }

    var body: some View {
        VStack(alignment: .leading) {
            Map(coordinateRegion: $region, annotationItems: [Pin(coordinate: coordinate)]) { pin in
                MapMarker(coordinate: pin.coordinate, tint: PawlyColors.primary)
            }
            .frame(height: 210).clipShape(RoundedRectangle(cornerRadius: 12))
            .accessibilityLabel("Ubicación aproximada del aviso")
            Button {
                let item = MKMapItem(placemark: MKPlacemark(coordinate: coordinate))
                item.name = name
                item.openInMaps()
            } label: { Label("Abrir en Mapas", systemImage: "map") }
        }
    }
}
