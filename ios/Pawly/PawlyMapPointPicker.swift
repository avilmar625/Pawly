import SwiftUI
import MapKit

struct PawlyMapPointPicker: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var gps = PawlyLocationProvider()
    @StateObject private var search = PawlyPlaceSearch()
    @State private var query: String
    @State private var results: [MKMapItem] = []
    @State private var region: MKCoordinateRegion
    @State private var candidate: MKPlacemark?
    @State private var message: String?
    @State private var usingGPS = false
    @State private var reverseName = ""
    @State private var reversePoint: CLLocationCoordinate2D?
    @State private var searchID = UUID()
    @FocusState private var typing: Bool
    let initialPoint: CLLocationCoordinate2D?
    let approximate: Bool
    let title: LocalizedStringKey
    let onSelect: (String, CLLocationCoordinate2D) -> Void

    init(query: String, point: CLLocationCoordinate2D?, title: LocalizedStringKey, approximate: Bool = false,
         onSelect: @escaping (String, CLLocationCoordinate2D) -> Void) {
        _query = State(initialValue: query)
        initialPoint = point; self.title = title; self.approximate = approximate; self.onSelect = onSelect
        _region = State(initialValue: point.map { MKCoordinateRegion(center: $0, latitudinalMeters: 2400, longitudinalMeters: 2400) }
                        ?? MKCoordinateRegion(MKMapRect.world))
    }

    private var ready: Bool { region.span.latitudeDelta < 8 && CLLocationCoordinate2DIsValid(region.center) }
    private var point: CLLocationCoordinate2D { approximate ? PawlyMapPoint.approximate(region.center) : region.center }
    private var centerKey: String { PawlyMapPoint.label(point, approximate: approximate) }
    private func close(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Bool {
        CLLocation(latitude: a.latitude, longitude: a.longitude).distance(from: CLLocation(latitude: b.latitude, longitude: b.longitude)) < (approximate ? 1000 : 80)
    }
    private func address(_ placemark: CLPlacemark) -> String {
        let pieces = approximate ? [placemark.subLocality, placemark.locality] : [placemark.name, placemark.locality]
        var unique = Set<String>()
        return pieces.compactMap { $0 }.filter { !$0.isEmpty && unique.insert($0).inserted }.joined(separator: ", ")
    }
    private var selectedName: String {
        if let reversePoint, close(point, reversePoint), !reverseName.isEmpty { return reverseName }
        if let candidate, close(point, candidate.coordinate) {
            let name = address(candidate)
            if !name.isEmpty { return name }
        }
        return centerKey
    }
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    TextField("Dirección o coordenadas", text: $query).textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("map.address").focused($typing).submitLabel(.search).onSubmit { Task { await lookUp() } }
                    Button { Task { await lookUp() } } label: { Image(systemName: "magnifyingglass") }
                        .accessibilityLabel("Buscar en el mapa").disabled(query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Button {
                        searchID = UUID(); search.cancel(); typing = false; message = nil; usingGPS = true; gps.request()
                    } label: { Image(systemName: "location.fill") }.accessibilityLabel("Usar mi ubicación")
                }.padding(.horizontal)
                if search.searching { ProgressView().padding(.horizontal) }
                if let status = (usingGPS ? gps.message : nil) ?? message { Text(LocalizedStringKey(status)).font(.caption).padding(.horizontal) }
                if !results.isEmpty {
                    ScrollView {
                        VStack(alignment: .leading) {
                            ForEach(Array(results.enumerated()), id: \.offset) { _, item in
                                Button {
                                    choose(item.placemark.coordinate, placemark: item.placemark)
                                    results = []; typing = false
                                } label: {
                                    VStack(alignment: .leading) {
                                        Text(verbatim: item.name ?? item.placemark.title ?? "")
                                        if let detail = item.placemark.title { Text(verbatim: detail).font(.caption).foregroundStyle(.secondary) }
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                }.buttonStyle(.borderless).padding(.vertical, 3)
                            }
                        }
                    }.frame(maxHeight: 135).padding(.horizontal)
                }
                Map(coordinateRegion: $region, interactionModes: .all)
                    .overlay { Image(systemName: "scope").font(.largeTitle.bold()).foregroundStyle(.red)
                        .shadow(color: .white, radius: 2).allowsHitTesting(false).accessibilityHidden(true) }
                    .accessibilityLabel("Mapa de selección")
                VStack(alignment: .leading, spacing: 4) {
                    Text("Acerca o mueve el mapa y confirma el punto central.").font(.caption)
                    if ready { Text(verbatim: selectedName).font(.subheadline) }
                    Button("Usar este punto") { onSelect(selectedName, point); dismiss() }
                        .buttonStyle(.borderedProminent).disabled(!ready).accessibilityIdentifier("map.confirm")
                }.padding(.horizontal)
            }.padding(.vertical, 8).navigationTitle(title).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } } }
                .onReceive(gps.$location) { fix in
                    if let fix { results = []; message = nil; choose(fix.coordinate) }
                }
                .task {
                    if initialPoint == nil && !query.isEmpty { await lookUp() }
                }
                .task(id: centerKey) {
                    guard ready else { return }
                    let location = point
                    if let candidate, close(candidate.coordinate, location), !address(candidate).isEmpty { return }
                    do {
                        try await Task.sleep(nanoseconds: 650_000_000)
                        let mark = try await CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: location.latitude, longitude: location.longitude)).first
                        try Task.checkCancellation()
                        reverseName = mark.map(address) ?? ""; reversePoint = location
                    } catch { }
                }
                .onDisappear { gps.cancel(); searchID = UUID(); search.cancel() }
        }
    }
    private func choose(_ coordinate: CLLocationCoordinate2D, placemark: MKPlacemark? = nil) {
        candidate = placemark; reverseName = ""; reversePoint = nil
        let center = approximate ? PawlyMapPoint.approximate(coordinate) : coordinate
        region = MKCoordinateRegion(center: center, latitudinalMeters: 2300, longitudinalMeters: 2300)
    }
    @MainActor private func lookUp() async {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return }
        let id = UUID(); searchID = id; search.cancel(); gps.cancel(); usingGPS = false; message = nil; results = []; typing = false
        if let coordinate = PawlyMapPoint.parse(term) { choose(coordinate); return }
        let request = MKLocalSearch.Request(); request.naturalLanguageQuery = term
        if ready { request.region = region }
        do {
            let matches = try await search.results(for: request)
            guard searchID == id, !Task.isCancelled else { return }
            if let first = matches.first {
                choose(first.placemark.coordinate, placemark: first.placemark)
                results = matches.count > 1 ? Array(matches.prefix(8)) : []; return
            }
        } catch { if searchID != id || Task.isCancelled { return } }
        do {
            let mark = try await CLGeocoder().geocodeAddressString(term).first
            guard searchID == id, !Task.isCancelled else { return }
            if let mark, let location = mark.location { choose(location.coordinate, placemark: MKPlacemark(placemark: mark)); return }
        } catch { if searchID != id || Task.isCancelled { return } }
        message = "No se encontró la dirección. Puedes elegir el punto en el mapa."
    }
}
