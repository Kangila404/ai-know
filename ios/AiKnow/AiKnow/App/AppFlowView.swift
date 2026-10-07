import SwiftUI

struct AppFlowView: View {
    @State private var screen: LaunchScreen = .splash

    var body: some View {
        Group {
            switch screen {
            case .splash:
                SplashView {
                    move(to: .login)
                }
            case .login:
                LoginView {
                    move(to: .onboarding)
                }
            case .onboarding:
                OnboardingView {
                    move(to: .app)
                }
            case .app:
                RootTabView()
            }
        }
        .animation(.snappy, value: screen)
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
