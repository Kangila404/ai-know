import SwiftUI

struct NewsCategory: Codable, Hashable, Identifiable {
    let id: Int64
    let name: String
}

struct NewsCard: Decodable, Identifiable, Hashable {
    let id: Int64
    let title: String
    let titleImgUrl: String?
    let keyPoints: [String]
    let summary: String?
    let sourceTitle: String?
    let sourceUrl: String?
    let sourcePublishedAt: String?
    let titleImageOrigin: String?
    let titleImageCredit: String?
    let titleImageSourceUrl: String?
    let contentType: String?
    let publicationDate: String?
    let categories: [NewsCategory]
    var liked: Bool
    var read: Bool
    var categoryLabel: String { categories.map(\.name).joined(separator: " · ") }
    var date: String { publicationDate ?? "" }
}

struct StorySlide: Decodable, Identifiable, Hashable {
    let id: Int64
    let title: String?
    let imgUrl: String?
    let content: String?
    let sequence: Int
    let layout: String?
    let imageOrigin: String?
    let imageCredit: String?
    let imageSourceUrl: String?
}

struct Notice: Decodable, Identifiable {
    let id: Int64
    let title: String
    let content: String
    let publishedAt: String?
}
struct NoticePage: Decodable { let content: [Notice]; let last: Bool }
struct NotificationSetting: Codable {
    var settingTime: String
    var isAllowed: Bool
}

enum AppTab: Hashable { case today, archive, profile, admin }

@MainActor @Observable
final class StoryStore {
    var selectedTab: AppTab = .today
    var today: NewsCard?
    var todayLoading = false
    var todayError: String?
    var cards: [NewsCard] = []
    var categories: [NewsCategory] = []
    var archiveLoading = false
    var archiveError: String?
    var categoriesError: String?
    var actionError: String?
    var hasMore = true
    var query = ""
    var categoryID: Int64?
    var likesOnly = false
    var busyIDs: Set<Int64> = []
    var state: [Int64: NewsCard] = [:]
    var pushedCardID: Int64?
    private var page = 0
    private var generation = 0
    private let pageSize = 20

    func remember(_ card: NewsCard) { state[card.id] = card }
    func current(_ card: NewsCard) -> NewsCard { state[card.id] ?? card }

    func loadToday() async {
        guard !todayLoading else { return }
        todayLoading = true; todayError = nil
        defer { todayLoading = false }
        do {
            let value: NewsCard = try await APIClient.shared.decode("api/v1/cardNews/today")
            today = value; remember(value)
        } catch APIError.http(404) { today = nil }
        catch { todayError = error.localizedDescription }
    }

    func loadCategories() async {
        do {
            categories = try await APIClient.shared.decode("api/v1/cardNews/categories")
            categoriesError = nil
        } catch { categoriesError = error.localizedDescription }
    }

    func loadArchive(reset: Bool = false) async {
        if !reset && (archiveLoading || !hasMore) { return }
        if reset { generation += 1; page = 0; cards = []; hasMore = true }
        let requestGeneration = generation
        archiveLoading = true; archiveError = nil
        defer { if requestGeneration == generation { archiveLoading = false } }
        var items = [URLQueryItem(name: "page", value: String(page)), URLQueryItem(name: "size", value: String(pageSize)),
                     URLQueryItem(name: "query", value: query.trimmingCharacters(in: .whitespacesAndNewlines)),
                     URLQueryItem(name: "isOnlyLiked", value: String(likesOnly))]
        if let categoryID { items.append(URLQueryItem(name: "categoryId", value: String(categoryID))) }
        do {
            let result: [NewsCard] = try await APIClient.shared.decode("api/v1/cardNews", query: items)
            try Task.checkCancellation()
            guard requestGeneration == generation else { return }
            let known = Set(cards.map(\.id))
            cards.append(contentsOf: result.filter { !known.contains($0.id) })
            result.forEach(remember)
            hasMore = result.count == pageSize; page += 1
        } catch is CancellationError { }
        catch let error as URLError where error.code == .cancelled { }
        catch { if requestGeneration == generation { archiveError = error.localizedDescription } }
    }

    func toggleLike(_ original: NewsCard) async {
        let card = current(original)
        guard !busyIDs.contains(card.id) else { return }
        busyIDs.insert(card.id); defer { busyIDs.remove(card.id) }
        do {
            struct Like: Codable { let liked: Bool }
            let response: Like = try await APIClient.shared.decode("api/v1/cardNews/\(card.id)/like", method: "PATCH",
                body: JSONEncoder().encode(Like(liked: !card.liked)))
            var updated = current(card); updated.liked = response.liked; remember(updated)
            if likesOnly && !updated.liked { await loadArchive(reset: true) }
        } catch { actionError = error.localizedDescription }
    }

    func markRead(_ original: NewsCard) async -> Bool {
        let card = current(original)
        if card.read { return true }
        do {
            _ = try await APIClient.shared.request(path: "api/v1/cardNews/\(card.id)/read", method: "PUT")
            var updated = current(card); updated.read = true; remember(updated)
            return true
        } catch { actionError = error.localizedDescription; return false }
    }
}

struct RequestFailureView: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            Text(message).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
            Button("다시 시도", action: retry).buttonStyle(.bordered)
        }.padding()
    }
}

struct RemoteImage: View {
    let raw: String?
    var body: some View {
        AsyncImage(url: AuthSession.shared.isPreview ? nil : APIClient.shared.mediaURL(raw)) { phase in
            if let image = phase.image { image.resizable().scaledToFit() }
            else {
                RoundedRectangle(cornerRadius: 16).fill(Color.indigo.opacity(0.1))
                    .overlay { Image(systemName: "photo").foregroundStyle(.indigo) }
            }
        }
    }
}
