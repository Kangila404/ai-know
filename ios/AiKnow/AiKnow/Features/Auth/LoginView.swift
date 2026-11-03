import SwiftUI

struct LoginView: View {
    var onGuest: (() -> Void)? = nil
    let onLogin: () -> Void
    @State private var login = AuthSession.shared
    @State private var providers: Set<APIClient.Provider> = []
    @State private var loadingProviders = true
    @State private var providerError: String?

    var body: some View {
        ZStack {
            AppPalette.background
                .ignoresSafeArea()

            GeometryReader { geometry in
                ScrollView {
                    VStack(spacing: 0) {
                        Spacer(minLength: 56)

                        VStack(spacing: 18) {
                            BrandLogo(size: 82)
                            Text("AI를 더 쉽게,\n매일 한 장씩")
                                .font(.system(size: 30, weight: .bold, design: .rounded))
                                .multilineTextAlignment(.center)
                                .accessibilityIdentifier("login.headline")
                            Text("복잡한 AI 이야기를 핵심만 담아 전해드려요.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
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
                                SocialLoginButton(provider: .apple) {
                                    Task { if await login.signInApple() { onLogin() } }
                                }
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

                        .frame(maxWidth: 360)
                        .disabled(login.busy)

                        if let onGuest {
                            Button(action: onGuest) {
                                Text("비회원 사용")
                                    .font(.subheadline.weight(.medium))
                                    .foregroundStyle(.secondary)
                                    .frame(maxWidth: .infinity).frame(minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                                .buttonStyle(.plain).padding(.top, 12)
                                .disabled(login.busy).accessibilityIdentifier("login.guest")
                        }

                        if login.busy { ProgressView("로그인 중…").padding(.top, 12) }
                        if let message = login.errorMessage {
                            Text(message).accessibilityIdentifier("login.error").font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center).padding(.top, 12)
                        }


                        Text("계속하면 서비스 이용약관 및 개인정보 처리방침에 동의하게 됩니다.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.top, 18)
                            .padding(.bottom, 12)
                    }
                    .frame(maxWidth: 360)
                    .padding(.horizontal, 24)
                    .frame(width: geometry.size.width)
                    .frame(minHeight: geometry.size.height)
                }.scrollBounceBehavior(.basedOnSize)
            }
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

// Official artwork with one text style and fixed icon/label alignment for every provider.
private struct SocialLoginButton: View {
    enum Provider: String { case apple, google, kakao }
    @Environment(\.colorScheme) private var colorScheme
    let provider: Provider
    let action: () -> Void
    private let height: CGFloat = 54
    private var title: String {
        switch provider {
        case .apple: return "Apple로 로그인"
        case .google: return "Google 로그인"
        case .kakao: return "카카오 로그인"
        }
    }
    private var background: Color {
        switch provider {
        case .apple: return colorScheme == .dark ? .white : .black
        case .kakao: return Color(red: 254 / 255, green: 229 / 255, blue: 0)
        case .google: return colorScheme == .dark ? Color(red: 19 / 255, green: 19 / 255, blue: 20 / 255) : .white
        }
    }
    private var foreground: Color {
        switch provider {
        case .apple: return colorScheme == .dark ? .black : .white
        case .kakao: return .black.opacity(0.85)
        case .google: return colorScheme == .dark ? Color(white: 227 / 255) : Color(white: 31 / 255)
        }
    }
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.custom("GoogleSans-Regular", size: 16, relativeTo: .body).weight(.medium))
                .lineLimit(1).minimumScaleFactor(0.8)
                .padding(.horizontal, 60)
                .frame(maxWidth: .infinity).frame(height: height)
                .overlay(alignment: .leading) {
                    Group {
                        if provider == .apple {
                            // Apple's supplied 24 × 44 artwork includes its own spacing.
                            // Scale to button height without cropping or adding vertical padding.
                            Image(colorScheme == .dark ? "AppleSymbolBlack" : "AppleSymbolWhite")
                                .renderingMode(.original).resizable().scaledToFit()
                                .frame(width: height * 24 / 44, height: height)
                        } else {
                            Image(provider == .google ? "GoogleSymbol" : "KakaoSymbol")
                                .renderingMode(.original).resizable().scaledToFit()
                                .frame(width: 20, height: 20)
                        }
                    }.frame(width: 60, height: height).accessibilityHidden(true)
                }
                .foregroundStyle(foreground)
                .background(background, in: RoundedRectangle(cornerRadius: 8))
                .overlay {
                    if provider == .google {
                        RoundedRectangle(cornerRadius: 8)
                            .strokeBorder(colorScheme == .dark ? Color(white: 143 / 255) : Color(white: 117 / 255), lineWidth: 1)
                    }
                }
                .contentShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityIdentifier("login.\(provider.rawValue)")
    }
}
