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
승인 시 READY 카드의 publication_date는 NULL이고 첫 발송 때 지정됩니다(V6).
