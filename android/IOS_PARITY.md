# iOS 기준 대조

2026-10-25 원격 `origin` fetch 후 대조했습니다.

- `origin/main`: `f3fe856` (2026-10-25 21:24 KST), TestFlight build 7 준비 커밋 `65fcbdb` 포함.
- `origin/develop`: `f1a5186` (2026-10-25 21:31 KST). main 대비 `ios/README.md` 릴리스 기록만 변경됐고 iOS 앱 소스/API 차이는 없습니다.
- Android 최초 구현 `36c3b22`는 위 main의 iOS 파일과 동일한 원본을 포함했으나 일부 기능 반영이 누락돼 이번 대조에서 보완했습니다. iOS나 운영 서버 소스는 수정하지 않습니다.

| 기능 | 대조한 iOS 소스 | Android 반영 |
|---|---|---|
| 오늘/아카이브/마이/관리자 | `App/RootTabView.swift` | Material 3 NavigationBar, 실제 ADMIN 응답과 복귀 시 권한 재조회 |
| 로고·로그인 안내·온보딩 | `Components/BrandLogo.swift`, `Features/Auth`, `Features/Onboarding` | 같은 K 로고와 완료 API. 10월 26일 요청에 따라 Android는 대화형 소개 → 닉네임 → 알림·시간 설정으로 확장 |
| 오늘의 카드·미게시·핵심 포인트 | `Features/Home/HomeView.swift` | 동일 API, 404 미게시 안내와 아카이브 이동 |
| 검색·카테고리·좋아요·읽음 | `Features/Library/LibraryView.swift` | 필터/페이지, 변경 후 목록 재조회 |
| 카드 높이·긴 본문 | `Features/Home/CardNewsDetailView.swift` | 본문 측정 후 남은 높이에 이미지 배치, 비율 유지, 긴 내용 세로 스크롤 |
| 이미지 확대·출처 | 같은 파일 | 핀치/드래그 확대, 원본 요청, 표지/슬라이드 출처 모음 시트 |
| 읽음 | 같은 파일 | 마지막 페이지 2초 머무른 후 PUT, 실패 시 명시적 재시도, 좋아요/읽음 목록 갱신 |
| 이미지 로더 | `Services/ImageLoader.swift` | API가 광고한 320/1280 preview, Commons 표준 크기, 서명 URL·SVG 보존, 원본 대체, 오류 구분/캐시 우회 재시도 |
| 닉네임·프로필 | `Features/Profile/ProfileView.swift` | 직접 탭하여 개별 PATCH, 저장 중 중복 입력/시트 닫기 차단 |
| 관리자 홈 | `Features/Admin/AdminViews.swift` | 콘텐츠 운영 / 서비스 관리 / 문제 해결. 독립 조회수 대시보드 제거 |
| 검수 | 같은 파일 | 최초 접수/원문 날짜 구분, 유형 추정 제거, 유형 선택 전 승인 차단, 승인된 카드 관리 이동 |
| 카드 관리 | 같은 파일 및 `AdminModels.swift` | 게시 중/발송 대기/숨김, 승인/게시/첫·최근 발송 시작, 미발송 안내, 카드 조회수 |
| 구버전 메타데이터 | `AdminModels.swift` | 미제공을 0으로 표시하지 않음. 기존 analytics에서 ID로 조회수 보완 |
| 콘텐츠 편집 | `AdminViews.swift` | 편집 펼치기, 제목/요약/핵심 포인트, 1~10 슬라이드 추가·삭제/레이아웃, 이미지 사용 해제/출처, 검증/사유/버전 |
| 계정 관리 | `AdminOperations.swift` | 검색·권한·정지 필터, 가입/최근 로그인, 사유/버전, 자기 계정 변경 금지 |
| 공지·프로필 관리·이력 | `AdminViews.swift` | 같은 CRUD API와 제한, 변경 전·후 JSON 열람 |
| 생성 복구 | 같은 파일 | 텍스트 JSON 및 사진 선택→PNG→multipart `recover-image`, 새 유료 요청 없음 |
| 푸시/세션 | `Services/AuthSession.swift`, `PushNotifications.swift` | 동일 백엔드 계약, Android 설치 ID/FCM, 앱 복귀·로그인·토큰 갱신 동기화, 로그아웃 해제 |

## 플랫폼 차이와 미검증 경계

- iOS SwiftUI 배치를 픽셀 단위 복제하지 않습니다. Android의 Material 3, Compose Pager/BottomSheet, Credential Manager, 시스템 사진 선택기를 사용합니다. 2026-10-26 사용자 요청에 따라 다음 카드/완료 버튼을 제거하고 스와이프와 TalkBack 페이지 동작을 사용하며, 테마는 휴대폰 설정만 따릅니다.
- Apple iOS 네이티브 로그인은 Android에 그대로 이식할 수 없습니다. Android Apple 로그인용 Services ID·웹 리다이렉트와 서버 연결은 미구현입니다. Google/Kakao/FCM도 발급 설정이 없어 실계정 인증·실기기 수신은 미검증입니다.
- Android 운영 API와 외부 이미지는 HTTPS를 사용합니다. 데모는 별도 flavor의 로컬 데이터만 사용하며 실계정·관리자 권한을 우회하지 않습니다.
- 날짜/승인/공개 정책은 서버가 결정합니다. Android에서 PENDING을 자동 승인하거나 READY를 임의로 공개하지 않습니다.
- 사진 선택 UI와 운영 복구 성공은 실제 관리자 연결 후 최종 확인이 필요합니다. 자동 검증은 격리된 multipart 요청 계약, 데모 읽기·검수 흐름, 이미지 URL 및 데이터 검증을 대상으로 합니다.

검증 결과와 실행 명령은 [README.md](README.md)의 검증 기록 및 테스트 소스를 참조하세요.

## 2026-10-26 사용성 개선

- 인트로는 사용자의 `D:/projects/ethica/android` 내 `JourneyExperience.kt`와 `ConversationMotion.kt`를 참고했습니다. 응답을 누르면 이전 문장이 축소·흐림 처리되며 위로 이동하고, 작은 화면/큰 글씨에서는 현재 문장을 우선합니다. 건너뛰기, 이전, 이어보기, 로그인에서 소개 다시 보기를 지원합니다.
- 신규 계정은 닉네임 PATCH 이후 알림 시간/동의를 PUT하고 마지막에 온보딩 완료 PATCH를 실행합니다. 설정 실패 시 완료로 건너뛰지 않습니다. 권한 거절 또는 알림 없이 시작을 선택하면 수신을 끄고 가입을 완료합니다. 기존 완료 계정에는 온보딩을 다시 강제하지 않습니다.
- 오늘 화면은 대표 이미지 카드, 번호와 펼침 동작이 있는 핵심 포인트, 아카이브 바로가기를 사용합니다. 서버 콘텐츠와 출처는 변경하지 않습니다.
