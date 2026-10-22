import SwiftUI

struct AppFlowView: View {
    @State private var login = AuthSession.shared
    @State private var screen: LaunchScreen = .splash

    var body: some View {
        Group {
            switch screen {
            case .splash:
                SplashView {
                    Task {
                        #if DEBUG
                        if ProcessInfo.processInfo.arguments.contains("--preview-admin") || ProcessInfo.processInfo.arguments.contains("--preview-user") {
                            login.beginPreview(admin: ProcessInfo.processInfo.arguments.contains("--preview-admin"))
                            move(to: .app); return
                        }
                        #endif
                        let restored = await login.restoreSession()
                        move(to: restored ? authenticatedScreen : .login)
                    }
                }
            case .login:
                LoginView {
                    move(to: authenticatedScreen)
                }
            case .onboarding:
                OnboardingView {
                    move(to: .app)
                }
            case .app:
                RootTabView()
                    .task { if !login.isPreview { await PushNotifications.shared.requestPermission() } }
            }
        }
        .animation(.snappy, value: screen)
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("AIKnowSessionExpired"))) { _ in
            if !login.isPreview { login.expireSession() }
        }
        .onChange(of: login.user == nil) { _, signedOut in
            if signedOut { move(to: .login) }
        }
    }

    private var authenticatedScreen: LaunchScreen {
        login.user?.onboardingCompleted == true ? .app : .onboarding
    }

    private func move(to screen: LaunchScreen) {
        self.screen = screen
    }
}

private enum LaunchScreen {
    case splash
    case login
    case onboarding
    case app
}
