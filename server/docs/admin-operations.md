# 유저 관리와 조회수

모든 관리자 API는 관리자 세션이 필요하고 변경 요청은 CSRF 헤더도 필요합니다.

| 기능 | API |
| --- | --- |
| 계정 검색 | GET /api/v1/admin/users?query=&role=USER&suspended=false&page=0&size=20 |
| 계정 상세 | GET /api/v1/admin/users/{id} |
| 권한·정지 변경 | PATCH /api/v1/admin/users/{id} |
| 계정 변경 이력 | GET /api/v1/admin/editorial/history?resourceType=USER&resourceId={id} |
| 운영 요약 | GET /api/v1/admin/analytics/summary |
| 카드별 조회수 | GET /api/v1/admin/analytics/cards?query=&page=0&size=20 |

변경 요청은 `{"version":0,"role":"USER","suspended":true,"reason":"운영 검토"}`입니다.
조회한 version과 다르면 409를 반환하므로 새로 조회한 뒤 검토합니다.
본인의 관리자 권한 해제/정지는 차단하며 관리자 변경을 직렬화합니다.
기존 세션도 매 요청마다 DB의 권한과 정지 상태를 확인합니다. 정지 시 세션을 종료하며,
신규 로그인도 차단합니다. 해제 후 다시 로그인할 수 있습니다.
정지 계정은 새 알림 계획 및 발송 직전 검사에서 제외합니다. 이미 전달된 알림은 회수하지 않습니다.

조회수는 공개된 카드의 GET /api/v1/cardNews/{id} 상세 응답에 대해 집계합니다.
한 계정이 같은 카드를 30초 이내에 반복 요청하면 1회입니다. 30초 이후 다시 열면 전체 조회수만 늘어납니다.
고유 조회자는 카드별 계정 수이며 전체 요약에서는 카드 간 중복 계정을 한 번만 셉니다.
목록·오늘 카드 요약·관리자 미리보기는 조회수에 포함하지 않습니다. 읽기 완료(card_read)와 별도입니다.
V7 배포 이후부터 집계하며 이전 열람 횟수를 추정하지 않습니다.
계정 탈퇴 후에도 개인 식별 정보 없는 숫자 계정 ID 기반 집계는 보존합니다.

iOS 관리자 메뉴에서 계정 목록/검색/수정과 조회수 현황을 제공합니다.
새 화면은 앱 업데이트가 필요합니다. 기존 앱의 상세 조회도 서버 배포 이후 집계됩니다.
뉴스는 승인 시 READY이며 publication_date는 첫 발송 때 지정됩니다(V6).
AI 이론은 승인 즉시 PUBLISHED로 아카이브에 공개되며, 기존 승인된 READY 이론도 V8에서 공개합니다.
검수 대기·반려·숨김 콘텐츠는 자동 공개하지 않습니다.
# 자동화·발송 운영 알림

`GET /api/v1/admin/alerts?page=0&size=20`은 ADMIN 전용 운영 알림 목록을 최신 순으로 반환합니다. 새 초안 접수, 뉴스 승인(발송 후보), AI 이론 공개, 날짜별 발송 콘텐츠 선정, n8n 실패·미생성 종료를 기록합니다. 검수·승인은 기존과 같이 관리자가 수행하며 알림이 콘텐츠를 자동 승인하지 않습니다.

뉴스 승인 알림은 다음 날 **후보** 등록 안내입니다. 실제 한 건은 기존 일일 선정 정책에 따라 확정됩니다. 자동 발송이 꺼져 있으면 날짜를 약속하지 않고 꺼진 상태를 안내합니다.

운영 알림은 원래 작업과 같은 트랜잭션에 저장하고 활성 ADMIN 기기별 outbox를 만듭니다. FCM 발송 직전에 권한·정지·기기 소유권을 다시 검사합니다. 동일 이벤트/기기 중복 예약을 막고 일시 실패는 최대 5회 재시도합니다. FCM 재시도로 발생 가능한 중복 표시는 collapse key로 완화하며 정확히 한 번 전달을 보장하지 않습니다. 2일 넘은 미발송 알림은 푸시하지 않고 목록에는 남깁니다.

푸시 데이터는 `adminAlertId`, `type`이며 일반 카드 푸시의 `cardNewsId`와 구분합니다. iOS/Android는 관리자 알림 목록을 열고 관련 검수/카드 관리로 이동합니다. 일반 사용자의 일일 뉴스 수신 설정과 별개이며 OS 알림 권한과 등록된 기기가 필요합니다.

`POST /internal/v1/automation/events`는 기존 ingestion Bearer 인증을 사용합니다. 본문은 `workflowId`, `executionId`, `status` (`FAILED` 또는 `NO_CONTENT`), 선택적인 `stage`입니다. 응답/프롬프트/토큰/오류 스택을 보내지 않습니다. 동일 workflow/execution 조합은 한 번만 저장합니다.

FCM은 `FCM_ENABLED=true`와 `FCM_CREDENTIALS_PATH`의 서비스 계정 JSON이 필요합니다. iOS도 Firebase를 통해 APNs로 보내므로 Android 설정과 무관하게 이 서버 인증이 필요합니다. Firebase 프로젝트에 iOS APNs 인증도 설정되어 있어야 합니다. 파일이 없으면 발송을 켜지 말고 운영 알림 목록부터 사용합니다. 일일 회원 뉴스 발송은 별도 `NEWS_BATCH_ENABLED` 설정입니다.
