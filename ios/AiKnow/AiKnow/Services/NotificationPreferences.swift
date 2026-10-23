import Foundation
import Observation

struct NotificationSetting: Codable, Equatable {
    var settingTime: String
    var isAllowed: Bool
}

/// Serializes autosaves so an older, slower response cannot overwrite a newer edit.
@MainActor @Observable
final class NotificationPreferences {
    private(set) var value: NotificationSetting?
    private(set) var loading = false
    private(set) var saving = false
    private(set) var error: String?
    private var confirmed: NotificationSetting?
    private var pending: NotificationSetting?
    private var saveTask: Task<Void, Never>?
    private let read: () async throws -> NotificationSetting
    private let write: (NotificationSetting) async throws -> NotificationSetting

    init(read: @escaping () async throws -> NotificationSetting = {
        try await APIClient.shared.decode("api/v1/notification-setting")
    }, write: @escaping (NotificationSetting) async throws -> NotificationSetting = { value in
        try await APIClient.shared.decode("api/v1/notification-setting", method: "PUT", body: JSONEncoder().encode(value))
    }) { self.read = read; self.write = write }

    func load() async {
        guard !loading, !saving else { return }
        loading = true; error = nil
        defer { loading = false }
        do { let result = try await read(); confirmed = result; value = result }
        catch { self.error = "알림 설정을 불러오지 못했어요. 화면을 아래로 당겨 다시 불러와 주세요." }
    }

    func setAllowed(_ allowed: Bool) {
        guard var next = value else { return }
        next.isAllowed = allowed; update(next)
    }
    func setTime(_ time: String) {
        guard var next = value else { return }
        next.settingTime = time; update(next)
    }
    private func update(_ next: NotificationSetting) {
        guard next != value, !loading else { return }
        value = next; pending = next; error = nil; saving = true
        guard saveTask == nil else { return }
        // This task finishes even when the user immediately leaves the screen.
        saveTask = Task { await drain() }
    }
    private func drain() async {
        try? await Task.sleep(for: .milliseconds(250))
        while let next = pending {
            pending = nil
            if next == confirmed { continue }
            do {
                let result = try await write(next)
                confirmed = result
                if pending == nil { value = result; error = nil }
            } catch {
                if pending == nil {
                    value = confirmed
                    self.error = "저장하지 못해 변경 전 설정으로 되돌렸어요. 연결 상태를 확인해 주세요."
                }
            }
        }
        saving = false; saveTask = nil
    }
}
