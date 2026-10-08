# 로컬 개발과 배포 환경

워크플로 JSON은 하나를 유지하고 `infra/.env`의 `AIKNOW_*` 값을 가져오기 직전에 적용합니다.
서버 저장소는 `server/.env`의 `MEDIA_*` 값으로 설정합니다. NAS 연결이나 OpenAI 권한 변경이
`allowImageGeneration`이 자동으로 켜지지는 않습니다. 환경변수를 바꾼 뒤 서버 재시작/워크플로 `apply`가 필요합니다.

| 설정 | 로컬 개발 | 배포 |
| --- | --- | --- |
| Spring 프로필 | `local` | `prod` |
| `MEDIA_STORAGE_MODE` | `local` | `mounted` |
| 실제 이미지 파일 | 저장소 루트 `.local/media/generated/` | 서버에 마운트한 TrueNAS 폴더 |
| 폴더 생성 | 자동 생성 | 자동 생성 없음, 표식 검사 |
| 공개 이미지 주소 | `http://localhost:8080/media/generated` | 실제 API 도메인의 HTTPS 주소 |
| NAS 인증정보 | 불필요 | 운영체제의 SMB/NFS 마운트 설정에만 보관 |
| 로컬 생성 제한 | 실행당 최대 1장 | env에서 0~3장 지정 |

## 지금 로컬에서 실행

현재 로컬 `server/.env`와 `infra/.env`에 아래 설정을 적용합니다. 기존 DB/OAuth/OpenAI 인증은 유지합니다.

`server/.env`:

```dotenv
SPRING_PROFILES_ACTIVE=local
MEDIA_STORAGE_MODE=local
MEDIA_STORAGE_ENABLED=true
MEDIA_ROOT=../.local/media/generated
MEDIA_PUBLIC_BASE_URL=http://localhost:8080/media/generated
MEDIA_VOLUME_ID=
```

`infra/.env`:

```dotenv
AIKNOW_ENV=local
AIKNOW_SERVER_BASE_URL=http://host.docker.internal:8080
AIKNOW_MEDIA_PUBLIC_BASE_URL=http://localhost:8080/media/generated
AIKNOW_DRY_RUN=false
AIKNOW_ALLOW_IMAGE_GENERATION=true
AIKNOW_MAX_IMAGE_GENERATIONS=1
```

`AIKNOW_SERVER_BASE_URL`은 **n8n 컨테이너 → Spring** 주소이고,
`MEDIA_PUBLIC_BASE_URL`은 **브라우저/앱 → 이미지** 주소입니다. 두 주소는 용도가 다릅니다.
서버의 `MEDIA_PUBLIC_BASE_URL`과 n8n의 `AIKNOW_MEDIA_PUBLIC_BASE_URL`은 일치해야 합니다.
불일치나 저장소 연결 실패는 유료 API 호출 전에 중단합니다.

저장소 루트에서 서버 빌드/실행:

```powershell
.\server\gradlew.bat -p server bootJar
.\server\run-local.ps1
```

이 스크립트는 `server/.env`를 읽고 서버 폴더에서 실행하므로 상대 경로가 일정합니다.
Java 21의 `JAVA_HOME`이 필요하며 이미 8080 서버가 실행 중이면 해당 서버를 먼저 종료합니다.
IDE 실행 시에도 환경변수를 로드하고 working directory를 `server/`로 지정하세요.

다른 터미널에서 n8n 설정 반영:

```powershell
.\infra\n8n\deploy.ps1 check
.\infra\n8n\deploy.ps1 apply
```

[로컬 워크플로](http://localhost:5678/workflow/iwGOpyXhwAq9tON2)를 새로고침하고 Execute workflow를 누릅니다.
기존 이미지 검색·라이선스 확인을 먼저 수행하고 적절한 이미지가 없을 때만 생성합니다.
생성 한도를 넘은 이미지는 텍스트로 대체하고 검수 경고를 남깁니다. 배포/재시작만으로 유료 생성이나 예약 실행은 시작하지 않습니다.
실행 후 검수 대기 `PENDING` 데이터가 생기며 승인/게시/푸시는 별도입니다.

Android 에뮬레이터에서는 `localhost`가 PC가 아니므로 두 공개 URL 값을 `http://10.0.2.2:8080/media/generated`로 바꿉니다.
실기기는 PC의 접근 가능한 LAN 주소를 사용합니다. 변경 전 DB에 저장된 URL은 자동으로 바뀌지 않습니다.
생성 파일은 Git에서 제외되어 재시작 후에도 남습니다. `.local` 폴더를 삭제하면 해당 로컬 이미지가 사라집니다.

## 배포 환경 전환

`server/.env.prod.example`, `infra/.env.prod.example`을 각각 서버 환경 파일과 Compose 환경 파일로 복사합니다.
DB/OAuth/암호화 키를 채우고 예시 도메인·경로를 실제 값으로 교체합니다. [Nginx 운영 배포](../PRODUCTION.md)를 따르세요.
기존 `N8N_ENCRYPTION_KEY`를 바꾸지 않습니다. `.env.prod` 실제 파일은 Git에서 제외됩니다.

1. NAS 소유자에게 공유 경로·계정을 받고 Spring 호스트에 SMB/NFS로 마운트합니다.
2. 마운트 확인 후 저장 폴더 안에 `.aiknow-storage-id`를 만들고 `MEDIA_VOLUME_ID`와 일치시킵니다.
3. `prod` 프로필과 `mounted` 모드, 실제 경로와 HTTPS 공개 URL로 Spring을 실행합니다.
   `.env`는 Spring이 자동 로드하지 않으므로 서비스 매니저/컨테이너로 환경변수를 주입합니다.
   운영 DB는 Flyway로 마이그레이션하고 `ddl-auto=validate`로 검증합니다. 기존 데이터가 있는 DB는 [도입 절차](../../server/docs/migrations.md)가 필요합니다.
4. n8n Owner 설정 후 지정한 env 파일로 워크플로를 적용합니다. 내부 API Credential은 실행 중인
   `server`의 `N8N_INGEST_TOKEN`으로 자동 연결합니다. OpenAI Credential은 별도로 연결합니다.

```sh
AIKNOW_INFRA_ENV_FILE="$PWD/infra/.env.prod" AIKNOW_COMPOSE_FILE="$PWD/infra/docker-compose.prod.yml" sh infra/n8n/deploy.sh check
AIKNOW_INFRA_ENV_FILE="$PWD/infra/.env.prod" AIKNOW_COMPOSE_FILE="$PWD/infra/docker-compose.prod.yml" sh infra/n8n/deploy.sh apply
```

PowerShell은 `deploy.ps1 apply -EnvFile infra/.env.prod -ComposeFile infra/docker-compose.prod.yml`입니다.
운영 예제는 `AIKNOW_DRY_RUN=true`, 이미지 생성 `false`로 시작합니다.
NAS 쓰기·공개 이미지 조회·Credential 연결을 확인한 뒤 실제 실행용 `false/true`로 바꾸고 다시 `apply`합니다.
워크플로는 초안 상태로 가져오므로 매일 실행하려면 별도로 스케줄 노드 활성화와 Publish가 필요합니다.

로컬 Compose는 Spring을 호스트에서 실행하고, 운영 Compose는 Spring과 Nginx를 함께 실행합니다.
운영 이미지 저장소 마운트는 `MEDIA_HOST_PATH`로 지정합니다. NAS 연결 전에는 이미지 생성을 끄고 텍스트 초안을 수집할 수 있습니다.
로컬 폴더를 NAS에 자동 복사하거나 개발 DB의 localhost 이미지 URL을 운영용으로 바꾸지는 않습니다.

## 검증 범위

Java 테스트는 로컬 폴더 생성·HTTP 이미지 수집, 외부 HTTP 차단, prod/local 혼용 차단,
NAS 표식 불일치/연결 해제 시 실패를 검사합니다. Node 테스트는 환경 적용과 이미지 업로드/변환을 검사합니다.
`verify-image-upload.mjs`는 실제 n8n HTTP 노드로 테스트 PNG를 저장하고 검수 대기 테스트 데이터 1건을 만듭니다.
유료 이미지 모델은 호출하지 않으므로 이 검증은 실제 이미지 생성 모델이나 NAS 접속의 성공을 의미하지 않습니다.
