import SwiftUI

struct OnboardingView: View {
    let onFinished: () -> Void
    @State private var page = 0
    @State private var showSetup = false
    @State private var login = AuthSession.shared

    private let pages = OnboardingPage.samples

    var body: some View {
        ZStack {
            AppPalette.background
                .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button("건너뛰기", action: finish)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 24)
                .frame(height: 56)

                TabView(selection: $page) {
                    ForEach(Array(pages.enumerated()), id: \.element.id) { index, item in
                        OnboardingPageView(item: item)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                if let message = login.errorMessage {
                    Text(message).font(.footnote).foregroundStyle(.red).padding(.horizontal)
                }
                if login.busy { ProgressView("설정을 저장하는 중…") }
                VStack(spacing: 20) {
                    HStack(spacing: 7) {
                        ForEach(pages.indices, id: \.self) { index in
                            Capsule()
                                .fill(index == page ? Color.indigo : Color.indigo.opacity(0.16))
                                .frame(width: index == page ? 22 : 7, height: 7)
                        }
                    }

                    Button {
                        if page == pages.count - 1 {
                            finish()
                        } else {
                            withAnimation(.snappy) {
                                page += 1
                            }
                        }
                    } label: {
                        Label(
                            page == pages.count - 1 ? "AI Know 시작하기" : "다음",
                            systemImage: page == pages.count - 1 ? "sparkles" : "arrow.right"
                        )
                        .font(.body.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.indigo)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 22)
            }
        }.disabled(login.busy)
            .sheet(isPresented: $showSetup) {
                OnboardingSetupView(onFinished: onFinished)
                    .interactiveDismissDisabled()
            }
    }
    private func finish() { showSetup = true }
}

private struct OnboardingPageView: View {
    let item: OnboardingPage

    var body: some View {
        VStack(spacing: 30) {
            Spacer()

            ZStack {
                Circle()
                    .fill(item.tint.opacity(0.13))
                    .frame(width: 234, height: 234)
                Image(systemName: item.symbol)
                    .font(.system(size: 78, weight: .regular))
                    .foregroundStyle(item.tint)
            }

            VStack(spacing: 12) {
                Text(item.title)
                    .font(.system(size: 27, weight: .bold, design: .rounded))
                    .multilineTextAlignment(.center)
                Text(item.description)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            .padding(.horizontal, 32)

            Spacer()
        }
    }
}

private struct OnboardingPage: Identifiable {
    let id: Int
    let symbol: String
    let tint: Color
    let title: String
    let description: String

    static let samples: [OnboardingPage] = [
        OnboardingPage(
            id: 0,
            symbol: "rectangle.stack.fill",
            tint: .indigo,
            title: "하루 한 장의\nAI 인사이트",
            description: "복잡한 AI 소식을 카드 한 장으로\n쉽고 빠르게 만나보세요."
        ),
        OnboardingPage(
            id: 1,
            symbol: "hand.thumbsup.fill",
            tint: .pink,
            title: "마음에 드는 이야기는\n좋아요로 모아보세요",
            description: "좋아요한 카드와 관심 있는 카테고리를\n아카이브에서 다시 볼 수 있어요."
        ),
        OnboardingPage(
            id: 2,
            symbol: "bell.badge.fill",
            tint: .purple,
            title: "매일 새로운\nAI 이야기를 알려드려요",
            description: "알림 설정으로 오늘의 카드가 도착했을 때\n놓치지 않고 확인해 보세요."
        )
    ]
}


private struct OnboardingSetupView: View {
    let onFinished: () -> Void
    @State private var login = AuthSession.shared
    @State private var nickname = ""
    @State private var allowed = false
    @State private var time = Date()
    @State private var original: NotificationSetting?
    @State private var loading = true
    @State private var saving = false
    @State private var error: String?
    @FocusState private var editingName: Bool
    private var calendar: Calendar { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Seoul")!; return c }
    private var validName: Bool { let name = nickname.trimmingCharacters(in: .whitespacesAndNewlines); return !name.isEmpty && name.utf16.count <= 20 }

    var body: some View {
        NavigationStack {
            Form {
                Section("어떻게 불러드릴까요?") {
                    TextField("닉네임 (20자 이내)", text: $nickname).focused($editingName)
                        .accessibilityIdentifier("onboarding.nickname")
                }
                Section {
                    Toggle("매일 카드 알림", isOn: $allowed).accessibilityIdentifier("onboarding.daily")
                    if allowed {
                        DatePicker("받는 시간", selection: $time, displayedComponents: .hourAndMinute)
                            .environment(\.timeZone, calendar.timeZone).accessibilityIdentifier("onboarding.time")
                    }
                } footer: { Text("알림을 켜면 아이폰의 알림 권한을 확인합니다. 수신 시각은 한국 시간 기준이며, 마이에서 언제든 변경할 수 있어요.") }
                if loading { ProgressView() }
                if let error { Text(error).foregroundStyle(.red).accessibilityIdentifier("onboarding.error") }
                if original == nil && !loading { Button("설정 다시 불러오기") { Task { await load() } } }
                Button(saving ? "저장 중…" : "저장하고 시작하기") { Task { await finish(saveSettings: true) } }
                    .disabled(loading || saving || !validName || original == nil)
                Button("나중에 설정") { Task { await finish(saveSettings: false) } }.disabled(saving || loading)
            }
            .disabled(saving)
            .navigationTitle("시작 설정").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("입력 완료") { editingName = false } } }
            .task { nickname = login.user?.nickname ?? ""; await load() }
        }.tint(AppPalette.accent)
    }
    private func load() async {
        loading = true; error = nil; defer { loading = false }
        do {
            let settings: NotificationSetting = try await APIClient.shared.decode("api/v1/notification-setting")
            original = settings; allowed = settings.isAllowed
            let parts = settings.settingTime.split(separator: ":").compactMap { Int($0) }
            time = calendar.date(bySettingHour: parts.first ?? 9, minute: parts.count > 1 ? parts[1] : 0, second: 0, of: Date()) ?? Date()
        } catch { self.error = "알림 설정을 불러오지 못했습니다. 다시 시도하거나 나중에 설정할 수 있어요." }
    }
    private func finish(saveSettings: Bool) async {
        guard !saving else { return }
        saving = true; error = nil; editingName = false
        defer { saving = false }
        do {
            if saveSettings {
                guard validName, original != nil else { return }
                let name = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
                if name != login.user?.nickname {
                    struct Name: Encodable { let nickname: String }
                    _ = try await APIClient.shared.request(path: "api/v1/users/nickname", method: "PATCH", body: JSONEncoder().encode(Name(nickname: name)))
                    try await login.refreshUser()
                }
                if allowed {
                    let push = PushNotifications.shared
                    await push.refresh()
                    if push.authorization == .notDetermined { await push.requestPermission() }
                    allowed = [.authorized, .provisional, .ephemeral].contains(push.authorization)
                }
                let setting = NotificationSetting(settingTime: String(format: "%02d:%02d:00", calendar.component(.hour, from: time), calendar.component(.minute, from: time)), isAllowed: allowed)
                if setting != original {
                    original = try await APIClient.shared.decode("api/v1/notification-setting", method: "PUT", body: JSONEncoder().encode(setting))
                }
            }
            if await login.completeOnboarding() { onFinished() }
            else { error = login.errorMessage }
        } catch { self.error = error.localizedDescription }
    }
}
