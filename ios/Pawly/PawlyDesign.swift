import SwiftUI

enum PawlyColors {
    static var isDarkMode: Bool {
        UserDefaults.standard.bool(forKey: "pawly.darkMode")
    }

    static var primary: Color {
        switch UserDefaults.standard.string(forKey: "pawly.accent") ?? "forest" {
        case "ocean": return Color(red: 0.10, green: 0.39, blue: 0.57)
        case "berry": return Color(red: 0.48, green: 0.29, blue: 0.50)
        case "sunset": return Color(red: 0.72, green: 0.34, blue: 0.22)
        default: return Color(red: 0.17, green: 0.48, blue: 0.39)
        }
    }

    /// A readable card/control surface that follows Pawly's in-app appearance setting.
    static var surface: Color {
        isDarkMode ? Color(red: 0.12, green: 0.15, blue: 0.13) : .white
    }

    /// A subtly raised surface for nested cards and media controls.
    static var raisedSurface: Color {
        isDarkMode ? Color(red: 0.16, green: 0.19, blue: 0.17) : .white
    }

    static var mint: Color {
        isDarkMode
            ? Color(red: 0.13, green: 0.22, blue: 0.19)
            : Color(red: 0.88, green: 0.95, blue: 0.90)
    }

    static var canvas: Color {
        isDarkMode
            ? Color(red: 0.07, green: 0.09, blue: 0.08)
            : Color(red: 0.96, green: 0.97, blue: 0.94)
    }

    static var ink: Color {
        isDarkMode ? .white : Color(red: 0.13, green: 0.20, blue: 0.17)
    }

    static var muted: Color {
        isDarkMode
            ? Color(red: 0.68, green: 0.73, blue: 0.69)
            : Color(red: 0.43, green: 0.49, blue: 0.45)
    }

    static var peach: Color {
        isDarkMode
            ? Color(red: 0.25, green: 0.19, blue: 0.14)
            : Color(red: 0.99, green: 0.90, blue: 0.82)
    }
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
            .background(PawlyColors.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(PawlyColors.ink.opacity(0.045), lineWidth: 1)
            )
            .shadow(color: PawlyColors.ink.opacity(0.045), radius: 18, x: 0, y: 8)
    }
}
