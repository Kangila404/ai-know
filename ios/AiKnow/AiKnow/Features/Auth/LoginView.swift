import SwiftUI
import AuthenticationServices

struct LoginView: View {
    let onLogin: () -> Void
    @State private var login = AuthSession.shared
    @State private var providers: Set<APIClient.Provider> = []
    @State private var loadingProviders = true
    @State private var providerError: String?

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
                    if loadingProviders {
                        ProgressView("로그인 방법을 확인하는 중…")
                    } else if let providerError {
                        RequestFailureView(message: providerError) { Task { await loadProviders() } }
                    } else if providers.isEmpty {
                        Text("현재 이용 가능한 로그인 방법이 없습니다.").font(.footnote).foregroundStyle(.secondary)
                        Button("다시 확인") { Task { await loadProviders() } }
                    }
                    if providers.contains(.apple) {
                        AppleLoginButton {
                            Task { if await login.signInApple() { onLogin() } }
                        }
                        .frame(height: 54)
                    }
                    if providers.contains(.kakao) {
                        SocialLoginButton(provider: .kakao) {
                            Task { if await login.signInKakao() { onLogin() } }
                        }
                    }
                    if providers.contains(.google) {
                        SocialLoginButton(provider: .google) {
                            Task { if await login.signInGoogle() { onLogin() } }
                        }
                    }
                }

                .frame(maxWidth: 300)
                .disabled(login.busy)

                if login.busy { ProgressView("로그인 중…").padding(.top, 12) }
                if let message = login.errorMessage {
                    Text(message).accessibilityIdentifier("login.error").font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center).padding(.top, 12)
                }


                Text("계속하면 서비스 이용약관 및 개인정보 처리방침에 동의하게 됩니다.")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 18)
                    .padding(.bottom, 12)
            }
            .padding(.horizontal, 24)
        }.task { await loadProviders() }
    }

    private func loadProviders() async {
        loadingProviders = true; providerError = nil; providers = []
        defer { loadingProviders = false }
        do { providers = try await APIClient.shared.availableProviders() }
        catch is CancellationError { }
        catch let error as URLError where error.code == .cancelled { }
        catch { providerError = error.localizedDescription }
    }
}

// Use official symbols with a common native text layout, without scaling button screenshots.
private struct SocialLoginButton: View {
    enum Provider { case google, kakao }
    @Environment(\.colorScheme) private var colorScheme
    let provider: Provider
    let action: () -> Void
    private var isGoogle: Bool { provider == .google }
    private var title: String { isGoogle ? "Google 로그인" : "카카오 로그인" }
    private var background: Color {
        if !isGoogle { return Color(red: 254 / 255, green: 229 / 255, blue: 0) }
        return colorScheme == .dark ? Color(red: 19 / 255, green: 19 / 255, blue: 20 / 255) : .white
    }
    private var foreground: Color {
        if !isGoogle { return .black.opacity(0.85) }
        return colorScheme == .dark ? Color(red: 227 / 255, green: 227 / 255, blue: 227 / 255) : Color(white: 31 / 255)
    }
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(isGoogle ? "GoogleSymbol" : "KakaoSymbol")
                    .renderingMode(.original).resizable().scaledToFit()
                    .frame(width: 20, height: 20).accessibilityHidden(true)
                Text(title).font(.custom("GoogleSans-Regular", size: 16, relativeTo: .body).weight(.medium))
            }
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity).frame(height: 54)
            .background(background, in: RoundedRectangle(cornerRadius: 8))
            .overlay {
                if isGoogle {
                    RoundedRectangle(cornerRadius: 8)
                        .strokeBorder(colorScheme == .dark ? Color(white: 143 / 255) : Color(white: 117 / 255), lineWidth: 1)
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

// Keep AuthSession's server challenge/nonce flow while using Apple's official UI.
private struct AppleLoginButton: UIViewRepresentable {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.isEnabled) private var isEnabled
    let action: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(action: action) }

    func makeUIView(context: Context) -> UIView { UIView() }

    func updateUIView(_ container: UIView, context: Context) {
        context.coordinator.action = action
        let style: ASAuthorizationAppleIDButton.Style = colorScheme == .dark ? .white : .black
        if context.coordinator.style != style {
            container.subviews.forEach { $0.removeFromSuperview() }
            let button = ASAuthorizationAppleIDButton(type: .signIn, style: style)
            button.cornerRadius = 8
            button.accessibilityIdentifier = "login.apple"
            button.addTarget(context.coordinator, action: #selector(Coordinator.signIn), for: .touchUpInside)
            button.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(button)
            NSLayoutConstraint.activate([
                button.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                button.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                button.topAnchor.constraint(equalTo: container.topAnchor),
                button.bottomAnchor.constraint(equalTo: container.bottomAnchor)
            ])
            context.coordinator.style = style
        }
        (container.subviews.first as? ASAuthorizationAppleIDButton)?.isEnabled = isEnabled
    }

    final class Coordinator: NSObject {
        var action: () -> Void
        var style: ASAuthorizationAppleIDButton.Style?
        init(action: @escaping () -> Void) { self.action = action }
        @objc func signIn() { action() }
    }
}
