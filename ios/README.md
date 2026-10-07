# AiKnow iOS

SwiftUI로 만든 AiKnow의 정적 퍼블리싱 앱입니다. 제공된 HTML의 정보 구조(오늘의 카드, 카드 리더, 아카이브, 마이)를 참고하되 iOS의 NavigationStack, TabView, 페이지형 카드 리더, 검색, 시트 등 네이티브 패턴으로 재구성했습니다. 서버 API는 호출하지 않으며 화면 데이터는 앱 내부의 목 데이터입니다.

## 실행

1. Xcode에서 `ios/AiKnow/AiKnow.xcodeproj`를 엽니다.
2. 상단 실행 대상에서 iPhone Simulator를 고릅니다.
3. `⌘R`을 눌러 실행합니다.

## 구조

```text
AiKnow/
├── App/          # 앱 진입점과 탭 구성
├── Models/       # 화면용 목 데이터 모델
└── Features/     # 홈, 카드 리더, 아카이브, 마이 화면
```

서버 연동은 이후 `Features`에 API 클라이언트를 추가하는 방식으로 진행하면 됩니다.
