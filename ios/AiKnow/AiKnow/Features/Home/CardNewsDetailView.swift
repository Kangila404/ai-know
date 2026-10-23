import SwiftUI

struct CardNewsDetailView: View {
    let cardID: Int64
    @State private var card: NewsCard?
    @State private var slides: [StorySlide] = []
    @State private var currentSlide = 0
    @State private var loading = true
    @State private var error: String?
    @State private var savingRead = false
    @Environment(StoryStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    init(card: NewsCard) { cardID = card.id; _card = State(initialValue: card) }
    init(cardID: Int64) { self.cardID = cardID }

    var body: some View {
        VStack(spacing: 12) {
            if loading { ProgressView("카드뉴스를 불러오는 중…").frame(maxWidth: .infinity, maxHeight: .infinity) }
            else if let error { RequestFailureView(message: error) { Task { await load() } }.frame(maxHeight: .infinity) }
            else if slides.isEmpty { ContentUnavailableView("아직 카드가 없어요", systemImage: "rectangle.stack") }
            else {
                TabView(selection: $currentSlide) {
                    ForEach(Array(slides.enumerated()), id: \.element.id) { index, slide in
                        ScrollView {
                            VStack(alignment: .leading, spacing: 20) {
                                Text("\(index + 1) / \(slides.count)").font(.caption.bold()).foregroundStyle(AppPalette.accent)
                                if slide.imgUrl != nil { RemoteImage(raw: slide.imgUrl).clipShape(RoundedRectangle(cornerRadius: 20)) }
                                if let title = slide.title { Text(title).font(.title.bold()) }
                                if let content = slide.content { Text(content).font(.body).lineSpacing(5) }
                                if let credit = slide.imageCredit { Text(credit).font(.caption).foregroundStyle(.secondary) }
                                if let url = APIClient.shared.mediaURL(slide.imageSourceUrl) { Link("이미지 출처", destination: url).font(.caption) }
                            }.padding(24).frame(maxWidth: .infinity, alignment: .leading)
                                .background(AppPalette.surface, in: RoundedRectangle(cornerRadius: 26)).padding(16)
                        }.tag(index)
                    }
                }.tabViewStyle(.page(indexDisplayMode: .automatic))
                if let card, let url = APIClient.shared.mediaURL(card.sourceUrl) {
                    Link(card.sourceTitle ?? "원문 보기", destination: url).font(.caption).lineLimit(2).padding(.horizontal)
                }
                Button {
                    if currentSlide == slides.count - 1, let card {
                        savingRead = true
                        Task { if await store.markRead(card) { dismiss() }; savingRead = false }
                    } else { withAnimation { currentSlide += 1 } }
                } label: {
                    Label(currentSlide == slides.count - 1 ? "다 읽었어요" : "다음 카드", systemImage: "arrow.right")
                        .frame(maxWidth: .infinity).padding(8)
                }.disabled(savingRead).buttonStyle(.borderedProminent).padding(.horizontal, 25).padding(.bottom)
            }
        }.background(AppPalette.background)
            .navigationTitle("카드 뉴스").navigationBarTitleDisplayMode(.inline)
            .task(id: cardID) { await load() }
            .toolbar {
                if let card {
                    let current = store.current(card)
                    Button { Task { await store.toggleLike(current) } } label: {
                        Image(systemName: current.liked ? "heart.fill" : "heart").foregroundStyle(current.liked ? .pink : .primary)
                    }.disabled(store.busyIDs.contains(card.id) || loading || error != nil)
                        .accessibilityLabel(current.liked ? "좋아요 취소" : "좋아요")
                }
            }
    }
    private func load() async {
        loading = true; error = nil
        defer { loading = false }
        do {
            async let news: NewsCard = APIClient.shared.decode("api/v1/cardNews/\(cardID)")
            async let pages: [StorySlide] = APIClient.shared.decode("api/v1/cardNews/cardSlides", query: [URLQueryItem(name: "cardNewsId", value: String(cardID))])
            let (result, content) = try await (news, pages)
            card = result; store.remember(result); slides = content.sorted { $0.sequence < $1.sequence }; currentSlide = 0
        } catch { self.error = error.localizedDescription }
    }
}
