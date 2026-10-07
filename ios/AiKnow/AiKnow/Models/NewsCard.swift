import Foundation
import Observation
import SwiftUI

struct NewsCard: Identifiable, Hashable {
    let id: String
    let date: String
    let category: StoryCategory
    let title: String
    let summary: String
    let symbol: String
    let readTime: String
    let slides: [StorySlide]

    static let samples: [NewsCard] = [
        NewsCard(
            id: "collaboration",
            date: "2026년 10월 7일",
            category: .generativeAI,
            title: "사람과 AI의 협업, 경쟁이 아닌 새로운 가능성",
            summary: "AI는 대체가 아니라, 함께 더 멀리 가는 파트너입니다.",
            symbol: "person.2.fill",
            readTime: "3분 읽기",
            slides: [
                StorySlide(id: "collaboration-1", eyebrow: "TODAY'S AI ISSUE", title: "사람과 AI의 협업,\n경쟁이 아닌\n새로운 가능성", body: "우리의 일상에 들어온 AI.\n함께 더 멀리 나아가는 방법을\n오늘의 카드뉴스에서 만나보세요.", caption: "하루 한 장, 세상을 보는 새로운 시선.", theme: .violet),
                StorySlide(id: "collaboration-2", eyebrow: "01", title: "AI는 대체가 아닌,\n함께하는\n파트너입니다.", body: "AI는 인간의 능력을 대신하기보다\n더 큰 가능성을 함께 만들어가는\n협력자가 될 수 있습니다.", caption: "함께할 때, 더 멀리 갈 수 있습니다.", theme: .sky),
                StorySlide(id: "collaboration-3", eyebrow: "02", title: "인간의 창의성에,\nAI의 생산성을\n더하다.", body: "아이디어는 사람이, 반복 작업은 AI가.\n서로 잘하는 일에 집중할 때\n새로운 가능성이 열립니다.", caption: "더 중요한 생각에 시간을 써보세요.", theme: .cream),
                StorySlide(id: "collaboration-4", eyebrow: "03", title: "함께할 때\n더 멀리 갑니다.", body: "AI를 경쟁자가 아닌 협업자로 바라보기.\n내일은 작은 일 하나부터\nAI와 함께 시작해 보는 건 어떨까요?", caption: "오늘의 인사이트를 모두 읽었어요.", theme: .night)
            ]
        ),
        NewsCard(
            id: "agent",
            date: "2026년 10월 6일",
            category: .industry,
            title: "AI 에이전트가 만드는 일의 새로운 방식",
            summary: "질문에 답하는 AI에서, 일을 함께하는 AI로.",
            symbol: "cpu.fill",
            readTime: "4분 읽기",
            slides: [
                StorySlide(id: "agent-1", eyebrow: "AI AGENT", title: "AI 에이전트가\n만드는 일의\n새로운 방식", body: "질문에 답하는 것을 넘어,\n일을 함께하는 AI를 생각해 봅니다.", caption: "오늘의 키워드, AI 에이전트.", theme: .night),
                StorySlide(id: "agent-2", eyebrow: "01", title: "대화 다음에는,\n작은 실행.", body: "해야 할 일을 작은 단계로 나누고\n필요한 도구를 연결하는\n작업 방식을 떠올려 보세요.", caption: "일의 흐름을 설계하는 새로운 시선.", theme: .sky),
                StorySlide(id: "agent-3", eyebrow: "02", title: "자동화에도\n확인은 필요해요.", body: "중요한 결정과 개인정보가 포함된 일은\n사람이 확인하고 승인하는 과정을\n함께 설계해야 합니다.", caption: "편리함만큼 중요한, 신뢰.", theme: .cream)
            ]
        ),
        NewsCard(
            id: "startup",
            date: "2026년 10월 5일",
            category: .industry,
            title: "지금 주목해야 할 AI 스타트업 5선",
            summary: "새로운 아이디어가 만들어가는 AI 산업의 가능성.",
            symbol: "chart.line.uptrend.xyaxis",
            readTime: "3분 읽기",
            slides: [
                StorySlide(id: "startup-1", eyebrow: "STARTUP", title: "다음 가능성을\n만드는 사람들.", body: "AI를 활용한 새로운 서비스는\n어떤 문제에서 출발할까요?", caption: "작은 문제에서 시작되는 새로운 기회.", theme: .violet),
                StorySlide(id: "startup-2", eyebrow: "01", title: "좋은 서비스는\n불편함을 줄입니다.", body: "화려한 기술 설명보다\n사용자의 하루를 어떻게 바꾸는지\n질문해 보세요.", caption: "기술보다 먼저, 해결하고 싶은 문제.", theme: .cream)
            ]
        ),
        NewsCard(
            id: "education",
            date: "2026년 10월 4일",
            category: .society,
            title: "AI가 바꾸는 교육의 미래",
            summary: "나의 속도에 맞춘 학습을 상상하다.",
            symbol: "graduationcap.fill",
            readTime: "3분 읽기",
            slides: [
                StorySlide(id: "education-1", eyebrow: "EDUCATION", title: "배우는 속도는\n모두 다르니까.", body: "이해하지 못한 부분을 다시 묻고\n나에게 맞는 예시를 찾아보는\n학습 방식을 생각해 봅니다.", caption: "나에게 맞는 배움의 속도.", theme: .cream),
                StorySlide(id: "education-2", eyebrow: "01", title: "답보다 중요한 건,\n이해하는 과정.", body: "왜 그런지 설명해 달라고 질문하고\n다른 자료와 함께 확인해 보세요.", caption: "좋은 질문에서 시작되는 배움.", theme: .sky)
            ]
        ),
        NewsCard(
            id: "ethics",
            date: "2026년 10월 3일",
            category: .technology,
            title: "더 똑똑해진 AI, 더 중요한 윤리적 질문",
            summary: "기술과 함께 생각해야 하는 신뢰와 책임.",
            symbol: "checkmark.shield.fill",
            readTime: "4분 읽기",
            slides: [
                StorySlide(id: "ethics-1", eyebrow: "AI ETHICS", title: "더 똑똑한 AI,\n더 중요한 질문.", body: "편리함이 커질수록\n우리가 지켜야 할 기준도\n함께 생각해야 합니다.", caption: "기술에 꼭 필요한, 사람의 관점.", theme: .night),
                StorySlide(id: "ethics-2", eyebrow: "01", title: "책임 있게\n사용하는 연습.", body: "민감한 정보를 조심하고,\nAI가 만든 결과물임을 알리고,\n최종 판단은 직접 내려보세요.", caption: "오늘의 인사이트를 모두 읽었어요.", theme: .cream)
            ]
        )
    ]

    static var today: NewsCard { samples[0] }
}

enum StoryCategory: String, CaseIterable, Hashable, Identifiable {
    case generativeAI = "생성형 AI"
    case industry = "산업"
    case technology = "기술"
    case society = "사회"

    var id: String { rawValue }

    var tint: Color {
        switch self {
        case .generativeAI: .indigo
        case .industry: .blue
        case .technology: .purple
        case .society: .teal
        }
    }
}

struct StorySlide: Identifiable, Hashable {
    let id: String
    let eyebrow: String
    let title: String
    let body: String
    let caption: String
    let theme: StoryTheme
}

enum StoryTheme: String, Hashable {
    case violet
    case sky
    case cream
    case night

    var colors: [Color] {
        switch self {
        case .violet: [.indigo, Color(red: 0.60, green: 0.45, blue: 0.96)]
        case .sky: [Color(red: 0.36, green: 0.57, blue: 0.93), Color(red: 0.72, green: 0.86, blue: 0.98)]
        case .cream: [Color(red: 0.99, green: 0.73, blue: 0.50), Color(red: 0.94, green: 0.88, blue: 0.72)]
        case .night: [Color(red: 0.08, green: 0.10, blue: 0.29), Color(red: 0.24, green: 0.27, blue: 0.59)]
        }
    }

    var symbol: String {
        switch self {
        case .violet: "sparkles"
        case .sky: "person.2.fill"
        case .cream: "lightbulb.fill"
        case .night: "moon.stars.fill"
        }
    }
}

enum AppTab: Hashable {
    case today
    case archive
    case profile
}

@Observable
final class StoryStore {
    var selectedTab: AppTab = .today
    var likedIDs: Set<String> = []
    var readIDs: Set<String> = []
    var dailyNotificationEnabled = true
    var updateNotificationEnabled = true
    var noticeNotificationEnabled = true
    var marketingNotificationEnabled = false

    func toggleLike(for story: NewsCard) {
        if likedIDs.contains(story.id) {
            likedIDs.remove(story.id)
        } else {
            likedIDs.insert(story.id)
        }
    }

    func markRead(_ story: NewsCard) {
        readIDs.insert(story.id)
    }
}
