import SwiftUI

struct ProfileView: View {
    @State private var login = AuthSession.shared
    @State private var confirmingWithdrawal = false
    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 14) {
                        ProfileAvatar(raw: login.user?.profile?.imgUrl, size: 54)
                        VStack(alignment: .leading, spacing: 5) {
                            Text("\(login.user?.nickname ?? "사용자")님").font(.headline)
                            Text("AI와 함께 더 나은 내일을 만들어가요!").font(.caption).foregroundStyle(.secondary)
                        }
                    }.padding(.vertical, 8)
                }
                Section {
                    NavigationLink { NoticeListView() } label: { Label("공지사항", systemImage: "megaphone") }
                    NavigationLink { PushSettingsView() } label: { Label("알림 설정", systemImage: "bell") }
                    NavigationLink { ProfileInfoView() } label: { Label("프로필 편집", systemImage: "person.text.rectangle") }
                }
                Section {
                    Button("로그아웃") { Task { await login.logout() } }.disabled(login.busy)
                    Button("회원탈퇴", role: .destructive) { confirmingWithdrawal = true }.disabled(login.busy)
                    if let message = login.errorMessage { Text(message).font(.footnote).foregroundStyle(.red) }
                }
            }.navigationTitle("마이")
                .refreshable { await login.refreshAccountIfSignedIn() }
                .confirmationDialog("계정을 삭제할까요?", isPresented: $confirmingWithdrawal, titleVisibility: .visible) {
                    Button("회원탈퇴", role: .destructive) { Task { await login.withdraw() } }
                    Button("취소", role: .cancel) { }
                } message: { Text("계정과 좋아요·읽음 기록·알림 설정이 삭제되며 복구할 수 없습니다.") }
        }
    }
}

private struct NoticeListView: View {
    @State private var notices: [Notice] = []
    @State private var page = 0
    @State private var hasMore = true
    @State private var loading = false
    @State private var error: String?
    var body: some View {
        List {
            ForEach(notices) { notice in
                NavigationLink { NoticeDetailView(id: notice.id) } label: {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(notice.title).font(.headline)
                        Text(String((notice.publishedAt ?? "").prefix(10))).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            if loading { ProgressView() }
            else if let error { RequestFailureView(message: error) { Task { await load() } } }
            else if notices.isEmpty { ContentUnavailableView("등록된 공지가 없어요", systemImage: "megaphone") }
            else if hasMore { Button("더 보기") { Task { await load() } } }
        }.navigationTitle("공지사항").navigationBarTitleDisplayMode(.inline)
            .task { if notices.isEmpty { await load() } }
            .refreshable { guard !loading else { return }; page = 0; notices = []; hasMore = true; await load() }
    }
    private func load() async {
        guard !loading, hasMore else { return }
        loading = true; error = nil; defer { loading = false }
        do {
            let result: NoticePage = try await APIClient.shared.decode("api/v1/notices", query: [URLQueryItem(name: "page", value: String(page))])
            let ids = Set(notices.map(\.id))
            notices.append(contentsOf: result.content.filter { !ids.contains($0.id) }); page += 1; hasMore = !result.last
        } catch { self.error = error.localizedDescription }
    }
}

private struct NoticeDetailView: View {
    let id: Int64
    @State private var notice: Notice?
    @State private var error: String?
    var body: some View {
        ScrollView {
            if let error { RequestFailureView(message: error) { Task { await load() } } }
            else if let notice {
                VStack(alignment: .leading, spacing: 20) {
                    Text(notice.title).font(.title2.bold())
                    Text(String((notice.publishedAt ?? "").prefix(10))).font(.caption).foregroundStyle(.secondary)
                    Text(notice.content).textSelection(.enabled)
                }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
            } else { ProgressView().padding() }
        }.navigationTitle("공지사항").navigationBarTitleDisplayMode(.inline).task { await load() }
    }
    private func load() async {
        error = nil
        do { notice = try await APIClient.shared.decode("api/v1/notices/\(id)") }
        catch { self.error = error.localizedDescription }
    }
}

private struct ProfileInfoView: View {
    @State private var login = AuthSession.shared
    @State private var nickname = ""
    @FocusState private var editingNickname: Bool
    @State private var profiles: [APIClient.Profile] = []
    @State private var selected: Int64?
    @State private var loading = true
    @State private var saving = false
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss
    private var hasChanges: Bool { nickname.trimmingCharacters(in: .whitespacesAndNewlines) != login.user?.nickname || selected != login.user?.profile?.id }
    var body: some View {
        Form {
            if loading { ProgressView() }
            Section("닉네임") {
                TextField("닉네임 (20자 이내)", text: $nickname)
                    .focused($editingNickname)
                    .submitLabel(.done)
                    .onSubmit { editingNickname = false }
                    .onChange(of: nickname) { _, value in if value.count > 20 { nickname = String(value.prefix(20)) } }
            }
            Section("프로필 이미지") {
                if profiles.isEmpty && !loading { Text("선택할 수 있는 이미지가 없습니다.").foregroundStyle(.secondary) }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 88), spacing: 12)], spacing: 20) {
                    ForEach(profiles) { profile in
                        Button { selected = profile.id } label: {
                            VStack(spacing: 8) {
                                ProfileAvatar(raw: profile.imgUrl, size: 72)
                                    .padding(4)
                                    .overlay(Circle().stroke(selected == profile.id ? AppPalette.accent : .clear, lineWidth: 3))
                                    .overlay(alignment: .bottomTrailing) {
                                        if selected == profile.id {
                                            Image(systemName: "checkmark.circle.fill").symbolRenderingMode(.palette)
                                                .foregroundStyle(.white, AppPalette.accent).font(.title3)
                                        }
                                    }
                                Text(profile.name).font(.caption.weight(.medium)).foregroundStyle(.primary)
                            }.frame(maxWidth: .infinity).contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityLabel(profile.name)
                            .accessibilityAddTraits(selected == profile.id ? .isSelected : [])
                    }
                }.padding(.vertical, 12)

            }
            if let error { Section { Text(error).font(.footnote).foregroundStyle(.red) } }
            Section {
                Button(saving ? "저장 중…" : "저장") { Task { await save() } }
                    .disabled(loading || saving || !hasChanges || nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("입력 완료") { editingNickname = false }
            }
        }
        .navigationTitle("프로필 편집").navigationBarTitleDisplayMode(.inline).disabled(saving).task { await load() }.refreshable { await load() }
    }
    private func load() async {
        loading = true; error = nil; defer { loading = false }
        do {
            try await login.refreshUser()
            nickname = login.user?.nickname ?? ""; selected = login.user?.profile?.id
            profiles = try await APIClient.shared.decode("api/v1/profile")
        } catch { self.error = error.localizedDescription }
    }
    private func save() async {
        saving = true; error = nil; defer { saving = false }
        do {
            struct Nickname: Encodable { let nickname: String }
            let trimmed = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed != login.user?.nickname {
                _ = try await APIClient.shared.request(path: "api/v1/users/nickname", method: "PATCH", body: JSONEncoder().encode(Nickname(nickname: trimmed)))
            }
            if let selected, selected != login.user?.profile?.id {
                _ = try await APIClient.shared.request(path: "api/v1/users/profile/\(selected)", method: "PATCH")
            }
            try await login.refreshUser(); dismiss()
        } catch { self.error = error.localizedDescription }
    }
}

private struct ProfileAvatar: View {
    let raw: String?
    let size: CGFloat
    var body: some View {
        Group {
            if let raw, !raw.isEmpty {
                RemoteImage(raw: raw, aspectRatio: 1, maxPixelSize: 320, imageBackground: .clear)
            } else {
                Circle().fill(AppPalette.accent.opacity(0.12))
                    .overlay { Image(systemName: "person.fill").font(.system(size: size * 0.45)).foregroundStyle(AppPalette.accent) }
            }
        }.frame(width: size, height: size).clipShape(Circle())
    }
}
