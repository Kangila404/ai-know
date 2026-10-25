# AiKnow iOS

SwiftUI 앱입니다. Google·Apple·Kakao 로그인은 ID token을 서버 세션으로 교환하며, 푸시 수신은 Firebase Messaging SDK로 연결되어 있습니다. 오늘의 카드·아카이브·상세·좋아요·읽음·공지·프로필·알림 설정은 서버 API를 사용합니다. 온보딩 안내 문구는 앱에 포함하며 완료 상태는 서버에 저장합니다.

**현재 Debug/Release 모두 실제 로그인을 사용합니다. 개발 미리보기와 가짜 ADMIN 계정은 제거했으며 서버가 ADMIN으로 응답하는 계정에만 관리자 탭이 표시됩니다.**

## 2026-10-25 TestFlight 업로드 — 1.0 (7)

21:25 KST에 App Store Connect가 `Upload succeeded`, `Uploaded package is processing`, `EXPORT SUCCEEDED`를 반환했습니다. 앱 코드 `65fcbdb`는 [배포 PR #32](https://github.com/Kangila404/ai-know/pull/32)의 main 병합 `f3fe856`과 동일한 트리입니다. 관리자 UI 정리·이미지 로더·프로필 직접 변경·알림 자동 저장 등 아래의 이전 미업로드 변경을 포함합니다.

App Store 배포 IPA의 코드 서명, Bundle ID/Team, 만료되지 않은 provisioning profile, Production APNs, Sign in with Apple, 디버깅 비활성화와 개발 미리보기 제거를 검증했습니다. API는 `https://aiknow.kro.kr`이며 Firebase 설정은 기존 운영 파일과 일치합니다. 업로드 시 빌드 번호 자동 변경을 끄고 7로 고정했습니다. Apple 처리 완료·테스트 그룹 배정·새 빌드의 실기기 로그인/푸시는 아직 확인하지 않았습니다.

서버 PR #30·#31을 develop에 병합하고 배포 PR #32를 main에 반영했습니다. main의 Server CI 후 [Server release EC2](https://github.com/Kangila404/ai-know/actions/runs/40193126506)가 `f3fe856` 배포에 성공했습니다. 운영 HTTPS에서 헬스 체크·로그인 제공자 목록·기본 프로필 PNG 5종·320/1280px JPEG 축소본의 9개 응답을 검증했습니다. 검수 접수 시각·카드 게시/발송 이력·조회수 응답 보완도 이 배포에 포함됩니다. 배포 후 실제 관리자 계정으로 접수 시각 화면까지 다시 로그인해 확인한 것은 아닙니다.

확인한 생성 이미지 원본 1,426,073바이트에 대해 320px는 29,194바이트, 1280px는 150,106바이트였습니다. 이는 해당 이미지의 전송 크기이며 모든 이미지의 성능 보장은 아닙니다.

아카이브·검증 IPA·서명 검사·업로드 로그는 `.local/testflight/2026-10-25-build-7/`에만 보관합니다. Secret이나 환경 파일은 커밋하지 않습니다.

## 2026-10-25 관리자 작업 흐름·외부 이미지

관리자 홈을 콘텐츠 운영 / 서비스 관리 / 문제 해결로 묶었습니다. ‘콘텐츠 검수’는 뉴스·AI 이론 공용 접수함이며, 목록에 제목과 최초 접수 시각을 표시합니다. 승인 전에 콘텐츠 유형을 직접 선택해야 합니다. 뉴스는 발송 대기 후 첫 유효 발송 시작 시 게시되고(다음 날 발송 보장 아님), AI 이론은 승인 즉시 아카이브에 게시되며 뉴스가 없는 날 대체 발송하는 기존 정책을 유지합니다.

‘카드뉴스 관리’는 게시 중 / 발송 대기 / 숨김을 나눕니다. 별도 조회수 대시보드를 없애고 카드 목록·상세에 조회수를 표시합니다. 상세에서 승인·게시·첫/최근 발송 시작을 구분하며 긴 수정 양식은 ‘편집’으로 펼칩니다. 실제 권한은 기존 ADMIN 응답을 따릅니다.

검수 `title`/`createdAt`, 카드 `approvedAt`/`firstUsedAt`/`lastUsedAt`/`hasStartedDelivery`/`totalViews`/`uniqueViewers`는 서버 응답 보완이 필요합니다. 이전 배포 서버에서는 접수 시각을 추정하지 않고 ‘미제공’으로 표시하며 조회수는 기존 analytics API에서 ID로 찾아 보완합니다. 서버 변경은 별도 PR이며 이 앱 변경만으로 서버가 배포되지는 않습니다.

외부 Wikimedia Commons PNG/JPEG/WebP 원본은 [표준 썸네일 크기](https://www.mediawiki.org/wiki/Common_thumbnail_sizes)를 사용합니다. 이미 캐시된 원본은 재사용하며 썸네일이 없거나 이미지로 해석되지 않으면 원본으로 돌아갑니다. 다른 도메인·서명 쿼리·SVG URL은 임의로 변환하지 않고 확대는 원본을 유지합니다. 404 같은 실패 응답이나 HTML 응답이 캐시되면 다음 재시도는 캐시를 건너뛰고 재요청합니다. 관리자 이미지 영역은 파일 없음·접근 거부·형식 오류를 구분해 표시하며 URL/출처/저장 데이터는 수정하지 않습니다.

검증 자료는 `.local/admin-workflow/`에만 보관합니다. 서버 검수/발송/계정/편집 관련 테스트 24개, 격리 HTTP 관리자 UI 4개(검수→승인→숨김/복원, 상태별 목록·조회수, READY 공개 제한, ADMIN↔USER 탭 갱신), 이미지 로더 동시 요청·다운샘플링·캐시·오류 재시도·썸네일 원본 대체 테스트를 수행합니다. 배포 서버의 검수 #14에서 외부 이미지 4개가 실제 표시됨을 확인했고, 별도 새 로더 측정은 약 0.15~0.91초였습니다. 이 측정은 특정 이미지·네트워크의 결과이며 모든 링크의 가용성이나 전체 화면 로딩 속도를 보장하지 않습니다. 원본 서버가 삭제/차단한 파일 자체는 앱에서 복구할 수 없습니다.
Debug 시뮬레이터와 Release iOS 빌드(서명 제외)를 통과했습니다. 두 빌드의 API 주소는 `https://aiknow.kro.kr`이며, 이번 변경의 TestFlight 업로드·실기기 검증은 수행하지 않았습니다. 서버 메타데이터 변경은 [PR #31](https://github.com/Kangila404/ai-know/pull/31) 배포 후 사용할 수 있습니다.

## 2026-10-23 프로필 직접 변경·카드 전체 높이 배치

마이 화면의 프로필 이미지를 누르면 선택 시트가 열리고, 이미지를 선택하면 프로필 API만 호출하여 바로 저장합니다. 닉네임을 누르면 키보드가 열린 입력 시트에서 이름만 수정·저장합니다. 별도 ‘프로필 편집’ 메뉴를 제거했으며, 저장 오류는 해당 시트에서 표시하고 저장 중 중복 입력/닫기를 막습니다.

카드 상세는 고정 4:3 이미지와 내용 높이만큼의 카드 대신, 실제 페이지 높이에서 본문 높이를 제외한 공간을 이미지에 배정합니다. 이미지 좌우의 중복 여백을 없애고 본문 글자를 키웠습니다. 이미지는 비율을 유지해 전체를 표시하며, 긴 본문은 이미지 최소 높이를 유지한 채 세로 스크롤로 끝까지 읽습니다. 페이지 표시·출처 시트·확대는 유지합니다.

검증: 격리 HTTP UI 테스트에서 닉네임 PATCH와 프로필 PATCH가 각각 한 번씩만 호출되고, 프로필 선택이 재진입 후 유지되는 것을 확인했습니다. 짧은 카드의 본문이 페이지 하단까지 배치되고 긴 본문의 끝까지 스크롤되는 검사도 통과했습니다. 운영 로그인 상태에서 라이트·다크 카드 배치, 닉네임 시트 취소, 프로필 선택 시트와 관리자 탭을 확인했습니다. Debug와 Release iOS 빌드(서명 제외)는 통과했으며 API 주소는 `https://aiknow.kro.kr`입니다. 이 변경의 실기기 실행과 TestFlight 업로드는 수행하지 않았습니다. 캡처·로그는 `.local/direct-profile-layout/`에 보관합니다.

## 2026-10-23 읽기·프로필·알림 화면 정리 (TestFlight 미업로드)

- 카드의 다음/완료 버튼을 제거했습니다. 좌우 스와이프 또는 VoiceOver 페이지 조절로 이동하고, 마지막 카드를 2초 동안 보면 읽음 API를 호출합니다. 출처·생성 크레딧·참고 링크는 상단 정보 버튼의 시트에서 확인합니다.
- 화면 모드 선택을 제거하고 iPhone 시스템 설정을 따릅니다. 프로필 편집은 루미·모리·노바·오르비·피코 5종 선택 그리드이며, 변경된 닉네임/프로필만 저장한 뒤 편집 화면을 닫습니다.
- 알림 토글과 시간은 250ms 동안 변경을 모아 순서대로 자동 저장합니다. 실패하면 마지막 저장값으로 되돌리고 오류를 표시합니다. 화면을 떠나도 진행 중인 저장을 마칩니다. 저장/등록 재시도/권한 요청 버튼과 개발용 토큰 표시는 제거했습니다. 처음 알림을 켤 때 OS 권한을 요청하고, 이미 차단한 경우에만 iPhone 설정으로 안내합니다.
- 기본 프로필 5종은 운영 관리자 API로 중복 없이 등록했습니다. 새 앱은 동일 PNG를 번들에서 즉시 표시합니다. 기존 앱/웹에서 새 프로필 URL을 읽으려면 서버의 정적 리소스와 V9 마이그레이션을 배포해야 합니다. V9는 기존 등록 항목을 덮어쓰지 않습니다.
- 서버 개선안은 원본 PNG를 보존하면서 320/1280px JPEG와 32MiB 메모리 캐시를 제공합니다. API가 `?preview=1280`을 반환한 경우에만 새 앱이 목록용 320px·본문용 1280px·확대용 원본으로 나눠 요청합니다. 아직 해당 API를 배포하지 않은 서버에서는 기존 URL을 그대로 사용합니다.
- 실제 운영 원본 3,028,143바이트는 첫 바이트 0.93초, 전체 다운로드 3.21초였습니다. 같은 파일의 로컬 서버 변환 결과는 320px 39,223바이트, 1280px 373,748바이트, 최초 변환 약 0.33초였습니다. 운영 서버 배포 후의 전송/화면 표시 시간은 아직 측정하지 않았습니다.

검증: 운영 로그인 상태의 iPhone 16e/iOS 26.3에서 시스템 라이트·다크 전환, 실제 카드 스와이프·출처 시트·프로필 5종·간소화된 알림 화면과 ADMIN 탭을 확인했습니다. iPhone SE 3세대/iOS 18의 격리 HTTP UI 테스트에서 스와이프·출처 열기·읽음·좋아요·프로필/닉네임·알림 자동 저장 등 12개 API 요청 항목을 확인했습니다. 알림 저장 모델과 이미지 로더 회귀 테스트도 통과했습니다. 서버 이미지/모바일 콘텐츠/프로필 권한 테스트 및 실제 로컬 MySQL 8.4 마이그레이션 6개 테스트를 통과했습니다. Debug 시뮬레이터와 Release iOS 빌드(서명 제외)를 통과했으며 두 빌드의 API 주소는 `https://aiknow.kro.kr`입니다. 실기기 푸시 수신과 새 버전 TestFlight 업로드는 이번 변경에서 수행하지 않았습니다.

알림 저장 모델 검증: `xcrun swiftc -parse-as-library ios/AiKnow/AiKnow/Services/APIClient.swift ios/AiKnow/AiKnow/Services/NotificationPreferences.swift ios/tests/NotificationPreferencesProbe.swift -o /tmp/aiknow-notification-probe && /tmp/aiknow-notification-probe`. 빠른 연속 변경, 순차 저장, 실패 복구, 화면 해제 후 저장 완료를 검사합니다. 기본 프로필 PNG는 저장소 루트에서 `xcrun swift ios/scripts/generate_avatars.swift`로 재생성합니다.

## 2026-10-23 운영 이미지·레이아웃 점검 (TestFlight 6 이후 수정)

- 운영 서버 `https://aiknow.kro.kr`에 실제 로그인한 iPhone 16e / iOS 26.3 시뮬레이터에서 아카이브의 `[AI 이론] LLM은 어떻게 문장을 이어갈까?` 게시물과 슬라이드 5장을 확인했습니다. 이미지 로딩 전 영역이 줄어드는 현상, 페이지 표시와 출처의 겹침, 투명 도식의 다크 모드 가독성을 수정했습니다.
- 이미지 영역은 로딩 전후 같은 크기를 유지하며 전체 이미지를 맞춰 표시합니다. 상세에서는 하단 탭을 숨기고 페이지 표시를 스크롤 영역 밖에 배치합니다. 긴 본문·출처는 스크롤로 끝까지 읽을 수 있고, 긴 도식은 확대 버튼 → 핀치 줌으로 확인할 수 있습니다.
- `ImageLoader`는 공개 이미지용 HTTP 캐시, 크기별 디코딩 캐시, 동시 요청 공유, 백그라운드 다운샘플링을 사용합니다. 현재 이미지가 준비되면 다음 한 장만 미리 읽습니다. API 세션·권한 조회의 캐시 정책은 변경하지 않았습니다. 프로필/목록은 작은 크기로, 확대 화면은 최대 원본 4096px로 읽습니다.
- 앱 측정값: 표지 1,186,889바이트의 최초 읽기 약 2.18초, 같은 원본의 캐시 읽기 약 0.0019초. 본문 이미지 중 하나는 3,028,143바이트였습니다. 이 값은 해당 네트워크에서의 이미지 데이터 읽기 시간이며, 전체 화면 표시 시간이나 모든 기기의 성능 보장이 아닙니다. 서버 원본 크기를 줄이는 썸네일 API/CDN 배포는 이번 변경에 포함하지 않았습니다.
- 검증: 실제 게시물의 5장 이동·스크롤·재진입, 관리자 탭 복귀, 라이트/다크 확대 화면과 3배 핀치 줌. 격리 이미지 테스트에서 20개 동시 요청 공유, 크기별 다운샘플링, 원본 보존, HTTP/디코딩 캐시, 실패 후 재시도, 잘못된 이미지 처리를 확인했습니다. 운영 좋아요·읽음·편집 요청은 보내지 않았지만 상세 열기에 따른 서버 조회수 집계는 발생합니다.
- 빌드: Debug 시뮬레이터 테스트와 Release iOS 빌드(`CODE_SIGNING_ALLOWED=NO`)를 통과했습니다. 이번 수정본의 배포 서명·아카이브 검증은 수행하지 않았습니다.
- 실기기·다른 화면 크기의 시각 검증과 이 수정본의 TestFlight 업로드는 아직 진행하지 않았습니다. 이 점검 당시 최신 업로드는 `1.0 (6)`이었으며, 현재 업로드는 위의 `1.0 (7)` 기록을 참고합니다.

이미지 테스트는 `python3 ios/tests/test_image_loader.py --device <booted-simulator-UDID>`로 실행합니다. 운영 화면 자동 점검은 실제 로그인된 시뮬레이터에서만 사용합니다. 빈 임시 디렉터리를 `python3 -m http.server 18089 --bind 127.0.0.1 --directory <empty-directory>`로 열어 명시적으로 활성화한 뒤 `AiKnowUITests/ProductionLayoutInspectionTests`를 선택합니다. 기본 API 주소를 변경하지 않으며, 상세 열기는 운영 조회수에 반영됩니다. 점검 후 이 로컬 서버를 종료합니다. 스크린샷·측정 로그는 `.local/production-media-inspection/`에만 보관하고 커밋하지 않습니다.

## 실행

1. 로컬 Firebase 설정 파일을 `AiKnow/AiKnow.xcodeproj/GoogleService-Info.plist`에 둡니다. Firebase의 iOS 앱 Bundle ID는 `org.aiknow.AiKnow`여야 합니다. 이 파일은 Git에서 제외됩니다.
2. Xcode에서 `AiKnow/AiKnow.xcodeproj`를 엽니다. Swift Package Manager가 FirebaseMessaging, GoogleSignIn 9.2.0, KakaoSDKUser 2.29.0과 관련 의존성을 내려받습니다. Package.resolved는 저장소에 포함해 SDK 버전을 고정합니다.
3. Signing & Capabilities의 Team은 `SS32PHC392`, Bundle ID는 `org.aiknow.AiKnow`로 설정되어 있습니다. Sign in with Apple, Push Notifications, Background Modes / Remote notifications가 프로젝트에 설정되어 있습니다. Xcode에 해당 Team 계정이 로그인되어 있어야 하며, 두 capability가 포함된 앱 전용 provisioning profile을 사용해야 합니다.
4. iPhone을 실행 대상으로 선택하고 실행합니다. 마이 → 알림 설정에서 매일 카드 알림을 켜고 OS 알림 권한을 허용합니다.

## 푸시 테스트

- Firebase Console → 프로젝트 설정 → Cloud Messaging → iOS 앱에 APNs 인증 키(.p8), Key ID, Team ID를 등록합니다. Apple Developer App ID에서도 Push Notifications가 활성화되어 있어야 합니다.
- 실제 로그인한 기기에서 매일 카드 알림을 켭니다. FCM 토큰은 인증된 세션으로 `/api/v1/device-tokens`에 자동 등록됩니다.
- 서버의 승인된 발송 경로 또는 Firebase Console에서 테스트 메시지를 보냅니다. 토큰은 사용자 설정 화면에 노출하지 않습니다. 전경에서는 배너·소리로 표시되고, 배경에서는 iOS가 알림을 표시합니다. `cardNewsId`가 있으면 알림을 눌러 해당 카드를 엽니다.
- 실제 기기의 수신 여부로 최종 검증합니다. 서명 없이 수행한 Simulator 빌드는 APNs 전달 검증을 대신하지 않습니다.

## 서버 토큰 등록

앱 Build Settings의 `AIKNOW_API_BASE_URL`로 서버 주소를 지정합니다. **Debug/Release 및 Simulator/iPhone 모두 기본값은 `https://aiknow.kro.kr`입니다.** Xcode에서 실행해도 TestFlight와 같은 운영 서버에 연결하며 개발자의 Mac 서버나 핫스팟은 필요하지 않습니다. 실제 계정으로 실행하면 운영 데이터를 사용합니다.

로컬 서버를 개발할 때만 `AIKNOW_API_BASE_URL`을 별도로 지정합니다. Simulator에서는 `http://localhost:8080`, 실제 iPhone에서는 해당 Mac의 LAN IP 또는 `.local` 호스트 주소를 사용합니다. 로컬 연결 시 Mac과 iPhone이 같은 네트워크에 있어야 하며 iPhone의 로컬 네트워크 접근을 허용합니다. 개인 Mac 주소를 프로젝트 기본값으로 커밋하지 않습니다.

```sh
xcodebuild -project ios/AiKnow/AiKnow.xcodeproj -scheme AiKnow \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  AIKNOW_API_BASE_URL=http://localhost:8080 build
```

다른 개발자가 이전 버전에서 연결 오류를 봤다면 이 변경을 받은 뒤 Xcode에서 앱을 다시 빌드·실행합니다. 계속 로컬 주소가 사용되면 Target → Build Settings → `AIKNOW_API_BASE_URL`에 남은 개인 설정을 확인하고 Product → Clean Build Folder 후 다시 실행합니다. 아래 날짜별 검증 기록의 로컬 주소는 당시 빌드의 설정입니다.

`PushNotifications.shared.syncToken()`은 공유 APIClient의 세션 쿠키와 CSRF로 `PUT /api/v1/device-tokens`에 IOS 플랫폼과 설치 UUID를 등록합니다. Google·Apple·Kakao 로그인 완료, FCM 토큰 갱신, 앱 활성화, 수동 재시도 시 등록합니다. 로그아웃은 진행 중인 등록을 기다린 뒤 기기 토큰을 비활성화하고 서버 세션·로컬 쿠키·Keychain 세션·Google SDK 상태를 정리합니다.

서버 발송에 필요한 서비스 계정 JSON(`FCM_CREDENTIALS_PATH`)은 iOS의 GoogleService-Info.plist와 별개의 파일입니다. Firebase Console 테스트 수신에는 서버 서비스 계정이 필요하지 않습니다.

## 구조

- `App/`: 진입점과 화면 흐름
- `Services/PushNotifications.swift`: Firebase 초기화, APNs/FCM 콜백, 알림 권한, 서버 등록, 알림 설정 화면
- `Models/`, `Features/`: 화면과 목 데이터


## Google 로그인

- `Info.plist`의 `GIDClientID`는 iOS Client ID, `GIDServerClientID`는 웹 Client ID입니다. 콜백 URL Scheme은 iOS Client ID의 reversed scheme입니다. Bundle ID는 `org.aiknow.AiKnow`입니다. 웹 Secret은 서버 로컬 `.env`에만 둡니다.
- 앱은 `GET /api/v1/auth/csrf`로 익명 CSRF와 세션 쿠키를 받고, CSRF를 붙여 `POST /api/v1/auth/social/GOOGLE/challenge`를 호출합니다.
- 서버 nonce를 Google SDK의 `signIn(..., nonce:)`에 그대로 전달합니다. 받은 Google ID token을 CSRF와 같은 쿠키를 사용해 서버 로그인 API에 전달합니다. 서버 성공 응답의 새 CSRF로 교체하고, 회전된 JSESSIONID 쿠키를 유지합니다.
- 세션 쿠키는 서버 주소별로 Keychain에 보관합니다. 재실행 시 `/api/v1/users`로 유효성을 확인하고 CSRF를 새로 받아 사용하며, 401이면 세션을 제거합니다. Google 토큰 자체는 앱 로그나 자체 저장소에 기록하지 않습니다.
- iPhone에서 Google로 계속하기 → 브라우저 열기 허용 → Google 계정 선택·동의 → 앱 복귀 순서로 테스트합니다. 서버 인증이 성공해야 온보딩/홈으로 이동합니다. 마이에서 실제 닉네임을 확인하고 로그아웃·재로그인도 확인합니다.
- Google Cloud OAuth 동의 화면이 테스트 상태라면 로그인할 계정을 테스트 사용자에 추가해야 합니다. iOS OAuth 클라이언트의 Bundle ID와 웹 OAuth 클라이언트의 localhost redirect URI도 확인합니다.

## 서버 실행과 검증

Mac에서 Java 21과 서버 JAR가 준비되면 저장소 루트에서 `python3 server/run-local.py`를 실행합니다. 실행기는 `server/.env`를 shell 실행 없이 읽고 local 프로파일을 사용합니다. `JAVA_HOME` 또는 `--java`로 Java를 지정할 수 있고, 이 작업에서 내려받은 `.local/tools`의 JDK도 자동으로 찾습니다. JAR 빌드는 `server/`에서 `bash gradlew bootJar`입니다. `.env`, Firebase 설정, 로컬 도구와 로그는 Git에서 제외됩니다.

- `python3 ios/tests/test_api_client.py`: 실제 Swift APIClient를 로컬 HTTP fixture에 연결해 익명 쿠키/CSRF, Google·Apple·Kakao provider별 challenge nonce, ID token 전달, 로그인 후 쿠키/CSRF 교체, CSRF 재시도, 로그아웃을 검사합니다. 실제 Google 인증 테스트는 아닙니다.
- `server/`에서 `bash gradlew test --tests 'org.aiknow.server.auth.mobile.*'`: JWT 서명/issuer/audience/만료 검증, nonce 재사용 거부, Google·Apple·Kakao 로그인 이후 프로필 조회·기기 등록·CSRF 회전·로그아웃을 검사합니다. 테스트는 H2와 mock provider를 사용합니다.
- 실제 Google 계정의 SDK 인증, 실기기 Keychain 복원, APNs/FCM 푸시 수신은 실기기에서 별도로 확인합니다.

SDK nonce API: https://github.com/google/GoogleSignIn-iOS/blob/9.2.0/GoogleSignIn/Sources/Public/GoogleSignIn/GIDSignIn.h


## Apple 로그인과 실기기 확인

- `server/.env`: `APPLE_LOGIN_ENABLED=true`, `APPLE_CLIENT_IDS=org.aiknow.AiKnow`. 카카오 설정은 `KAKAO_LOGIN_ENABLED=true`와 `KAKAO_CLIENT_IDS`에 제공한 클라이언트 키를 반영합니다.
- Apple 버튼은 먼저 서버 APPLE challenge를 받은 뒤 `ASAuthorizationAppleIDRequest.nonce`에 그 nonce를 그대로 전달합니다. Apple identityToken은 같은 세션과 CSRF로 서버 APPLE 로그인 API에 전달합니다. 서버가 서명, issuer, audience, 만료, nonce를 검증한 후 발급한 세션과 새 CSRF로 프로필과 기기 토큰 API를 호출합니다.
- Apple은 최초 동의에서만 이름을 돌려줄 수 있습니다. 이름이 없으면 기존 서버 사용자 정보를 사용하며 신규 사용자 닉네임은 서버 기본값으로 생성됩니다.
- Xcode → Settings → Accounts에서 `SS32PHC392` Team에 속한 Apple 계정을 추가합니다. 앱 타깃 Signing & Capabilities에서 Automatically manage signing을 켜고 이 Team을 선택합니다. 와일드카드 `iOS Team Provisioning Profile: *`는 Apple 로그인과 Push를 지원하지 않습니다. 이 Mac에서는 자동 갱신한 앱 전용 profile로 서명 빌드가 성공했고, Team/App ID/Apple 로그인/Debug APNs entitlement를 확인했습니다. 다른 Mac에서는 해당 profile을 자동 갱신합니다.
- Developer Mode가 켜진 iPhone을 연결하고 실행합니다. Mac과 같은 Wi-Fi, 로컬 네트워크 허용 상태에서 Apple로 계속하기 → Apple 인증 동의/Face ID → 앱 복귀를 진행합니다. 인증 성공 뒤 온보딩/홈으로 이동하고 마이에서 닉네임을 확인합니다.
- 알림을 허용하고 마이 → 알림 설정의 FCM 발급/서버 등록 상태를 확인합니다. Firebase 콘솔 테스트 전송은 Debug의 FCM 테스트 토큰 복사로 확인할 수 있습니다.
- Debug entitlement는 `aps-environment=development`, Release는 `production`입니다. Firebase에 등록한 APNs 키가 Production 전용이면 Debug 푸시를 검증하려면 Sandbox 키도 등록해야 합니다. 또는 적절한 Release 배포 서명과 실제 HTTPS 서버 주소를 사용한 TestFlight 빌드로 Production 푸시를 검증합니다. Production 키만으로 Debug 수신이 검증됐다고 간주하지 않습니다.
- Apple 계정의 실제 인증, 실기기 설치, Keychain 세션 복원, APNs/FCM 실제 수신은 사용자 기기에서 최종 확인합니다. 서버/HTTP fixture 테스트의 identityToken과 FCM token은 테스트용입니다.

Apple nonce API: https://developer.apple.com/documentation/authenticationservices/asauthorizationopenidrequest/nonce
Firebase APNs 설정: https://firebase.google.com/docs/cloud-messaging/ios/get-started


## 시뮬레이터에서 실제 API 연결 테스트

서버를 `localhost:8080`에서 실행하고 시뮬레이터를 부팅한 뒤 저장소 루트에서 `python3 ios/tests/test_simulator_api.py --device <시뮬레이터 UDID>`를 실행합니다. 앱의 실제 `APIClient.swift`를 iOS Simulator용으로 컴파일해 시뮬레이터 프로세스 안에서 실행합니다. health, provider 조회, 세 제공자의 challenge와 쿠키/CSRF 유지, 잘못된 ID token 거부, 미로그인 프로필·기기 등록 차단을 확인합니다. 실제 소셜 계정으로 인증하지 않으며 사용자나 기기를 생성하지 않습니다.

Xcode의 `AiKnowUITests` scheme은 로그인 버튼을 누르고 외부 인증 화면 경계까지 검사합니다. 실행 중인 서버와 로그인하지 않은 앱 상태가 필요합니다. Debug 앱 로그의 subsystem `org.aiknow.AiKnow`, category `API`에는 HTTP 메서드·경로·상태 코드만 기록합니다. UI 테스트와 함께 `GOOGLE/challenge`, `APPLE/challenge`의 200 응답을 확인해야 합니다. UI 테스트가 통과해도 실제 소셜 로그인 완료나 FCM 등록 성공을 의미하지 않습니다.


## 카카오 로그인

KakaoSDKUser의 `loginWithKakaoAccount(nonce:)`로 시뮬레이터와 실제 기기에서 계정 로그인을 사용합니다. 서버 KAKAO challenge nonce → SDK → ID token → 서버 세션 교환 순서이며 Google/Apple과 같은 쿠키·CSRF·푸시 등록 처리를 사용합니다. SDK 상세 로깅은 꺼두었습니다.

`Info.plist`의 `KakaoNativeAppKey`와 `kakao{네이티브 앱 키}` URL Scheme, 서버 `KAKAO_CLIENT_IDS`가 같은 네이티브 앱 키를 가리켜야 합니다. 사용자가 네이티브 앱 키임을 확인한 키를 반영했습니다. Kakao Developers에서 iOS Bundle ID `org.aiknow.AiKnow`, 카카오 로그인, OpenID Connect도 설정해야 합니다. REST API 키를 네이티브 키 대신 사용할 수 없습니다.

공식 가이드: https://developers.kakao.com/docs/ko/kakaologin/ios

## 시뮬레이터 인증 서명

앱/UI 인증 테스트는 기본 Xcode 서명을 유지합니다. `CODE_SIGNING_ALLOWED=NO`로 앱을 빌드하지 않습니다. 서명이 없는 이전 빌드에서는 Apple 인증이 `AKAuthenticationError -7026` / `ASAuthorizationError 1000`으로 바로 실패했으며, 정상 서명 후 Apple 계정 로그인 안내 창이 표시되었습니다. Simulator entitlement는 코드 서명 출력뿐 아니라 생성된 `AiKnow.app-Simulated.xcent`에도 들어갑니다.

```sh
xcodebuild -project ios/AiKnow/AiKnow.xcodeproj -scheme AiKnowUITests \
  -destination 'platform=iOS Simulator,id=<UDID>' -parallel-testing-enabled NO test
```

Apple 인증을 계속하려면 시뮬레이터의 설정 앱에서 Apple 계정에 로그인합니다. Google/Kakao UI 테스트는 계정 입력 폼이 로드되는지 확인하며 계정 정보 입력이나 동의 완료를 자동화하지 않습니다. 실패는 `Authentication` 로그에 단계·오류 도메인·코드만 남깁니다. 원본 SDK 오류 문자열, 토큰, 쿠키, Secret은 기록하지 않습니다.


### 2026-10-07 로그인 장애 재검증

- iPhone 16 Pro / iOS 18 Simulator에 정상 서명한 앱을 설치했습니다. 이전 서명 없는 앱의 Apple 즉시 실패가 사라지고 시스템 Apple 계정 로그인 안내가 나타납니다. 이 Simulator는 설정 앱에서 Apple 계정 로그인이 필요합니다.
- Google은 기존 계정 선택과 재로그인 동의까지 진행한 실제 인증에서 `POST /api/v1/auth/social/GOOGLE` 200, `GET /api/v1/users` 200과 온보딩 진입을 확인했습니다. 앱 프로세스를 종료·재실행한 뒤에도 사용자 조회 200, CSRF 갱신 200으로 세션 복원을 확인했습니다. 개인 계정 동의는 일반 UI 회귀 테스트에 포함하지 않습니다.
- Kakao는 서버 challenge 200과 accounts.kakao.com의 계정·비밀번호 입력 폼까지 확인했습니다. 실제 계정 입력·동의 후 ID token 교환, Apple 실제 인증, FCM/APNs 수신은 아직 확인하지 않았습니다.
- 세 제공자의 Swift HTTP fixture 테스트와 Simulator → 실행 중인 서버의 challenge/CSRF/잘못된 토큰 거부 테스트가 통과했습니다. 이것은 실제 계정 인증 성공과 별도 검증입니다.


### 2026-10-07 푸시 재검증

- iPhone 16 Pro / iOS 18 Simulator: 알림 권한 허용, FCM 토큰 발급, `PUT /api/v1/device-tokens` 200과 서버의 해당 설치 토큰 활성화를 확인했습니다.
- 서버의 실제 `FirebasePushSender`와 로컬 서비스 계정으로 해당 Simulator 한 대에 발송을 시도했습니다. 결과는 `RETRY / THIRD_PARTY_AUTH_ERROR`입니다. 직접 SDK 진단도 같은 오류로 실패했습니다. FCM/APNs 실제 전달 성공은 확인하지 못했습니다.
- Firebase의 APNs 인증 정보가 없거나 유효하지 않을 때 발생하는 오류입니다. 현재 Debug는 Sandbox이고 사용자가 등록했다고 알린 키는 Production용이므로 개발용 APNs 키 등록을 우선 확인해야 합니다. Firebase Console의 iOS 앱 `org.aiknow.AiKnow`에 개발용 키, 그 키의 Key ID, Team ID `SS32PHC392`를 등록한 뒤 다시 발송합니다.
- `simctl push`로 별도 주입한 알림은 전경 배너와 앱 배지가 표시됐습니다. 이는 앱/iOS의 표시 처리 검증이며 FCM/APNs 전달 검증을 대신하지 않습니다.
- 토큰·서비스 계정 내용은 출력하거나 커밋하지 않았습니다. 임시 개인 계정 로그인 UI 테스트는 일반 테스트에서 제거했습니다.

오류 설명: https://firebase.google.com/docs/cloud-messaging/error-codes
키 등록: https://firebase.google.com/docs/cloud-messaging/ios/get-started

## TestFlight 앱에서 Mac 로컬 서버 사용

서버를 클라우드에 배포하지 않아도 앱만 TestFlight로 배포할 수 있습니다. 아이폰에서 도달 가능한 서버 주소를 Release 빌드에 넣어야 합니다. 실기기에서 `localhost`는 Mac이 아닌 아이폰 자신입니다. 현재 `AIKNOW_API_BASE_URL`의 Release 기본값은 `https://aiknow.kro.kr`입니다. 이 도메인은 서버 배포와 TLS 설정 후 사용합니다.

- 같은 Wi-Fi 내부 테스트: Release의 `AIKNOW_API_BASE_URL`을 `http://gang-il-aui-MacBookPro.local:8080`으로 설정합니다. 기존 Info.plist의 로컬 네트워크 설정을 유지하고 iPhone에서 로컬 네트워크 접근을 허용합니다. Mac 서버와 MySQL이 실행 중이고 방화벽에서 연결을 허용해야 합니다.
- 외부 네트워크 테스트: 고정 HTTPS 도메인을 Cloudflare Tunnel 등으로 Mac의 `http://localhost:8080`에 연결한 뒤 그 HTTPS 주소를 Release의 `AIKNOW_API_BASE_URL`에 설정합니다. 잠깐 확인할 때는 `cloudflared tunnel --url http://localhost:8080`으로 임시 URL을 만들 수 있지만, 임시 URL은 재시작 때 바뀌므로 TestFlight에 넣을 주소로는 고정 도메인이 적합합니다. Mac·MySQL·서버·터널을 계속 실행해야 합니다.
- HTTPS 터널로 사용할 때 로컬 서버 실행 환경에서 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`, `SERVER_FORWARD_HEADERS_STRATEGY=framework`를 적용하고 서버를 재시작합니다. Secure 쿠키를 켠 상태에서는 Simulator의 기존 HTTP 주소 대신 같은 HTTPS 주소를 사용합니다. 네이티브 Google/Apple/Kakao 로그인은 SDK 토큰 교환 방식이므로 웹 OAuth의 localhost redirect URI 변경과는 별개입니다.
- Xcode에서 `AiKnow` scheme과 실제 기기용 목적지를 선택 → Product / Archive → Distribute App / App Store Connect로 업로드 → App Store Connect의 TestFlight에서 내부 테스트 그룹에 빌드를 배정합니다. 외부 테스터의 첫 빌드는 TestFlight 심사가 필요하며 심사 기간에도 API에 접근할 수 있어야 합니다.
- TestFlight의 배포 서명은 Production APNs를 사용합니다. Firebase에 등록한 Production 키로 검증할 수 있으나 이번 Simulator 테스트로 Production 수신 성공까지 확인된 것은 아닙니다. 앱이 발급받은 새 FCM 토큰이 로컬 서버에 등록된 뒤 그 토큰으로 재시험해야 합니다.

고정 주소 연결: https://developers.cloudflare.com/tunnel/concepts/routing/
임시 터널: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/
TestFlight: https://developer.apple.com/testflight/


### USB 연결 기기의 Production APNs 테스트

TestFlight를 거치지 않고도 등록된 iPhone에 Ad Hoc (`release-testing`) 배포 빌드를 설치해 Production APNs를 시험할 수 있습니다. Release 설정 이름만으로 판단하지 않고 **내보낸 앱 서명과 embedded.mobileprovision 모두**에서 `aps-environment=production`을 확인합니다.

2026-10-07 이 Mac에서 `.local/iphone-production/AiKnow.xcarchive`를 만들고, 자동 배포 서명으로 `.local/iphone-production/export/AiKnow.ipa`를 내보냈습니다. 연결된 iPhone 16 Pro가 프로파일에 포함되고 Apple 로그인 권한, Production APNs, `get-task-allow=false`를 확인한 뒤 설치·실행했습니다. App Store Connect 업로드는 하지 않았습니다.

이 시험 빌드의 API는 `http://gang-il-aui-MacBookPro.local:8080`입니다. 아이폰 개인용 핫스팟으로 연결했을 때 확인한 Mac IP는 `172.20.10.3`이었으며 네트워크 재연결 시 바뀔 수 있습니다. 앱에서 로컬 네트워크 접근 허용 → 로그인 → 온보딩 완료/건너뛰기 → 알림 허용 → 마이 / 알림 설정의 서버 등록 완료를 확인합니다. 그 뒤 해당 실제 기기의 새 토큰으로 발송해야 합니다. Simulator의 Sandbox 토큰을 재사용하지 않습니다.

Ad Hoc 배포: https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices


Production 재시험에서는 연결된 실제 iPhone의 새 IOS 기기 등록을 확인한 뒤 그 토큰 1개에 발송했습니다. Firebase Admin SDK 결과는 `SENT`였습니다. Simulator의 `THIRD_PARTY_AUTH_ERROR`와 달리 Production 요청은 수락됐습니다. 단, 발송 수락과 기기 화면의 실제 수신 확인은 구분해야 합니다.

사용자가 iPhone에서 ‘AiKnow 푸시 검증 / FCM 실제 전달 테스트’ 알림 도착을 확인했습니다. 따라서 이 Ad Hoc Production 빌드의 로컬 서버 연결, 기기 토큰 등록, 실제 FCM/APNs 전달 및 기기 수신까지 검증됐습니다. TestFlight 업로드와 배경/종료 상태별 전달은 이번 검증에 포함하지 않았습니다.


### 2026-10-07 앱 화면 API 연결

| 화면 / 기능 | API |
| --- | --- |
| 오늘의 카드 | `GET /api/v1/cardNews/today` |
| 아카이브 / 검색 / 카테고리 / 좋아요 필터 / 페이지 | `GET /api/v1/cardNews?query=...&categoryId=...&isOnlyLiked=...&page=...&size=20` |
| 카테고리 | `GET /api/v1/cardNews/categories` |
| 상세 / 슬라이드 | `GET /api/v1/cardNews/{id}`, `GET /api/v1/cardNews/cardSlides?cardNewsId=...` |
| 좋아요 / 읽음 | `PATCH /api/v1/cardNews/{id}/like`, `PUT /api/v1/cardNews/{id}/read` |
| 공지 목록 / 상세 | `GET /api/v1/notices`, `GET /api/v1/notices/{id}` |
| 내 정보 / 닉네임 / 프로필 | `GET /api/v1/users`, `PATCH /api/v1/users/nickname`, `GET /api/v1/profile`, `PATCH /api/v1/users/profile/{id}` |
| 온보딩 완료 / 건너뛰기 | `PATCH /api/v1/users/onboarding` |
| 알림 시간 / 수신 여부 | `GET`, `PUT /api/v1/notification-setting` (한국 시간) |
| 회원탈퇴 | `DELETE /api/v1/users/me` (연관 데이터 삭제 및 현재 세션 종료) |
| 로그아웃 | 기기 토큰 비활성화 후 `POST /api/v1/auth/logout` |

서버 카드 응답에 `categories`, 사용자별 `liked`, `read`가 추가되었습니다. 읽음은 마지막 슬라이드의 ‘다 읽었어요’를 눌러 저장합니다. `V4__card_read.sql`을 배포 DB의 Flyway 마이그레이션에 포함해야 합니다. 로컬 프로필은 Hibernate update로 새 테이블을 생성합니다. 앱 업데이트 전에 서버를 먼저 업데이트합니다.

알림의 `cardNewsId`로 상세 API를 조회합니다. 로그인/온보딩이 필요하면 완료 후 해당 카드를 엽니다. 게시가 취소된 카드는 오류/재시도를 표시합니다. 빈 목록과 오늘의 카드 404는 빈 화면 안내로 처리합니다. 앱은 샘플 카드를 대신 보여주지 않습니다.

검증:
- 서버 전체 65개 테스트 통과(실제 MySQL의 격리된 스키마에서 신규 마이그레이션, 기존 데이터 보존, Hibernate validate 포함).
- H2 통합 테스트: 검색·페이지·비공개 카드 제외·사용자별 좋아요/읽음·읽음 중복 방지·온보딩 저장·탈퇴 데이터 정리·세션 종료·CSRF 거부.
- 실제 Swift APIClient의 Google/Apple/Kakao HTTP fixture 테스트: 쿠키/CSRF 회전, 재시도, 로그아웃.
- Simulator에서 실행 중인 실제 로컬 서버에 health/provider/challenge 요청 성공, 잘못된 ID token 및 익명 접근 거부 확인.
- `python3 ios/tests/test_content_ui.py --device <Simulator UDID>`: 격리된 HTTP fixture에 연결한 앱을 XCUITest로 조작하여 온보딩 저장/재실행, 검색/카테고리/좋아요 필터, 상세/좋아요/읽음, 공지 상세, 닉네임/프로필, 알림 설정 저장 검증. 실제 사용자 계정과 DB를 변경하지 않습니다. 이 테스트 뒤 일반 빌드로 API 주소를 복원해야 합니다.

현재 로컬 DB의 승인·게시된 카드 수는 0개입니다. 홈/아카이브의 빈 상태는 정상이며, 실제 콘텐츠는 관리자 게시 절차를 거쳐야 표시됩니다. 실제 사용자 계정 탈퇴는 실행하지 않았습니다. Apple/Kakao 실제 계정 인증 완료, 알림 탭의 실기기 상세 이동, 배경/종료 상태별 푸시, TestFlight 배포는 별도 확인이 필요합니다.


전체 API 연결 버전을 `.local/iphone-production/AiKnow-connected.xcarchive`에 아카이브하고 `export-connected/AiKnow.ipa`로 Ad Hoc 배포 서명했습니다. Production APNs, Apple 로그인 entitlement, 팀, 기기 프로비저닝, API 주소를 확인한 뒤 연결된 iPhone에 설치했습니다. 서버도 새 JAR로 재시작했습니다. 재로그인이 필요할 수 있습니다. Simulator는 fixture 주소가 아닌 `http://localhost:8080` 빌드로 복원했습니다. 새 버전의 전체 화면을 실기기에서 직접 조작한 검증은 아직 포함하지 않습니다.


### 2026-10-07 TestFlight 업로드

현재 UI와 로컬 API 주소를 유지한 채 `.local/testflight/AiKnow.xcarchive`를 App Store Connect에 업로드했습니다. 첫 검증에서 누락된 앱 아이콘과 iPad 화면 방향이 거절되어, 기존 로고를 불투명 1024px AppIcon으로 등록하고 iPhone/iPad 화면 방향을 명시했습니다. 라이트/다크 색상은 변경하지 않았습니다. 앱의 시스템/표준 인증·전송 암호화 사용에 맞춰 `ITSAppUsesNonExemptEncryption=false`를 명시했습니다.

20:30 KST에 Xcode가 `Upload succeeded`와 `Uploaded package is processing`을 반환했습니다. 이는 업로드 수락 상태이며 Apple의 빌드 처리 완료나 테스터 설치까지 확인한 것은 아닙니다. App Store Connect → AiKnow → TestFlight에서 처리 상태 및 내부 테스트 그룹의 빌드 배정을 확인합니다. 서버 주소는 `http://gang-il-aui-MacBookPro.local:8080`이며 Mac 서버와 동일한 로컬 네트워크가 필요합니다.

서버 변경사항은 별도 `feat/mobile-content-api` 브랜치의 PR https://github.com/Kangila404/ai-know/pull/13 에 포함했고, 대상은 `develop`입니다. 서버/인프라/필수 CI가 모두 통과했습니다. iOS 변경사항이나 환경 파일은 해당 PR에 포함하지 않았습니다.


### 2026-10-08 개발/배포 API 주소

`develop`의 모바일 API 계약을 사용하며 경로는 `/api/v1/...`를 유지합니다. 새 Release 빌드는 `https://aiknow.kro.kr`, Debug Simulator는 `http://localhost:8080`, Debug iPhone은 `http://gang-il-aui-MacBookPro.local:8080`에 연결합니다. Xcode에서 `AiKnow` scheme을 Run하면 Debug로 실행됩니다. 기존에 올린 TestFlight 빌드의 맥 주소는 소스 설정 변경만으로 바뀌지 않습니다.

운영 주소가 아직 서비스되지 않아도 로컬 API로 화면 개발을 계속할 수 있습니다. 서버 준비 후 Release 빌드를 설치하여 HTTPS·로그인 세션·콘텐츠·푸시 등록을 확인하고 TestFlight에 새 빌드를 올립니다. 운영 서버가 응답하지 않을 때 로컬 서버로 자동 전환하지 않습니다.

로그인 화면은 `GET /api/v1/auth/social/providers`가 반환한 제공자만 표시합니다. 서버가 연결되지 않으면 연결 안내와 재시도를 표시하고, 활성화된 제공자가 없으면 로그인 준비 상태를 표시합니다. 서버의 `status/code/message` 오류 응답을 처리하며 입력 검증 메시지는 화면에 전달합니다. 401 세션 만료, 오늘의 카드 404 빈 상태, Security의 403 CSRF 갱신은 기존 동작을 유지하고 업무 규칙의 403은 자동 재시도하지 않습니다. 네트워크/TLS 실패도 별도로 안내합니다.


### 개발 미리보기 도입 기록 (2026-10-08, 2026-10-23 제거)

2026-10-08에 UI 확인용 임시 미리보기를 도입했으나, 2026-10-23에 사용자 요청으로 진입 버튼·실행 인자·가짜 데이터·API 대체 코드·해당 UI 테스트를 삭제했습니다. 아래 검증 기록은 당시 상태이며 현재는 Xcode에서도 실제 로그인과 서버 계정 권한을 사용합니다.

실제 로그인은 `/api/v1/users`의 `userRole == ADMIN`일 때 네 번째 관리자 탭을 표시합니다. 모든 관리자 요청은 기존 세션/CSRF를 사용하며 서버의 ADMIN 권한 검사를 거칩니다.

| 관리자 화면 | 연결 기능 |
| --- | --- |
| 뉴스 검수 | 상태별 목록/페이지·상세·초안 수정·승인(유형/카테고리)·반려·재검수 |
| 카드뉴스 관리 | 상태별 목록/페이지·상세·제목/요약/핵심 포인트/이미지/슬라이드 수정·숨김/복원 |
| 생성 복구 | 목록/상세·응답 JSON 복구·이미지 복구(PNG, 최대 10MiB/4096px) |
| 변경 이력 | 콘텐츠별 페이지·작업자·사유·변경 전후 내용 |
| 공지 관리 | 목록/상세·추가·수정·공개/비공개·삭제 확인 |
| 프로필 이미지 관리 | 목록·추가·수정·삭제 확인 |

편집/복구는 서버 버전과 사유를 전달합니다. 충돌 응답이 오면 다시 조회해야 합니다. 승인/반려는 서버에 저장된 초안에 적용하므로 먼저 수정 저장을 완료합니다. READY 카드의 숨김/복원은 서버 규칙대로 막습니다.

당시 사용자 관리 API는 없어 준비 중으로 표시했으며, 2026-10-23 최신 main의 계정 관리 API와 화면을 반영했습니다. 프로필 이미지 URL은 현재 서버가 20자로 제한하므로 해당 제한을 안내하고 준수합니다. `/internal/v1/...` 수집·생성 예약·이미지 업로드는 n8n/서버 전용 인증을 사용하는 내부 API이며 앱에 내부 키를 넣거나 직접 노출하지 않습니다. 앱의 이미지 복구는 ADMIN 인증이 적용된 `/api/v1/admin/generations/{id}/recover-image`를 사용합니다.

마이 → 프로필 편집에서 닉네임과 서버에 등록된 프로필 이미지를 변경할 수 있습니다. 밝은/어두운 화면은 iPhone 시스템 설정을 따릅니다. 카드·입력창·본문·관리자 Form은 시스템 배경/텍스트 색상으로 전환됩니다.

이번 변경의 검증 범위:
- Debug Simulator 빌드 성공. `PreviewAdminTests`의 일반 사용자(관리자 탭 미노출·프로필/닉네임 저장·미리보기 종료), 관리자 검수(수정·승인·이력), 공지 생성/목록 반영·생성 응답 복구 시나리오 통과. 공지/복구 테스트에서 발견한 상단 버튼 터치 영역 겹침과 키보드 접근 문제를 수정 후 재검증했습니다.
- 라이트 홈/프로필 및 다크 홈/관리자 화면 캡처 확인. 화면 증거는 로컬 `.local/admin-ui-evidence/`에 보관합니다.
- `python3 ios/tests/test_api_client.py` 통과: 관리자 역할 디코딩, multipart 본문·Content-Type·세션 쿠키·CSRF 유지, 제공자별 인증 교환, 오류 처리.
- UI 테스트의 저장/승인은 메모리 미리보기 데이터에 대한 검증입니다. 실제 ADMIN 계정의 서버 데이터 변경, 실제 이미지 복구 업로드, 운영 도메인 HTTPS 연결 및 이번 변경의 실기기 실행은 아직 검증하지 않았습니다.
- iPhone용 Release 빌드 및 코드 서명 검증 성공. Release 실행 파일에 미리보기 실행 인자·`DevelopmentPreview`·미리보기 버튼/배너 문자열이 없고 Debug에는 진입 코드가 있는 것을 확인했습니다. 이 검증은 로컬 빌드이며 TestFlight 업로드를 수행하지 않았습니다.

### 2026-10-08 TestFlight 업로드 — 1.0 (2)

라이트/다크 모드, 프로필 편집, ADMIN 전용 운영 화면을 포함한 버전 1.0 빌드 2를 업로드했습니다. 12:58 KST에 Xcode가 `Upload succeeded`와 `Uploaded package is processing`을 반환했습니다. Apple의 업로드 수락까지 확인했으며, 처리 완료·테스트 그룹 배정·테스터 설치는 아직 확인하지 않았습니다.

아카이브와 업로드 로그는 `.local/testflight/2026-10-08-build-2/`에 보관합니다. App Store 배포용 IPA를 별도로 내보내 서명, Production APNs, Sign in with Apple, Bundle ID/Team을 검증했습니다. 최종 실행 파일에 개발 미리보기 진입 코드와 표시 문자열이 없는 것도 확인했습니다.

API 주소는 `https://aiknow.kro.kr`입니다. 현재 운영 서버와 HTTPS가 준비되지 않았으므로 실제 로그인/API 사용은 서버 배포 후 가능합니다. Debug의 로그인 없는 미리보기는 TestFlight에 포함되지 않습니다.

### 2026-10-23 서버 API와 관리자 화면 정리

최신 `main`의 서버·수집 변경과 `develop`의 iOS 변경을 함께 반영했습니다. 앱용 `/api/v1/...` 경로는 기존 세션·CSRF 계약을 유지합니다. 신규 `/internal/v1/generation/selection-context` 및 `/selection-context/recover`는 서버 전용 수집 권한이 필요하므로 iOS에 내부 인증키나 직접 실행 버튼을 추가하지 않습니다. ADMIN용 생성 상세 API가 반환하는 `contextJson`은 생성 복구 화면의 ‘이미지 선택 참고 자료’에서 확인할 수 있습니다.

- 관리자 탭은 `/api/v1/users`의 `userRole == ADMIN`일 때 탭 맨 오른쪽에 나타납니다. 앱이 다시 활성화되거나 마이 화면을 당겨 새로고침하면 권한을 다시 조회합니다. 관리자 권한이 해제되면 열린 관리자 탭에서 마이로 이동합니다.
- 뉴스 검수의 반려 사유·검수 시각·검수자·게시 상태를 표시하고 승인된 카드 관리로 이동할 수 있습니다. 콘텐츠 미리보기와 서버 제한에 맞춘 입력 오류 안내를 제공합니다.
- 생성 기록에는 응답 대기/저장/복구 상태와 원본 응답·복구 응답·이미지 선택 참고 자료를 표시합니다. 내부 유료 생성 실행은 제공하지 않습니다.
- 편집 화면의 ‘입력 완료’로 키보드를 닫고 저장·검수 작업을 계속할 수 있습니다.
- 사용자 전체 목록/정지/권한 변경은 최신 main의 `/api/v1/admin/users` API와 연결합니다. 프로필 이미지는 서버에서 등록한 항목을 선택하는 방식이며 관리자 이미지 주소의 20자 제한을 유지합니다.

관리자 API UI 검증은 `python3 ios/tests/test_admin_ui.py --device <UDID>`로 실행합니다. 별도 HTTP 서버에서 세션 쿠키·CSRF·ADMIN 역할·버전·변경 사유·요청 본문을 검사하며 검수, 카드 공개 상태, 공지/프로필 CRUD, 생성 복구와 탭 권한 갱신을 조작합니다. 실제 운영 계정·콘텐츠는 변경하지 않습니다. `--only <test method>`로 특정 시나리오만 재실행할 수 있습니다. 기존 일반 사용자 흐름은 `test_content_ui.py`로 검증합니다. 테스트 후 일반 Xcode 빌드로 테스트용 API 주소를 복원해야 합니다.


소셜 로그인은 공식 심볼과 네이티브 텍스트를 조합합니다. Google/카카오는 ‘Google 로그인 / 카카오 로그인’ 문구, 54pt 높이, 16pt Medium 글꼴, 20pt 심볼, 12pt 간격을 공유합니다. Google은 라이트/다크 색상을 전환합니다.

- Google: [브랜딩 가이드](https://developers.google.com/identity/branding-guidelines)의 [공식 G 심볼 PNG](https://developers.google.com/static/identity/images/g-logo.png)를 변경 없이 사용합니다. [Google Fonts의 Google Sans](https://github.com/google/fonts/tree/main/ofl/googlesans)를 두 버튼에 공통으로 적용하며 한국어는 시스템 글꼴로 대체됩니다. SIL Open Font License를 앱 번들에 함께 포함합니다.
- 카카오: [공식 디자인 리소스](https://developers.kakao.com/tool/resource/login)의 [한국어 완성형 SVG](https://developers.kakao.com/tool/images/resource/preview/login-complete-ko.svg)에서 말풍선 심볼 path를 분리했습니다. 심볼의 형태·비율·색상은 유지합니다.
- Apple: [ASAuthorizationAppleIDButton](https://developer.apple.com/documentation/authenticationservices/asauthorizationappleidbutton)의 로그인 타입을 사용합니다. 버튼 높이는 54pt이며 문구/서체는 Apple이 제공합니다. 한국어 지원 언어를 선언해 한국어 기기에서 시스템 로그인 문구도 현지화됩니다. 동작은 기존 `AuthSession.signInApple()`의 서버 challenge → nonce → 인증 → 세션 처리에 연결됩니다.

`python3 ios/tests/test_login_ui.py --device <UDID>`는 세 제공자를 반환하는 별도 HTTP fixture에서 밝은/어두운 로그인 화면을 캡처하고 각 버튼이 해당 제공자의 challenge API와 CSRF 처리로 연결되는지 검사합니다. 실제 Google·카카오·Apple 계정 로그인 성공을 대신하는 검증은 아닙니다. 운영 로그인 버튼 노출은 서버의 `/api/v1/auth/social/providers` 응답을 따릅니다.


2026-10-23 검증: 관리자 검수·게시 숨김/복원, 공지/프로필 CRUD, 생성 응답 복구, ADMIN↔USER 탭 갱신의 4개 UI 시나리오는 수정 후 개별 재실행을 포함해 통과했습니다. 일반 사용자 HTTP UI 테스트도 11개 요청 항목을 모두 검증했습니다. APIClient 인증·세션·CSRF·multipart 회귀 테스트와 `node scripts/validate-infra.mjs`도 통과했습니다. 운영 `https://aiknow.kro.kr/health-check`와 로그인 제공자 목록은 정상 응답했으며 현재 제공자는 GOOGLE/KAKAO입니다. 실제 운영 관리자 계정으로 쓰기, 실제 이미지 복구 업로드, 이번 빌드의 실기기 소셜 로그인·푸시 수신은 아직 재검증하지 않았습니다.

로그인 버튼 UI 테스트는 라이트/다크 2개 시나리오와 APPLE/GOOGLE/KAKAO challenge 연결을 통과했습니다. Google SVG 렌더링 문제는 공식 PNG 원본으로 교체했으며, 마지막 일반 Debug 빌드에서 운영 API 주소와 최종 라이트/다크 버튼 렌더링을 별도로 확인했습니다. 화면 캡처는 `.local/login-branding-evidence/final-production-{light,dark}.png`에 있습니다.


### 2026-10-23 TestFlight 업로드 — 1.0 (3)

14:58 KST에 Xcode가 `Upload succeeded`, `Uploaded package is processing`, `EXPORT SUCCEEDED`를 반환했고 전송 메타데이터의 버전 3을 확인했습니다. 배포 코드 커밋은 `94bfdcc`이며 [PR #25](https://github.com/Kangila404/ai-know/pull/25)에서 검토할 수 있습니다. Apple 처리 완료·테스트 그룹 배정·테스터 설치는 아직 확인하지 않았습니다.

App Store 배포용 IPA의 코드 서명과 provisioning profile에서 Bundle ID `org.aiknow.AiKnow`, Team `SS32PHC392`, Production APNs, Sign in with Apple, 디버깅 비활성화를 확인했습니다. 최종 실행 파일에 개발 미리보기 진입 인자·타입·표시 문자열이 없고 API 주소가 `https://aiknow.kro.kr`인 것도 확인했습니다. 아카이브·검증 IPA·업로드 로그는 `.local/testflight/2026-10-23-build-3/`에 보관합니다.


### Xcode에서도 실제 로그인과 서버 관리자 권한 사용

Xcode Debug와 TestFlight 모두 실제 소셜 로그인/서버 세션을 사용합니다. 개발 미리보기 버튼, 실행 인자, 가짜 사용자/콘텐츠 데이터 및 API 응답 대체 코드를 제거했습니다. 기존 `--preview-admin`/`--preview-user` 인자를 넣어도 로그인 화면을 우회하지 않습니다.

- `/api/v1/users`의 `userRole == ADMIN`일 때만 관리자 탭이 맨 오른쪽에 표시됩니다. Xcode로 실행했다는 이유로 관리자 권한을 부여하지 않습니다.
- 로그인 후 계정 권한을 바꿨다면 앱을 다시 활성화하거나 마이 화면을 당겨 새로고침합니다. 최신 서버는 기존 세션도 매 요청마다 계정 권한과 정지 여부를 확인하며, 정지된 계정은 세션을 종료합니다.
- 현재 최신 iOS 코드는 `develop` 기준입니다. 다른 Mac에서는 실행 브랜치/커밋과 API 주소를 확인합니다.
- `test_login_ui.py`는 라이트/다크 버튼과 인증 challenge 연결, 이전 미리보기 인자로도 로그인 우회가 되지 않는지 검사합니다. `test_admin_ui.py`는 별도 HTTP fixture의 ADMIN↔USER 응답에 따라 탭이 갱신되는지 검사합니다. 테스트용 HTTP 서버는 앱 배포물에 포함되지 않습니다.

미리보기 삭제 후 검증: 인증/세션/CSRF/multipart APIClient 회귀 테스트, 라이트/다크 로그인 화면과 기존 인자를 통한 우회 차단 3개 UI 테스트, HTTP ADMIN→USER→ADMIN 응답에 따른 탭 갱신 테스트를 통과했습니다. 실제 다른 사용자의 운영 계정 권한은 조회하거나 변경하지 않았습니다.


### 2026-10-23 TestFlight 업로드 — 1.0 (4)

개발 미리보기 제거와 소셜 로그인 버튼 통일을 포함한 버전 1.0 빌드 4를 업로드했습니다. 15:27 KST에 Xcode가 `Upload succeeded`와 `Uploaded package is processing`을 반환했으며 업로드 메타데이터의 빌드 번호도 4로 확인했습니다. Apple의 업로드 수락까지 확인했으며 처리 완료·테스트 그룹 배정·테스터 설치는 아직 확인하지 않았습니다.

Debug/Release 모두 실제 로그인과 서버의 ADMIN 권한을 사용합니다. 최종 일반 Debug 빌드와 Release 아카이브의 API 주소는 `https://aiknow.kro.kr`입니다. App Store 배포 IPA의 코드 서명, Bundle ID/Team, Production APNs, Sign in with Apple 및 디버깅 비활성화를 검증했습니다. 최종 실행 파일에 개발 미리보기 코드/진입 인자가 없는 것도 확인했습니다. 실제 다른 사용자의 계정 권한과 이번 빌드의 실기기 로그인·푸시는 아직 검증하지 않았습니다.

아카이브와 업로드 로그는 로컬 `.local/testflight/2026-10-23-build-4/`에, 최종 로그인 라이트/다크 화면은 `.local/login-no-preview-evidence/final-{light,dark}.png`에 보관합니다.


### 2026-10-23 최신 main API 반영 — 1.0 (5)

`main`의 사용자 관리·조회수 집계 API와 iOS 화면을 기존 로그인/실제 ADMIN 권한 흐름에 통합했습니다. 관리자 탭의 ‘계정 목록·권한 관리’에서 검색, 권한/정지 필터, 페이지 조회, 계정 상세, 권한·정지 변경 및 USER 변경 이력을 제공합니다. 변경 시 조회한 version과 사유를 전송하며 409 충돌 후에는 ‘계정 다시 조회’로 최신 상태를 받은 뒤 다시 검토합니다. 본인의 권한 해제와 정지를 막고 키보드를 닫는 버튼을 제공합니다.

‘조회수·운영 현황’은 전체 계정/정지 계정/발송 대기·게시 카드 수와 전체 조회수·고유 조회자를 표시합니다. 카드 제목 검색과 페이지 조회, 관리자 카드 상세 이동을 지원합니다. 일반 카드 상세의 GET 요청으로 서버가 조회수를 집계하므로 별도 중복 집계 요청은 보내지 않습니다. API 요청은 로컬 응답 캐시를 우회해 서버에서 권한과 카드 열람을 확인합니다.

서버 배포에 V6/V7 DB 마이그레이션이 포함되어야 새 기능이 동작합니다. TestFlight 업로드만으로 운영 서버를 배포하지는 않습니다. API 계약은 `server/docs/admin-operations.md`를 참조합니다.

검증 결과:
- 서버 `AdminOperationsTests` 5개 통과: 관리자 접근 제어, 권한/정지 변경·기존 세션 반영, 변경 이력, 조회수 집계. 최신 main `d8ec119`의 Server CI 및 EC2 publish/deploy 작업도 성공한 것을 확인했습니다.
- 신규 사용자 관리·조회수 HTTP UI 시나리오 2개 통과: 검색/필터/페이지, 자기 계정 보호, 409 충돌 후 재조회와 최신 version 저장, 권한·정지 변경·USER 이력, 운영 통계와 카드 상세 이동. 요청의 세션 쿠키·CSRF·본문도 검사했습니다.
- 검수/게시 변경, ADMIN↔USER 탭 갱신, publicationDate가 null인 READY 카드 편집·공개 상태 변경 차단의 UI 시나리오 3개 통과.
- 일반 사용자 HTTP UI의 12개 요청 항목(카드 상세 열람, 검색/필터, 좋아요/읽음, 공지, 닉네임/프로필, 알림/온보딩) 통과.
- 라이트/다크 로그인과 미리보기 인자 우회 차단 3개 UI 테스트, 세 제공자 challenge 경로와 APIClient 인증/세션/CSRF/multipart 회귀 테스트 통과.
- 운영 HTTPS health-check와 로그인 제공자 응답은 정상입니다. 인증된 운영 관리자 API의 실제 데이터 변경, 이번 빌드의 실기기 소셜 로그인·푸시 수신은 재검증하지 않았습니다.

새 관리자 화면 캡처는 로컬 `.local/build5-admin-evidence/`에 보관합니다. UI 검증 데이터는 별도 HTTP fixture이며 앱에는 포함되지 않습니다.


### 2026-10-23 AI 이론 승인 정책 반영 — 1.0 (6)

빌드 5 업로드 직후 추가된 최신 main `de1868b`(PR #29)를 통합했습니다. AI 이론은 승인 즉시 아카이브에 공개되고, 뉴스는 최초 발송 전까지 READY 상태로 유지됩니다. 앱 검수 화면의 승인 안내를 선택한 콘텐츠 유형에 따라 표시합니다. 요청/응답 경로와 형식은 동일하며 서버가 반환한 게시 상태를 따릅니다. 기존 발송 대기 AI 이론은 V8 마이그레이션이 처리합니다.

빌드 5는 16:05 KST 업로드 수락/처리 시작을 확인했으며, 이 정책 안내까지 반영한 빌드 6을 후속 배포합니다.

추가 검증: `NewsIngestionTests` 9개와 AI 이론 선택→승인→게시 카드 관리, READY 뉴스 공개 변경 차단 UI 시나리오 2개를 통과했습니다. 최신 main `de1868b`의 Server CI와 EC2 배포 성공도 확인했습니다. 빌드 5에서 검증한 사용자 관리·조회수·로그인·일반 사용자 흐름의 구현은 유지합니다.


빌드 6 배포 결과: 2026-10-23 16:14 KST에 Xcode가 `Upload succeeded`와 `Uploaded package is processing`을 반환했고 전송 메타데이터의 빌드 번호 6을 확인했습니다. 앱 코드 기준은 `efbc734`이며 최신 main `de1868b`(PR #29)를 포함합니다. Debug/Release 빌드, App Store IPA 서명과 Production APNs/Sign in with Apple, 운영 HTTPS 주소 검증을 통과했습니다. 시뮬레이터도 운영 API를 사용하는 빌드 6으로 복원했습니다.

아카이브·서명 검증·업로드 로그는 로컬 `.local/testflight/2026-10-23-build-6/`에 보관합니다. Apple 처리 완료·테스트 그룹 배정·테스터 설치와 이번 빌드의 실기기 소셜 로그인·푸시 수신은 아직 확인하지 않았습니다. 빌드 5 구현 커밋 `32cda6d`의 GitHub Server CI/iOS build도 통과했으며, 후속 커밋의 자동 검사는 별도로 실행됩니다.
