import SwiftUI

struct CardNewsDetailView: View {
    let cardID: Int64
    @State private var card: NewsCard?
    @State private var slides: [StorySlide] = []
    @State private var currentSlide = 0
    @State private var loading = true
    @State private var error: String?
    @State private var savingRead = false
    @State private var showImage = false
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
                                if APIClient.shared.mediaURL(slide.imgUrl) != nil {
                                    RemoteImage(raw: slide.imgUrl).clipShape(RoundedRectangle(cornerRadius: 20))
                                        .overlay(alignment: .bottomTrailing) {
                                            Button { showImage = true } label: {
                                                Image(systemName: "arrow.up.left.and.arrow.down.right")
                                                    .frame(width: 44, height: 44)
                                                    .background(.regularMaterial, in: Circle())
                                            }.buttonStyle(.plain).padding(8)
                                                .accessibilityLabel("이미지 확대")
                                        }
                                }
                                if let title = slide.title { Text(title).font(.title.bold()).fixedSize(horizontal: false, vertical: true) }
                                if let content = slide.content { Text(content).font(.body).lineSpacing(5).fixedSize(horizontal: false, vertical: true) }
                                if let credit = slide.imageCredit { Text(credit).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }
                                if let url = APIClient.shared.mediaURL(slide.imageSourceUrl) { Link("이미지 출처", destination: url).font(.caption) }
                            }.padding(24).frame(maxWidth: .infinity, alignment: .leading)
                                .background(AppPalette.surface, in: RoundedRectangle(cornerRadius: 26)).padding(16)
                        }.tag(index)
                    }
                }.tabViewStyle(.page(indexDisplayMode: .never))
                // UIKit's page indicator overlays the scrollable card and its credits.
                // Reserve a separate row so all of the card can be read by scrolling.
                HStack(spacing: 7) {
                    if slides.count <= 10 {
                        ForEach(slides.indices, id: \.self) { index in
                            Circle().fill(index == currentSlide ? AppPalette.accent : Color.secondary.opacity(0.35))
                                .frame(width: 6, height: 6)
                        }
                    } else { Text("\(currentSlide + 1) / \(slides.count)").font(.caption) }
                }.frame(height: 16).accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(slides.count)장 중 \(currentSlide + 1)번째 카드")
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
            .toolbar(.hidden, for: .tabBar)
            .task(id: cardID) { await load() }
            .task(id: "\(slides.map(\.id)):\(currentSlide)") { await preloadNextSlide() }
            .sheet(isPresented: $showImage) {
                if slides.indices.contains(currentSlide), let url = APIClient.shared.mediaURL(slides[currentSlide].imgUrl) {
                    CardImageViewer(url: url)
                }
            }
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
    private func preloadNextSlide() async {
        guard slides.indices.contains(currentSlide), slides.indices.contains(currentSlide + 1) else { return }
        let currentURL = APIClient.shared.mediaURL(slides[currentSlide].imgUrl)
        let nextURL = APIClient.shared.mediaURL(slides[currentSlide + 1].imgUrl)
        // Finish the visible image first so prefetching does not compete with it.
        // Both calls share the same download/decoded image as RemoteImage.
        if let currentURL { _ = try? await ImageLoader.shared.image(at: currentURL, maxPixelSize: 1_280) }
        guard !Task.isCancelled, let nextURL else { return }
        _ = try? await ImageLoader.shared.image(at: nextURL, maxPixelSize: 1_280)
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

private struct CardImageViewer: View {
    let url: URL
    @State private var image: UIImage?
    @State private var failed = false
    @State private var retry = 0
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if let image { ZoomableCardImage(image: image).accessibilityIdentifier("image.zoom") }
                else if failed {
                    RequestFailureView(message: "이미지를 불러오지 못했어요.") { retry += 1 }
                } else { ProgressView("이미지를 불러오는 중…") }
            }.frame(maxWidth: .infinity, maxHeight: .infinity)
                .navigationTitle("이미지 확대").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("닫기") { dismiss() } } }
                .safeAreaInset(edge: .bottom) { Text("두 손가락으로 확대하고 드래그해서 살펴보세요.").font(.caption).foregroundStyle(.secondary).padding(10) }
                .task(id: retry) {
                    failed = false
                    do {
                        let loaded = try await ImageLoader.shared.image(at: url, maxPixelSize: 4_096)
                        try Task.checkCancellation()
                        image = loaded
                    } catch { if !Task.isCancelled { failed = true } }
                }
        }
    }
}

private struct ZoomableCardImage: UIViewRepresentable {
    let image: UIImage
    func makeUIView(context: Context) -> CardImageScrollView { CardImageScrollView(image: image) }
    func updateUIView(_ uiView: CardImageScrollView, context: Context) { }
}

private final class CardImageScrollView: UIScrollView, UIScrollViewDelegate {
    private let imageView: UIImageView
    private var lastSize = CGSize.zero

    init(image: UIImage) {
        imageView = UIImageView(image: image)
        super.init(frame: .zero)
        delegate = self
        backgroundColor = .white
        contentInsetAdjustmentBehavior = .never
        addSubview(imageView)
        imageView.frame = CGRect(origin: .zero, size: image.size)
        contentSize = image.size
        imageView.isAccessibilityElement = true
        imageView.accessibilityLabel = "확대 가능한 카드 이미지"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, bounds.height > 0 else { return }
        if bounds.size != lastSize {
            lastSize = bounds.size
            let size = imageView.image!.size
            minimumZoomScale = min(bounds.width / size.width, bounds.height / size.height)
            maximumZoomScale = max(1, minimumZoomScale * 6)
            zoomScale = minimumZoomScale
        }
        centerImage()
    }
    func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }
    func scrollViewDidZoom(_ scrollView: UIScrollView) { centerImage() }
    private func centerImage() {
        let horizontal = max(0, (bounds.width - imageView.frame.width) / 2)
        let vertical = max(0, (bounds.height - imageView.frame.height) / 2)
        contentInset = UIEdgeInsets(top: vertical, left: horizontal, bottom: vertical, right: horizontal)
    }
}
