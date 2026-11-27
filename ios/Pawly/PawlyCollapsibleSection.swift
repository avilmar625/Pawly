import SwiftUI

/// Native DisclosureGroup with a per-section expansion preference.
struct PawlyPersistentDisclosureGroup<Content: View>: View {
    let title: String
    let subtitle: String?
    let icon: String?
    let storageKey: String
    let highlighted: Bool
    let onAdd: (() -> Void)?
    let tint: Color
    private let content: Content
    @State private var isExpanded: Bool

    init(
        title: String,
        subtitle: String? = nil,
        icon: String? = nil,
        storageKey: String,
        defaultExpanded: Bool = true,
        highlighted: Bool = false,
        tint: Color = PawlyColors.primary,
        onAdd: (() -> Void)? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.subtitle = subtitle
        self.icon = icon
        self.storageKey = storageKey
        self.highlighted = highlighted
        self.onAdd = onAdd
        self.tint = tint
        self.content = content()
        _isExpanded = State(
            initialValue: UserDefaults.standard.object(forKey: storageKey) as? Bool ?? defaultExpanded
        )
    }

    var body: some View {
        HStack(alignment: .top) {
        DisclosureGroup(isExpanded: $isExpanded) {
            content.padding(.top, 8)
        } label: {
            HStack(spacing: 11) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(tint)
                        .frame(width: 36, height: 36)
                        .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 11))
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(LocalizedStringKey(title)).font(.headline).foregroundStyle(PawlyColors.ink)
                    if let subtitle, !subtitle.isEmpty {
                        Text(LocalizedStringKey(subtitle)).font(.caption).foregroundStyle(PawlyColors.muted)
                    }
                }
                Spacer(minLength: 8)
            }
            .padding(highlighted ? 12 : 2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                if highlighted {
                    RoundedRectangle(cornerRadius: 15, style: .continuous).fill(PawlyColors.mint)
                }
            }
            .contentShape(Rectangle())
        }
        if let onAdd {
            Button(action: onAdd) { Image(systemName: "plus.circle.fill").font(.title3).frame(width: 40, height: 40) }
                .buttonStyle(.borderless).accessibilityLabel("Añadir registro").accessibilityHint(Text(LocalizedStringKey(title)))
        }
        }
        .tint(tint)
        .onChange(of: isExpanded) { value in
            UserDefaults.standard.set(value, forKey: storageKey)
        }
    }
}
