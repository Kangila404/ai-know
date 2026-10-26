# AI KNOW Android

Kotlin + Jetpack Compose로 만든 별도 Android 네이티브 클라이언트입니다. iOS SwiftUI 앱과 같은 서버·계정·콘텐츠 API를 사용합니다. iOS 코드를 Android에서 실행하거나 WebView로 감싸는 방식이 아닙니다.

## 개발 환경

- Android Studio에서 **이 디렉터리(`android/`)** 를 엽니다.
- JDK 17 이상(로컬 검증 JDK 21), Android SDK 36, Gradle wrapper 사용.
- 이 PC의 기존 IDE는 `D:\android_studio`, SDK는 `D:\Android\Sdk`입니다. 개인 SDK 경로는 Git에서 제외되는 `local.properties`에만 둡니다.
- AGP 8.13.2 / Kotlin 및 Compose compiler 2.2.21. 의존성 버전은 `app/build.gradle.kts`에 고정합니다.

```powershell
.\gradlew.bat :app:assembleDemoDebug :app:assembleLiveDebug :app:testLiveDebugUnitTest
.\gradlew.bat :app:lintLiveDebug
.\gradlew.bat :app:connectedDemoDebugAndroidTest
```

## 바로 화면 확인

Build Variants에서 **demoDebug** 선택 후 Run합니다. 앱 이름은 AI KNOW, 패키지는 `org.aiknow.android.demo`입니다. 첫 실행의 대화형 소개 → `데모 둘러보기` → 닉네임 → 알림·시간 설정 → 오늘/아카이브/마이/관리자를 확인합니다. 소개는 로그인 화면에서 다시 볼 수 있습니다. 데모는 별도 `src/demo` 소스와 로컬 이미지·JSON만 사용하며 운영 서버, 실제 로그인, 유료 생성, 푸시 발송을 호출하지 않습니다. 지원하지 않는 데모 쓰기는 성공처럼 표시하지 않습니다.

`demoRelease`는 비활성화돼 배포용 AAB에 데모 인증 코드가 포함되지 않습니다. 본 서비스는 **liveDebug/liveRelease**, 패키지는 **`org.aiknow.android`** 입니다. 설정이 없는 live 빌드에서는 로그인 제공자 버튼이 비활성화되고 준비 중임을 안내합니다. 인증 우회는 없습니다.

## 구조와 기능

- `ui/`: Material 3 NavigationBar, TopAppBar, 카드/목록, HorizontalPager, ModalBottomSheet, TimeInput, Dialog, 다크 모드, 시스템 글자 크기, 뒤로 가기.
- `data/`: OkHttp + Kotlin coroutines, JSON API, 세션 쿠키/CSRF 계약. 사용자 API 응답 캐시를 끄고 ADMIN 권한을 복귀 시 재조회합니다. 401은 로그아웃, 409는 재조회 안내, 실패한 쓰기는 자동 재전송하지 않습니다.
- `SessionVault`: 서버 JSESSIONID를 Android Keystore AES-GCM으로 암호화해 보관합니다. 계정 백업을 비활성화하며 OAuth ID 토큰은 자체 저장하지 않습니다. 쿠키는 API origin에만 보냅니다.
- `auth/`: Google Credential Manager, Kakao SDK. 서버 challenge nonce를 그대로 전달하고 ID token을 기존 세션 API에 교환합니다.
- `notifications/`: FCM 등록/해제, ANDROID 설치 UUID, Android 13+ 알림 권한, 알림의 cardNewsId로 상세 이동. 설정 전에는 Firebase를 강제로 초기화하지 않습니다.
- 이미지: 별도 Coil 3 메모리/디스크 캐시, 서버가 제공한 320/1280 preview, Commons 래스터 썸네일, 화면 크기에 맞는 디코딩, 다음 카드 한 장 미리 구성. 확대 화면은 원본을 사용하며 외부 이미지 호스트로 인증 쿠키를 보내지 않습니다.

사용자: 이미지 중심 오늘 카드와 펼쳐보는 핵심 포인트, 아카이브 검색/카테고리/좋아요/페이지, 스와이프 카드 넘김·출처·좋아요·자동 읽음, 닉네임/아바타, 알림 시간과 시간대별 프리셋, 공지, 로그아웃/탈퇴. 화면 테마는 휴대폰 설정을 따르며 앱 자체 테마 선택은 없습니다.

관리자: 콘텐츠 운영 / 서비스 관리 / 문제 해결 구성, 콘텐츠 유형 직접 선택 후 승인, 슬라이드 추가·삭제·출처 편집, 상태별 카드 관리와 카드별 조회수, 사용자 검색·권한/정지 필터, 공지/프로필 CRUD, 변경 전·후 이력. 생성 복구는 JSON 응답 또는 시스템 사진 선택기를 통한 PNG 업로드를 지원합니다(4096×4096, 변환 후 10MB 이하).

최신 iOS 기준과 의도적인 플랫폼 차이는 [IOS_PARITY.md](IOS_PARITY.md)에 기록합니다. 로고 원본은 iOS 저장소의 `AiKnowLogo`를 공유하며, 화면 구성 요소는 Android Material 3를 사용합니다.

## 실제 로그인 및 푸시 연결에 필요한 설정

`app-config.properties.example`을 `app-config.properties`로 복사하고 아래 **공개 클라이언트 식별자**를 입력합니다. OAuth secret/서버 내부 키/서비스 계정 JSON은 앱에 넣지 않습니다.

1. **Google**: 기존 iOS/서버와 같은 Google Cloud 프로젝트에 Android OAuth 클라이언트를 추가합니다. 패키지 `org.aiknow.android`, 테스트/배포 서명의 SHA-1/SHA-256을 등록합니다. `GOOGLE_WEB_CLIENT_ID`에는 **서버가 이미 허용하는 웹 클라이언트 ID**를 넣습니다. Android 클라이언트 ID를 넣는 항목이 아닙니다. 서버의 계정 연결 정책은 그대로 유지합니다.
2. **Kakao**: 기존 Kakao 앱에 Android 플랫폼을 추가하고 패키지/키해시를 등록합니다. 네이티브 앱 키를 `KAKAO_NATIVE_APP_KEY`에 넣고 OpenID Connect를 활성화합니다. 서버의 허용 audience에 해당 앱 키가 포함되는지 확인합니다.
3. **Firebase**: 기존 FCM 프로젝트에 Android 앱을 추가하고 다운로드한 `google-services.json`을 `app/google-services.json`에 둡니다(Git 제외). Google services plugin은 파일이 있을 때만 적용됩니다. 새 프로젝트를 별도로 만들면 서버 FCM 서비스 계정과 달라 전송이 실패할 수 있습니다.
4. **Play Console**: 앱 생성, 업로드 키와 Play App Signing 인증서 등록, 개인정보처리방침·Data safety·테스트 트랙 설정이 필요합니다. 실제 키가 없는 상태에서 임의 서명으로 스토어 배포하지 않습니다. `signingReport`로 개발 서명을 확인할 수 있습니다.

Apple 로그인은 iOS 네이티브 인증 전용입니다. Android에서 Apple 계정을 지원하려면 별도 웹 Services ID/리다이렉트/서버 인증 연결이 필요하며 현재 Android 로그인 버튼에는 노출하지 않습니다.

## 배포 범위와 주의사항

- `liveRelease`는 프로젝트만으로 production 서명이 생성되지 않습니다. 업로드 키를 로컬/CI secret으로 연결한 다음 `:app:bundleLiveRelease`로 AAB를 생성합니다. 비회원 기능은 이 변경의 서버를 먼저 배포한 뒤 iOS/Android 새 빌드를 배포해야 합니다. 이전 서버에서는 공개 조회도 401을 반환합니다.
- 운영 API는 `https://aiknow.kro.kr/`입니다. 실제 계정으로 승인·정지·삭제하면 운영 데이터가 변경됩니다. 자동 UI 테스트는 `demoDebug`에서만 실행합니다.
- 오늘 카드가 미게시(404)면 아카이브 이동을 안내합니다. 아카이브 이론 승인과 오늘의 발송 배치는 서로 다릅니다.
- 키 없이 가능한 검증: 컴파일, lint, HTTP 세션/CSRF/오류/이미지 URL 단위 테스트, 데모 UI. 실계정 로그인·푸시 수신·Play 업로드는 설정 후 별도 검증해야 합니다.
- 데모 이미지의 원본·저작자·라이선스는 `app/src/demo/assets/image-credits.json`과 카드 출처에 남겼습니다.

참고: [Compose](https://developer.android.com/develop/ui/compose), [Google Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Kakao Android](https://developers.kakao.com/docs/en/kakaologin/android).

## 검증 기록 (2026-10-26)

- `assembleDemoDebug`, `assembleLiveDebug`, `testLiveDebugUnitTest`, `lintLiveDebug` 통과. 단위 테스트 14개, lint 오류 0개. 세션/CSRF, 이미지 URL 정책, 편집 검증, 조회수 연결, PNG multipart, 온보딩 저장 순서·실패·재시도를 검사합니다. 일부 의존성 업데이트와 코드 스타일 권고 경고는 남아 있습니다.
- D드라이브의 Android Studio에서 프로젝트와 Compose 라이트/다크/큰 글씨 Preview를 열었습니다. D드라이브 SDK의 API 36 Pixel 7 에뮬레이터에서 데모 APK를 실행했습니다.
- `NavigationTest.readerAndEditorialJourneys`: 소개·재생/건너뛰기, 닉네임/시간 저장과 알림 미동의, 아카이브, 이미지 확대·출처 시트, 실제 터치 스와이프, 좋아요, 마지막 장 자동 읽음과 목록 갱신, 슬라이드 추가·삭제, 유형 선택 전 승인 차단, 승인 후 카드 관리 이동·조회수. 이미지 로딩 완료와 오류 자리표시자가 없음을 함께 검사합니다. 운영 데이터에는 접근하지 않습니다.
- `SystemThemeTest`: 예전 앱 테마 선호가 남아 있어도 시스템 Configuration의 라이트/다크 전환을 따르는지 검증합니다. 1.3배 글자 크기에서 핵심 포인트를 캡처하고 펼침 동작을 검사합니다. 두 UI 테스트는 API 36 에뮬레이터에서 실행합니다.
- 테스트가 앱 외부 파일 디렉터리에 `00-intro-first.png`, `00-onboarding-notifications.png`, `01-today.png`, `03-reader.png`, `08-system-dark-points.png` 등의 화면 증거를 저장합니다. 이 PC의 복사본은 저장소 루트 `.local/android-ui-evidence/files/`에 있습니다(Git 제외).
- Google/Kakao 실계정 인증, 실제 FCM 수신, 운영 관리자 변경, 서명한 Play 배포는 아직 검증하지 않았습니다. 위 설정을 준비한 뒤 실제 기기에서 확인해야 합니다.

데모 이미지 리소스는 숫자 ID 주소로 변환합니다. Coil 3에서 이름 기반 리소스 URI 지원이 제거된 내용은 [공식 변경 기록](https://github.com/coil-kt/coil/blob/main/CHANGELOG.md)에 설명되어 있습니다.

## 비회원 둘러보기 (2026-10-26)

로그인 화면에서 `로그인 없이 둘러보기`를 선택합니다. 데모에서도 같은 진입과 보호 동작을 확인할 수 있으며, `데모 둘러보기`는 기존 테스트 계정 진입입니다. Ethica iOS `AppSession.swift`의 공개 탭/기능별 로그인 안내 구조를 참고했습니다.

| 기능 | 비회원 |
| --- | --- |
| 오늘의 게시 카드, 아카이브 검색·카테고리 | 열람 가능 |
| 게시 카드의 슬라이드·이미지 확대·출처, 게시된 공지 | 열람 가능 |
| 좋아요, 좋아요 목록, 마이, 알림 설정 | 누를 때 로그인 안내, 취소하면 읽던 화면 유지 |
| 읽음 기록·개인 조회 이력·푸시 등록 | 저장/등록 요청을 보내지 않음 |
| 관리자, 검수 초안, READY/HIDDEN 카드, 미게시 공지 | 비공개 유지 |

비회원 상세 GET은 회원 조회수/고유 조회자에 포함되지 않습니다. 익명 계정을 만들지 않고, 응답의 `liked/read`는 false입니다. 로그인 성공/로그아웃/세션 만료 시 화면 상태를 분리해 다른 계정의 좋아요·프로필·관리 화면이 재사용되지 않습니다. 새 계정은 로그인 후 닉네임·알림 온보딩을 진행합니다. 비회원 이용 내역을 회원 기록으로 자동 이관하지 않습니다.

인트로는 화면의 이전 버튼을 제거하고 응답 버튼의 폭·색상을 유지합니다. 눌림 스프링과 문구 전환 애니메이션을 적용했고 시스템 애니메이션 비활성화를 따릅니다. Android 시스템 뒤로 가기는 유지합니다.

검증: 서버 테스트 88개 중 81개 통과, 환경/옵트인 조건으로 7개 건너뜀. Android 단위 테스트 14개와 API 36의 UI 테스트 3개 통과, liveDebug 빌드 및 lint 오류 0개. `GuestJourneyTest`는 공개 읽기·로그인 취소 후 위치 유지·로그인 전환·개인 API 요청이 없는 것을 검사합니다. 이 기록은 로컬 검증이며 운영 배포를 의미하지 않습니다.
