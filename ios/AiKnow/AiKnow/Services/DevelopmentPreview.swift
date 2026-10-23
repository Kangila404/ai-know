#if DEBUG
import Foundation

/// In-memory UI preview only. This entire transport is absent from Release builds.
/// No session is created, no server request is sent, and no preview state is persisted.
@MainActor
final class DevelopmentPreview {
    private var account: [String: Any]
    var user: APIClient.User { try! JSONDecoder().decode(APIClient.User.self, from: JSONSerialization.data(withJSONObject: account)) }
    private var card: [String: Any]
    private var draft: [String: Any]
    private var submissionStatus = "PENDING"
    private var publicationStatus = "PUBLISHED"
    private var version = 1
    private var history: [[String: Any]] = []
    private var notices: [[String: Any]] = [["id": 1, "title": "미리보기 안내", "content": "이 화면의 데이터는 개발 미리보기용입니다. 실제 서버에는 저장되지 않습니다.", "published": true, "publishedAt": "2026-10-08T00:00:00Z"]]
    private var profiles: [[String: Any]] = [["id": 1, "name": "기본 프로필", "imgUrl": "/profile/default"], ["id": 2, "name": "두 번째 프로필", "imgUrl": "/profile/second"]]
    private var settings: [String: Any] = ["settingTime": "09:00:00", "isAllowed": true]
    private var recovered = false
    private var recoveredResponse: String?
    init(admin: Bool) {
        account = ["userId": "preview", "nickname": admin ? "미리보기 관리자" : "미리보기 사용자", "userRole": admin ? "ADMIN" : "USER", "onboardingCompleted": true,
                   "profile": ["id": 1, "name": "기본 프로필", "imgUrl": "/profile/default"]]
        card = ["id": 1, "title": "AI와 함께 만드는 새로운 하루", "summary": "홈, 아카이브, 상세 화면의 색상과 동작을 확인해 보세요.", "keyPoints": ["실제 서버에 저장하지 않습니다", "라이트·다크 모드를 전환해 볼 수 있습니다"],
                "publicationDate": "2026-10-08", "sourceTitle": "미리보기 원문", "sourceUrl": "https://example.com/preview", "sourcePublishedAt": "2026-10-08T00:00:00Z", "contentType": "NEWS",
                "categories": [["id": 1, "name": "AI 기술"]], "liked": false, "read": false]
        draft = ["sourceTitle": "미리보기 원문", "sourceUrl": "https://example.com/preview", "publishedAt": "2026-10-08T00:00:00Z", "title": "AI와 함께 만드는 새로운 하루", "summary": "초안 내용을 수정하고 승인·반려 동작을 확인할 수 있습니다.", "keyPoints": ["개발 미리보기 데이터"],
                 "slides": [["sequence": 1, "title": "AI 소식을 한눈에", "content": "본문과 이미지를 카드 형태로 읽을 수 있습니다."], ["sequence": 2, "title": "읽음과 좋아요", "content": "다 읽었어요를 눌러 읽음 표시를 확인해 보세요."]]]
    }
    private func page(_ values: [[String: Any]], _ query: [URLQueryItem]) -> [String: Any] {
        let number = Int(query.first { $0.name == "page" }?.value ?? "0") ?? 0
        return ["content": number == 0 ? values : [], "last": true]
    }
    private var slides: [[String: Any]] {
        (draft["slides"] as? [[String: Any]] ?? []).enumerated().map { index, slide in
            var value = slide; value["id"] = index + 1
            if let image = slide["image"] as? [String: Any] { value["imgUrl"] = image["url"]; value["imageOrigin"] = image["origin"]; value["imageCredit"] = image["credit"]; value["imageSourceUrl"] = image["sourceUrl"] }
            return value
        }
    }
    private var submission: [String: Any] { ["id": 1, "version": version, "status": submissionStatus, "draft": draft] }
    private var article: [String: Any] { ["version": version, "publicationStatus": publicationStatus, "news": card, "slides": slides] }
    private var generation: [String: Any] { ["id": 1, "version": version, "sourceUrl": "https://example.com/preview", "task": "TEXT", "createdAt": "2026-10-08T00:00:00Z", "responseSaved": true, "recovered": recovered, "requestJson": "{\"purpose\":\"UI preview\"}", "responseJson": recoveredResponse ?? "{\"title\":\"미리보기 응답\"}"] }
    func request(_ path: String, _ method: String, _ body: Data?, _ query: [URLQueryItem]) throws -> Data {
        let input = body.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
        let parts = path.split(separator: "/").map(String.init)
        let id = Int64(parts.last ?? "")
        var result: Any = [:]
        if path.hasPrefix("api/v1/admin/"), account["userRole"] as? String != "ADMIN" { throw APIError.http(403) }
        switch path {
        case "api/v1/users": result = account
        case "api/v1/users/nickname": account["nickname"] = input["nickname"]; result = account
        case "api/v1/users/onboarding": account["onboardingCompleted"] = true; result = account
        case "api/v1/users/me": throw APIError.http(403)
        case "api/v1/profile": result = profiles
        case "api/v1/notification-setting": if method == "PUT" { settings = input }; result = settings
        case "api/v1/device-tokens": result = [:]
        case "api/v1/cardNews/today", "api/v1/cardNews/1": result = card
        case "api/v1/cardNews/categories": result = [["id": 1, "name": "AI 기술"]]
        case "api/v1/cardNews/cardSlides": result = slides
        case "api/v1/cardNews/1/like": card["liked"] = input["liked"]; result = ["liked": card["liked"] ?? false]
        case "api/v1/cardNews/1/read": card["read"] = true
        case "api/v1/cardNews":
            let liked = query.first { $0.name == "isOnlyLiked" }?.value == "true"
            let search = query.first { $0.name == "query" }?.value ?? ""
            let matches = search.isEmpty || (card["title"] as? String ?? "").localizedCaseInsensitiveContains(search)
            result = matches && (!liked || card["liked"] as? Bool == true) ? [card] : []
        case "api/v1/notices": result = page(notices.filter { $0["published"] as? Bool == true }, query)
        case "api/v1/admin/notices":
            if method == "POST" { var value = input; value["id"] = (notices.compactMap { $0["id"] as? Int }.max() ?? 0) + 1; notices.append(value); result = value }
            else { result = page(notices, query) }
        case "api/v1/admin/profile":
            var value = input; value["id"] = (profiles.compactMap { $0["id"] as? Int }.max() ?? 0) + 1; profiles.append(value); result = value
        case "api/v1/admin/news-submissions":
            let status = query.first { $0.name == "status" }?.value ?? "PENDING"
            result = page(status == submissionStatus ? [submission] : [], query)
        case "api/v1/admin/card-news":
            let status = query.first { $0.name == "status" }?.value ?? ""
            result = page(status.isEmpty || status == publicationStatus ? [article] : [], query)
        case "api/v1/admin/generations": result = page([generation], query)
        case "api/v1/admin/generations/1": result = generation
        case "api/v1/admin/editorial/history": result = page(history, query)
        default:
            if path.hasPrefix("api/v1/users/profile/"), let id {
                guard let profile = profiles.first(where: { ($0["id"] as? Int64) == id || ($0["id"] as? Int).map(Int64.init) == id }) else { throw APIError.http(404) }
                account["profile"] = profile; result = profile
            } else if path.hasPrefix("api/v1/notices/") || path.hasPrefix("api/v1/admin/notices/") {
                guard let id, let index = notices.firstIndex(where: { ($0["id"] as? Int) == Int(id) }) else { throw APIError.http(404) }
                if method == "DELETE" { notices.remove(at: index) }
                else if method == "PUT" { var value = input; value["id"] = Int(id); notices[index] = value; result = value }
                else { result = notices[index] }
            } else if path.hasPrefix("api/v1/admin/profile/"), let id {
                guard let index = profiles.firstIndex(where: { ($0["id"] as? Int) == Int(id) }) else { throw APIError.http(404) }
                if method == "DELETE" { profiles.remove(at: index) }
                else { var value = input; value["id"] = Int(id); profiles[index] = value; result = value }
            } else if path.hasPrefix("api/v1/admin/news-submissions/1") || path.hasPrefix("api/v1/admin/card-news/1") {
                if method != "GET" {
                    if let edit = input["draft"] as? [String: Any] { draft = edit; card["title"] = edit["title"]; card["summary"] = edit["summary"]; card["keyPoints"] = edit["keyPoints"] }
                    if path.hasSuffix("/approve") { submissionStatus = "APPROVED" }
                    if path.hasSuffix("/reject") { submissionStatus = "DENIED" }
                    if path.hasSuffix("/reopen") { submissionStatus = "PENDING" }
                    if path.hasSuffix("/hide") { publicationStatus = "HIDDEN" }
                    if path.hasSuffix("/restore") { publicationStatus = "PUBLISHED" }
                    record(input, action: parts.last ?? "EDIT")
                }
                result = path.contains("news-submissions") ? submission : article
            } else if path.hasPrefix("api/v1/admin/generations/1/recover") {
                recovered = true; recoveredResponse = input["responseJson"] as? String; record(input, action: "RECOVER"); result = generation
            } else { throw APIError.http(404) }
        }
        return try JSONSerialization.data(withJSONObject: result)
    }
    private func record(_ input: [String: Any], action: String) {
        version += 1
        history.insert(["id": history.count + 1, "action": action, "actorId": 1, "occurredAt": ISO8601DateFormatter().string(from: Date()), "reason": input["reason"] ?? "미리보기 변경"], at: 0)
    }
}
#endif
