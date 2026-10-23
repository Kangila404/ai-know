import SwiftUI
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // Explicit mapping is required for SwiftUI delegate integration.
        Messaging.messaging().apnsToken = deviceToken
        Messaging.messaging().token { token, error in
            Task { @MainActor in
                if let token { PushNotifications.shared.received(token: token) }
                else if error != nil { PushNotifications.shared.status = "FCM 등록에 실패했습니다. 다시 시도해 주세요." }
            }
        }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Task { @MainActor in
            PushNotifications.shared.status = "APNs 등록에 실패했습니다. 기기 서명과 네트워크를 확인해 주세요."
        }
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Task { @MainActor in PushNotifications.shared.received(token: fcmToken) }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        Task { @MainActor in
            PushNotifications.shared.lastNotificationTitle = response.notification.request.content.title
            let raw = response.notification.request.content.userInfo["cardNewsId"]
            if let raw, let id = Int64(String(describing: raw)), id > 0 {
                PushNotifications.shared.pendingCardID = id
            }
            completionHandler()
        }
    }
}

@MainActor
@Observable
final class PushNotifications {
    static let shared = PushNotifications()
    private(set) var authorization: UNAuthorizationStatus = .notDetermined
    private(set) var token: String?
    var status = "알림 권한을 허용해 주세요."
    var lastNotificationTitle: String?
    var pendingCardID: Int64?
    private var syncing = false
    private var syncWaiters: [CheckedContinuation<Void, Never>] = []

    private var installationID: String {
        let key = "push.installationID"
        if let saved = UserDefaults.standard.string(forKey: key) { return saved }
        let value = UUID().uuidString
        UserDefaults.standard.set(value, forKey: key)
        return value
    }

    var permissionDescription: String {
        switch authorization {
        case .authorized, .provisional, .ephemeral: return "허용됨"
        case .denied: return "차단됨 — iOS 설정에서 변경할 수 있습니다."
        default: return "아직 요청하지 않음"
        }
    }

    func requestPermission() async {
        do {
            let allowed = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
            await refresh()
            if !allowed { status = "iOS 설정에서 알림을 허용해 주세요." }
        } catch {
            status = "알림 권한을 요청하지 못했습니다. 다시 시도해 주세요."
        }
    }

    func refresh() async {
        guard !AuthSession.shared.isPreview else { status = "개발 미리보기 · 기기에 등록하지 않습니다."; return }
        authorization = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        guard [.authorized, .provisional, .ephemeral].contains(authorization) else { return }
        UIApplication.shared.registerForRemoteNotifications()
        await syncToken()
    }

    func received(token: String) {
        self.token = token
        status = "FCM 토큰 발급 완료"
        Task { await syncToken() }
    }

    /// Call after native login completes. Uses the same session-cookie store as URLSession.shared.
    func syncToken() async {
        guard AuthSession.shared.user != nil, !AuthSession.shared.isPreview, !AuthSession.shared.busy, !syncing, let token,
              [.authorized, .provisional, .ephemeral].contains(authorization) else { return }
        syncing = true
        defer {
            syncing = false
            let waiters = syncWaiters
            syncWaiters.removeAll()
            waiters.forEach { $0.resume() }
        }
        do {
            _ = try await APIClient.shared.currentUser()
            let body = try JSONEncoder().encode(Registration(token: token, platform: "IOS", installationId: installationID))
            _ = try await APIClient.shared.request(path: "api/v1/device-tokens", method: "PUT", body: body)
            status = "서버 푸시 등록 완료"
        } catch APIError.http(401) {
            status = "FCM 준비 완료 · 서버 등록은 로그인 후 진행됩니다."
        } catch {
            status = "서버 푸시 등록 실패 · 다시 시도해 주세요."
        }
    }

    func waitForSynchronization() async {
        guard syncing else { return }
        await withCheckedContinuation { syncWaiters.append($0) }
    }

    private struct Registration: Encodable { let token: String; let platform: String; let installationId: String }
}

struct PushSettingsView: View {
    @State private var push = PushNotifications.shared
    @State private var allowed = false
    @State private var settingTime = Date()
    @State private var settingsLoading = true
    @State private var settingsLoaded = false
    @State private var saving = false
    @State private var settingsError: String?
    @State private var saved = false
    private var seoul: TimeZone { TimeZone(identifier: "Asia/Seoul")! }
    private var calendar: Calendar { var value = Calendar(identifier: .gregorian); value.timeZone = seoul; return value }

    var body: some View {
        Form {
            Section("매일 카드뉴스 알림") {
                if settingsLoading { ProgressView("알림 설정을 불러오는 중…") }
                Toggle("매일 알림 받기", isOn: $allowed)
                    .disabled(!settingsLoaded || saving)
                DatePicker("받는 시간 (한국 시간)", selection: $settingTime, displayedComponents: .hourAndMinute)
                    .environment(\.timeZone, seoul).disabled(!settingsLoaded || saving)
                Button(saving ? "저장 중…" : "알림 설정 저장") { Task { await saveSettings() } }
                    .disabled(!settingsLoaded || saving)
                if saved { Text("저장했습니다.").foregroundStyle(.green) }
                if let settingsError { RequestFailureView(message: settingsError) { Task { await loadSettings() } } }
                if allowed && push.authorization == .denied {
                    Text("기기의 알림 권한도 허용해야 알림을 받을 수 있습니다.").font(.footnote)
                }
            }
            Section("푸시 알림") {
                LabeledContent("기기 알림 권한", value: push.permissionDescription)
                Text(push.status).font(.footnote).foregroundStyle(.secondary)
                Button("알림 허용") { Task { await push.requestPermission() } }
                    .disabled(push.authorization != .notDetermined)
                Button("iOS 알림 설정 열기") {
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
                }
                Button("등록 다시 시도") { Task { await push.refresh() } }
                if let title = push.lastNotificationTitle {
                    LabeledContent("최근 연 알림", value: title)
                }
            }
            #if DEBUG
            Section("개발용 테스트") {
                Button("FCM 테스트 토큰 복사") {
                    UIPasteboard.general.string = push.token
                }
                .disabled(push.token == nil)
                Text("Firebase 콘솔의 테스트 메시지 수신자로 붙여 넣으세요.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            #endif
        }
        .navigationTitle("알림 설정")
        .navigationBarTitleDisplayMode(.inline)
        .task { await push.refresh(); await loadSettings() }
        .onChange(of: allowed) { _, _ in saved = false }
        .onChange(of: settingTime) { _, _ in saved = false }
    }
    private func loadSettings() async {
        settingsLoading = true; settingsError = nil
        defer { settingsLoading = false }
        do {
            let settings: NotificationSetting = try await APIClient.shared.decode("api/v1/notification-setting")
            let components = settings.settingTime.split(separator: ":").compactMap { Int($0) }
            guard components.count >= 2,
                  let time = calendar.date(bySettingHour: components[0], minute: components[1], second: 0, of: Date()) else { throw APIError.invalidResponse }
            allowed = settings.isAllowed; settingTime = time; settingsLoaded = true
        } catch { settingsError = error.localizedDescription }
    }
    private func saveSettings() async {
        saving = true; saved = false; settingsError = nil
        defer { saving = false }
        do {
            let time = String(format: "%02d:%02d:00", calendar.component(.hour, from: settingTime), calendar.component(.minute, from: settingTime))
            let _: NotificationSetting = try await APIClient.shared.decode("api/v1/notification-setting", method: "PUT",
                body: JSONEncoder().encode(NotificationSetting(settingTime: time, isAllowed: allowed)))
            saved = true
            if allowed && push.authorization == .notDetermined { await push.requestPermission() }
        } catch { settingsError = error.localizedDescription }
    }

}
