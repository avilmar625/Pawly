import SwiftUI
import FirebaseAuth
import FirebaseFirestore

struct PawlyModerationReport: Identifiable {
    var id: String
    var reporterID: String
    var targetID: String
    var targetType: String
    var reason: String
    var status: String
    var createdAt: Date?
}

@MainActor
final class PawlyModerationStore: ObservableObject {
    @Published private(set) var isAdmin = false
    @Published private(set) var reports: [PawlyModerationReport] = []
    @Published private(set) var pendingPlaces: [PawlyCommunityPlace] = []
    @Published var errorMessage: String?
    @Published var statusMessage: String?
    @Published var isLoading = false

    private let db = Firestore.firestore()

    func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let user = try await verifiedAdminUser()
            let (reportSnapshot, placeSnapshot) = try await (
                db.collection("reports").order(by: "createdAt", descending: true).limit(to: 100).getDocuments(),
                db.collection("communityPlaces").whereField("status", isEqualTo: "pending").limit(to: 100).getDocuments()
            )
            reports = reportSnapshot.documents.map { document in
                PawlyModerationReport(
                    id: document.documentID,
                    reporterID: document.get("reporterId") as? String ?? "",
                    targetID: document.get("targetId") as? String ?? "",
                    targetType: document.get("targetType") as? String ?? "",
                    reason: document.get("reason") as? String ?? "",
                    status: document.get("status") as? String ?? "open",
                    createdAt: (document.get("createdAt") as? Timestamp)?.dateValue()
                )
            }
            pendingPlaces = placeSnapshot.documents.compactMap { document in
                guard let name = document.get("name") as? String,
                      let category = document.get("category") as? String,
                      let location = document.get("location") as? String else { return nil }
                return PawlyCommunityPlace(
                    id: document.documentID,
                    name: name,
                    category: category,
                    location: location,
                    description: document.get("description") as? String ?? "",
                    authorUID: document.get("authorUid") as? String ?? "",
                    createdAt: (document.get("createdAt") as? Timestamp)?.dateValue(),
                    latitude: (document.get("latitude") as? NSNumber)?.doubleValue,
                    longitude: (document.get("longitude") as? NSNumber)?.doubleValue,
                    status: document.get("status") as? String ?? "pending"
                )
            }
            isAdmin = true
            errorMessage = nil
            statusMessage = nil
            _ = user
        } catch {
            isAdmin = false
            reports = []
            pendingPlaces = []
            errorMessage = error.localizedDescription
        }
    }

    func reviewPlace(_ place: PawlyCommunityPlace, approve: Bool) async throws {
        _ = try await verifiedAdminUser()
        try await db.collection("communityPlaces").document(place.id).updateData([
            "status": approve ? "approved" : "rejected"
        ])
        pendingPlaces.removeAll { $0.id == place.id }
        statusMessage = approve ? "Lugar aprobado y publicado en Explorar." : "Propuesta rechazada."
    }

    func markReportReviewed(_ report: PawlyModerationReport) async throws {
        _ = try await verifiedAdminUser()
        try await db.collection("reports").document(report.id).updateData([
            "status": "reviewed",
            "reviewedAt": FieldValue.serverTimestamp()
        ])
        reports.removeAll { $0.id == report.id }
        statusMessage = "Denuncia marcada como revisada."
    }

    func restrictReportedUser(_ report: PawlyModerationReport) async throws {
        _ = try await verifiedAdminUser()
        guard report.targetType == "user" else { throw PawlyModerationError.invalidTarget }
        try await db.collection("users").document(report.targetID).updateData([
            "isPublic": false,
            "moderationStatus": "restricted"
        ])
        try await markReportReviewed(report)
        statusMessage = "Perfil restringido y denuncia revisada."
    }

    func removeReportedEvent(_ report: PawlyModerationReport) async throws {
        _ = try await verifiedAdminUser()
        guard report.targetType == "event" else { throw PawlyModerationError.invalidTarget }
        try await db.collection("events").document(report.targetID).delete()
        try await markReportReviewed(report)
        statusMessage = "Evento retirado y denuncia revisada."
    }

    func removeReportedPlace(_ report: PawlyModerationReport) async throws {
        _ = try await verifiedAdminUser()
        guard report.targetType == "place" else { throw PawlyModerationError.invalidTarget }
        try await db.collection("communityPlaces").document(report.targetID).delete()
        try await markReportReviewed(report)
        statusMessage = "Lugar retirado y denuncia revisada."
    }

    func removeReportedPost(_ report: PawlyModerationReport) async throws {
        _ = try await verifiedAdminUser()
        guard report.targetType == "post" else { throw PawlyModerationError.invalidTarget }
        let reference = db.collection("posts").document(report.targetID)
        let snapshot = try await reference.getDocument()
        guard snapshot.exists else {
            try await markReportReviewed(report)
            return
        }
        let urls = [
            snapshot.get("imageUrl") as? String ?? "",
            snapshot.get("communityImageUrl") as? String ?? "",
            snapshot.get("videoUrl") as? String ?? ""
        ].filter { $0.hasPrefix("https://") }

        // Delete indexed Cloudinary media through the trusted Worker before
        // deleting the post. If the Worker is not configured, leave the post
        // intact so a moderator can retry rather than orphan its media.
        for url in Set(urls) {
            _ = try await PawlyBackendClient.postJSON(
                path: "/v1/cloudinary/assets/delete",
                body: ["secureUrl": url]
            )
        }
        try await reference.delete()
        try await markReportReviewed(report)
        statusMessage = "Publicación retirada, recursos multimedia eliminados y denuncia revisada."
    }

    private func verifiedAdminUser() async throws -> User {
        guard let user = Auth.auth().currentUser, user.isEmailVerified else {
            throw PawlyModerationError.notAuthorized
        }
        let claims = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[String: Any], Error>) in
            user.getIDTokenResult(forcingRefresh: true) { result, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let result {
                    continuation.resume(returning: result.claims)
                } else {
                    continuation.resume(throwing: PawlyModerationError.notAuthorized)
                }
            }
        }
        guard claims["admin"] as? Bool == true else { throw PawlyModerationError.notAuthorized }
        return user
    }
}

enum PawlyModerationError: LocalizedError {
    case notAuthorized
    case invalidTarget

    var errorDescription: String? {
        switch self {
        case .notAuthorized:
            return "El centro de moderación está reservado a cuentas administradoras verificadas."
        case .invalidTarget:
            return "El elemento denunciado no coincide con la acción de moderación."
        }
    }
}

struct PawlyModerationView: View {
    @StateObject private var store = PawlyModerationStore()
    @State private var actionError: String?

    var body: some View {
        List {
            if store.isLoading {
                ProgressView("Comprobando permisos y cargando moderación…")
            } else if !store.isAdmin {
                PawlyEmptyState(
                    icon: "lock.shield",
                    title: "Acceso restringido",
                    detail: store.errorMessage ?? "Esta sección solo está disponible para el equipo de moderación."
                )
            } else {
                Section {
                    Text("Revisa las denuncias y las propuestas de lugares. Las acciones se registran en Firebase y requieren el claim de administrador.")
                        .font(.caption).foregroundStyle(.secondary)
                    if let message = store.statusMessage {
                        Text(message).font(.caption).foregroundStyle(PawlyColors.primary)
                    }
                    if let error = actionError ?? store.errorMessage {
                        Text(error).font(.caption).foregroundStyle(.red)
                    }
                }
                Section("Lugares pendientes") {
                    if store.pendingPlaces.isEmpty {
                        Text("No hay propuestas pendientes.").foregroundStyle(.secondary)
                    }
                    ForEach(store.pendingPlaces) { place in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(place.name).font(.headline)
                            Text("\(place.category) · \(place.location)")
                                .font(.caption).foregroundStyle(PawlyColors.primary)
                            Text(place.description).font(.subheadline)
                            Text("Autor: \(place.authorUID)")
                                .font(.caption2).foregroundStyle(.secondary)
                            HStack {
                                Button("Aprobar") {
                                    Task { await runAction { try await store.reviewPlace(place, approve: true) } }
                                }
                                .buttonStyle(.borderedProminent).tint(PawlyColors.primary)
                                Button("Rechazar", role: .destructive) {
                                    Task { await runAction { try await store.reviewPlace(place, approve: false) } }
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
                Section("Denuncias") {
                    if store.reports.isEmpty {
                        Text("No hay denuncias registradas.").foregroundStyle(.secondary)
                    }
                    ForEach(store.reports) { report in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(report.targetType.uppercased()).font(.caption.weight(.bold))
                                Spacer()
                                Text(report.status).font(.caption).foregroundStyle(.secondary)
                            }
                            Text(report.reason).font(.subheadline)
                            Text("Elemento: \(report.targetID)")
                                .font(.caption2).foregroundStyle(.secondary)
                            HStack(spacing: 8) {
                                if report.targetType == "post" {
                                    Button("Retirar publicación", role: .destructive) {
                                        Task { await runAction { try await store.removeReportedPost(report) } }
                                    }
                                } else if report.targetType == "user" {
                                    Button("Restringir perfil", role: .destructive) {
                                        Task { await runAction { try await store.restrictReportedUser(report) } }
                                    }
                                } else if report.targetType == "event" {
                                    Button("Retirar evento", role: .destructive) {
                                        Task { await runAction { try await store.removeReportedEvent(report) } }
                                    }
                                } else if report.targetType == "place" {
                                    Button("Retirar lugar", role: .destructive) {
                                        Task { await runAction { try await store.removeReportedPlace(report) } }
                                    }
                                }
                                Button("Marcar revisada") {
                                    Task { await runAction { try await store.markReportReviewed(report) } }
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("Moderación")
        .task { await store.load() }
        .refreshable { await store.load() }
    }

    @MainActor private func runAction(_ action: () async throws -> Void) async {
        do {
            try await action()
            actionError = nil
        } catch {
            actionError = error.localizedDescription
        }
    }
}
