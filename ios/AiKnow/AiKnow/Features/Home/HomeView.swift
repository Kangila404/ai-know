import SwiftUI

struct HomeView: View {
    @Environment(StoryStore.self) private var store
    @State private var showNotificationSheet = false
    @State private var navigationPath = NavigationPath()
    private let story = NewsCard.today

    var body: some View {
        NavigationStack(path: $navigationPath) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    AppHeader(
                        title: "오늘의 AI 카드뉴스",
                        subtitle: story.date,
                        trailingIcon: "bell",
                        trailingAction: { showNotificationSheet = true }
                    )
                    featuredCard
                    insightCard
                    keyPoints
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 28)
            }
            .background(AppPalette.background)
            .navigationDestination(for: NewsCard.self) { card in
                CardNewsDetailView(card: card)
            }
            .sheet(isPresented: $showNotificationSheet) {
                NotificationPreviewSheet()
                    .presentationDetents([.height(310)])
                    .presentationDragIndicator(.visible)
            }
        }
        .onChange(of: store.selectedTab) { _, selectedTab in
            if selectedTab == .today {
                navigationPath = NavigationPath()
            }
        }
    }

    private var featuredCard: some View {
        NavigationLink(value: story) {
            ZStack(alignment: .bottomLeading) {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [Color(red: 0.07, green: 0.08, blue: 0.23), Color(red: 0.18, green: 0.21, blue: 0.53)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                Circle()
                    .fill(Color.indigo.opacity(0.75))
                    .frame(width: 220)
                    .blur(radius: 2)
                    .offset(x: 145, y: -145)

                Image(systemName: "person.2.fill")
                    .font(.system(size: 125, weight: .thin))
                    .foregroundStyle(.white.opacity(0.12))
                    .offset(x: 104, y: -48)

                VStack(alignment: .leading, spacing: 14) {
                    Text(story.category.rawValue)
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 11)
                        .padding(.vertical, 7)
                        .background(.white.opacity(0.16), in: Capsule())

                    Spacer()

                    Text(story.title)
                        .font(.title2.weight(.bold))
                        .lineSpacing(2)
                        .multilineTextAlignment(.leading)

                    Text(story.summary)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.78))
                        .lineLimit(2)

                    HStack {
                        Label("카드 뉴스 보기", systemImage: "arrow.right")
                            .font(.subheadline.weight(.bold))
                    }
                    .foregroundStyle(.indigo)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(.white, in: Capsule())
                    .shadow(color: .black.opacity(0.14), radius: 8, y: 4)
                    .padding(.top, 2)
                }
                .padding(22)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 330)
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var insightCard: some View {
        HStack(spacing: 14) {
            Image(systemName: "brain.head.profile")
                .font(.title2)
                .foregroundStyle(.indigo)
                .frame(width: 52, height: 52)
                .background(.white.opacity(0.78), in: RoundedRectangle(cornerRadius: 17, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text("AI와 사람이 함께 만드는\n더 나은 내일")
                    .font(.subheadline.weight(.bold))
                Text("오늘의 카드뉴스에서 새로운 가능성을 만나보세요.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(15)
        .background(
            LinearGradient(colors: [Color.indigo.opacity(0.13), Color.blue.opacity(0.10)], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 21, style: .continuous)
        )
    }

    private var keyPoints: some View {
        VStack(alignment: .leading, spacing: 11) {
            Text("오늘의 핵심 포인트")
                .font(.headline.weight(.bold))

            ForEach(Array([
                "AI는 도구를 넘어 협업 파트너로 진화하고 있어요.",
                "인간의 창의성과 AI의 생산성이 시너지를 만듭니다.",
                "좋은 질문에서 더 나은 결과가 시작됩니다."
            ].enumerated()), id: \.offset) { index, item in
                HStack(spacing: 11) {
                    Text(String(format: "%02d", index + 1))
                        .font(.caption.weight(.heavy))
                        .foregroundStyle(.indigo)
                        .frame(width: 40, height: 36)
                        .background(Color.indigo.opacity(0.10), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                    Text(item)
                        .font(.subheadline)
                        .foregroundStyle(AppPalette.ink)
                    Spacer(minLength: 0)
                }
                .padding(5)
                .background(.white.opacity(0.90), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }
    }
}

enum AppPalette {
    static let background = Color(red: 0.965, green: 0.973, blue: 1.0)
    static let ink = Color(red: 0.07, green: 0.10, blue: 0.21)
}
