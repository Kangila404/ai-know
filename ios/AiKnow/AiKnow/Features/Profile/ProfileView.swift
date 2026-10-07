import SwiftUI

struct ProfileView: View {
    @Environment(StoryStore.self) private var store

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    AppHeader(title: "마이")
                    profileHeader
                    menuGroup
                    accountGroup
                }
                .padding(20)
            }
            .background(AppPalette.background)
        }
    }

    private var profileHeader: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(LinearGradient(colors: [.indigo.opacity(0.80), .purple.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing))
                Image(systemName: "person.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(.white)
            }
            .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 5) {
                Text("김지수님")
                    .font(.headline.weight(.bold))
                Text("AI와 함께 더 나은 내일을 만들어가요!")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 4)
    }

    private var menuGroup: some View {
        VStack(spacing: 0) {
            NavigationLink {
                NoticeListView()
            } label: {
                MenuRow(symbol: "megaphone.fill", title: "공지사항")
            }
            Divider().padding(.leading, 54)
            NavigationLink {
                NotificationSettingsView()
            } label: {
                MenuRow(symbol: "bell.fill", title: "알림 설정", detail: store.dailyNotificationEnabled ? "ON" : "OFF", tint: .indigo)
            }
            Divider().padding(.leading, 54)
            NavigationLink {
                ProfileInfoView()
            } label: {
                MenuRow(symbol: "person.text.rectangle", title: "개인정보")
            }
        }
        .background(.white.opacity(0.92), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var accountGroup: some View {
        VStack(spacing: 0) {
            MenuRow(symbol: "rectangle.portrait.and.arrow.right", title: "데모 로그아웃", tint: .secondary)
            Divider().padding(.leading, 54)
            MenuRow(symbol: "trash", title: "회원탈퇴", tint: .red)
        }
        .background(.white.opacity(0.92), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

private struct MenuRow: View {
    let symbol: String
    let title: String
    var detail: String? = nil
    var tint: Color = AppPalette.ink

    var body: some View {
        HStack(spacing: 13) {
            Image(systemName: symbol)
                .font(.body.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 28)
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(AppPalette.ink)
            Spacer()
            if let detail {
                Text(detail)
                    .font(.caption2.weight(.heavy))
                    .foregroundStyle(.indigo)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.indigo.opacity(0.10), in: Capsule())
            }
            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 16)
        .frame(height: 57)
    }
}

private struct NotificationSettingsView: View {
    @Environment(StoryStore.self) private var store

    var body: some View {
        @Bindable var store = store

        Form {
            Section {
                Toggle("오늘의 카드 도착", isOn: $store.dailyNotificationEnabled)
                Toggle("서비스 업데이트", isOn: $store.updateNotificationEnabled)
                Toggle("공지사항", isOn: $store.noticeNotificationEnabled)
                Toggle("마케팅 정보", isOn: $store.marketingNotificationEnabled)
            } header: {
                Text("알림")
            } footer: {
                Text("이 설정은 정적 목 데이터 기반의 화면 동작을 위한 것입니다. 실제 푸시 알림은 발송되지 않습니다.")
            }
        }
        .navigationTitle("알림 설정")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct NoticeListView: View {
    private let notices: [(String, String, String, Bool)] = [
        ("서비스 정식 오픈 안내", "투데이 AI 이슈가 정식 오픈했습니다!", "10월 1일", true),
        ("iOS 앱 업데이트 안내", "더 편안한 카드 읽기 경험을 준비하고 있어요.", "9월 29일", true),
        ("개인정보 처리방침 변경 안내", "더 나은 서비스 제공을 위한 변경 사항입니다.", "9월 23일", false)
    ]

    var body: some View {
        List(notices, id: \.0) { notice in
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(notice.0).font(.headline)
                    if notice.3 {
                        Text("NEW")
                            .font(.caption2.weight(.heavy))
                            .foregroundStyle(.indigo)
                    }
                }
                Text(notice.1)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(notice.2)
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
            .padding(.vertical, 6)
        }
        .navigationTitle("공지사항")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ProfileInfoView: View {
    var body: some View {
        Form {
            Section("프로필") {
                LabeledContent("닉네임", value: "김지수")
                LabeledContent("이메일", value: "jisu@example.com")
            }
            Section {
                Text("이 정보는 화면 퍼블리싱을 위한 정적 목 데이터입니다.")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("개인정보")
        .navigationBarTitleDisplayMode(.inline)
    }
}
