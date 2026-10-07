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

struct NotificationPreviewSheet: View {
    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "bell.badge.fill")
                .font(.system(size: 30))
                .foregroundStyle(.indigo)
                .frame(width: 62, height: 62)
                .background(Color.indigo.opacity(0.12), in: Circle())
            VStack(spacing: 6) {
                Text("오늘의 카드가 도착했어요")
                    .font(.headline)
                Text("매일 한 장의 AI 인사이트를 알려드릴게요.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Text("정적 목 데이터 기반 미리보기입니다.")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding()
    }
}
