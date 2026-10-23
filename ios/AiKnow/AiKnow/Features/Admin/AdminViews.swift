import SwiftUI
import PhotosUI

struct AdminHomeView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("콘텐츠 운영") {
                    ForEach(AdminKind.allCases) { kind in
                        NavigationLink { AdminListView(kind: kind) } label: { Label(kind.title, systemImage: kind.symbol) }
                    }
                }
                Section("사용자 관리") {
                    Label("사용자 관리 준비 중", systemImage: "person.2")
                    Text("현재 서버에는 사용자 목록·권한 변경 API가 없습니다. 계정 관리는 API가 추가된 뒤 제공됩니다.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }.navigationTitle("관리자")
        }
    }
}

private struct AdminRow: Identifiable { let id: Int64; let title: String; let subtitle: String }
struct AdminListView: View {
    let kind: AdminKind
    @State private var rows: [AdminRow] = []
    @State private var filter = ""
    @State private var page = 0
    @State private var last = false
    @State private var loading = false
    @State private var error: String?
    @State private var creating = false
    @State private var generation = 0
    var body: some View {
        List {
            if !kind.filters.isEmpty {
                Picker("상태", selection: $filter) { ForEach(kind.filters, id: \.self) { Text(statusLabel($0)).tag($0) } }
            }
            ForEach(rows) { row in
                NavigationLink { destination(row.id) } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(row.title).font(.headline).lineLimit(2)
                        Text(row.subtitle).font(.caption).foregroundStyle(.secondary)
                    }.padding(.vertical, 4)
                }
            }
            if loading { ProgressView() }
            else if let error { RequestFailureView(message: error) { Task { await load(reset: rows.isEmpty) } } }
            else if rows.isEmpty { ContentUnavailableView("등록된 항목이 없습니다", systemImage: kind.symbol) }
            else if !last { Button("더 보기") { Task { await load() } } }
        }.navigationTitle(kind.title).navigationBarTitleDisplayMode(.inline)
            .task(id: filter) {
                if kind == .submissions && filter.isEmpty { filter = "PENDING"; return }
                await load(reset: true)
            }
            .refreshable { await load(reset: true) }
            .toolbar {
                if kind == .notices || kind == .profiles { Button("추가", systemImage: "plus") { creating = true } }
            }
            .sheet(isPresented: $creating, onDismiss: { Task { await load(reset: true) } }) {
                NavigationStack {
                    if kind == .notices { AdminNoticeEditor(id: nil) }
                    else { AdminProfileEditor(id: nil) }
                }
            }
    }
    @ViewBuilder private func destination(_ id: Int64) -> some View {
        switch kind {
        case .submissions, .articles: EditorialDetailView(id: id, kind: kind)
        case .notices: AdminNoticeEditor(id: id)
        case .profiles: AdminProfileEditor(id: id)
        case .generations: GenerationDetailView(id: id)
        }
    }
    private func load(reset: Bool = false) async {
        if !reset && (loading || last) { return }
        if reset { generation += 1; page = 0; rows = []; last = false }
        let ticket = generation
        loading = true; error = nil
        defer { if ticket == generation { loading = false } }
        var query = [URLQueryItem(name: "page", value: String(page)), URLQueryItem(name: "size", value: "20")]
        if !filter.isEmpty { query.append(URLQueryItem(name: "status", value: filter)) }
        do {
            var result: [AdminRow]; var isLast: Bool
            switch kind {
            case .submissions:
                let data: AdminPage<Submission> = try await APIClient.shared.decode(kind.path, query: query)
                result = data.content.map { AdminRow(id: $0.id, title: "검수 요청 #\($0.id)", subtitle: statusLabel($0.status)) }; isLast = data.last
            case .articles:
                let data: AdminPage<AdminArticle> = try await APIClient.shared.decode(kind.path, query: query)
                result = data.content.map { AdminRow(id: $0.id, title: $0.news.title, subtitle: statusLabel($0.publicationStatus)) }; isLast = data.last
            case .generations:
                let data: AdminPage<Generation> = try await APIClient.shared.decode(kind.path, query: query)
                result = data.content.map { AdminRow(id: $0.id, title: "\($0.task) #\($0.id)", subtitle: $0.sourceUrl) }; isLast = data.last
            case .notices:
                let data: AdminPage<AdminNotice> = try await APIClient.shared.decode(kind.path, query: query)
                result = data.content.compactMap { value in value.id.map { AdminRow(id: $0, title: value.title, subtitle: value.published ? "공개" : "비공개") } }; isLast = data.last
            case .profiles:
                let data: [APIClient.Profile] = try await APIClient.shared.decode(kind.path)
                result = data.map { AdminRow(id: $0.id, title: $0.name, subtitle: $0.imgUrl) }; isLast = true
            }
            try Task.checkCancellation(); guard ticket == generation else { return }
            let ids = Set(rows.map(\.id)); rows += result.filter { !ids.contains($0.id) }; page += 1; last = isLast
        } catch is CancellationError { }
        catch let e as URLError where e.code == .cancelled { }
        catch { if ticket == generation { self.error = error.localizedDescription } }
    }
}

struct EditorialDetailView: View {
    let id: Int64
    let kind: AdminKind
    @State private var draft: EditorialDraft?
    @State private var version: Int64 = 0
    @State private var status = ""
    @State private var reason = ""
    @State private var contentType = "NEWS"
    @State private var categories: [NewsCategory] = []
    @State private var selected: Set<Int64> = []
    @State private var busy = false
    @State private var error: String?
    @State private var message: String?
    @State private var action: String?
    private var resource: String { kind == .submissions ? "SUBMISSION" : "ARTICLE" }
    private var canEdit: Bool { kind == .articles || status != "APPROVED" }
    private var hasReason: Bool { !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && reason.count <= 2000 }
    var body: some View {
        Form {
            if let draft {
                Section {
                    Text(draft.title).font(.headline)
                    Text(statusLabel(status)).foregroundStyle(.secondary)
                    if let url = URL(string: draft.sourceUrl) { Link(draft.sourceTitle, destination: url) }
                }
                if canEdit {
                    EditorialDraftFields(draft: Binding(get: { self.draft ?? draft }, set: { self.draft = $0 }))
                    Section("변경 사유") { TextField("수정·반려·복원 사유 (필수)", text: $reason, axis: .vertical) }
                    Button("수정 저장") { action = "edit" }.disabled(!draft.valid || !hasReason)
                }
                if kind == .submissions && status == "PENDING" {
                    Section("승인 설정") {
                        Picker("콘텐츠 유형", selection: $contentType) {
                            Text("뉴스").tag("NEWS"); Text("AI 이론").tag("AI_THEORY")
                        }
                        ForEach(categories) { category in
                            Toggle(category.name, isOn: Binding(get: { selected.contains(category.id) }, set: { value in
                                if value { selected.insert(category.id) } else { selected.remove(category.id) }
                            }))
                        }
                        Button("승인") { action = "approve" }.disabled(selected.count > 10)
                        Button("반려", role: .destructive) { action = "reject" }.disabled(!hasReason)
                        Text("승인된 콘텐츠는 발송 대기 상태가 됩니다.").font(.caption).foregroundStyle(.secondary)
                    }
                }
                if kind == .submissions && status == "DENIED" { Button("재검수로 돌리기") { action = "reopen" }.disabled(!hasReason) }
                if kind == .articles {
                    if status == "READY" { Text("최초 발송 전 콘텐츠는 공개 상태를 변경할 수 없습니다.").font(.footnote) }
                    else { Button(status == "HIDDEN" ? "복원" : "숨김") { action = status == "HIDDEN" ? "restore" : "hide" }.disabled(!hasReason) }
                }
                NavigationLink("변경 이력") { EditorialHistoryView(type: resource, id: id) }
            } else if busy { ProgressView() }
            if let error { RequestFailureView(message: error) { Task { await load() } } }
            if let message { Text(message).foregroundStyle(.secondary) }
        }.navigationTitle(kind == .submissions ? "뉴스 검수 #\(id)" : "카드뉴스 수정")
            .navigationBarTitleDisplayMode(.inline).disabled(busy)
            .task { await load() }
            .confirmationDialog("변경을 적용할까요?", isPresented: Binding(get: { action != nil }, set: { if !$0 { action = nil } }), titleVisibility: .visible) {
                if let action { Button("적용", role: action == "hide" || action == "reject" ? .destructive : nil) { Task { await apply(action) } } }
                Button("취소", role: .cancel) { action = nil }
            } message: { Text("승인·반려는 서버에 저장된 초안을 기준으로 처리합니다. 내용을 수정했다면 먼저 ‘수정 저장’을 해 주세요.") }
    }
    private func load() async {
        busy = true; error = nil; defer { busy = false }
        do {
            if kind == .submissions {
                let value: Submission = try await APIClient.shared.decode("\(kind.path)/\(id)")
                draft = value.draft; version = value.version ?? 0; status = value.status
                categories = try await APIClient.shared.decode("api/v1/cardNews/categories")
            } else {
                let value: AdminArticle = try await APIClient.shared.decode("\(kind.path)/\(id)")
                draft = value.draft; version = value.version; status = value.publicationStatus
            }
            if draft == nil { error = "원문 정보가 없는 콘텐츠라 수정 양식을 만들 수 없습니다." }
        } catch { self.error = error.localizedDescription }
    }
    private func apply(_ operation: String) async {
        guard let draft else { return }; action = nil; busy = true; error = nil; message = nil
        do {
            let path = "\(kind.path)/\(id)"
            var body: Data
            if operation == "edit" { body = try JSONEncoder().encode(EditorialEdit(version: version, reason: reason, draft: draft)) }
            else if operation == "approve" {
                struct Approval: Encodable { let contentType: String; let categoryIds: [Int64] }
                body = try JSONEncoder().encode(Approval(contentType: contentType, categoryIds: selected.sorted()))
            } else { body = try JSONEncoder().encode(EditorialAction(version: version, reason: reason)) }
            _ = try await APIClient.shared.request(path: operation == "edit" ? path : path + "/" + operation,
                method: operation == "edit" ? "PUT" : "POST", body: body)
            await load(); reason = ""; message = "반영했습니다."
        } catch { self.error = error.localizedDescription }
        busy = false
    }
}

struct EditorialDraftFields: View {
    @Binding var draft: EditorialDraft
    var body: some View {
        Section("제목·요약") {
            TextField("제목 (200자 이내)", text: $draft.title, axis: .vertical)
            TextField("요약 (5,000자 이내)", text: $draft.summary, axis: .vertical)
            TextField("핵심 포인트 (줄마다 하나)", text: Binding(get: { (draft.keyPoints ?? []).joined(separator: "\n") }, set: { draft.keyPoints = $0.components(separatedBy: "\n").filter { !$0.isEmpty } }), axis: .vertical)
        }
        Section("대표 이미지") { EditorialImageFields(image: $draft.titleImage) }
        ForEach(draft.slides.indices, id: \.self) { index in
            Section("슬라이드 \(index + 1)") {
                TextField("제목", text: $draft.slides[index].title, axis: .vertical)
                TextField("본문", text: $draft.slides[index].content, axis: .vertical)
                TextField("레이아웃", text: Binding(get: { draft.slides[index].layout ?? "" }, set: { draft.slides[index].layout = $0 }))
                EditorialImageFields(image: $draft.slides[index].image)
                Button("슬라이드 삭제", role: .destructive) {
                    draft.slides.remove(at: index); resequence()
                }.disabled(draft.slides.count <= 1)
            }
        }
        Button("슬라이드 추가") {
            draft.slides.append(EditorialSlide(sequence: draft.slides.count + 1, title: "", content: "", layout: nil, image: nil))
        }.disabled(draft.slides.count >= 10)
    }
    private func resequence() { for i in draft.slides.indices { draft.slides[i].sequence = i + 1 } }
}
struct EditorialImageFields: View {
    @Binding var image: EditorialImage?
    var body: some View {
        Toggle("이미지 사용", isOn: Binding(get: { image != nil }, set: { image = $0 ? EditorialImage(url: "", origin: "GENERATED", sourceUrl: nil, credit: nil) : nil }))
        if image != nil {
            RemoteImage(raw: image?.url).frame(height: 120)
            TextField("이미지 URL", text: field(\.url)).textInputAutocapitalization(.never).autocorrectionDisabled()
            Picker("이미지 유형", selection: field(\.origin)) { Text("생성 이미지").tag("GENERATED"); Text("출처 이미지").tag("SOURCE") }
            TextField("원문 URL", text: optionalField(\.sourceUrl)).textInputAutocapitalization(.never).autocorrectionDisabled()
            TextField("이미지 출처 표기", text: optionalField(\.credit))
        }
    }
    private func field(_ key: WritableKeyPath<EditorialImage, String>) -> Binding<String> { Binding(get: { image?[keyPath: key] ?? "" }, set: { image?[keyPath: key] = $0 }) }
    private func optionalField(_ key: WritableKeyPath<EditorialImage, String?>) -> Binding<String> { Binding(get: { image?[keyPath: key] ?? "" }, set: { image?[keyPath: key] = $0.isEmpty ? nil : $0 }) }
}

struct AdminNoticeEditor: View {
    let id: Int64?
    @State private var value = AdminNotice(id: nil, title: "", content: "", published: false, publishedAt: nil)
    @State private var busy = false
    @State private var loaded = false
    @State private var error: String?
    @State private var deleting = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        Form {
            Section("공지") {
                TextField("제목 (200자 이내)", text: $value.title, axis: .vertical).accessibilityIdentifier("admin.notice.title")
                TextField("본문 (10,000자 이내)", text: $value.content, axis: .vertical).accessibilityIdentifier("admin.notice.content")
                Toggle("공개", isOn: $value.published)
            }
            if let error { RequestFailureView(message: error) { Task { await load() } } }
            Button("저장") { Task { await save() } }.disabled(!loaded || value.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || value.title.count > 200 || value.content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || value.content.count > 10000)
            if id != nil { Button("공지 삭제", role: .destructive) { deleting = true }.disabled(!loaded) }
        }.disabled(busy).navigationTitle(id == nil ? "공지 추가" : "공지 수정").navigationBarTitleDisplayMode(.inline)
            .task { await load() }.toolbar { if id == nil { Button("취소") { dismiss() } } }
            .confirmationDialog("공지를 삭제할까요?", isPresented: $deleting, titleVisibility: .visible) {
                Button("삭제", role: .destructive) { Task { await save(delete: true) } }
            }
    }
    private func load() async {
        busy = true; error = nil; defer { busy = false }
        do { if let id { value = try await APIClient.shared.decode("api/v1/admin/notices/\(id)") }; loaded = true }
        catch { self.error = error.localizedDescription }
    }
    private func save(delete: Bool = false) async {
        busy = true; error = nil; defer { busy = false }
        do {
            struct Write: Encodable { let title: String; let content: String; let published: Bool }
            let path = "api/v1/admin/notices" + (id.map { "/\($0)" } ?? "")
            _ = try await APIClient.shared.request(path: path, method: delete ? "DELETE" : (id == nil ? "POST" : "PUT"),
                body: delete ? nil : JSONEncoder().encode(Write(title: value.title, content: value.content, published: value.published)))
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}

struct AdminProfileEditor: View {
    let id: Int64?
    @State private var name = ""
    @State private var url = ""
    @State private var busy = false
    @State private var loaded = false
    @State private var error: String?
    @State private var deleting = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        Form {
            Section("프로필 이미지") {
                RemoteImage(raw: url).frame(height: 100)
                TextField("이름 (20자 이내)", text: $name)
                TextField("이미지 주소", text: $url).textInputAutocapitalization(.never).autocorrectionDisabled()
                Text("현재 서버는 이미지 주소도 최대 20자로 제한합니다.").font(.caption).foregroundStyle(.secondary)
            }
            if let error { RequestFailureView(message: error) { Task { await load() } } }
            Button("저장") { Task { await save() } }.disabled(!loaded || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || name.count > 20 || url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || url.count > 20)
            if id != nil { Button("프로필 이미지 삭제", role: .destructive) { deleting = true }.disabled(!loaded) }
        }.disabled(busy).navigationTitle(id == nil ? "프로필 이미지 추가" : "프로필 이미지 수정").navigationBarTitleDisplayMode(.inline)
            .task { await load() }.toolbar { if id == nil { Button("취소") { dismiss() } } }
            .confirmationDialog("프로필 이미지를 삭제할까요?", isPresented: $deleting, titleVisibility: .visible) {
                Button("삭제", role: .destructive) { Task { await save(delete: true) } }
            }
    }
    private func load() async {
        busy = true; error = nil; defer { busy = false }
        do {
            if let id {
                let items: [APIClient.Profile] = try await APIClient.shared.decode("api/v1/profile")
                guard let item = items.first(where: { $0.id == id }) else { throw APIError.http(404) }
                name = item.name; url = item.imgUrl
            }
            loaded = true
        } catch { self.error = error.localizedDescription }
    }
    private func save(delete: Bool = false) async {
        busy = true; error = nil; defer { busy = false }
        do {
            struct Write: Encodable { let name: String; let imgUrl: String }
            _ = try await APIClient.shared.request(path: "api/v1/admin/profile" + (id.map { "/\($0)" } ?? ""),
                method: delete ? "DELETE" : (id == nil ? "POST" : "PATCH"), body: delete ? nil : JSONEncoder().encode(Write(name: name, imgUrl: url)))
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}

struct EditorialHistoryView: View {
    let type: String
    let id: Int64
    @State private var items: [EditorialHistory] = []
    @State private var page = 0
    @State private var last = false
    @State private var busy = false
    @State private var error: String?
    var body: some View {
        List {
            ForEach(items) { item in
                DisclosureGroup("\(item.action) · \(item.occurredAt)") {
                    Text(item.reason)
                    Text("작업자 #\(item.actorId)").font(.caption).foregroundStyle(.secondary)
                    if let before = item.beforeJson { DisclosureGroup("변경 전") { Text(before).font(.caption.monospaced()).textSelection(.enabled) } }
                    if let after = item.afterJson { DisclosureGroup("변경 후") { Text(after).font(.caption.monospaced()).textSelection(.enabled) } }
                }
            }
            if busy { ProgressView() }
            else if let error { RequestFailureView(message: error) { Task { await load() } } }
            else if items.isEmpty { Text("변경 이력이 없습니다.").foregroundStyle(.secondary) }
            else if !last { Button("더 보기") { Task { await load() } } }
        }.navigationTitle("변경 이력").navigationBarTitleDisplayMode(.inline).task { if items.isEmpty { await load() } }
    }
    private func load() async {
        guard !busy, !last else { return }; busy = true; error = nil; defer { busy = false }
        do {
            let data: AdminPage<EditorialHistory> = try await APIClient.shared.decode("api/v1/admin/editorial/history", query: [URLQueryItem(name: "resourceType", value: type), URLQueryItem(name: "resourceId", value: String(id)), URLQueryItem(name: "page", value: String(page))])
            items += data.content; last = data.last; page += 1
        } catch { self.error = error.localizedDescription }
    }
}

struct GenerationDetailView: View {
    let id: Int64
    @State private var value: Generation?
    @State private var response = ""
    @State private var reason = ""
    @State private var photo: PhotosPickerItem?
    @State private var imageData: Data?
    @State private var busy = false
    @State private var error: String?
    @State private var message: String?
    @State private var confirming = false
    @FocusState private var inputFocused: Bool
    private var validResponse: Bool {
        response.count <= 1_000_000 && (try? JSONSerialization.jsonObject(with: Data(response.utf8))) is [String: Any]
    }
    private var validReason: Bool { !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && reason.count <= 2000 }
    var body: some View {
        Form {
            if let value {
                Section("생성 기록") {
                    Text(value.task).font(.headline)
                    Text(value.sourceUrl).font(.caption).textSelection(.enabled)
                    if let raw = value.requestJson { DisclosureGroup("요청 내용") { Text(raw).font(.caption.monospaced()).textSelection(.enabled) } }
                }
                Section("복구") {
                    TextField("복구 사유 (필수)", text: $reason, axis: .vertical).focused($inputFocused)
                    if value.task.hasPrefix("image_generation_") {
                        PhotosPicker("복구할 이미지 선택", selection: $photo, matching: .images)
                        if let imageData, let image = UIImage(data: imageData) { Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 200) }
                    } else {
                        Text("텍스트 응답 복구").font(.headline)
                        TextEditor(text: $response).font(.body.monospaced()).frame(minHeight: 180).focused($inputFocused)
                    }
                    Text("저장된 생성 응답을 수정해 복구합니다. 새 유료 생성 요청은 실행하지 않습니다.").font(.caption).foregroundStyle(.secondary)
                    Button(imageData == nil ? "응답 복구" : "선택한 이미지로 복구") { confirming = true }
                        .disabled(!validReason || (value.task.hasPrefix("image_generation_") ? imageData == nil : !validResponse))
                    if imageData != nil { Button("이미지 선택 취소") { imageData = nil; photo = nil } }
                }
                NavigationLink("변경 이력") { EditorialHistoryView(type: "GENERATION", id: id) }
            }
            if let error { RequestFailureView(message: error) { Task { await load() } } }
            if let message { Text(message).foregroundStyle(.secondary) }
            if busy { ProgressView() }
        }.disabled(busy).navigationTitle("생성 복구 #\(id)").navigationBarTitleDisplayMode(.inline).task { await load() }
            .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("입력 완료") { inputFocused = false } } }
            .onChange(of: photo) { _, value in Task {
                guard let value else { return }
                imageData = nil; error = nil
                do {
                    guard let raw = try await value.loadTransferable(type: Data.self), let image = UIImage(data: raw),
                          let cg = image.cgImage, cg.width <= 4096, cg.height <= 4096,
                          let png = image.pngData(), png.count <= 10 * 1024 * 1024 else { throw APIError.invalidResponse }
                    imageData = png
                } catch { self.error = "4096×4096 이하, PNG 변환 후 10MB 이하인 이미지를 선택해 주세요." }
            } }
            .confirmationDialog("이 내용으로 복구할까요?", isPresented: $confirming, titleVisibility: .visible) { Button("복구") { Task { await recover() } } }
    }
    private func load() async {
        busy = true; error = nil; defer { busy = false }
        do {
            let data: Generation = try await APIClient.shared.decode("api/v1/admin/generations/\(id)")
            value = data; response = data.recoveredResponseJson ?? data.responseJson ?? ""
        } catch { self.error = error.localizedDescription }
    }
    private func recover() async {
        guard let value else { return }; busy = true; error = nil; message = nil
        do {
            if let imageData {
                let boundary = "AiKnow-\(UUID().uuidString)"
                var body = Data()
                for (name, text) in [("version", String(value.version)), ("reason", reason)] {
                    body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(text)\r\n".utf8))
                }
                body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"recovery.png\"\r\nContent-Type: image/png\r\n\r\n".utf8)); body.append(imageData); body.append(Data("\r\n--\(boundary)--\r\n".utf8))
                _ = try await APIClient.shared.request(path: "api/v1/admin/generations/\(id)/recover-image", method: "POST", body: body, contentType: "multipart/form-data; boundary=\(boundary)")
            } else {
                struct Recovery: Encodable { let version: Int64; let reason: String; let responseJson: String }
                _ = try await APIClient.shared.request(path: "api/v1/admin/generations/\(id)/recover", method: "POST", body: JSONEncoder().encode(Recovery(version: value.version, reason: reason, responseJson: response)))
            }
            await load(); imageData = nil; photo = nil; reason = ""; message = "복구했습니다."
        } catch { self.error = error.localizedDescription }
        busy = false
    }
}
