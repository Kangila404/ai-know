# AI_KNOW NAS 최초 실행·자동 배포 인수인계

이 저장소를 NAS에서 최초 실행하고, 이후 main 반영 시 자동 배포되도록 설정해 주세요.
운영 API는 `https://aiknow.kro.kr`, 프록시는 Nginx입니다. 실제 완료한 검증과 미완료 항목을 구분해 보고해 주세요.
비밀값은 기존 NAS env/키 파일에서 읽고 채팅·로그·Git에 출력하지 마세요.

## 설치 원칙: 최초 한 번 수동 준비

폴더 생성, env·키·인증서 배치, 최초 Docker Compose 실행은 한 번 수동으로 진행합니다.
이후 main 배포마다 폴더 구조를 바꾸거나 env를 다시 복사하지 않습니다.
아래 고정 경로를 계속 사용하고, 배포 스크립트가 릴리스 폴더와 백업을 자동으로 추가합니다.

```text
/opt/aiknow/
├── infra.env                   # 최초 배치: 인프라 운영 설정
├── server.env                  # 최초 배치: Spring 운영 설정
├── secrets/
│   └── firebase-admin.json     # 최초 배치: Firebase 서비스 계정
├── tls/
│   ├── fullchain.pem           # 최초 배치, 이후 인증서 갱신 시 교체
│   └── privkey.pem
├── media/
│   └── generated/              # 고정 NAS 이미지 저장 위치
│       └── .aiknow-storage-id
├── releases/                   # 자동 생성: 배포할 소스와 Compose
│   └── <commit SHA>/
├── backups/                    # 자동 생성: 배포 전 DB 백업
└── current-server-image        # 자동 기록: 마지막 배포 성공 이미지
```

MySQL과 n8n 데이터는 Compose의 named volume에 보관됩니다. 위 트리에 DB 파일을 옮기지 않습니다.
`COMPOSE_PROJECT_NAME=aiknow-prod`를 유지해 배포마다 같은 볼륨을 사용하세요.
인증서 갱신이나 설정 변경이 필요할 때만 고정 파일을 수정하고 해당 서비스를 다시 반영합니다.

## 현재 자동 배포 흐름

`main push/merge → Server CI 성공 → Server release → Docker Hub에 커밋 SHA 이미지 게시 → Tailscale 연결 → SSH → NAS pull → DB 백업 → Compose 교체·Flyway·health 확인 → n8n 워크플로/인증정보 반영`

- 정의: `.github/workflows/server-ci.yml`, `.github/workflows/server-release.yml`, `infra/deploy-server.sh`.
- NAS가 이미지를 감시하는 방식이 아니라 GitHub Actions가 SSH로 배포를 실행합니다.
- 이미지: `docker.io/<Docker Hub 계정명>/aiknow-server:<40자리 커밋 SHA>`. 현재 빌드는 Linux amd64 기준입니다.
- SSH는 현재 22번 포트, 작업 경로는 `/opt/aiknow`로 고정돼 있습니다. 다른 포트/경로가 필요하면 워크플로부터 수정하세요.

## 최초 NAS 준비

1. NAS의 Docker Compose 지원, CPU, 실제 dataset 경로, 포트 점유와 Tailscale을 통한 SSH 접근을 확인하세요.
   `aiknow.kro.kr` DNS, Nginx로의 80/443 연결과 TLS 인증서를 준비하세요. NAS 관리 UI로 전달하면 안 됩니다.
2. `/opt/aiknow`가 NAS의 영구 dataset을 사용하게 준비하세요. 필요하면 영구 dataset을 가리키는 경로로 연결하세요.
   배포 계정은 이 경로 쓰기 및 비대화형 Docker 실행 권한이 필요합니다.
3. 준비한 `infra/.env.prod`, `server/.env.prod`를 각각 `/opt/aiknow/infra.env`, `/opt/aiknow/server.env`로 배치하세요.
   기존 파일/DB가 있으면 비밀번호·암호화 키·데이터를 유지하세요. 앞으로 운영 설정은 이 두 고정 파일에서 관리합니다.
4. `/opt/aiknow/infra.env`에서 아래 경로를 절대 경로로 바꾸세요. 릴리스별 폴더 안의 `../.local` 경로는 쓰지 마세요.

```dotenv
COMPOSE_PROJECT_NAME=aiknow-prod
SERVER_ENV_FILE=/opt/aiknow/server.env
SERVER_SECRETS_PATH=/opt/aiknow/secrets
TLS_CERT_PATH=/opt/aiknow/tls
MEDIA_HOST_PATH=/opt/aiknow/media/generated
MEDIA_ROOT=/var/aiknow-media
API_DOMAIN=aiknow.kro.kr
AIKNOW_SERVER_BASE_URL=http://server:8080
AIKNOW_MEDIA_PUBLIC_BASE_URL=https://aiknow.kro.kr/media/generated
```

5. `secrets/firebase-admin.json`, `tls/fullchain.pem`, `tls/privkey.pem`을 준비하세요.
   실제 NAS dataset의 `media/generated/.aiknow-storage-id` 내용은 `aiknow-media-prod` 한 줄입니다.
   Spring UID/GID `10001:10001`에 JSON 읽기·미디어 읽기/쓰기 권한을 주세요. env는 배포 계정만 읽도록 제한하세요.
6. 최초에는 저장소 루트에서 `docker build -t aiknow-server:20261008 ./server`를 실행하고,
   `/opt/aiknow/infra.env`의 `SERVER_IMAGE=aiknow-server:20261008`로 설정한 뒤 실행하세요.

```sh
docker compose --env-file /opt/aiknow/infra.env -f infra/docker-compose.prod.yml config --quiet
docker compose --env-file /opt/aiknow/infra.env -f infra/docker-compose.prod.yml up -d --wait
```

7. 신규 DB는 Flyway가 초기화합니다. 기존 DB 도입은 `server/docs/migrations.md`를 먼저 확인하세요.
   `down -v`나 임의 baseline/repair로 기존 데이터를 초기화하지 마세요.
8. SSH 터널로 n8n 최초 Owner 설정을 완료하고 아래 명령으로 반영하세요. OpenAI Credential은 연결이 필요합니다.
   Ingestion Credential은 실행 중인 서버의 `N8N_INGEST_TOKEN`으로 자동 생성·연결됩니다.

```sh
AIKNOW_INFRA_ENV_FILE=/opt/aiknow/infra.env \
AIKNOW_COMPOSE_FILE="$PWD/infra/docker-compose.prod.yml" \
sh infra/n8n/deploy.sh apply
```

## GitHub와 NAS에 등록할 인증정보

저장소 `Settings → Secrets and variables → Actions → Variables`에 **Repository variable**을 등록하세요.

| 이름 | 값 |
| --- | --- |
| `DEPLOY_ENABLED` | 최초 준비 완료 후 `true` |

같은 Actions 설정의 **Secrets → Repository secrets**에 등록하세요.

| 이름 | 값 |
| --- | --- |
| `DOCKERHUB_USERNAME` | Docker Hub 계정명(이메일이 아님). 이 계정 아래 `aiknow-server` 저장소를 먼저 생성 |
| `DOCKERHUB_TOKEN` | Docker Hub에서 발급한 Read & Write 권한의 Personal Access Token |

Docker Hub 게시는 production 환경 밖에서 실행되므로 이 토큰은 Repository secret이어야 합니다.
계정명은 Repository secret을 우선 사용하며, 없으면 같은 이름의 Repository variable을 사용합니다.
이미지 이름은 각 job에서 구성하므로 계정명이 Secret이어도 job 간 출력 차단에 영향을 받지 않습니다.
Docker 계정 비밀번호 대신 PAT을 사용합니다. 토큰은 채팅에 보내지 말고 GitHub에 직접 등록하세요.

다음 값도 **Repository secrets**에 등록하세요. 이미 등록했다면 옮길 필요가 없습니다.
`production` Environment secrets로 관리해도 지원하지만, 중복 등록하면 Environment 값이 우선합니다.

| 이름 | 값 |
| --- | --- |
| `DEPLOY_HOST` | NAS Tailscale 주소 `100.103.104.89`. URL·포트 제외 |
| `DEPLOY_USER` | NAS 배포 계정 `soop` |
| `DEPLOY_SSH_KEY` | 배포 전용 SSH 개인키 전체. 대응 공개키는 NAS 계정의 `authorized_keys`에 등록 |
| `DEPLOY_KNOWN_HOSTS` | 위 호스트의 SSH host key를 신뢰 가능한 경로로 지문 대조한 `known_hosts` 행 |
| `TAILSCALE_AUTHKEY` | NAS가 속한 tailnet에서 발급한 Reusable·Ephemeral Auth key 전체 (`tskey-auth-…`) |

- Actions는 Ethica와 같은 `tailscale/github-action@v4` Auth key 방식으로 접속합니다.
  키가 속한 사용자 또는 태그에 NAS TCP 22번 접근 권한이 있어야 하며, 장치 승인 정책이 있다면 승인도 필요합니다.
  태그 없는 키에는 태그를 강제로 지정하지 않습니다. 만료 전에 GitHub의 Auth key를 교체하세요.
- SSH는 NAS의 일반 SSH 서비스와 키 인증을 사용합니다. Tailscale 네트워크 연결만으로 SSH 계정 인증이 완료되지는 않습니다.
- `production` 환경을 생성하고 배포 보호 규칙을 확인하세요.
- `DEPLOY_ENABLED`는 job 시작 전 조건에 쓰이므로 Environment variable이 아닌 Repository variable로 둡니다.
- production 환경에 승인자를 설정하면 매번 승인 후 배포됩니다. 승인 없이 배포하려면 그 보호 규칙을 사용하지 않습니다.
- 이미지 **게시**는 위 Docker Hub PAT을 사용합니다. GitHub 패키지용 PAT은 필요하지 않습니다.
- 비공개 Docker Hub 이미지 **다운로드**에는 해당 저장소를 읽을 수 있는 Docker Hub Read 전용 PAT을 준비합니다.
  NAS에서 실제 배포 계정으로 `docker login --username <DockerHub계정>`을 실행하고 비밀번호 입력란에 PAT을 넣으세요.
  현재 워크플로는 다운로드 PAT을 GitHub Secret에서 NAS로 전달하지 않습니다. 공개 이미지는 로그인 없이 pull 가능합니다.
- Docker Hub가 NAS 컨테이너를 자동으로 재시작하지는 않습니다. 자동 배포를 위해 위 SSH 설정도 필요합니다.
- Google/Kakao/Firebase/n8n 비밀값은 NAS에 유지합니다. GitHub에 env 전체를 등록할 필요가 없습니다.

## 사용자가 준비할 것과 작업 순서

1. **NAS 접속 정보:** 실제 NAS 경로, CPU 종류, 배포 계정과 Tailscale 주소를 확인합니다.
   Tailscale 정책·NAS 방화벽에서 runner의 TCP 22번 접속을 허용합니다. 공유기의 공인 SSH 포트포워딩은 필요하지 않습니다.
2. **운영 파일:** 두 env 파일, Firebase JSON, `aiknow.kro.kr` TLS 인증서를 준비합니다.
   위 폴더 생성과 파일 배치는 NAS에서 작업하는 AI에게 맡겨도 됩니다. `/opt/aiknow`의 영구 저장과 재부팅 후 유지도 확인합니다.
3. **SSH 인증:** 배포 전용 키 쌍을 만들고 공개키를 NAS 배포 계정에 등록합니다.
   현재 워크플로에는 키 암호를 입력하는 기능이 없으므로 비대화형 인증 가능한 전용 키를 사용합니다.
   개인키 전체와 검증한 host key 행을 위 GitHub Secrets에 등록합니다. NAS 로그인 비밀번호를 넣는 칸이 아닙니다.
4. **Docker Hub:** 계정 아래 `aiknow-server` 저장소를 만들고 공개/비공개를 선택합니다.
   계정명과 업로드용 PAT을 위 GitHub Repository 설정에 등록합니다.
   비공개라면 별도의 Read 전용 PAT으로 NAS 배포 계정에서도 한 번 로그인합니다.
5. **최초 실행과 확인:** 위 Compose 명령으로 시작하고 n8n Owner·OpenAI 인증정보를 연결합니다.
   HTTPS, 로그인, DB, n8n 검수 대기 저장을 검증합니다. 인증서 자동 갱신도 NAS 환경에 맞게 별도 설정합니다.
6. **자동 배포 켜기:** 위 Secrets 7개를 등록하고 `production` 환경을 준비한 뒤,
   Repository variable `DEPLOY_ENABLED=true`를 마지막에 설정합니다.
   배포 코드가 포함된 main push/merge 후 Actions와 실제 NAS 이미지 버전을 확인합니다.

변수만 켜면 배포가 즉시 시작되는 것은 아닙니다. 준비 후 main push/merge에 대한 CI 성공이 배포를 시작합니다.
이후 일반 서버 변경은 자동 반영됩니다. n8n 초안의 Publish와 운영 수집·배치 활성화는 아래 완료 확인을 따릅니다.

## 완료 확인

- HTTPS 인증서와 `/health-check`, 실제 로그인·API 연결, NAS 이미지 저장/공개 URL, 검수 대기 저장을 확인하세요.
- 준비된 main 변경으로 `Server CI`와 `Server release`의 publish/deploy 결과를 확인하세요.
  NAS 서버 이미지가 해당 SHA인지, DB 백업과 `/opt/aiknow/current-server-image`가 남았는지 확인하세요.
- 자동 배포 스크립트는 `SERVER_IMAGE`를 실행 시 덮어쓰고 성공 이미지를 위 파일에 기록합니다.
  이후 수동 Compose 실행 시에는 그 기록의 이미지로 env를 맞춰 최초 로컬 이미지로 돌아가지 않게 하세요.
- n8n JSON 변경/최초 인증 연결은 미게시 초안이 됩니다. 실제 수집은 OpenAI 연결·수동 검증 후 스케줄 활성화와 Publish가 필요합니다.
  `AIKNOW_DRY_RUN`, 이미지 생성, `NEWS_BATCH_ENABLED`는 실제 검증 후 운영 설정으로 전환하세요.
- 파일 변경만 로컬에 남아 있다면 NAS와 main에 포함됐는지도 확인하세요. 소스가 없는 기능을 배포 완료라고 보고하지 마세요.

상세 절차: [운영 배포](PRODUCTION.md). 공식 근거: [Docker Hub Actions 인증](https://docs.docker.com/guides/gha/),
[GitHub 변수 적용 시점](https://docs.github.com/en/actions/reference/workflows-and-actions/variables#configuration-variable-precedence),
[Tailscale GitHub Action](https://tailscale.com/docs/integrations/github/github-action).
