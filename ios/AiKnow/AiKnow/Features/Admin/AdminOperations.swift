import SwiftUI

private struct ManagedAccount: Decodable, Identifiable {
    let id: Int64
    let version: Int64
    let userId: String
    let nickname: String
    let role: String
    let suspended: Bool
    let createdAt: String?
    let lastLoginAt: String?
}

struct AdminUsersView: View {
    @State private var accounts: [ManagedAccount] = []
    @State private var query = ""
    @State private var role = ""
    @State private var state = ""
    @State private var page = 0
    @State private var last = false
    @State private var loading = false
    @State private var error: String?
    @State private var generation = 0
    var body: some View {
        List {
            Picker("권한", selection: $role) {
                Text("전체 권한").tag(""); Text("관리자").tag("ADMIN"); Text("일반 사용자").tag("USER")
            }
            Picker("계정 상태", selection: $state) {
                Text("전체 상태").tag(""); Text("정상").tag("false"); Text("이용 정지").tag("true")
            }
            ForEach(accounts) { account in
                NavigationLink { AdminAccountDetail(id: account.id) } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(account.nickname).font(.headline)
                        Text("\(account.role == "ADMIN" ? "관리자" : "일반 사용자") · \(account.suspended ? "이용 정지" : "정상")")
                            .font(.caption).foregroundStyle(.secondary)
                        Text(account.userId).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
            }
            if loading { ProgressView() }
            else if let error { RequestFailureView(message: error) { Task { await load(reset: true) } } }
            else if accounts.isEmpty { ContentUnavailableView("검색 결과가 없습니다", systemImage: "person.2") }
            else if !last { Button("더 보기") { Task { await load(reset: false) } } }
        }
        .navigationTitle("유저 관리")
        .searchable(text: $query, prompt: "닉네임 또는 계정 ID")
        .task(id: query + "|" + role + "|" + state) { await load(reset: true) }
        .refreshable { await load(reset: true) }
    }
    private func load(reset: Bool) async {
        if !reset && (loading || last) { return }
        if reset { generation += 1; page = 0; accounts = []; last = false }
        let ticket = generation
        loading = true; error = nil
        defer { if ticket == generation { loading = false } }
        var params = [URLQueryItem(name: "query", value: query), URLQueryItem(name: "page", value: String(page))]
        if !role.isEmpty { params.append(URLQueryItem(name: "role", value: role)) }
        if !state.isEmpty { params.append(URLQueryItem(name: "suspended", value: state)) }
        do {
            let result: AdminPage<ManagedAccount> = try await APIClient.shared.decode("api/v1/admin/users", query: params)
            guard ticket == generation, !Task.isCancelled else { return }
            accounts += result.content; page += 1; last = result.last
        } catch { if ticket == generation, !Task.isCancelled { self.error = error.localizedDescription } }
    }
}

private struct AdminAccountDetail: View {
    let id: Int64
    @State private var account: ManagedAccount?
    @State private var role = "USER"
    @State private var suspended = false
    @State private var reason = ""
    @State private var busy = false
    @State private var error: String?
    @State private var saved = false
    @State private var confirming = false
    var body: some View {
        Form {
            if let account {
                Section("계정") {
                    LabeledContent("닉네임", value: account.nickname)
                    Text(account.userId).font(.caption).textSelection(.enabled)
                    if let date = account.createdAt { LabeledContent("가입", value: date.replacingOccurrences(of: "T", with: " ")) }
                    if let date = account.lastLoginAt { LabeledContent("최근 로그인", value: date.replacingOccurrences(of: "T", with: " ")) }
                }
                Section {
                    Picker("권한", selection: $role) { Text("일반 사용자").tag("USER"); Text("관리자").tag("ADMIN") }
                    Toggle("이용 정지", isOn: $suspended)
                    TextField("변경 사유 (필수)", text: $reason, axis: .vertical).lineLimit(3...6)
                    Button("변경 사항 저장") { confirming = true }
                        .disabled(busy || reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || reason.count > 2000 ||
                            (role == account.role && suspended == account.suspended))
                } footer: {
                    Text("권한과 이용 정지는 기존 로그인에도 적용됩니다. 본인의 관리자 권한 해제와 본인 정지는 허용되지 않습니다.")
                }
                NavigationLink("변경 이력") { EditorialHistoryView(type: "USER", id: id) }
            }
            if busy { ProgressView() }
            if let error { Text(error).foregroundStyle(.red) }
            if saved { Label("저장했습니다", systemImage: "checkmark.circle").foregroundStyle(.green) }
        }.navigationTitle("계정 관리").navigationBarTitleDisplayMode(.inline)
            .task { await load() }
            .refreshable { await load() }
            .confirmationDialog("이 계정의 권한과 상태를 변경할까요?", isPresented: $confirming, titleVisibility: .visible) {
                Button("변경 적용", role: .destructive) { Task { await save() } }
            }
    }
    private func load() async {
        busy = true; error = nil; saved = false
        defer { busy = false }
        do {
            let result: ManagedAccount = try await APIClient.shared.decode("api/v1/admin/users/\(id)")
            account = result; role = result.role; suspended = result.suspended
        } catch { self.error = error.localizedDescription }
    }
    private func save() async {
        guard let account, !busy else { return }
        struct Update: Encodable { let version: Int64; let role: String; let suspended: Bool; let reason: String }
        busy = true; error = nil; saved = false
        defer { busy = false }
        do {
            let body = try JSONEncoder().encode(Update(version: account.version, role: role, suspended: suspended, reason: reason))
            let result: ManagedAccount = try await APIClient.shared.decode("api/v1/admin/users/\(id)", method: "PATCH", body: body)
            self.account = result; role = result.role; suspended = result.suspended; reason = ""; saved = true
        } catch { self.error = error.localizedDescription }
    }
}

private struct OperationsSummary: Decodable {
    let users: Int64; let suspendedUsers: Int64; let readyCards: Int64; let publishedCards: Int64
    let totalViews: Int64; let uniqueViewers: Int64
}
private struct CardStatistics: Decodable, Identifiable {
    let id: Int64; let title: String; let publicationStatus: String; let totalViews: Int64; let uniqueViewers: Int64
}
struct AdminAnalyticsView: View {
    @State private var summary: OperationsSummary?
    @State private var cards: [CardStatistics] = []
    @State private var query = ""
    @State private var page = 0
    @State private var last = false
    @State private var loading = false
    @State private var error: String?
    @State private var generation = 0
    var body: some View {
        List {
            if let summary {
                Section("운영 현황") {
                    LabeledContent("가입 계정", value: "\(summary.users)명")
                    LabeledContent("이용 정지 계정", value: "\(summary.suspendedUsers)명")
                    LabeledContent("발송 대기 카드", value: "\(summary.readyCards)개")
                    LabeledContent("게시된 카드", value: "\(summary.publishedCards)개")
                    LabeledContent("전체 조회수", value: "\(summary.totalViews)회")
                    LabeledContent("전체 고유 조회자", value: "\(summary.uniqueViewers)명")
                }
            }
            Section {
                ForEach(cards) { card in
                    NavigationLink { EditorialDetailView(id: card.id, kind: .articles) } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(card.title).font(.headline)
                            Text("\(statusLabel(card.publicationStatus)) · 조회 \(card.totalViews)회 · 고유 조회자 \(card.uniqueViewers)명")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            } header: { Text("카드별 조회수") } footer: {
                Text("집계 기능 배포 이후의 상세 열람을 집계합니다. 같은 계정의 30초 이내 반복 요청은 한 번이며, 목록 노출·관리자 미리보기·읽기 완료 수와는 다릅니다.")
            }
            if loading { ProgressView() }
            else if let error { RequestFailureView(message: error) { Task { await load(reset: true) } } }
            else if cards.isEmpty { ContentUnavailableView("카드가 없습니다", systemImage: "chart.bar") }
            else if !last { Button("더 보기") { Task { await load(reset: false) } } }
        }.navigationTitle("조회수·운영 현황")
            .searchable(text: $query, prompt: "카드 제목 검색")
            .task(id: query) { await load(reset: true) }
            .refreshable { await load(reset: true) }
    }
    private func load(reset: Bool) async {
        if !reset && (loading || last) { return }
        if reset { generation += 1; page = 0; cards = []; last = false }
        let ticket = generation
        loading = true; error = nil
        defer { if ticket == generation { loading = false } }
        do {
            if reset {
                let result: OperationsSummary = try await APIClient.shared.decode("api/v1/admin/analytics/summary")
                guard ticket == generation, !Task.isCancelled else { return }
                summary = result
            }
            let result: AdminPage<CardStatistics> = try await APIClient.shared.decode("api/v1/admin/analytics/cards",
                query: [URLQueryItem(name: "query", value: query), URLQueryItem(name: "page", value: String(page))])
            guard ticket == generation, !Task.isCancelled else { return }
            cards += result.content; page += 1; last = result.last
        } catch { if ticket == generation, !Task.isCancelled { self.error = error.localizedDescription } }
    }
}
