# 사용자 시간에 맞춘 뉴스 발송

사용자마다 스케줄러를 만들지 않고, 매분 Spring Batch `dailyNewsJob`을 실행한다. 기본 시간대는 `Asia/Seoul`이다.

```mermaid
flowchart LR
    A[매분 스케줄러] --> B[Spring Batch Job]
    B --> C[알림 동의 · 설정 시간 확인]
    C --> D[오늘 승인된 뉴스 선택]
    D --> E[기기별 news_delivery 저장]
    E --> F[발송 권한 재확인 · 행 잠금]
    F --> G[FCM: Android / iOS]
    G --> H[성공 기록 / 실패 재시도]
```

- `PUT /api/v1/notification-setting`에 `{"settingTime":"09:30:00","isAllowed":true}`를 보내면 매일 09:30 이후 처리한다. 실제 수신 시각은 배치 대기열과 OS/네트워크 상태에 영향을 받는다.
- 오늘 발행일의 승인된 뉴스 중 ID가 가장 큰 한 건을 선택한다. 정해진 시간에 뉴스가 없으면 이후 승인되는 뉴스를 같은 날 보낸다. 다음 날에는 전날 미발송분을 취소한다.
- 사용자·기기·한국 날짜의 유니크 제약과 잠금으로 발송 대상을 중복 생성하지 않는다. 같은 사용자의 Android와 iOS 모두 등록되어 있으면 양쪽에 보낸다.
- 발송 직전 알림 동의, 토큰 활성 상태, 기기 소유자, 사용자 존재, 뉴스 승인 상태를 다시 확인한다. 시간 설정을 뒤로 옮겼으면 새 시간까지 미룬다.
- 기기 한 대의 실패는 다른 기기의 재발송을 유발하지 않는다. 일시 오류는 1분부터 지수 간격으로 최대 5회 시도한다. FCM `UNREGISTERED`는 토큰을 비활성화한다. 토큰이 이미 교체됐으면 새 토큰을 비활성화하지 않는다.
- 외부 FCM 호출은 DB 트랜잭션 밖에서 한다. 처리 중인 작업은 기본 5분의 임대로 보호하며 서버 장애 후 회수한다. 접수 직후 서버가 중단되면 중복 수신할 수 있으므로 앱은 `notificationId`로 중복 처리한다. 정확히 한 번 전달은 보장하지 않는다.
- `SENT`는 FCM 접수 성공이며 실제 사용자 열람을 의미하지 않는다. 발송은 기본 100건씩 처리하고 다음 실행에서 나머지를 처리한다. 처리량이 커지면 발송 지연을 측정하고 워커 확장을 검토한다.

## 실행 설정

`server/.env.example`을 참고해 환경변수를 IDE 또는 실행 환경에 주입한다. Spring은 `.env`를 자동으로 읽지 않는다. Firebase 프로젝트에서 Android/iOS 앱을 등록하고 iOS APNs 키를 연결해야 한다. 두 앱 모두 같은 Firebase 프로젝트의 FCM 등록 토큰을 서버에 등록한다.

```dotenv
NEWS_BATCH_ENABLED=true
FCM_ENABLED=true
FCM_CREDENTIALS_PATH=/run/secrets/firebase-service-account.json
NEWS_TIME_ZONE=Asia/Seoul
NEWS_BATCH_CRON=0 * * * * *
BATCH_SCHEMA_INIT=never
```

서비스 계정 JSON은 저장소에 넣지 않고 읽기 전용 파일로 제공한다. 경로를 비우면 Google Application Default Credentials를 사용한다. 기본 설정에서는 자동 발송이 꺼져 있다. `NEWS_BATCH_ENABLED=true`인데 FCM이 꺼져 있으면 잘못된 운영 설정으로 간주해 시작을 거부한다.

MySQL 최초 배포 전에 같은 데이터베이스에 `docs/sql/spring-batch-mysql.sql`을 **한 번만** 적용한다. Spring Batch 6.0.5 배포본의 공식 스키마다. 기존 Batch 테이블이 있다면 버전에 맞는 마이그레이션을 사용한다. 테스트 H2에서는 자동 생성한다. 애플리케이션 엔티티는 로컬 `ddl-auto=update`에서 생성되며 운영에서는 별도 스키마 변경 절차가 필요하다.

운영에서는 `news_delivery`의 FAILED 및 오래된 PENDING/PROCESSING, Batch 실패 기록을 모니터링한다. 이력 보관 기간을 정하고 완료된 Batch/발송 이력만 보관 정책에 따라 정리한다. 실행 중인 이력을 삭제하지 않는다.

통합 테스트는 실제 Batch 작업, 양 플랫폼, 동의 해제, 재시도, 만료 토큰, 임대 회수와 소유권 변경을 검증한다. 실제 FCM 수신은 서비스 계정 및 물리 기기로 별도 확인해야 한다.
