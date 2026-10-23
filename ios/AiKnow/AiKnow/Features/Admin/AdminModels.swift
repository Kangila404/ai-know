import Foundation

struct AdminPage<T: Decodable>: Decodable { let content: [T]; let last: Bool }
struct Submission: Decodable, Identifiable {
    let id: Int64
    let status: String
    let cardNewsId: Int64?
    let version: Int64?
    let draft: EditorialDraft?
    let reviewNote: String?
}
struct EditorialImage: Codable, Hashable {
    var url: String
    var origin: String
    var sourceUrl: String?
    var credit: String?
    var valid: Bool {
        guard let parsed = URL(string: url), ["http", "https"].contains(parsed.scheme ?? ""), url.count <= 2048 else { return false }
        return origin == "GENERATED" || ((sourceUrl ?? "").hasPrefix("https://") && !(credit ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
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
    var valid: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && title.count <= 200 &&
        !summary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && summary.count <= 5000 &&
        (titleImage?.valid ?? true) && (1...10).contains(slides.count) &&
        (keyPoints ?? []).count <= 10 && (keyPoints ?? []).allSatisfy { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.count <= 1000 } &&
        slides.allSatisfy { !$0.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.title.count <= 200 && !$0.content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.content.count <= 5000 && ($0.image?.valid ?? true) }
    }
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
