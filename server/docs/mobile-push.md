# Android / iOS 디바이스 등록

두 앱을 같은 Firebase 프로젝트에 등록하고 모두 **FCM 등록 토큰**을 서버에 보낸다.
iOS의 APNs 토큰을 직접 보내지 않는다. Firebase Console에 APNs 인증 키를 등록하고
iOS에서 Push Notifications capability 및 사용자 알림 권한을 설정해야 한다.
Android 13 이상도 알림 권한을 요청하며 `ai_news` 알림 채널을 앱에서 생성한다.

로그인 후, 앱 시작 및 Firebase 토큰 갱신 콜백에서 다음 API를 호출한다.
기존 세션 쿠키 인증과 `GET /api/v1/auth/csrf`의 CSRF 헤더를 사용한다.

```http
PUT /api/v1/device-tokens
Content-Type: application/json
X-CSRF-TOKEN: <csrf token>

{
  "installationId": "19c9f4f2-9ee4-4389-bb69-038853593c73",
  "platform": "IOS",
  "token": "<FCM registration token>"
}
```

`installationId`는 앱 설치마다 생성하여 보관하는 UUID다. 토큰이 바뀌어도 같은 ID를
보내면 기존 행의 토큰을 교체한다. 다른 계정으로 로그인하면 소유자를 현재 로그인
계정으로 변경한다. 한 계정에 여러 설치를 등록할 수 있다. 기존 앱의 호환성을 위해
ID 없는 요청도 지원하지만 새 앱은 반드시 ID를 보낸다.

로그아웃 전에 `DELETE /api/v1/device-tokens`에 현재 토큰을 보내 비활성화한다.
다른 사용자의 토큰은 비활성화할 수 없다. 푸시를 누르면 payload의 `cardNewsId`로
기사 화면을 열고, `notificationId`로 앱 내부의 중복 처리를 방지한다.

앱 바이너리 빌드·스토어 배포 및 Firebase/APNs 프로젝트 설정은 모바일 프로젝트의 작업이다.
서버는 등록 API와 플랫폼별 푸시 전송을 제공한다.

참고: https://firebase.google.com/docs/cloud-messaging/manage-tokens
