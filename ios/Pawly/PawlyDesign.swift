import SwiftUI

enum PawlyColors {
    static let primary = Color(red: 0.17, green: 0.48, blue: 0.39)
    static let mint = Color(red: 0.88, green: 0.95, blue: 0.90)
    static let canvas = Color(red: 0.96, green: 0.97, blue: 0.94)
    static let ink = Color(red: 0.13, green: 0.20, blue: 0.17)
    static let muted = Color(red: 0.43, green: 0.49, blue: 0.45)
    static let peach = Color(red: 0.99, green: 0.90, blue: 0.82)
}

struct PawlyLogo: View {
    var body: some View {
        HStack(spacing: 9) {
            Image(systemName: "pawprint.fill")
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(PawlyColors.primary)
            Text("pawly")
                .font(.system(size: 30, weight: .heavy, design: .rounded))
                .tracking(-1.2)
                .foregroundStyle(PawlyColors.ink)
        }
    }
}

extension View {
    func pawlyCard() -> some View {
        padding(18)
            .background(.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(PawlyColors.ink.opacity(0.045), lineWidth: 1))
            .shadow(color: PawlyColors.ink.opacity(0.045), radius: 18, x: 0, y: 8)
    }
}
