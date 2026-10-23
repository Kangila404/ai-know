# AiKnow iOS

SwiftUI 앱입니다. Google·Apple·Kakao 로그인은 ID token을 서버 세션으로 교환하며, 푸시 수신은 Firebase Messaging SDK로 연결되어 있습니다. 오늘의 카드·아카이브·상세·좋아요·읽음·공지·프로필·알림 설정은 서버 API를 사용합니다. 온보딩 안내 문구는 앱에 포함하며 완료 상태는 서버에 저장합니다.

**현재 Debug/Release 모두 실제 로그인을 사용합니다. 개발 미리보기와 가짜 ADMIN 계정은 제거했으며 서버가 ADMIN으로 응답하는 계정에만 관리자 탭이 표시됩니다.**

## 실행

1. 로컬 Firebase 설정 파일을 `AiKnow/AiKnow.xcodeproj/GoogleService-Info.plist`에 둡니다. Firebase의 iOS 앱 Bundle ID는 `org.aiknow.AiKnow`여야 합니다. 이 파일은 Git에서 제외됩니다.
2. Xcode에서 `AiKnow/AiKnow.xcodeproj`를 엽니다. Swift Package Manager가 FirebaseMessaging, GoogleSignIn 9.2.0, KakaoSDKUser 2.29.0과 관련 의존성을 내려받습니다. Package.resolved는 저장소에 포함해 SDK 버전을 고정합니다.
3. Signing & Capabilities의 Team은 `SS32PHC392`, Bundle ID는 `org.aiknow.AiKnow`로 설정되어 있습니다. Sign in with Apple, Push Notifications, Background Modes / Remote notifications가 프로젝트에 설정되어 있습니다. Xcode에 해당 Team 계정이 로그인되어 있어야 하며, 두 capability가 포함된 앱 전용 provisioning profile을 사용해야 합니다.
4. iPhone을 실행 대상으로 선택하고 실행합니다. 앱 진입 시 또는 마이 → 알림 설정에서 알림 권한을 허용합니다.

## 푸시 테스트

- Firebase Console → 프로젝트 설정 → Cloud Messaging → iOS 앱에 APNs 인증 키(.p8), Key ID, Team ID를 등록합니다. Apple Developer App ID에서도 Push Notifications가 활성화되어 있어야 합니다.
- Debug 앱의 마이 → 알림 설정 → FCM 테스트 토큰 복사를 누릅니다.
- Firebase Console의 Messaging 테스트 메시지 수신자에 토큰을 붙여 넣어 전송합니다. 전경에서는 배너·소리로 표시되고, 배경에서는 iOS가 알림을 표시합니다. 알림을 누르면 앱이 열리며 최근 연 알림 제목을 알림 설정에서 확인할 수 있습니다.
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

사용자 관리 API(전체 사용자 조회, 권한 변경, 정지)는 현재 서버에 없어서 준비 중으로 표시합니다. 프로필 이미지 URL은 현재 서버가 20자로 제한하므로 해당 제한을 안내하고 준수합니다. `/internal/v1/...` 수집·생성 예약·이미지 업로드는 n8n/서버 전용 인증을 사용하는 내부 API이며 앱에 내부 키를 넣거나 직접 노출하지 않습니다. 앱의 이미지 복구는 ADMIN 인증이 적용된 `/api/v1/admin/generations/{id}/recover-image`를 사용합니다.

마이 → 프로필 편집에서 닉네임과 서버에 등록된 프로필 이미지를 변경할 수 있습니다. 마이 → 화면 모드에서 시스템/라이트/다크를 선택합니다. 카드·입력창·본문·관리자 Form은 시스템 배경/텍스트 색상으로 전환됩니다.

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
- 사용자 전체 목록/정지/권한 변경은 현재 서버에 API가 없어 준비 중입니다. 프로필 이미지는 서버에서 등록한 항목을 선택하는 방식이며 관리자 이미지 주소의 20자 제한을 유지합니다.

관리자 API UI 검증은 `python3 ios/tests/test_admin_ui.py --device <UDID>`로 실행합니다. 별도 HTTP 서버에서 세션 쿠키·CSRF·ADMIN 역할·버전·변경 사유·요청 본문을 검사하며 검수, 카드 공개 상태, 공지/프로필 CRUD, 생성 복구와 탭 권한 갱신을 조작합니다. 실제 운영 계정·콘텐츠는 변경하지 않습니다. `--only <test method>`로 특정 시나리오만 재실행할 수 있습니다. 기존 일반 사용자 흐름은 `test_content_ui.py`로 검증합니다. 테스트 후 일반 Xcode 빌드로 테스트용 API 주소를 복원해야 합니다.


소셜 로그인은 공식 심볼과 네이티브 텍스트를 조합합니다. Google/카카오는 ‘Google 로그인 / 카카오 로그인’ 문구, 54pt 높이, 16pt Medium 글꼴, 20pt 심볼, 12pt 간격을 공유합니다. Google은 라이트/다크 색상을 전환합니다.

- Google: [브랜딩 가이드](https://developers.google.com/identity/branding-guidelines)의 [공식 G 심볼 PNG](https://developers.google.com/static/identity/images/g-logo.png)를 변경 없이 사용합니다. [Google Fonts의 Google Sans](https://github.com/google/fonts/tree/main/ofl/googlesans)를 두 버튼에 공통으로 적용하며 한국어는 시스템 글꼴로 대체됩니다. SIL Open Font License를 앱 번들에 함께 포함합니다.
- 카카오: [공식 디자인 리소스](https://developers.kakao.com/tool/resource/login)의 [한국어 완성형 SVG](https://developers.kakao.com/tool/images/resource/preview/login-complete-ko.svg)에서 말풍선 심볼 path를 분리했습니다. 심볼의 형태·비율·색상은 유지합니다.
- Apple: [ASAuthorizationAppleIDButton](https://developer.apple.com/documentation/authenticationservices/asauthorizationappleidbutton)의 로그인 타입을 사용합니다. 버튼 높이는 54pt이며 문구/서체는 Apple이 제공합니다. 한국어 지원 언어를 선언해 한국어 기기에서 시스템 로그인 문구도 현지화됩니다. 동작은 기존 `AuthSession.signInApple()`의 서버 challenge → nonce → 인증 → 세션 처리에 연결됩니다.

`python3 ios/tests/test_login_ui.py --device <UDID>`는 세 제공자를 반환하는 별도 HTTP fixture에서 밝은/어두운 로그인 화면을 캡처하고 각 버튼이 해당 제공자의 challenge API와 CSRF 처리로 연결되는지 검사합니다. 실제 Google·카카오·Apple 계정 로그인 성공을 대신하는 검증은 아닙니다. 운영 로그인 버튼 노출은 서버의 `/api/v1/auth/social/providers` 응답을 따릅니다.


2026-10-23 검증: 관리자 검수·게시 숨김/복원, 공지/프로필 CRUD, 생성 응답 복구, ADMIN↔USER 탭 갱신의 4개 UI 시나리오는 수정 후 개별 재실행을 포함해 통과했습니다. 일반 사용자 HTTP UI 테스트도 11개 요청 항목을 모두 검증했습니다. APIClient 인증·세션·CSRF·multipart 회귀 테스트와 `node scripts/validate-infra.mjs`도 통과했습니다. 운영 `https://aiknow.kro.kr/health-check`와 로그인 제공자 목록은 정상 응답했으며 현재 제공자는 GOOGLE/KAKAO입니다. 실제 운영 관리자 계정으로 쓰기, 실제 이미지 복구 업로드, 이번 빌드의 실기기 소셜 로그인·푸시 수신은 아직 재검증하지 않았습니다.

로그인 버튼 UI 테스트는 라이트/다크 2개 시나리오와 APPLE/GOOGLE/KAKAO challenge 연결을 통과했습니다. Google SVG 렌더링 문제는 공식 PNG 원본으로 교체했으며, 마지막 일반 Debug 빌드에서 운영 API 주소와 최종 라이트/다크 버튼 렌더링을 별도로 확인했습니다. 화면 캡처는 `.local/login-branding-evidence/final-production-{light,dark}.png`에 있습니다.


### Xcode에서도 실제 로그인과 서버 관리자 권한 사용

Xcode Debug와 TestFlight 모두 실제 소셜 로그인/서버 세션을 사용합니다. 개발 미리보기 버튼, 실행 인자, 가짜 사용자/콘텐츠 데이터 및 API 응답 대체 코드를 제거했습니다. 기존 `--preview-admin`/`--preview-user` 인자를 넣어도 로그인 화면을 우회하지 않습니다.

- `/api/v1/users`의 `userRole == ADMIN`일 때만 관리자 탭이 맨 오른쪽에 표시됩니다. Xcode로 실행했다는 이유로 관리자 권한을 부여하지 않습니다.
- 로그인 후 계정 권한을 바꿨다면 앱을 다시 활성화하거나 마이 화면을 당겨 새로고침합니다. 오래된 서버 세션의 권한이 남아 관리자 API가 403을 반환하면 로그아웃 후 다시 로그인합니다.
- 현재 최신 iOS 코드는 `develop` 기준입니다. 다른 Mac에서는 실행 브랜치/커밋과 API 주소를 확인합니다.
- `test_login_ui.py`는 라이트/다크 버튼과 인증 challenge 연결, 이전 미리보기 인자로도 로그인 우회가 되지 않는지 검사합니다. `test_admin_ui.py`는 별도 HTTP fixture의 ADMIN↔USER 응답에 따라 탭이 갱신되는지 검사합니다. 테스트용 HTTP 서버는 앱 배포물에 포함되지 않습니다.

미리보기 삭제 후 검증: 인증/세션/CSRF/multipart APIClient 회귀 테스트, 라이트/다크 로그인 화면과 기존 인자를 통한 우회 차단 3개 UI 테스트, HTTP ADMIN→USER→ADMIN 응답에 따른 탭 갱신 테스트를 통과했습니다. 실제 다른 사용자의 운영 계정 권한은 조회하거나 변경하지 않았습니다.
