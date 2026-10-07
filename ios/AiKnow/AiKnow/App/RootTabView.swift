import SwiftUI

struct RootTabView: View {
    @State private var store = StoryStore()

    var body: some View {
        @Bindable var store = store

        TabView(selection: $store.selectedTab) {
            HomeView()
                .tabItem {
                    Label("오늘", systemImage: "sparkles")
                }
                .tag(AppTab.today)

            LibraryView()
                .tabItem {
                    Label("아카이브", systemImage: "books.vertical.fill")
                }
                .tag(AppTab.archive)

            ProfileView()
                .tabItem {
                    Label("마이", systemImage: "person.crop.circle")
                }
                .tag(AppTab.profile)
        }
        .tint(Color(red: 0.36, green: 0.33, blue: 0.95))
        .environment(store)
    }
}
