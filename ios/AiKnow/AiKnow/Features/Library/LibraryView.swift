import SwiftUI

struct LibraryView: View {
    @Environment(StoryStore.self) private var store
    @State private var selectedCategory = "전체"
    @State private var searchText = ""
    @State private var likesOnly = false

    private let filters = ["전체"] + StoryCategory.allCases.map(\.rawValue)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    AppHeader(title: "아카이브")
                    searchField
                    filterControls

                    LazyVStack(spacing: 10) {
                        if filteredStories.isEmpty {
                            ContentUnavailableView(
                                likesOnly ? "좋아요한 카드가 없어요" : "검색 결과가 없어요",
                                systemImage: likesOnly ? "heart" : "magnifyingglass",
                                description: Text(likesOnly ? "마음에 드는 카드의 하트를 눌러보세요." : "다른 검색어 또는 카테고리를 선택해 보세요.")
                            )
                            .frame(maxWidth: .infinity)
                            .padding(.top, 36)
                        } else {
                            ForEach(filteredStories) { story in
                                ArchiveRow(story: story)
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
            }
            .background(AppPalette.background)
            .navigationDestination(for: NewsCard.self) { story in
                CardNewsDetailView(card: story)
            }
        }
    }

    private var filterControls: some View {
        HStack(spacing: 10) {
            Button {
                withAnimation(.snappy) {
                    likesOnly.toggle()
                }
            } label: {
                Label("좋아요", systemImage: likesOnly ? "heart.fill" : "heart")
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 13)
                    .padding(.vertical, 10)
                    .foregroundStyle(likesOnly ? .white : Color.pink)
                    .background(likesOnly ? Color.pink : Color.pink.opacity(0.10), in: Capsule())
            }
            .accessibilityLabel(likesOnly ? "좋아요 필터 해제" : "좋아요 카드만 보기")

            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(filters, id: \.self) { filter in
                        Button(filter) {
                            withAnimation(.snappy) {
                                selectedCategory = filter
                            }
                        }
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 15)
                        .padding(.vertical, 10)
                        .background(selectedCategory == filter ? Color.indigo : Color.indigo.opacity(0.08), in: Capsule())
                        .foregroundStyle(selectedCategory == filter ? .white : Color.indigo)
                    }
                }
            }
            .scrollIndicators(.hidden)
        }
    }

    private var searchField: some View {
        HStack(spacing: 9) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
            TextField("궁금한 AI 이야기를 검색해 보세요", text: $searchText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
        .font(.subheadline)
        .padding(.horizontal, 13)
        .frame(height: 44)
        .background(.white.opacity(0.88), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var filteredStories: [NewsCard] {
        NewsCard.samples.filter { story in
            let matchesCategory = selectedCategory == "전체" || story.category.rawValue == selectedCategory
            let matchesLike = !likesOnly || store.likedIDs.contains(story.id)
            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            let matchesQuery = query.isEmpty || (story.title + story.summary + story.category.rawValue).localizedCaseInsensitiveContains(query)
            return matchesCategory && matchesLike && matchesQuery
        }
    }
}

private struct ArchiveRow: View {
    @Environment(StoryStore.self) private var store
    let story: NewsCard

    var body: some View {
        HStack(spacing: 13) {
            NavigationLink(value: story) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(LinearGradient(colors: [story.category.tint.opacity(0.82), story.category.tint.opacity(0.42)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    Image(systemName: story.symbol)
                        .font(.title2)
                        .foregroundStyle(.white.opacity(0.94))
                }
                .frame(width: 78, height: 78)
            }
            .buttonStyle(.plain)

            NavigationLink(value: story) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(story.date)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Text(story.title)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(AppPalette.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    HStack(spacing: 5) {
                        Text(story.category.rawValue)
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(story.category.tint)
                        if store.readIDs.contains(story.id) {
                            Text("읽음")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)

            Button {
                withAnimation(.snappy) {
                    store.toggleLike(for: story)
                }
            } label: {
                Image(systemName: store.likedIDs.contains(story.id) ? "heart.fill" : "heart")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(store.likedIDs.contains(story.id) ? .pink : .secondary)
                    .frame(width: 38, height: 52)
            }
            .accessibilityLabel(store.likedIDs.contains(story.id) ? "좋아요 취소" : "좋아요")
        }
        .padding(10)
        .background(.white.opacity(0.92), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}
