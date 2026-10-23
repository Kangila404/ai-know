import SwiftUI
import GoogleSignIn
import KakaoSDKCommon
import KakaoSDKAuth

@main
struct AiKnowApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @AppStorage("appearance") private var appearance = "system"
    @Environment(\.scenePhase) private var scenePhase

    init() {
        if let key = Bundle.main.object(forInfoDictionaryKey: "KakaoNativeAppKey") as? String, !key.isEmpty {
            KakaoSDK.initSDK(appKey: key, loggingEnable: false)
        }
    }

    var body: some Scene {
        WindowGroup {
            AppFlowView()
                .preferredColorScheme(appearance == "dark" ? .dark : appearance == "light" ? .light : nil)
                .onOpenURL { url in
                    if AuthApi.isKakaoTalkLoginUrl(url) {
                        _ = AuthController.handleOpenUrl(url: url)
                    } else {
                        _ = GIDSignIn.sharedInstance.handle(url)
                    }
                }
                .task { await PushNotifications.shared.refresh() }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { Task {
                        await AuthSession.shared.refreshAccountIfSignedIn()
                        await PushNotifications.shared.refresh()
                    } }
                }
        }
    }
}
