import SwiftUI

struct RootTabView: View {
    @State private var store = StoryStore()
    @State private var login = AuthSession.shared
    @State private var pushed: PushDestination?
    @State private var showAdminAlerts = false
    private struct PushDestination: Identifiable { let id: Int64 }

    var body: some View {
        @Bindable var store = store

        VStack(spacing: 0) {
            TabView(selection: Binding(get: { store.selectedTab }, set: { tab in
                if tab == .profile && !login.requireLogin("내 프로필과 알림 설정은 로그인 후 이용할 수 있어요.") { return }
                if tab == .admin && !login.isAdmin { return }
                store.selectedTab = tab
            })) {
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

                Group { if login.user != nil { ProfileView() } else { Color.clear } }
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
        .modifier(GuestAccessPresentation(enabled: pushed == nil))
        .onChange(of: AuthSession.shared.isAdmin) { _, isAdmin in
            if !isAdmin { showAdminAlerts = false }
            if !isAdmin && store.selectedTab == .admin { store.selectedTab = .profile }
        }
        .task(id: PushNotifications.shared.pendingAdminAlertID) {
            guard PushNotifications.shared.pendingAdminAlertID != nil else { return }
            defer { PushNotifications.shared.pendingAdminAlertID = nil }
            guard login.isAdmin else { return }
            store.selectedTab = .admin
            pushed = nil
            showAdminAlerts = true
        }
        .sheet(isPresented: $showAdminAlerts) {
            NavigationStack {
                AdminAlertsView()
                    .toolbar { ToolbarItem(placement: .topBarLeading) { Button("닫기") { showAdminAlerts = false } } }
            }
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
            }.environment(store).modifier(GuestAccessPresentation(enabled: true))
        }
        .alert("요청을 완료하지 못했습니다", isPresented: Binding(get: { store.actionError != nil }, set: { if !$0 { store.actionError = nil } })) {
            Button("확인") { store.actionError = nil }
        } message: { Text(store.actionError ?? "") }
    }
}

// Present from the visible hierarchy, including a card opened from an old notification.
private struct GuestAccessPresentation: ViewModifier {
    let enabled: Bool
    @State private var login = AuthSession.shared
    func body(content: Content) -> some View {
        content
            .sheet(isPresented: Binding(get: { enabled && login.showLogin }, set: { if enabled { login.showLogin = $0 } })) {
                NavigationStack {
                    LoginView { login.showLogin = false }
                        .toolbar { ToolbarItem(placement: .topBarLeading) {
                            Button("닫기") { login.showLogin = false; login.errorMessage = nil }.disabled(login.busy)
                        } }
                }.interactiveDismissDisabled(login.busy)
            }
            .alert("로그인이 필요한 기능이에요", isPresented: Binding(get: { enabled && login.loginReason != nil }, set: { if enabled && !$0 { login.loginReason = nil } })) {
                Button("계속 둘러보기", role: .cancel) { login.loginReason = nil }
                Button("로그인") { login.loginReason = nil; login.showLogin = true }
            } message: { Text(login.loginReason ?? "") }
    }
}
