import SwiftUI

struct LoginView: View {
    let onLogin: () -> Void

    var body: some View {
        ZStack {
            AppPalette.background
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer(minLength: 56)

                VStack(spacing: 18) {
                    BrandLogo(size: 82)
                    Text("AI를 더 쉽게,\n매일 한 장씩")
                        .font(.system(size: 30, weight: .bold, design: .rounded))
                        .multilineTextAlignment(.center)
                    Text("복잡한 AI 이야기를 핵심만 담아 전해드려요.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                VStack(spacing: 11) {
                    SocialLoginButton(
                        title: "Apple로 계속하기",
                        systemImage: "apple.logo",
                        foreground: .white,
                        background: .black,
                        action: onLogin
                    )
                    SocialLoginButton(
                        title: "카카오로 계속하기",
                        systemImage: "bubble.left.and.bubble.right.fill",
                        foreground: Color(red: 0.12, green: 0.11, blue: 0.05),
                        background: Color(red: 0.99, green: 0.88, blue: 0.23),
                        action: onLogin
                    )
                    SocialLoginButton(
                        title: "Google로 계속하기",
                        systemImage: "g.circle.fill",
                        foreground: AppPalette.ink,
                        background: .white,
                        action: onLogin,
                        bordered: true
                    )
                }

                Text("계속하면 서비스 이용약관 및 개인정보 처리방침에 동의하게 됩니다.")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 18)
                    .padding(.bottom, 12)
            }
            .padding(.horizontal, 24)
        }
    }
}

private struct SocialLoginButton: View {
    let title: String
    let systemImage: String
    let foreground: Color
    let background: Color
    let action: () -> Void
    var bordered = false

    var body: some View {
        Button(action: action) {
            ZStack {
                Text(title)
                    .font(.body.weight(.semibold))
                HStack {
                    Image(systemName: systemImage)
                        .font(.title3.weight(.semibold))
                    Spacer()
                }
                .padding(.horizontal, 18)
            }
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(background, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
            .overlay {
                if bordered {
                    RoundedRectangle(cornerRadius: 17, style: .continuous)
                        .stroke(Color.black.opacity(0.10), lineWidth: 1)
                }
            }
        }
        .buttonStyle(.plain)
    }
}
