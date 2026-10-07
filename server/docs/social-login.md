# 모바일 소셜 로그인 계약

기존 Google 브라우저 로그인(`/oauth2/authorization/google`)은 유지한다.
새 API는 Google/Kakao/Apple **네이티브 SDK가 발급한 ID 토큰**을 검증하고 기존 세션 쿠키로 교환한다.
서버 자체 JWT를 새로 발급하지 않는다. 브라우저 전용 Apple 로그인 리다이렉트 구현과는 별개다.

## 앱 호출 순서

1. `GET /api/v1/auth/csrf`: 익명 세션 쿠키를 보관하고 응답의 headerName/token을 이후 POST에 사용한다.
2. `GET /api/v1/auth/social/providers`: 준비된 제공자만 표시한다. 기본값은 모두 비활성이다.
3. `POST /api/v1/auth/social/APPLE/challenge` (또는 `GOOGLE`, `KAKAO`): CSRF 헤더와 같은 쿠키를 보낸다.
4. 응답의 `nonce`를 해당 SDK/OIDC 로그인 요청의 nonce에 **그대로** 전달한다. 유효기간 5분이며 새 요청은 이전 nonce를 교체한다.
5. `POST /api/v1/auth/social/APPLE`에 `{"idToken":"SDK의 ID 토큰","nickname":"선택 닉네임"}`과 같은 쿠키/CSRF 헤더를 보낸다.
6. 응답의 새 `JSESSIONID` 쿠키와 `csrfHeaderName`/`csrfToken`을 사용한다. 로그인 시 세션 ID와 CSRF 토큰이 교체된다.

challenge는 한 번만 사용한다. 잘못된 토큰·만료·nonce 불일치로 실패하면 새 challenge부터 시작한다.
access token, authorization code, provider 사용자 ID를 ID 토큰 자리에 보내지 않는다.
앱 API 클라이언트가 쿠키를 유지해야 한다. 외부 브라우저의 쿠키를 앱에 복사하는 흐름이 아니다.
출처가 같은 이메일이라는 이유로 Google/Kakao/Apple 계정을 합치지 않고 제공자+subject로 구분한다.
nickname은 표시 이름이며 권한이나 계정 식별자로 사용하지 않는다.

## 제공자 설정

| 제공자 | 활성화 | 허용 audience | 준비할 항목 |
| --- | --- | --- | --- |
| Google | `GOOGLE_NATIVE_LOGIN_ENABLED` | `GOOGLE_NATIVE_CLIENT_IDS` | SDK의 서버용 client ID와 정확히 일치하는 값 |
| Kakao | `KAKAO_LOGIN_ENABLED` | `KAKAO_CLIENT_IDS` | 카카오 로그인/OpenID Connect 활성화, 사용하는 SDK의 앱 키에 해당하는 audience |
| Apple | `APPLE_LOGIN_ENABLED` | `APPLE_CLIENT_IDS` | Sign in with Apple capability, 네이티브 앱 Bundle ID 또는 사용하는 Services ID |

복수 audience는 쉼표로 구분한다. 임의 값을 허용하지 않으며 활성화했는데 값이 비면 서버 시작을 거부한다.
Apple 공개키로 ID 토큰을 검증하는 이 API에는 `.p8` 개인 키가 필요하지 않다.
Apple authorization code 교환·refresh token 관리·연결 해제 API까지 추가할 때는 별도 서버 키와 구현이 필요하다.
현재 회원 탈퇴는 서비스 계정 삭제이며 제공자 동의 철회까지 수행하지 않는다. 앱 출시 전 해당 운영 정책을 확정한다.

서명(RS256), 고정 issuer, 허용 audience/azp, 만료, issuedAt, 일회용 nonce를 검사한다.
JWK 주소는 서버 코드에 고정되어 요청자가 외부 주소로 바꿀 수 없다.
실제 개발자 등록과 앱 소스가 아직 없으므로 모의 인증 테스트와 실제 제공자 로그인 테스트를 구분한다.

공식 근거: [Kakao ID 토큰 검증](https://developers.kakao.com/docs/en/kakaologin/utilize),
[Apple 인증과 nonce](https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple),
[Spring JWT 검증](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
