import SwiftUI

struct RootTabView: View {
    @State private var store = StoryStore()
    @State private var pushed: PushDestination?
    private struct PushDestination: Identifiable { let id: Int64 }

    var body: some View {
        @Bindable var store = store

        VStack(spacing: 0) {
            #if DEBUG
            if AuthSession.shared.isPreview {
                HStack {
                    Text("개발 미리보기 · 변경은 임시 저장됩니다").font(.caption)
                    Spacer()
                    Button("종료") { AuthSession.shared.endPreview() }.font(.caption.bold())
                }.padding(10).background(AppPalette.surface)
            }
            #endif

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
                if AuthSession.shared.isAdmin {
                    AdminHomeView().tabItem { Label("관리자", systemImage: "checkmark.shield") }.tag(AppTab.admin).accessibilityIdentifier("tab.admin")
                }
            }
        }
        .tint(AppPalette.accent)
        .environment(store)
        .onChange(of: AuthSession.shared.isAdmin) { _, isAdmin in
            if !isAdmin && store.selectedTab == .admin { store.selectedTab = .profile }
        }
        .task(id: PushNotifications.shared.pendingCardID) {
            if let id = PushNotifications.shared.pendingCardID {
                pushed = PushDestination(id: id)
                PushNotifications.shared.pendingCardID = nil
            }
        }
        .sheet(item: $pushed) { destination in
            NavigationStack {
                CardNewsDetailView(cardID: destination.id)
                    .toolbar { ToolbarItem(placement: .topBarLeading) { Button("닫기") { pushed = nil } } }
            }.environment(store)
        }
        .alert("요청을 완료하지 못했습니다", isPresented: Binding(get: { store.actionError != nil }, set: { if !$0 { store.actionError = nil } })) {
            Button("확인") { store.actionError = nil }
        } message: { Text(store.actionError ?? "") }
    }
}
