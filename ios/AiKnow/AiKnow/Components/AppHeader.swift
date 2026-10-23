import SwiftUI

struct AppHeader: View {
    let title: String
    var subtitle: String? = nil
    var leadingIcon: String? = nil
    var leadingAction: (() -> Void)? = nil
    var trailingIcon: String? = nil
    var trailingAction: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 12) {
            if let leadingIcon, let leadingAction {
                HeaderIconButton(systemName: leadingIcon, action: leadingAction)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(AppPalette.ink)
                if let subtitle {
                    Text(subtitle)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                }
            }

            Spacer(minLength: 0)

            if let trailingIcon, let trailingAction {
                HeaderIconButton(systemName: trailingIcon, action: trailingAction)
            }
        }
        .frame(minHeight: 48)
        .padding(.horizontal, 12)
    }
}

private struct HeaderIconButton: View {
    let systemName: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.body.weight(.semibold))
                .foregroundStyle(AppPalette.ink)
                .frame(width: 42, height: 42)
                .background(.thinMaterial, in: Circle())
        }
    }
}
