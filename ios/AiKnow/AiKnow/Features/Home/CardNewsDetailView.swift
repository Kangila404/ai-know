import SwiftUI

struct CardNewsDetailView: View {
    let card: NewsCard
    @Environment(StoryStore.self) private var store
    @State private var currentSlide = 0

    var body: some View {
        VStack(spacing: 14) {
            TabView(selection: $currentSlide) {
                ForEach(Array(card.slides.enumerated()), id: \.element.id) { index, slide in
                    StoryPage(slide: slide, index: index + 1)
                        .tag(index)
                        .padding(.horizontal, 20)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            Text(card.slides[currentSlide].caption)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(minHeight: 22)
                .padding(.horizontal, 28)

            HStack(spacing: 7) {
                ForEach(card.slides.indices, id: \.self) { index in
                    Capsule()
                        .fill(index == currentSlide ? Color.indigo : Color.indigo.opacity(0.18))
                        .frame(width: index == currentSlide ? 19 : 7, height: 7)
                }
            }

            Button {
                if currentSlide == card.slides.count - 1 {
                    store.markRead(card)
                    store.selectedTab = .archive
                } else {
                    withAnimation(.snappy) {
                        currentSlide += 1
                    }
                }
            } label: {
                Label(
                    currentSlide == card.slides.count - 1 ? "다 읽었어요" : "다음 카드",
                    systemImage: currentSlide == card.slides.count - 1 ? "checkmark" : "arrow.right"
                )
                .font(.subheadline.weight(.bold))
                .frame(maxWidth: .infinity)
                .frame(height: 38)
            }
            .buttonStyle(.borderedProminent)
            .tint(.indigo)
            .frame(maxWidth: 300)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
        }
        .padding(.top, 6)
        .background(AppPalette.background)
        .onChange(of: currentSlide) { _, newValue in
            if newValue == card.slides.count - 1 {
                store.markRead(card)
            }
        }
        .navigationTitle("카드 뉴스")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    withAnimation(.snappy) {
                        store.toggleLike(for: card)
                    }
                } label: {
                    Image(systemName: store.likedIDs.contains(card.id) ? "heart.fill" : "heart")
                        .foregroundStyle(store.likedIDs.contains(card.id) ? .pink : .primary)
                }
                .accessibilityLabel(store.likedIDs.contains(card.id) ? "좋아요 취소" : "좋아요")
            }
        }
    }
}

private struct StoryPage: View {
    let slide: StorySlide
    let index: Int

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(LinearGradient(colors: slide.theme.colors, startPoint: .topLeading, endPoint: .bottomTrailing))

            Circle()
                .fill(.white.opacity(0.20))
                .frame(width: 260)
                .blur(radius: 1)
                .offset(x: 120, y: -195)

            Image(systemName: slide.theme.symbol)
                .font(.system(size: 132, weight: .thin))
                .foregroundStyle(.white.opacity(0.26))
                .offset(x: 105, y: -36)

            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Text(slide.eyebrow)
                        .font(.caption2.weight(.heavy))
                        .tracking(1.4)
                    Spacer()
                    Text(String(format: "%02d", index))
                        .font(.caption.weight(.bold))
                }
                .foregroundStyle(.white.opacity(0.9))

                Spacer()

                Text(slide.title)
                    .font(.system(size: 31, weight: .heavy, design: .rounded))
                    .lineSpacing(3)
                    .multilineTextAlignment(.leading)

                Text(slide.body)
                    .font(.subheadline)
                    .lineSpacing(4)
                    .foregroundStyle(.white.opacity(0.86))
            }
            .padding(28)
            .foregroundStyle(.white)

            Text("A BRIGHTER TOMORROW\nWITH AI")
                .font(.caption2.weight(.bold))
                .tracking(1.6)
                .foregroundStyle(.white.opacity(0.66))
                .multilineTextAlignment(.leading)
                .padding(28)
                .padding(.bottom, 2)
        }
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .shadow(color: Color.indigo.opacity(0.16), radius: 18, y: 8)
        .padding(.vertical, 8)
    }
}
