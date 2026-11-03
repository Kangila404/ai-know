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
        #if DEBUG
        if let raw = ProcessInfo.processInfo.environment["AIKNOW_UI_TEST_NOTIFICATION"], let url = URL(string: raw) {
            Task { @MainActor in _ = PushNotifications.handleUITestNotification(url) }
        }
        #endif
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
            PushNotifications.shared.openNotification(response.notification.request.content.userInfo)
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
    enum Destination: Hashable, Identifiable {
        case card(Int64), adminAlert(Int64)
        var id: String {
            switch self { case .card(let id): return "card-\(id)"; case .adminAlert(let id): return "admin-\(id)" }
        }
    }
    var pendingDestination: Destination?

    func openNotification(_ payload: [AnyHashable: Any]) {
        // One destination per tap. An admin payload never falls through to a public card.
        if let raw = payload["adminAlertId"] {
            guard let id = Int64(String(describing: raw)), id > 0 else { return }
            pendingDestination = .adminAlert(id)
        } else if let raw = payload["cardNewsId"], let id = Int64(String(describing: raw)), id > 0 {
            pendingDestination = .card(id)
        }
    }

    #if DEBUG
    // Test payloads use the real routing path, but only in an explicitly launched
    // localhost HTTP fixture. This never authenticates a user or changes a role.
    static func handleUITestNotification(_ url: URL) -> Bool {
        guard ProcessInfo.processInfo.arguments.contains("--ui-test-notifications"),
              let configured = Bundle.main.object(forInfoDictionaryKey: "AIKnowAPIBaseURL") as? String,
              let api = URL(string: configured), api.host == "localhost", api.port == 18082,
              url.host == "aiknow-ui-test" else { return false }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        var payload: [AnyHashable: Any] = [:]
        for item in items where ["adminAlertId", "cardNewsId"].contains(item.name) { payload[item.name] = item.value }
        shared.openNotification(payload)
        return true
    }
    #endif
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
        guard AuthSession.shared.user != nil, !AuthSession.shared.busy, !syncing, let token,
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
    @State private var preferences = NotificationPreferences()
    @State private var requestingPermission = false
    @Environment(\.scenePhase) private var scenePhase
    private var seoul: TimeZone { TimeZone(identifier: "Asia/Seoul")! }
    private var calendar: Calendar { var value = Calendar(identifier: .gregorian); value.timeZone = seoul; return value }
    private var selectedTime: Date {
        let parts = (preferences.value?.settingTime ?? "09:00:00").split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return Date() }
        return calendar.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: Date()) ?? Date()
    }
    var body: some View {
        Form {
            Section {
                Toggle("매일 카드 알림", isOn: Binding(get: { preferences.value?.isAllowed ?? false }, set: { enabled in
                    Task { await changePermission(enabled) }
                }))
                .accessibilityIdentifier("notifications.daily")
                .disabled(preferences.value == nil || requestingPermission)
                if preferences.value?.isAllowed == true {
                    DatePicker("받는 시간", selection: Binding(get: { selectedTime }, set: { date in
                        preferences.setTime(String(format: "%02d:%02d:00", calendar.component(.hour, from: date), calendar.component(.minute, from: date)))
                    }), displayedComponents: .hourAndMinute)
                    .environment(\.timeZone, seoul)
                    .accessibilityIdentifier("notifications.time")
                }
            } footer: {
                Text("하루 한 번, 새로운 AI 이야기를 보내드려요. 시간은 한국 시간 기준이에요.")
            }
            if preferences.loading { ProgressView() }
            if let error = preferences.error { Text(error).font(.footnote).foregroundStyle(.red).accessibilityIdentifier("notifications.error") }
            if push.authorization == .denied && preferences.value?.isAllowed == true {
                Section {
                    Button("아이폰 설정에서 알림 켜기", action: openSettings)
                } footer: { Text("아이폰에서 알림이 꺼져 있어요.") }
            }
        }
        .navigationTitle("알림 설정").navigationBarTitleDisplayMode(.inline)
        .task { await push.refresh(); await preferences.load() }
        .refreshable { await push.refresh(); await preferences.load() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await push.refresh() } }
        }
    }
    private func changePermission(_ enabled: Bool) async {
        guard !requestingPermission else { return }
        if enabled {
            requestingPermission = true
            defer { requestingPermission = false }
            await push.refresh()
            if push.authorization == .notDetermined { await push.requestPermission() }
            if push.authorization == .denied { openSettings(); return }
            guard [.authorized, .provisional, .ephemeral].contains(push.authorization) else { return }
        }
        preferences.setAllowed(enabled)
    }
    private func openSettings() {
        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
    }
}
