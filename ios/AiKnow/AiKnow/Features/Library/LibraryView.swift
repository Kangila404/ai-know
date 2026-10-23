import SwiftUI

struct LibraryView: View {
    @Environment(StoryStore.self) private var store
    @FocusState private var searchFocused: Bool
    private var filterKey: String { "\(store.query)|\(store.categoryID ?? -1)|\(store.likesOnly)" }
    var body: some View {
        @Bindable var store = store
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    AppHeader(title: "아카이브")
                    TextField("궁금한 AI 이야기를 검색해 보세요", text: $store.query)
                        .focused($searchFocused).submitLabel(.search).onSubmit { searchFocused = false }
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .padding(13).background(AppPalette.surface, in: RoundedRectangle(cornerRadius: 14))
                        .onChange(of: store.query) { _, value in if value.count > 200 { store.query = String(value.prefix(200)) } }
                    HStack {
                        Button { store.likesOnly.toggle() } label: {
                            Label("좋아요", systemImage: store.likesOnly ? "heart.fill" : "heart")
                        }.tint(.pink).buttonStyle(.bordered)
                        ScrollView(.horizontal) {
                            HStack {
                                categoryButton("전체", id: nil)
                                ForEach(store.categories) { category in categoryButton(category.name, id: category.id) }
                            }
                        }.scrollIndicators(.hidden)
                    }
                    if let error = store.categoriesError { RequestFailureView(message: error) { Task { await store.loadCategories() } } }
                    LazyVStack(spacing: 12) {
                        ForEach(store.cards) { original in
                            let card = store.current(original)
                            HStack(spacing: 12) {
                                NavigationLink { CardNewsDetailView(card: card) } label: {
                                    HStack(spacing: 12) {
                                        RemoteImage(raw: card.titleImgUrl).frame(width: 80, height: 90).clipShape(RoundedRectangle(cornerRadius: 12))
                                        VStack(alignment: .leading, spacing: 6) {
                                            Text(card.date).font(.caption2).foregroundStyle(.secondary)
                                            Text(card.title).font(.subheadline.bold()).foregroundStyle(AppPalette.ink).lineLimit(3)
                                            Text(card.categoryLabel).font(.caption2).foregroundStyle(AppPalette.accent)
                                            if card.read { Text("읽음").font(.caption2).foregroundStyle(.secondary) }
                                        }.frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                }.buttonStyle(.plain).accessibilityIdentifier("archive.card.\(card.id)")
                                Button { Task { await store.toggleLike(card) } } label: {
                                    Image(systemName: card.liked ? "heart.fill" : "heart").foregroundStyle(card.liked ? .pink : .secondary)
                                }.disabled(store.busyIDs.contains(card.id)).accessibilityLabel(card.liked ? "좋아요 취소" : "좋아요")
                            }.padding(12).background(AppPalette.surface, in: RoundedRectangle(cornerRadius: 20))
                        }
                        if store.archiveLoading { ProgressView().padding() }
                        else if let error = store.archiveError {
                            RequestFailureView(message: error) { Task { await store.loadArchive() } }
                        } else if store.cards.isEmpty {
                            ContentUnavailableView("카드뉴스가 없어요", systemImage: "newspaper", description: Text("다른 검색어나 필터를 선택해 보세요."))
                        } else if store.hasMore {
                            Button("더 보기") { Task { await store.loadArchive() } }.buttonStyle(.bordered)
                        }
                    }
                }.padding(20)
            }.background(AppPalette.background).scrollDismissesKeyboard(.interactively)
                .task { await store.loadCategories() }
                .task(id: filterKey) {
                    do { try await Task.sleep(for: .milliseconds(300)); await store.loadArchive(reset: true) }
                    catch { }
                }
                .refreshable { await store.loadCategories(); await store.loadArchive(reset: true) }
        }
    }
    private func categoryButton(_ name: String, id: Int64?) -> some View {
        Button(name) { store.categoryID = id }.buttonStyle(.bordered)
            .tint(store.categoryID == id ? .indigo : .gray)
    }
}
