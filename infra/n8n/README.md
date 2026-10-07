# n8n 워크플로 배포

이미지 검색 범위·선택 기준·생성 조건은 [이미지 처리 구조](IMAGES.md)를 참고하세요.

로컬 폴더와 배포용 NAS의 환경변수·실행 방법은 [로컬/배포 환경](ENVIRONMENTS.md)을 참고하세요.

생성 전 DB 중복 검사·근거 ID 선택·텍스트 응답 캐시는 [생성 및 재실행 구조](RELIABILITY.md)를 참고하세요.

Git의 JSON은 배포할 워크플로 원본이고, 실제 n8n 데이터는 `n8n_data` 볼륨에 저장됩니다.
컨테이너 시작/재시작과 JSON 배포를 분리합니다. **일반적인 `docker compose up -d`는 JSON을 가져오거나 실행하지 않습니다.**

```text
infra/
  .env                              # 서버별 설정·암호화 키, Git 제외
  docker-compose.yml
  n8n/
    workflows/
      ai-know-news-draft.json        # Git으로 관리하는 배포 원본
    deploy-workflow.mjs              # 비교·백업·가져오기
    deploy.ps1                      # Windows 배포 명령
    deploy.sh                       # Linux 배포 명령
```

## 최초 설치

저장소 루트에서 실행합니다. `infra/.env.example`을 참고해 `infra/.env`를 먼저 작성합니다.
기존 `.env`에는 `.env.example`의 `AIKNOW_*` 배포 설정을 추가합니다.
도구도 n8n과 같은 `N8N_IMAGE`, `N8N_ENCRYPTION_KEY`, 시간대 설정을 사용합니다.
검증한 n8n 버전은 **2.42.3**이며 서버의 `N8N_IMAGE`는 검증한 버전으로 고정하는 것을 권장합니다.

```sh
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
```

n8n 웹 화면에서 처음 한 번 Owner 계정을 설정한 뒤 워크플로를 가져옵니다.

Windows PowerShell:

```powershell
.\infra\n8n\deploy.ps1 check
.\infra\n8n\deploy.ps1 apply
```

Linux 서버:

```sh
sh infra/n8n/deploy.sh check
sh infra/n8n/deploy.sh apply
```

`check`는 현재 상태를 비교하고, `apply`가 배포를 수행합니다. SQLite DB를 안전하게 갱신하기 위해
`apply`는 실행 중인 n8n을 잠시 정지하고, 성공·실패 여부와 관계없이 다시 시작합니다.
MySQL과 샌드박스는 정지하지 않습니다. 배포 중에는 n8n 화면을 편집하지 않고, 배포 명령도 한 번에 하나만 실행합니다.
명령 실행 전에 진행 중인 워크플로 실행이 없는지 확인하세요.

최초 가져오기에서는 개발 PC의 Credential 참조를 제거합니다. 새 서버의 n8n에서 OpenAI Credential을 만들고
해당 노드에 연결하세요. API 키는 JSON이나 Git에 넣지 않습니다.
이후 배포에서는 같은 ID·종류의 노드에 연결된 서버 Credential을 유지합니다.

가져온 워크플로는 **게시되지 않은 초안**입니다. JSON 한 개에 환경별 `.env` 값을 적용해서 가져오며 오전 9시 트리거는 비활성화되어 있습니다.
로컬 예제는 `dryRun: false`, `allowImageGeneration: true`, 생성 최대 1장입니다. 배포 예제는 점검 모드로 시작합니다.
수집만 점검하려면 `AIKNOW_DRY_RUN=true`로 바꾸고 `apply`하세요. `false`로 수동 실행하면 OpenAI 호출 비용이 발생합니다.
환경변수를 수정한 후에는 컨테이너 재시작만 하지 말고 `deploy.ps1 apply` / `deploy.sh apply`로 워크플로에도 반영합니다.
실제 자동 실행은 별도 작업입니다. 내용을 확인하고 수동 테스트한 뒤, 의도한 설정으로 바꾸고 스케줄 노드를 활성화해 Publish해야 합니다.
배포 도구 자체는 뉴스 수집, OpenAI 호출, 이미지 생성 또는 서버 API 전송을 실행하지 않습니다.

## JSON 변경 반영

n8n에서 수정한 워크플로를 내보내 `workflows/ai-know-news-draft.json`을 교체하고 Git에 반영합니다.
서버에서 최신 파일을 받은 뒤 위의 `check`, `apply`를 다시 실행하면 됩니다.
업데이트할 때 JSON의 워크플로 `id`와 기존 노드의 `id`를 유지하세요.

| 결과 | 의미 |
| --- | --- |
| `IMPORT` | 최초 등록 또는 변경된 JSON을 같은 워크플로 ID에 반영 |
| `ADOPT` | 서버 내용과 JSON이 같아서 가져오기 없이 배포 기준만 등록 |
| `UNCHANGED` | 마지막 배포와 JSON이 같아서 기존 서버 내용을 유지 |
| 충돌 오류 | 서버 웹 편집 내용과 새 JSON이 달라서 덮어쓰기 중단 |

비교 대상은 JSON에 환경 설정을 적용한 결과입니다. `UNCHANGED`이면 서버에서 이후 수정한 내용, 게시 상태, 실행 이력을 유지합니다.
변경된 JSON을 실제로 가져오면 기존 게시가 해제됩니다. 변경 내용을 검토한 뒤 n8n에서 다시 Publish하세요.
워크플로 static data에 저장된 처리 URL 이력은 가져오기 전의 값을 유지합니다.

충돌이 나면 먼저 서버의 워크플로를 내보내 차이를 확인합니다.
확인 후 Git의 JSON으로 교체하려는 경우에만 아래 명령을 사용합니다. 교체 전 내용은 자동 백업합니다.

```powershell
.\infra\n8n\deploy.ps1 apply -Replace
```

```sh
sh infra/n8n/deploy.sh apply ai-know-news-draft.json --replace
```

다른 JSON도 파일명을 두 번째 인수로 지정해 개별 배포할 수 있습니다. 한 파일에는 하나의 워크플로 객체를 저장합니다.
`n8n-workflows` 서비스는 `tools` 프로필에 속한 일회성 도구이므로 상시 실행 컨테이너가 추가되지 않습니다.
서비스를 직접 실행하기보다는 n8n 정지·재시작을 처리하는 위 스크립트를 사용하세요.

## 저장과 복구

- 실행 중인 워크플로, 계정, Credential, 배포 기록은 `n8n_data` 볼륨에 저장됩니다.
- 일반 `down` 후 `up -d` 또는 서버 재부팅 시 같은 볼륨을 사용하면 기존 상태를 사용합니다. `down -v`는 데이터를 삭제하므로 사용하지 않습니다.
- 이전 워크플로 백업: 컨테이너 내부 `/home/node/.n8n/aiknow-deploy/backups/`.
- 마지막 배포 기준: `/home/node/.n8n/aiknow-deploy/<파일명>.state.json`.
- 자동 백업은 교체 직전 워크플로의 백업입니다. 전체 장애 복구에는 `n8n_data` 볼륨과 기존 `N8N_ENCRYPTION_KEY`를 별도로 함께 보관해야 합니다.

백업 파일을 로컬로 가져오는 예시:

```sh
docker cp aiknow-n8n:/home/node/.n8n/aiknow-deploy/backups ./n8n-workflow-backups
```

백업은 CLI 형식의 배열입니다. 복구하려면 백업의 첫 번째 워크플로 객체를 배포 JSON으로 저장하고
`check` → `apply --replace` 절차로 반영합니다(Windows는 `-Replace`). 이때도 현재 서버의 Credential과 처리 URL 이력을 유지합니다.
실패한 배포는 자동 롤백하지 않으며, n8n을 다시 시작하고 오류를 반환합니다. 오류가 나면 상태와 백업을 확인한 뒤 다시 배포하세요.

## 도구 검증

Node.js가 있는 환경에서 저장소 루트 기준:

```sh
node --test infra/n8n/deploy-workflow.test.mjs
```

배포 도구 테스트는 OpenAI나 뉴스 워크플로를 실행하지 않습니다.

## Spring 수집 API 연결

메인 워크플로의 마지막 경로는 다음과 같습니다.

```text
초안 검증 완료 → 생성 이미지 업로드(필요 시) → 이미지 URL 반영 → 서버 요청 변환 → 서버 검수 대기 저장 → 저장 확인 후 URL 기록
```

- 로컬 주소: `http://host.docker.internal:8080/internal/v1/card-news/import`.
- Spring의 `server/.env`에 `N8N_INGEST_TOKEN`을 설정하고 **서버 프로세스가 해당 환경변수를 읽도록 실행**해야 합니다. 파일만 수정하면 실행 중인 서버에는 반영되지 않습니다.
- n8n의 `AI_KNOW Ingestion`은 Header Auth Credential입니다. 이름은 `Authorization`, 값은 `Bearer <N8N_INGEST_TOKEN>`입니다. 비밀값은 n8n의 암호화된 Credential 저장소에 보관합니다.
- `202`와 유효한 저장 ID를 받은 뒤에만 URL을 기록합니다. 서버가 반환한 검수 상태가 `PENDING`이면 승인 전 초안이며 공개 게시글·푸시 대상이 아닙니다.
- 날짜를 ISO 형식으로, `slides[].visual.type`을 `layout`으로, 출처 이미지를 서버의 `image` 형식으로 변환합니다. 서버 API 계약상의 제목·본문·layout·이미지·출처를 저장합니다. 편집 계획, 인용 검사 기록, 시각 자료의 별도 `visual.data`는 현재 서버 계약에 포함되지 않습니다.
- 생성 이미지의 실행 Binary를 Spring 업로드 API로 보내고, TrueNAS의 마운트된 폴더에 저장한 HTTPS URL로 변환합니다. [TrueNAS 환경 설정](TRUENAS.md)을 완료해야 사용할 수 있습니다. 이미지 생성이 켜져 있으면 유료 호출 전 저장소를 점검합니다.
- `allowImageGeneration: false`일 때 사용 가능한 출처 이미지가 없으면, 해당 이미지가 필요한 슬라이드만 `text`로 전환해 검수 대기로 저장합니다. 기사 제목·본문·인용 근거는 유지하며 확보한 이미지는 그대로 사용합니다. 검색·관련성·라이선스 검사는 계속 적용됩니다.
- 텍스트로 전환한 장 번호와 사유는 최종 n8n 출력의 `reviewWarnings` 및 실행 audit에 남습니다. 서버 요청에는 경고를 섞지 않으므로 DB에는 `layout: text`, `image: null`로 저장되고, 경고 상세는 n8n 실행 이력에서 확인합니다.
- 변환 코드를 수정했을 때 `node infra/n8n/connect-ingestion.mjs`로 JSON에 반영하고 배포합니다. 실행 환경의 `AIKNOW_SERVER_BASE_URL`을 지정하면 점검·업로드·뉴스 저장 주소가 함께 변경됩니다. 미지정 시 현재 뉴스 저장 노드의 서버 주소를 보존합니다.

### 로컬 연결 재검증

Spring이 8080에서 실행되고 메인 워크플로를 배포한 상태에서:

```powershell
.\infra\n8n\verify-ingestion.ps1
```

이 명령은 n8n을 잠시 정지하고 실제 n8n 실행 엔진으로 전송을 테스트한 뒤 다시 시작합니다.
`server/.env`의 토큰을 읽어 로컬 Credential에 연결하고, `[LOCAL TEST] 서버 저장 확인` 워크플로를 등록합니다.
실행마다 **명시적으로 테스트라고 표시한 5장짜리 초안 1건**을 DB에 남깁니다. 실제 뉴스 수집·OpenAI 호출·이미지 생성·승인·푸시는 수행하지 않습니다.
동일 데이터 재전송은 같은 ID를 반환하고, 인증 누락은 `401`, 동일 URL의 다른 내용은 `409`인지 검사합니다.
따라서 실행 이력의 마지막 두 실패는 의도한 음성 테스트입니다. 종료 시 테스트 워크플로는 정상 인증 상태로 복구합니다.

2026-10-07 로컬 검증 결과:

| 검사 | 결과 |
| --- | --- |
| n8n 실행 → Spring → MySQL | `news_submission.id=1`, `PENDING`, 슬라이드 5장 |
| 같은 데이터 재전송 | 기존 ID 1 반환, DB 총 1건 유지 |
| 인증 누락 / 내용 충돌 | 각각 401 / 409, URL 기록 노드 실행 안 함 |
| 게시 여부 | `card_news_id=NULL`, 자동 승인·게시 없음 |
| n8n 실행 이력 | 6·7 성공, 8·9 의도한 인증/충돌 실패 |

검증 데이터 제목은 `[로컬 테스트] n8n DB 저장 확인`입니다. 검수 화면에서 실제 뉴스로 승인하지 마세요.

### 초안 수치 검증

`draft-validation.mjs`는 `$20 million`과 `2,000만 달러`, `six months`와 `6개월`, `July`와 `7월` 같은 표기를
같은 수치로 비교합니다. 금액이 달라지거나 근거에 없는 수치가 추가되면 계속 거부합니다.
`nearly a year` → `거의 1년`, `an hour` → `1시간`처럼 기간·수량 단위 앞의 `a/an`도 처리합니다.
일반 관사(`a platform`)나 불특정 수량(`a few years`)은 1로 처리하지 않습니다.
이는 인용문에 해당 수치가 있는지 검사하는 기능이며, 통화·문맥·인과관계까지 사실 검증하는 기능은 아닙니다.
JSON 문자열이 한 번 더 이스케이프된 비교표는 JSON 문자열 계층을 한 번만 해제하고 기존 구조 검증을 적용합니다.

이미지 계획의 `searchQuery`가 null 또는 빈 문자열이면 검증된 `assetKey`의 밑줄/하이픈을 공백으로
바꿔 검색어를 보완합니다. 예: `startup_team` → `startup team`. 기사 본문·근거·생성 프롬프트는 변경하지 않습니다.
이미지 식별자 형식, 검색어 길이, 생성 프롬프트의 필수 여부/길이는 계속 검사하며 오류에 슬라이드와 필드명을 표시합니다.
동일한 `assetKey`와 생성 프롬프트를 여러 장에서 재사용할 때 슬라이드별 설명은 달라도 됩니다.
이미지 검색·생성 계획은 첫 사용의 것을 유지하고, 같은 키에 다른 생성 프롬프트가 있으면 거부합니다.

헬퍼를 수정한 뒤 `node infra/n8n/sync-draft-validation.mjs`로 워크플로 내 모든 검증 노드에 반영합니다.
2026-10-07 실행 11의 기존 모델 응답을 추가 API 호출 없이 재검증해 5장 모두 통과했고, 허위 수치를 넣으면 차단되는 것도 확인했습니다.

### 텍스트 모델과 비용

2026-10-07 비용 절감을 위해 실행 설정의 `textModel`을 `gpt-6-luna`로 변경했습니다.
Responses API, JSON Schema 출력, `reasoning.effort: low`를 그대로 사용합니다.
모델 교체 자체가 입력·출력 토큰 수를 줄이는 것은 아닙니다. 기사, 지시문, 슬라이드, 인용 근거가 모두 토큰에 포함됩니다.

공식 일반 텍스트 단가(100만 토큰당)는 Luna 입력 $0.10 / 출력 $0.50,
6.1 Sol 입력 $2 / 출력 $10입니다.
입력 2,257·출력 2,360토큰을 동일하게 가정하면 1회 약 $0.0014 / $0.0281이며,
하루에 텍스트 요청을 정확히 한 번씩 30일 보내면 약 $0.04 / $0.84입니다.
캐시 읽기·쓰기, 이미지 생성, 추가 호출, 세금은 제외한 비교용 추정입니다. 모델에 따라 실제 출력·추론 토큰 수는 달라질 수 있습니다.

한 번의 워크플로 실행에서도 기사 후보와 이미지 선택에 따라 텍스트 요청을 여러 번 할 수 있습니다(현재 상한 6회).
AI 응답 이후 검증이나 저장이 실패해도 이미 완료한 AI 호출 비용은 발생하며, 수동 재실행도 별도 요청입니다.

공식 문서: [GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna),
[GPT-6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol).

### 이미지 미확보 시 초안 보존

`image-fallback.mjs`의 규칙을 수정한 뒤 `node infra/n8n/sync-image-fallback.mjs`로 워크플로에 반영합니다.
전송 노드의 변환 결과는 `{request, reviewWarnings}`이며 HTTP 노드는 `request`만 서버에 보냅니다.
이미지 생성이 켜져 있는 경우 기존 생성 경로를 사용하며, API 오류나 영구 URL이 없는 생성 이미지를 조용히 누락시키지는 않습니다.

2026-10-07 실행 13의 Luna 응답을 재사용해 추가 OpenAI·이미지 API 호출 없이 n8n의 수정된 처리 경로를 실행했습니다.
복구 워크플로는 `aiknow-recover-execution-13`이며, 실제 기사 초안이 `news_submission.id=2`, `PENDING`, 6장으로 저장됐습니다.
제목은 `첫 제품을 접고 다시 만든 광고 AI, 멜리어스의 전환`이고 1·4번 장의 이미지를 텍스트로 대체했습니다.
`card_news_id`는 NULL이며 검수·승인·게시·푸시는 수행하지 않았습니다. 수동 실행의 처리 이력 특성상 같은 기사를 새로 생성하면 내용 차이로 409가 날 수 있으므로 이번 기사는 저장된 2번 초안을 검수하면 됩니다.
