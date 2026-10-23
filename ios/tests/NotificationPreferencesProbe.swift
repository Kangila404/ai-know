import Foundation

@MainActor
final class PreferencesFixture {
    var stored = NotificationSetting(settingTime: "09:00:00", isAllowed: false)
    var writes: [NotificationSetting] = []
    var active = 0
    var maximumActive = 0
    var fail = false
    func write(_ value: NotificationSetting) async throws -> NotificationSetting {
        active += 1; maximumActive = max(maximumActive, active)
        defer { active -= 1 }
        writes.append(value)
        try await Task.sleep(for: .milliseconds(350))
        if fail { throw URLError(.notConnectedToInternet) }
        stored = value; return value
    }
}

@main
struct NotificationPreferencesProbe {
    @MainActor static func main() async throws {
        let fixture = PreferencesFixture()
        let model = NotificationPreferences(read: { fixture.stored }, write: { try await fixture.write($0) })
        await model.load()
        precondition(fixture.writes.isEmpty, "Loading must not save defaults")
        model.setAllowed(true)
        try await Task.sleep(for: .milliseconds(300))
        model.setTime("10:15:00"); model.setAllowed(false)
        while model.saving { try await Task.sleep(for: .milliseconds(20)) }
        precondition(fixture.stored == NotificationSetting(settingTime: "10:15:00", isAllowed: false))
        precondition(fixture.maximumActive == 1 && fixture.writes.count == 2, "Writes must be serialized and burst edits coalesced")
        fixture.fail = true; model.setAllowed(true)
        while model.saving { try await Task.sleep(for: .milliseconds(20)) }
        precondition(model.value == fixture.stored && model.error != nil, "Failed save must roll back")
        fixture.fail = false; model.setAllowed(true)
        while model.saving { try await Task.sleep(for: .milliseconds(20)) }
        precondition(fixture.stored.isAllowed && model.error == nil)
        weak var released: NotificationPreferences?
        do {
            let transient = NotificationPreferences(read: { fixture.stored }, write: { try await fixture.write($0) })
            released = transient; await transient.load(); transient.setTime("11:30:00")
        }
        try await Task.sleep(for: .seconds(1))
        precondition(fixture.stored.settingTime == "11:30:00" && released == nil, "Leaving a screen must finish its queued save without retaining the model")
        let failedLoad = NotificationPreferences(read: { throw URLError(.notConnectedToInternet) }, write: { _ in fatalError("Must not save before load") })
        await failedLoad.load(); failedLoad.setAllowed(true)
        precondition(failedLoad.value == nil && !failedLoad.saving && failedLoad.error != nil)
        print("PASS: no save on load, serialized/coalesced autosave, rollback and recovery, completion after dismissal, failed initial load")
    }
}
