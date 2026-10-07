import SwiftUI

struct OnboardingView: View {
    let onFinished: () -> Void
    @State private var page = 0

    private let pages = OnboardingPage.samples

    var body: some View {
        ZStack {
            AppPalette.background
                .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button("건너뛰기", action: onFinished)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 24)
                .frame(height: 56)

                TabView(selection: $page) {
                    ForEach(Array(pages.enumerated()), id: \.element.id) { index, item in
                        OnboardingPageView(item: item)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                VStack(spacing: 20) {
                    HStack(spacing: 7) {
                        ForEach(pages.indices, id: \.self) { index in
                            Capsule()
                                .fill(index == page ? Color.indigo : Color.indigo.opacity(0.16))
                                .frame(width: index == page ? 22 : 7, height: 7)
                        }
                    }

                    Button {
                        if page == pages.count - 1 {
                            onFinished()
                        } else {
                            withAnimation(.snappy) {
                                page += 1
                            }
                        }
                    } label: {
                        Label(
                            page == pages.count - 1 ? "AI Know 시작하기" : "다음",
                            systemImage: page == pages.count - 1 ? "sparkles" : "arrow.right"
                        )
                        .font(.body.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.indigo)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 22)
            }
        }
    }
}

private struct OnboardingPageView: View {
    let item: OnboardingPage

    var body: some View {
        VStack(spacing: 30) {
            Spacer()

            ZStack {
                Circle()
                    .fill(item.tint.opacity(0.13))
                    .frame(width: 234, height: 234)
                Image(systemName: item.symbol)
                    .font(.system(size: 78, weight: .regular))
                    .foregroundStyle(item.tint)
            }

            VStack(spacing: 12) {
                Text(item.title)
                    .font(.system(size: 27, weight: .bold, design: .rounded))
                    .multilineTextAlignment(.center)
                Text(item.description)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            .padding(.horizontal, 32)

            Spacer()
        }
    }
}

private struct OnboardingPage: Identifiable {
    let id: Int
    let symbol: String
    let tint: Color
    let title: String
    let description: String

    static let samples: [OnboardingPage] = [
        OnboardingPage(
            id: 0,
            symbol: "rectangle.stack.fill",
            tint: .indigo,
            title: "하루 한 장의\nAI 인사이트",
            description: "복잡한 AI 소식을 카드 한 장으로\n쉽고 빠르게 만나보세요."
        ),
        OnboardingPage(
            id: 1,
            symbol: "hand.thumbsup.fill",
            tint: .pink,
            title: "마음에 드는 이야기는\n좋아요로 모아보세요",
            description: "좋아요한 카드와 관심 있는 카테고리를\n아카이브에서 다시 볼 수 있어요."
        ),
        OnboardingPage(
            id: 2,
            symbol: "bell.badge.fill",
            tint: .purple,
            title: "매일 새로운\nAI 이야기를 알려드려요",
            description: "알림 설정으로 오늘의 카드가 도착했을 때\n놓치지 않고 확인해 보세요."
        )
    ]
}
