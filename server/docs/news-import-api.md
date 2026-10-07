# 뉴스 수집 · 검수 API 계약

## 인증과 수신

서버 실행 환경의 `N8N_INGEST_TOKEN`에 32자 이상의 무작위 비밀값을 넣는다. n8n HTTP Request 노드의 Header Auth Credential에 같은 값을 `Authorization: Bearer <값>`으로 저장한다. 개발 예시 URL은 `http://host.docker.internal:8080/internal/v1/card-news/import`이며 운영에서는 서버의 HTTPS 주소를 사용한다.

`POST /internal/v1/card-news/import`는 세션이나 CSRF 없이 **전용 Bearer 인증만** 사용한다. 이 키로 사용자·관리자 API에 접근할 수 없다. 키가 비어 있으면 수집 요청을 거부한다. n8n은 DB에 직접 연결하지 않는다.

```json
{
  "sourceTitle": "Original article title",
  "sourceUrl": "https://example.com/article",
  "publishedAt": "2026-10-07T00:00:00Z",
  "title": "한국어 카드뉴스 제목",
  "summary": "원문에 근거한 요약",
  "keyPoints": ["핵심 내용"],
  "titleImage": null,
  "slides": [
    {
      "sequence": 1,
      "title": "기사에 맞춘 제목",
      "content": "검수할 본문",
      "layout": "timeline",
      "image": {
        "url": "https://example.com/image.png",
        "origin": "SOURCE",
        "sourceUrl": "https://example.com/image-source",
        "credit": "이미지 제공자 및 사용 조건"
      }
    }
  ]
}
```

- 응답: `202 {"id":1,"status":"PENDING","cardNewsId":null}`. 검수 대기 상태에서는 공개 카드뉴스나 푸시 대상이 되지 않는다.
- 슬라이드는 1~10장이고 순서는 1부터 연속이어야 한다. n8n에서 약 5장으로 만들되 레이아웃은 기사마다 자유롭게 정한다. 서버는 고정된 슬라이드 양식을 강제하지 않는다.
- 기사 제목 최대 200자, 요약·슬라이드 본문 최대 5,000자. 원문 URL과 이미지 URL은 HTTPS다. 서버는 URL을 다운로드하거나 실행하지 않는다.
- 이미지는 선택 사항이다. `SOURCE`이면 출처 URL과 credit이 필수다. `GENERATED`는 생성 이미지이며 그 구분을 저장·반환한다. 생성 이미지 URL은 n8n 쪽에서 영구 저장소로 옮긴 후 보내야 한다. 출처 표기만으로 재사용 권한을 대신할 수 없으므로 검수자가 사용 조건도 확인한다.
- 동일한 원문 URL과 동일한 내용의 재전송은 기존 ID와 현재 상태를 반환한다. 같은 URL의 다른 내용은 `409`로 거부해 기존 검수 결과를 덮어쓰지 않는다. 원문 URL은 n8n에서 추적 파라미터를 정리해 일관되게 보내야 한다.
- 승인/반려한 초안은 이력으로 보존한다. 현재 API는 초안 수정·재검수를 지원하지 않는다.

## 관리자 검수

기존 관리자 로그인 세션을 사용하고 쓰기 요청에는 `X-CSRF-TOKEN`을 붙인다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/v1/admin/news-submissions?status=PENDING&page=0&size=20` | 대기 목록, `APPROVED`/`DENIED`도 조회 가능 |
| GET | `/api/v1/admin/news-submissions/{id}` | 전체 원문·슬라이드·출처·검수 이력 |
| POST | `/api/v1/admin/news-submissions/{id}/approve` | `{"contentType":"NEWS","categoryIds":[]}` |
| POST | `/api/v1/admin/news-submissions/{id}/reject` | `{"reason":"반려 사유"}` |

승인 시 하나의 **비공개 발송 대기 CardNews(`READY`)**와 슬라이드를 같은 트랜잭션에서 생성한다. 승인 시각은 서버가 기록하며 관리자가 발행일을 지정하지 않는다. 동시 승인이나 응답 유실 후 승인 재시도는 같은 카드뉴스 ID를 반환한다. 반려 이후 승인은 `409`다.

`contentType`은 필수이며 뉴스는 `NEWS`, AI 이론은 `AI_THEORY`다. 화면에 표시하는 카테고리 ID는 `categoryIds`로 함께 지정한다. 표시용 카테고리 이름을 바꿔도 발송 분류가 바뀌지 않도록 두 값을 분리한다. 이론도 같은 수집·검수 절차로 미리 준비하며, 승인 없이 자동 생성해 발송하지 않는다.

전날까지 승인된 미사용 뉴스 중 최신 1건을 자정에 확정하고, 뉴스가 없으면 승인된 이론을 사용한다. 첫 유효 발송을 시작할 때 `PUBLISHED`로 전환하고 실제 게시일을 기록한다. 관리자 상세 응답은 검수 `status`와 별개로 `publicationStatus`, `contentType`을 제공한다. 일반 목록·슬라이드·좋아요는 `APPROVED`이면서 `PUBLISHED`인 콘텐츠만 허용한다.

일반 카드뉴스 API에는 원문 및 이미지 출처, `contentType`, 최초 `publicationDate`가 추가된다. 기존 `titleImgUrl`, 슬라이드 `imgUrl`은 유지한다. 좋아요와 목록은 로그인한 사용자 기준이다.

## 공지사항 API

| 메서드 | 경로 | 요청 |
|---|---|---|
| GET | `/api/v1/notices`, `/api/v1/notices/{id}` | 로그인 사용자에게 게시된 공지만 제공 |
| GET | `/api/v1/admin/notices`, `/api/v1/admin/notices/{id}` | 비공개 포함 관리자 조회 |
| POST | `/api/v1/admin/notices` | `{"title":"공지","content":"본문","published":false}` |
| PUT | `/api/v1/admin/notices/{id}` | 동일한 형식으로 수정·게시·게시 취소 |
| DELETE | `/api/v1/admin/notices/{id}` | 삭제 |

목록은 0부터 시작하는 page와 1~100의 size를 받는다. 관리자 쓰기는 세션과 CSRF가 모두 필요하다. 본문은 일반 문자열로 저장하며 클라이언트는 HTML로 직접 실행하지 않고 안전하게 표시한다.
