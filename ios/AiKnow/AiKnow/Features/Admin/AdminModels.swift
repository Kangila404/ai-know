import Foundation

struct AdminPage<T: Decodable>: Decodable { let content: [T]; let last: Bool }
struct Submission: Decodable, Identifiable {
    let id: Int64
    let status: String
    let cardNewsId: Int64?
    let version: Int64?
    let draft: EditorialDraft?
    let reviewNote: String?
    let reviewedBy: Int64?
    let reviewedAt: String?
    let publicationStatus: String?
    let contentType: String?
}
struct EditorialImage: Codable, Hashable {
    var url: String
    var origin: String
    var sourceUrl: String?
    var credit: String?
    var valid: Bool {
        guard let parsed = URL(string: url), ["http", "https"].contains(parsed.scheme ?? ""),
              parsed.host != nil, url.utf16.count <= 2048, ["SOURCE", "GENERATED"].contains(origin),
              (credit ?? "").utf16.count <= 500 else { return false }
        if let sourceUrl {
            guard let source = URL(string: sourceUrl), source.scheme == "https", source.host != nil,
                  sourceUrl.utf16.count <= 2048 else { return false }
        }
        return origin == "GENERATED" || (sourceUrl != nil && !(credit ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }
}
struct EditorialSlide: Codable, Hashable {
    var sequence: Int
    var title: String
    var content: String
    var layout: String?
    var image: EditorialImage?
}
struct EditorialDraft: Codable {
    var sourceTitle: String
    var sourceUrl: String
    var publishedAt: String
    var title: String
    var summary: String
    var keyPoints: [String]?
    var titleImage: EditorialImage?
    var slides: [EditorialSlide]
    var validationMessage: String? {
        if title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || title.utf16.count > 200 { return "제목을 1~200자로 입력해 주세요." }
        if summary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || summary.utf16.count > 5000 { return "요약을 1~5,000자로 입력해 주세요." }
        if (keyPoints ?? []).count > 10 || !(keyPoints ?? []).allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.utf16.count <= 1000 }) { return "핵심 포인트는 각각 1,000자 이내로 최대 10개까지 입력할 수 있습니다." }
        if !(titleImage?.valid ?? true) { return "대표 이미지 URL과 출처를 확인해 주세요. 출처 URL은 HTTPS, 출처 표기는 500자 이내여야 합니다." }
        if !(1...10).contains(slides.count) { return "슬라이드는 1~10장이 필요합니다." }
        for (index, slide) in slides.enumerated() {
            if slide.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || slide.title.utf16.count > 200 || slide.content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || slide.content.utf16.count > 5000 { return "슬라이드 \(index + 1)의 제목(200자 이내)과 본문(5,000자 이내)을 확인해 주세요." }
            if (slide.layout ?? "").utf16.count > 100 { return "슬라이드 \(index + 1)의 레이아웃은 100자 이내로 입력해 주세요." }
            if !(slide.image?.valid ?? true) { return "슬라이드 \(index + 1)의 이미지 URL과 출처를 확인해 주세요." }
        }
        return nil
    }
    var valid: Bool { validationMessage == nil }
}
struct AdminArticle: Decodable, Identifiable {
    let version: Int64
    let publicationStatus: String
    let news: NewsCard
    let slides: [StorySlide]
    var id: Int64 { news.id }
    var draft: EditorialDraft? {
        guard let title = news.sourceTitle, let url = news.sourceUrl, let date = news.sourcePublishedAt else { return nil }
        let image = news.titleImgUrl.map { EditorialImage(url: $0, origin: news.titleImageOrigin ?? "GENERATED", sourceUrl: news.titleImageSourceUrl, credit: news.titleImageCredit) }
        return EditorialDraft(sourceTitle: title, sourceUrl: url, publishedAt: date, title: news.title,
            summary: news.summary ?? "", keyPoints: news.keyPoints, titleImage: image,
            slides: slides.sorted { $0.sequence < $1.sequence }.map { slide in
                EditorialSlide(sequence: slide.sequence, title: slide.title ?? "", content: slide.content ?? "", layout: slide.layout,
                    image: slide.imgUrl.map { url in EditorialImage(url: url, origin: slide.imageOrigin ?? "GENERATED", sourceUrl: slide.imageSourceUrl, credit: slide.imageCredit) })
            })
    }
}
struct AdminNotice: Codable, Identifiable {
    var id: Int64?
    var title: String
    var content: String
    var published: Bool
    var publishedAt: String?
}
struct Generation: Decodable, Identifiable {
    let id: Int64
    let version: Int64
    let sourceUrl: String
    let task: String
    let createdAt: String
    let responseSaved: Bool?
    let recovered: Bool?
    let requestJson: String?
    let responseJson: String?
    let recoveredResponseJson: String?
    let contextJson: String?
    var statusText: String {
        if recovered == true || recoveredResponseJson != nil { return "복구됨" }
        return responseSaved == true || responseJson != nil ? "응답 저장됨" : "응답 대기"
    }
}
struct EditorialHistory: Decodable, Identifiable {
    let id: Int64
    let action: String
    let actorId: Int64
    let occurredAt: String
    let reason: String
    let beforeJson: String?
    let afterJson: String?
}
struct EditorialAction: Encodable { let version: Int64; let reason: String }
struct EditorialEdit: Encodable { let version: Int64; let reason: String; let draft: EditorialDraft }

enum AdminKind: String, CaseIterable, Identifiable {
    case submissions, articles, generations, notices, profiles
    var id: String { rawValue }
    var title: String {
        switch self { case .submissions: return "뉴스 검수"; case .articles: return "카드뉴스 관리"; case .generations: return "생성 복구"; case .notices: return "공지 관리"; case .profiles: return "프로필 이미지 관리" }
    }
    var symbol: String {
        switch self { case .submissions: return "checkmark.seal"; case .articles: return "newspaper"; case .generations: return "arrow.clockwise"; case .notices: return "megaphone"; case .profiles: return "person.crop.square" }
    }
    var path: String {
        switch self { case .submissions: return "api/v1/admin/news-submissions"; case .articles: return "api/v1/admin/card-news"; case .generations: return "api/v1/admin/generations"; case .notices: return "api/v1/admin/notices"; case .profiles: return "api/v1/profile" }
    }
    var filters: [String] {
        switch self { case .submissions: return ["PENDING", "APPROVED", "DENIED"]; case .articles: return ["", "READY", "PUBLISHED", "HIDDEN"]; default: return [] }
    }
}
func statusLabel(_ raw: String) -> String {
    ["": "전체", "PENDING": "검수 대기", "APPROVED": "승인", "DENIED": "반려", "READY": "발송 대기", "PUBLISHED": "게시", "HIDDEN": "숨김", "NEWS": "뉴스", "AI_THEORY": "AI 이론"][raw] ?? raw
}
