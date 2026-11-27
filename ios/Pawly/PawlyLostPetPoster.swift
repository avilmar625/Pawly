import SwiftUI
import UIKit

/// Local-only poster artwork shared as a JPEG; no photo is uploaded to render it.
struct PawlyLostPetPoster: View {
    let name: String
    let breed: String
    let area: String
    let lastSeen: String
    let description: String
    let contact: String
    let photo: UIImage?

    private let ink = Color(red: 0.16, green: 0.22, blue: 0.19)
    private let muted = Color(red: 0.40, green: 0.46, blue: 0.42)
    private let paper = Color(red: 0.97, green: 0.95, blue: 0.91)
    private let mint = Color(red: 0.86, green: 0.92, blue: 0.87)

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 5) {
                Text("SE BUSCA")
                    .font(.system(size: 30, weight: .black, design: .rounded))
                    .tracking(1.5)
                Text(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Mascota perdida" : name)
                    .font(.system(size: 25, weight: .bold, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Text("AYÚDANOS A ENCONTRARLA")
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.1)
                    .foregroundStyle(Color(red: 1.0, green: 0.86, blue: 0.63))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.vertical, 17)
            .background(
                LinearGradient(
                    colors: [Color(red: 0.09, green: 0.24, blue: 0.19), PawlyColors.primary],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )

            VStack(spacing: 11) {
                Group {
                    if let photo {
                        Image(uiImage: photo)
                            .resizable()
                            .scaledToFill()
                    } else {
                        VStack(spacing: 8) {
                            Image(systemName: "pawprint.fill").font(.system(size: 36))
                            Text("FOTO DE TU MASCOTA").font(.system(size: 11, weight: .bold))
                        }
                        .foregroundStyle(muted)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(mint)
                    }
                }
                .frame(height: 158)
                .frame(maxWidth: .infinity)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))

                VStack(alignment: .leading, spacing: 7) {
                    detailLine("ZONA", area)
                    detailLine("ÚLTIMA VEZ VISTA", lastSeen)
                    detailLine("ESPECIE O RAZA", breed)
                    detailLine("SEÑAS", description)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if !contact.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("CONTACTO").font(.system(size: 10, weight: .bold)).tracking(0.8).foregroundStyle(muted)
                        Text(contact).font(.system(size: 14, weight: .bold)).foregroundStyle(ink).lineLimit(2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(mint, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }

                Spacer(minLength: 0)
                HStack(spacing: 5) {
                    Image(systemName: "pawprint.fill")
                    Text("Publicado con Pawly · Comparte este cartel")
                }
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(muted)
                .frame(maxWidth: .infinity, alignment: .center)
            }
            .padding(18)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(width: 360).fixedSize(horizontal: false, vertical: true)
        .background(paper)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    @ViewBuilder private func detailLine(_ label: String, _ value: String) -> some View {
        if !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            VStack(alignment: .leading, spacing: 2) {
                Text(LocalizedStringKey(label)).font(.system(size: 9, weight: .bold)).tracking(0.7).foregroundStyle(muted)
                Text(verbatim: value).font(.system(size: 13, weight: .medium)).foregroundStyle(ink).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct PawlyPosterShareSheet: UIViewControllerRepresentable {
    let image: UIImage
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [image], applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct PawlyFileShareSheet: UIViewControllerRepresentable {
    let file: URL
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [file], applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
