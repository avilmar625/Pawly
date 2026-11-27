import SwiftUI
import MapKit
import FirebaseFirestore

private enum ExploreLayer: String, CaseIterable {
    case nearby = "Cerca", favourite = "Guardados", event = "Eventos", community = "Comunidad"

    var symbol: String {
        switch self {
        case .nearby: return "mappin"
        case .favourite: return "bookmark.fill"
        case .event: return "calendar"
        case .community: return "pawprint.fill"
        }
    }

    var color: Color {
        switch self {
        case .nearby: return .blue
        case .favourite: return .orange
        case .event: return .purple
        case .community: return .green
        }
    }
}

private struct ExplorePin: Identifiable {
    let id: String
    let name: String
    let layer: ExploreLayer
    let coordinate: CLLocationCoordinate2D

    init?(_ id: String, _ name: String?, _ layer: ExploreLayer, _ latitude: Double?, _ longitude: Double?) {
        guard let name, !name.isEmpty, let latitude, let longitude else { return nil }
        let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
        self.id = layer.rawValue + "/" + id
        self.name = name
        self.layer = layer
        self.coordinate = coordinate
    }
}

struct PawlyExploreMap: View {
    let saved: [PawlySavedPlace]
    let nearby: [PawlyPlaceResult]
    @StateObject private var gps = PawlyLocationProvider()
    @State var region: MKCoordinateRegion
    @State private var enabled = Set(ExploreLayer.allCases)
    @State private var remote: [ExplorePin] = []
    @State private var failed: [ExploreLayer] = []
    @State private var loading = false
    @State private var selected: ExplorePin?

    private var visible: [ExplorePin] {
        let favourites = saved.compactMap { ExplorePin($0.id, $0.name, .favourite, $0.latitude, $0.longitude) }
        let results = nearby.compactMap { ExplorePin($0.id, $0.name, .nearby, $0.coordinate.latitude, $0.coordinate.longitude) }
        return (favourites + results + remote).filter { enabled.contains($0.layer) }
    }

    var body: some View {
        VStack {
            HStack {
                Menu {
                    ForEach(ExploreLayer.allCases, id: \.self) { layer in
                        Button {
                            if enabled.contains(layer) { enabled.remove(layer) } else { enabled.insert(layer) }
                            selected = nil
                        } label: {
                            Label(LocalizedStringKey(layer.rawValue), systemImage: enabled.contains(layer) ? "checkmark.circle.fill" : "circle")
                        }
                    }
                } label: { Label("Filtros", systemImage: "line.3.horizontal.decrease.circle") }
                Spacer()
                if loading { ProgressView() }
                Button { gps.request() } label: { Image(systemName: "location.fill") }.accessibilityLabel("Usar mi ubicación")
                Button { fit() } label: { Label("Mostrar todos", systemImage: "arrow.up.left.and.arrow.down.right") }
                    .disabled(visible.isEmpty)
            }.padding(.horizontal)
            if let message = gps.message { Text(LocalizedStringKey(message)).font(.caption) }
            Map(coordinateRegion: $region, showsUserLocation: gps.location != nil, annotationItems: visible) { pin in
                MapAnnotation(coordinate: pin.coordinate) {
                    Button { selected = pin } label: {
                        Image(systemName: pin.layer.symbol).foregroundStyle(.white).padding(8)
                            .background(pin.layer.color, in: Circle())
                    }.accessibilityLabel(pin.name)
                }
            }
            if let selected {
                HStack {
                    Label(selected.name, systemImage: selected.layer.symbol)
                    Spacer()
                    Button("Cómo llegar") {
                        let item = MKMapItem(placemark: MKPlacemark(coordinate: selected.coordinate))
                        item.name = selected.name
                        item.openInMaps()
                    }
                }.padding(.horizontal)
            } else if visible.isEmpty && !loading { Text("No hay lugares para estos filtros.").foregroundStyle(.secondary) }
            ForEach(failed, id: \.self) { layer in
                HStack { Text(LocalizedStringKey(layer.rawValue)); Button("Reintentar") { Task { await load() } }.disabled(loading) }
            }
        }
        .navigationTitle("Mapa de Pawly").navigationBarTitleDisplayMode(.inline)
        .onReceive(gps.$location) { fix in
            if let fix { region = MKCoordinateRegion(center: fix.coordinate, latitudinalMeters: 8000, longitudinalMeters: 8000) }
        }.onDisappear { gps.cancel() }
        .task { await load() }
    }

    @MainActor private func load() async {
        guard !loading else { return }
        loading = true
        defer { loading = false }
        let db = Firestore.firestore()
        let queries: [(ExploreLayer, Query, String)] = [
            (.event, db.collection("events").whereField("startAt", isGreaterThanOrEqualTo: Timestamp(date: Date())).order(by: "startAt").limit(to: 60), "title"),
            (.community, db.collection("communityPlaces").whereField("status", isEqualTo: "approved").limit(to: 100), "name")
        ]
        failed = []
        for (layer, query, field) in queries {
            do {
                let records = try await query.getDocuments()
                guard !Task.isCancelled else { return }
                let pins = records.documents.compactMap { doc in
                    ExplorePin(doc.documentID, doc.get(field) as? String, layer,
                               (doc.get("latitude") as? NSNumber)?.doubleValue, (doc.get("longitude") as? NSNumber)?.doubleValue)
                }
                remote.removeAll { $0.layer == layer }
                remote.append(contentsOf: pins)
            } catch { if !Task.isCancelled { failed.append(layer) } }
        }
    }

    private func fit() {
        let bounds = visible.reduce(MKMapRect.null) { rect, pin in
            let point = MKMapPoint(pin.coordinate)
            return rect.union(MKMapRect(x: point.x, y: point.y, width: 1, height: 1))
        }
        guard !bounds.isNull else { return }
        region = MKCoordinateRegion(bounds.insetBy(dx: -max(2000, bounds.width * 0.15), dy: -max(2000, bounds.height * 0.15)))
    }
}
