# 검수 수정과 복구

모든 경로는 관리자 세션과 변경 요청의 CSRF 헤더가 필요하다. n8n Bearer 키로는 접근할 수 없다.
GET으로 확인한 `version`을 변경 요청에 포함한다. 오래된 버전은 409이며 새로 조회해야 한다.

| 작업 | API |
| --- | --- |
| 초안 조회 | `GET /api/v1/admin/news-submissions/{id}` |
| 초안 편집 | `PUT /api/v1/admin/news-submissions/{id}` |
| 반려 초안 재검수 | `POST /api/v1/admin/news-submissions/{id}/reopen` |
| 게시글 관리 목록/조회 | `GET /api/v1/admin/card-news`, `GET /api/v1/admin/card-news/{id}` |
| 게시글 정정 | `PUT /api/v1/admin/card-news/{id}` |
| 비공개/복구 | `POST /api/v1/admin/card-news/{id}/hide`, `.../restore` |
| 변경 이력 | `GET /api/v1/admin/editorial/history?resourceType=SUBMISSION&resourceId=1` |
| 생성 기록 조회 | `GET /api/v1/admin/generations`, `GET /api/v1/admin/generations/{id}` |
| 보관된 텍스트 응답 복구 | `POST /api/v1/admin/generations/{id}/recover` |
| 보관된 생성 이미지 복구 | `POST /api/v1/admin/generations/{id}/recover-image` |

편집 요청은 `{"version":0,"reason":"정정 이유","draft":{...NewsImportRequest 전체...}}`이다.
원문 제목·URL·게시 시각은 변경할 수 없다. 뉴스 제목·요약·슬라이드·이미지를 정정한다.
재검수/비공개/복구는 `{"version":0,"reason":"사유"}`를 보낸다.
검수의 원본 payload와 idempotency hash는 보존하고 편집본을 별도 보관한다.
같은 n8n 요청을 재전송해도 편집 내용을 덮어쓰지 않으며, 이후 승인은 편집본을 사용한다.

최초 발송 전 READY 글을 강제로 공개하지 않는다. 비공개 글은 일반 조회·좋아요·슬라이드 접근 및 후속 발송에서 제외한다.
당일 이미 선택한 콘텐츠는 다른 것으로 바꾸지 않으므로 사용자 간 콘텐츠 일관성을 유지한다.
FCM에 이미 접수된 알림은 회수할 수 없다. 비공개로 취소된 발송을 복구 시 자동 재전송하지 않는다.
수정·승인·반려·재검수·비공개·복구는 관리자와 사유를 이력에 남긴다.

생성 응답 복구 요청은 `{"version":0,"reason":"검토 사유","responseJson":"보관된 전체 API 응답 JSON 문자열"}`이다.
원래 요청과 원래 응답은 보존하며 검토한 복구 응답을 별도 저장한다. 정상 검증 단계를 우회하지 않는다.
이미 검수 DB에 저장된 기사에는 이 복구 경로를 사용할 수 없다. 해당 초안을 편집한다.
이미지 복구는 multipart `file`, `version`, `reason`이다. n8n 실패 실행에 남아 있는 실제 PNG를 올린다.
서버가 파일을 검증·저장하고 실제 저장 URL을 기록하므로 임의 URL 문자열만 넣어 이미지 복구할 수 없다.
생성 예약을 시간 경과만으로 해제하거나 자동으로 유료 재호출하지 않는다.
