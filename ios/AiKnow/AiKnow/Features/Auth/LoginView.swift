import SwiftUI

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
                    SocialLoginButton(
                        title: "Apple로 계속하기",
                        systemImage: "apple.logo",
                        foreground: .white,
                        background: .black,
                        action: {
                            Task { if await login.signInApple() { onLogin() } }
                        }
                    )
                    }
                    if providers.contains(.kakao) {
                    SocialLoginButton(
                        title: "카카오로 계속하기",
                        systemImage: "bubble.left.and.bubble.right.fill",
                        foreground: Color(red: 0.12, green: 0.11, blue: 0.05),
                        background: Color(red: 0.99, green: 0.88, blue: 0.23),
                        action: { Task { if await login.signInKakao() { onLogin() } } }
                    )
                    }
                    if providers.contains(.google) {
                    SocialLoginButton(
                        title: "Google로 계속하기",
                        systemImage: "g.circle.fill",
                        foreground: AppPalette.ink,
                        background: AppPalette.surface,
                        action: {
                            Task { if await login.signInGoogle() { onLogin() } }
                        },
                        bordered: true
                    )
                    }
                }

                .disabled(login.busy)

                if login.busy { ProgressView("로그인 중…").padding(.top, 12) }
                if let message = login.errorMessage {
                    Text(message).accessibilityIdentifier("login.error").font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center).padding(.top, 12)
                }

                #if DEBUG
                HStack {
                    Button("화면 미리보기") { login.beginPreview(admin: false); onLogin() }
                    Button("관리자 미리보기") { login.beginPreview(admin: true); onLogin() }
                }.font(.footnote).padding(.top, 16)
                #endif

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
                        .stroke(AppPalette.separator, lineWidth: 1)
                }
            }
        }
        .buttonStyle(.plain)
    }
}
