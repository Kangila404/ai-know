import SwiftUI

struct HomeView: View {
    @Environment(StoryStore.self) private var store
    @State private var showNotifications = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    AppHeader(title: "오늘의 AI 카드뉴스", trailingIcon: "bell", trailingAction: { showNotifications = true })
                    if store.todayLoading && store.today == nil {
                        ProgressView("오늘의 소식을 불러오는 중…").frame(maxWidth: .infinity).padding(40)
                    } else if let error = store.todayError {
                        RequestFailureView(message: error) { Task { await store.loadToday() } }
                    } else if let original = store.today {
                        let card = store.current(original)
                        NavigationLink { CardNewsDetailView(card: card) } label: {
                            VStack(alignment: .leading, spacing: 15) {
                                RemoteImage(raw: card.titleImgUrl).frame(maxHeight: 270).clipShape(RoundedRectangle(cornerRadius: 20))
                                if !card.categoryLabel.isEmpty { Text(card.categoryLabel).font(.caption.bold()).foregroundStyle(AppPalette.accent) }
                                Text(card.title).font(.title2.bold()).foregroundStyle(AppPalette.ink)
                                if let summary = card.summary { Text(summary).font(.subheadline).foregroundStyle(.secondary) }
                                Label("카드 뉴스 보기", systemImage: "arrow.right").font(.subheadline.bold())
                            }.padding(20).background(AppPalette.surface, in: RoundedRectangle(cornerRadius: 28))
                        }.buttonStyle(.plain)
                        Text(card.date).font(.caption).foregroundStyle(.secondary)
                        if !card.keyPoints.isEmpty {
                            Text("오늘의 핵심 포인트").font(.headline)
                            ForEach(Array(card.keyPoints.enumerated()), id: \.offset) { index, point in
                                HStack(alignment: .top, spacing: 12) {
                                    Text(String(format: "%02d", index + 1)).foregroundStyle(AppPalette.accent).bold()
                                    Text(point).font(.subheadline)
                                }.padding().frame(maxWidth: .infinity, alignment: .leading)
                                    .background(AppPalette.surface, in: RoundedRectangle(cornerRadius: 16))
                            }
                        }
                    } else {
                        ContentUnavailableView("오늘의 소식을 준비 중이에요", systemImage: "newspaper", description: Text("게시된 카드뉴스는 아카이브에서 볼 수 있어요."))
                        Button("아카이브 보기") { store.selectedTab = .archive }.frame(maxWidth: .infinity)
                    }
                }.padding(20)
            }.background(AppPalette.background)
                .refreshable { await store.loadToday() }
                .task { await store.loadToday() }
                .sheet(isPresented: $showNotifications) { NavigationStack { PushSettingsView() } }
        }
    }
}

enum AppPalette {
    static let background = Color(uiColor: .systemGroupedBackground)
    static let ink = Color.primary
    static let surface = Color(uiColor: .secondarySystemGroupedBackground)
    static let accent = Color(uiColor: .systemIndigo)
    static let separator = Color(uiColor: .separator)
}
