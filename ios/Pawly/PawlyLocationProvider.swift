import Foundation
import Combine
import OSLog
@preconcurrency import CoreLocation

@MainActor final class PawlyLocationProvider: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var location: CLLocation?
    @Published private(set) var message: String?
    private let manager = CLLocationManager()
    private let log = Logger(subsystem: "com.alexvilarrubla.pawly", category: "Location")
    private var pending = false
    private var locating = false
    private var attempts = 0
    private var retry: Task<Void, Never>?
    private var deadline: Task<Void, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func request() {
        guard !pending else { return }
        pending = true
        attempts = 0
        location = nil
        message = "Buscando ubicación…"
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        else { authorizationChanged() }
    }

    func cancel() {
        pending = false
        locating = false
        retry?.cancel()
        deadline?.cancel()
        manager.stopUpdatingLocation()
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationChanged()
    }

    private func authorizationChanged() {
        guard pending else { return }
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            guard !locating else { return }
            locating = true
            manager.requestLocation()
            deadline = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 18_000_000_000)
                guard !Task.isCancelled else { return }
                self?.unavailable()
            }
        case .denied, .restricted:
            cancel()
            message = "Permite el acceso a la ubicación en Ajustes de iOS."
        case .notDetermined: break
        @unknown default: unavailable()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard pending else { return }
        guard let fix = locations.last(where: { $0.horizontalAccuracy >= 0 && abs($0.timestamp.timeIntervalSinceNow) < 120 }) else {
            retryLocation()
            return
        }
        cancel()
        location = fix
        message = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard pending else { return }
        let code = (error as NSError).code
        log.notice("Location request failed, code \(code, privacy: .public)")
        switch (error as? CLError)?.code {
        case .locationUnknown: retryLocation()
        case .denied:
            cancel()
            message = "Permite el acceso a la ubicación en Ajustes de iOS."
        default: unavailable()
        }
    }

    private func retryLocation() {
        guard attempts < 2 else { unavailable(); return }
        attempts += 1
        retry?.cancel()
        retry = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard !Task.isCancelled, let self, self.pending else { return }
            self.manager.requestLocation()
        }
    }

    private func unavailable() {
        cancel()
        message = "No se ha podido obtener la ubicación. Vuelve a intentarlo o busca por zona."
    }
}
