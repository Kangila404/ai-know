import SwiftUI

struct ProfileView: View {
    @State private var login = AuthSession.shared
    @State private var confirmingWithdrawal = false
    @State private var choosingAvatar = false
    @State private var editingNickname = false
    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 14) {
                        Button { choosingAvatar = true } label: {
                            ProfileAvatar(raw: login.user?.profile?.imgUrl, size: 64)
                                .overlay(alignment: .bottomTrailing) {
                                    Image(systemName: "pencil.circle.fill").symbolRenderingMode(.palette)
                                        .foregroundStyle(.white, AppPalette.accent).font(.title3)
                                }
                        }.buttonStyle(.plain).accessibilityLabel("프로필 이미지 변경").accessibilityIdentifier("profile.avatar")
                        VStack(alignment: .leading, spacing: 5) {
                            Button { editingNickname = true } label: {
                                HStack(spacing: 6) {
                                    Text("\(login.user?.nickname ?? "사용자")님").font(.headline)
                                    Image(systemName: "pencil").font(.caption).foregroundStyle(.secondary)
                                }.foregroundStyle(.primary).padding(.vertical, 10).contentShape(Rectangle())
                            }.buttonStyle(.plain).accessibilityLabel("닉네임 변경").accessibilityValue(login.user?.nickname ?? "사용자")
                                .accessibilityIdentifier("profile.nickname")
                            Text("AI와 함께 더 나은 내일을 만들어가요!").font(.caption).foregroundStyle(.secondary)
                        }
                    }.padding(.vertical, 8)
                }
                Section {
                    NavigationLink { NoticeListView() } label: { Label("공지사항", systemImage: "megaphone") }
                    NavigationLink { PushSettingsView() } label: { Label("알림 설정", systemImage: "bell") }
                }
                Section {
                    Button("로그아웃") { Task { await login.logout() } }.disabled(login.busy)
                    Button("회원탈퇴", role: .destructive) { confirmingWithdrawal = true }.disabled(login.busy)
                    if let message = login.errorMessage { Text(message).font(.footnote).foregroundStyle(.red) }
                }
            }.navigationTitle("마이")
                .refreshable { await login.refreshAccountIfSignedIn() }
                .sheet(isPresented: $choosingAvatar) { AvatarPickerView() }
                .sheet(isPresented: $editingNickname) { NicknameEditor(nickname: login.user?.nickname ?? "") }
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

private struct NicknameEditor: View {
    @State var nickname: String
    @State private var saving = false
    @State private var error: String?
    @FocusState private var focused: Bool
    @Environment(\.dismiss) private var dismiss
    private var trimmed: String { nickname.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canSave: Bool { !saving && !trimmed.isEmpty && trimmed != AuthSession.shared.user?.nickname }
    var body: some View {
        NavigationStack {
            Form {
                TextField("닉네임 (20자 이내)", text: $nickname)
                    .focused($focused).submitLabel(.done)
                    .onChange(of: nickname) { _, value in if value.count > 20 { nickname = String(value.prefix(20)) } }
                    .onSubmit { if canSave { Task { await save() } } }
                if let error { Text(error).font(.footnote).foregroundStyle(.red) }
            }.navigationTitle("닉네임 변경").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() }.disabled(saving) }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(saving ? "저장 중…" : "저장") { Task { await save() } }.disabled(!canSave)
                    }
                }
                .disabled(saving)
                .task { focused = true }
        }.presentationDetents([.medium]).interactiveDismissDisabled(saving)
    }
    private func save() async {
        guard canSave else { return }
        saving = true; error = nil
        defer { saving = false }
        do {
            struct Nickname: Encodable { let nickname: String }
            _ = try await APIClient.shared.request(path: "api/v1/users/nickname", method: "PATCH", body: JSONEncoder().encode(Nickname(nickname: trimmed)))
            try await AuthSession.shared.refreshUser(); dismiss()
        } catch { self.error = error.localizedDescription }
    }
}

private struct AvatarPickerView: View {
    @State private var login = AuthSession.shared
    @State private var profiles: [APIClient.Profile] = []
    @State private var loading = true
    @State private var saving = false
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if loading || saving { ProgressView() }
                    if let error { Text(error).font(.footnote).foregroundStyle(.red) }
                    if profiles.isEmpty && !loading && error == nil { Text("선택할 수 있는 이미지가 없습니다.").foregroundStyle(.secondary) }
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 88), spacing: 12)], spacing: 24) {
                        ForEach(profiles) { profile in
                            Button { Task { await select(profile) } } label: {
                                VStack(spacing: 8) {
                                    ProfileAvatar(raw: profile.imgUrl, size: 72).padding(4)
                                        .overlay(Circle().stroke(login.user?.profile?.id == profile.id ? AppPalette.accent : .clear, lineWidth: 3))
                                        .overlay(alignment: .bottomTrailing) {
                                            if login.user?.profile?.id == profile.id {
                                                Image(systemName: "checkmark.circle.fill").symbolRenderingMode(.palette)
                                                    .foregroundStyle(.white, AppPalette.accent).font(.title3)
                                            }
                                        }
                                    Text(profile.name).font(.caption.weight(.medium)).foregroundStyle(.primary)
                                }.frame(maxWidth: .infinity).contentShape(Rectangle())
                            }.buttonStyle(.plain).accessibilityLabel(profile.name)
                                .accessibilityAddTraits(login.user?.profile?.id == profile.id ? .isSelected : [])
                        }
                    }.disabled(saving)
                }.padding(24)
            }.navigationTitle("프로필 이미지").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() }.disabled(saving) } }
                .task { await load() }.refreshable { await load() }
        }.presentationDetents([.medium, .large]).interactiveDismissDisabled(saving)
    }
    private func load() async {
        guard !saving else { return }
        loading = true; error = nil
        defer { loading = false }
        do { profiles = try await APIClient.shared.decode("api/v1/profile") }
        catch { self.error = error.localizedDescription }
    }
    private func select(_ profile: APIClient.Profile) async {
        guard !saving else { return }
        if login.user?.profile?.id == profile.id { dismiss(); return }
        saving = true; error = nil
        defer { saving = false }
        do {
            _ = try await APIClient.shared.request(path: "api/v1/users/profile/\(profile.id)", method: "PATCH")
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
