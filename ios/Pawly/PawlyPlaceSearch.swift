import MapKit
import OSLog

@MainActor final class PawlyPlaceSearch: ObservableObject {
    @Published private(set) var searching = false
    private var active: MKLocalSearch?
    private let log = Logger(subsystem: "com.alexvilarrubla.pawly", category: "MapSearch")

    func cancel() {
        active?.cancel()
        active = nil
        searching = false
    }

    func results(for request: MKLocalSearch.Request) async throws -> [MKMapItem] {
        cancel()
        let search = MKLocalSearch(request: request)
        active = search
        searching = true
        defer {
            if active === search { active = nil; searching = false }
        }
        do {
            let response = try await search.start()
            guard active === search, !Task.isCancelled else { throw CancellationError() }
            return response.mapItems
        } catch {
            guard active === search, !Task.isCancelled else { throw CancellationError() }
            let cause = error as NSError
            log.notice("Map search failed: domain \\(cause.domain, privacy: .public), code \\(cause.code, privacy: .public)")
            throw error
        }
    }
}
