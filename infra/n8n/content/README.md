# AI 이론 대체 콘텐츠

`ai-theory-starter.json`은 공식 기술 문서를 참고해 작성한 한국어 이론 초안 10건(각 5장)이다.
각 초안의 `sourceTitle`과 `sourceUrl`에서 근거 문서를 확인한다. 원문 이미지는 복제하지 않고 개념 설명을 텍스트 슬라이드로 구성했다.
`preparedAt`은 이 초안의 작성일이며 참고 문서의 최초 발행일이 아니다. 수집 API의 `publishedAt`에도 이 작성일을 사용한다.

AI, 머신러닝, 딥러닝, 생성형 AI, LLM, 토큰, 임베딩, RAG, 환각, 에이전트를 다룬다.
검수자는 내용과 원문을 확인하고 필요하면 기존 초안 편집 API로 수정한다.
승인 요청에는 `{"contentType":"AI_THEORY","categoryIds":[]}`를 사용한다. 수집만으로 승인·게시되지 않는다.

```sh
node infra/n8n/import-theory-starter.mjs --check
# 전용 수집 토큰을 stdin으로 전달한다. 토큰을 명령 인수나 Git에 넣지 않는다.
node infra/n8n/import-theory-starter.mjs https://aiknow.kro.kr
```

동일 파일의 재수입은 같은 원문 URL·내용을 사용하므로 기존 ID를 반환한다.
운영에서 내용을 편집한 뒤에는 원본 파일을 바꿔 재수입하지 않고 [관리자 편집 API](../../../server/docs/editorial-operations.md)를 사용한다.

서버의 대체 발송은 전날까지 승인된 미사용 뉴스를 우선하고, 없으면 승인된 이론 중 미사용 → 가장 오래전에 사용한 순서로 선택한다.
관리자 승인과 운영 발송/FCM 설정이 완료되어야 실제 게시·발송된다. n8n의 `fallback` 결과는 게시 완료를 뜻하지 않는다.
