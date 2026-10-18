# 운영 배포: Docker Compose + Nginx

다른 작업자에게 전달할 짧은 실행 지침은 [NAS 배포 인수인계](NAS_DEPLOY_HANDOFF.md)를 참고한다.
Tailscale 없이 EC2 공인 IP로 배포할 때는 [EC2 전용 배포](EC2_DEPLOY.md)를 참고한다.

Linux x86_64를 기본으로 준비한다. 운영 도메인은 `aiknow.kro.kr`이며 코드 변경만으로 실제 배포가 완료되는 것은 아니다.
Spring·MySQL·n8n은 같은 Docker 네트워크를 사용한다. MySQL과 Spring 포트는 호스트에 공개하지 않는다.
외부 요청은 Nginx 80/443으로만 받고, n8n 관리 UI는 localhost 바인딩과 SSH 터널로 접근한다.
운영에는 Assistant 샌드박스를 띄우지 않는다. 저장된 워크플로 실행에는 해당 샌드박스가 필요하지 않다.

## 서버 준비

1. Docker/Compose를 설치하고 `/opt/aiknow`에 배포 계정의 작업 권한을 준다.
   GitHub CD는 `AIKNOW_DOCKER_SUDO=true`로 Docker만 `sudo -n /usr/bin/docker`를 통해 실행한다.
   배포 계정에 `/usr/bin/docker`의 비밀번호 없는 sudo 권한을 설정하고, 해당 계정으로
   `sudo -n /usr/bin/docker info`와 `sudo -n /usr/bin/docker compose version`이 성공하는지 확인한다.
   수동 배포·n8n 반영·Nginx reload에도 같은 권한을 쓸 때는 명령 앞에 `AIKNOW_DOCKER_SUDO=true`를 붙인다.
   지정하지 않으면 기존처럼 sudo 없이 Docker를 실행한다. 전체 배포 셸을 sudo로 실행하지 않는다.
2. `infra/.env.prod.example`을 `/opt/aiknow/infra.env`, `server/.env.prod.example`을 `/opt/aiknow/server.env`로 작성한다.
   예시 도메인·암호·이미지 태그를 실제 값으로 바꾼다. 실제 env는 Git에 올리지 않고 권한을 600으로 제한한다.
3. `/opt/aiknow/secrets`를 만들고 Firebase 준비 시 JSON을 둔다. Spring 컨테이너 UID/GID는 10001이다.
4. 미디어 마운트 경로를 준비한다. NAS 연결을 보류하는 동안 `MEDIA_STORAGE_ENABLED=false`를 유지한다.
   Compose bind mount 경로 자체는 존재해야 한다. `mounted` 모드에는 임의 로컬 폴더에 NAS 표식을 만들지 않는다.
5. `API_DOMAIN`의 공개 DNS와 인터넷에서 proxy로 들어오는 TCP 80/443 연결을 준비한다.
   `ACME_EMAIL`에 연락처를 입력하고 Let's Encrypt 가입자 약관에 동의한 뒤 `ACME_AGREE_TOS=true`로 설정한다.
6. [DB 전환 절차](../server/docs/migrations.md)를 확인한다. 기존 DB에 자동 baseline하지 않는다.

## HTTPS 인증서

PEM 파일을 미리 준비하지 않는다. `deploy-server.sh`가 다음 순서를 처리한다.

1. Certbot 계정 설정을 검사한다. 이메일/약관 동의가 없으면 서비스 변경 전에 중단한다.
2. Nginx를 앱과 독립적으로 시작한다. 인증서가 없으면 HTTP 인증 경로만 제공하고 일반 요청은 503으로 응답한다.
3. Certbot이 HTTP-01 방식으로 발급한다. 기존 인증서가 갱신 시점 전이면 재사용한다.
4. Nginx는 15초마다 인증서 변경을 확인하고 설정 검사 후 HTTPS 전환 또는 reload를 수행한다.
   잘못된 교체는 반영하지 않고 기존 실행 설정을 유지하며 재시도한다.
5. HTTPS 전환 확인 후 DB 백업과 앱 배포를 진행한다. 발급 실패 시 앱 교체 전에 CD가 실패한다.

Certbot 서비스는 NAS에서 12시간마다 갱신을 확인한다. CD가 실행되지 않아도 계속 동작한다.
갱신 오류는 로그에 기록되며 마지막 성공이 약 25시간 이상 지나면 unhealthy가 된다.
인증서·개인키·계정·갱신 설정은 `${COMPOSE_PROJECT_NAME}_letsencrypt` named volume에 저장한다.
Nginx는 볼륨 전체를 읽기 전용으로 마운트해 live/archive 심볼릭 링크를 유지한다.
컨테이너 교체·재부팅에도 같은 볼륨을 재사용한다. 이 볼륨을 백업하고 `down -v`로 삭제하지 않는다.
호스트 Certbot/cron 설치나 Docker 소켓 마운트는 필요 없다.

수동으로 Nginx 설정을 다시 읽힐 때만 다음 명령을 사용한다.

```sh
sh infra/reload-nginx.sh /opt/aiknow/infra.env
```

`/.well-known/acme-challenge/`는 두 서비스가 공유하는 `acme-webroot` 볼륨을 사용한다.
공개 DNS A/AAAA와 TCP 80이 이 proxy로 연결되어야 한다. Tailscale SSH 연결만으로 발급되지는 않는다.
NAS 관리 UI의 포트 점유도 확인한다. 공개 80번을 열 수 없으면 별도 DNS-01 구성이 필요하다.
기존 수동 `TLS_CERT_PATH` 인증서는 자동으로 가져오지 않는다. 전용 볼륨에서 새로 발급·관리한다.
실제 발급·HTTPS 연결·갱신 dry-run은 운영 DNS와 NAS 연결이 준비된 후 검증한다.

공식 참고: [Certbot webroot 및 갱신](https://eff-certbot.readthedocs.io/en/stable/using.html),
[Let's Encrypt 가입자 약관](https://letsencrypt.org/repository/).

## 최초 설치

최초에도 GitHub CD가 게시한 이미지를 사용한다. 직접 실행할 때도 전체 SHA 이미지로 같은 스크립트를 사용한다.

```sh
sh infra/deploy-server.sh /opt/aiknow/infra.env docker.io/ACCOUNT/aiknow-server:FULL_COMMIT_SHA
```

SSH 터널 `ssh -L 5678:127.0.0.1:5678 <배포계정>@<서버>`로 n8n Owner를 설정하고,
다음 명령으로 JSON을 반영한 뒤 OpenAI Credential을 연결한다.
Ingestion Credential은 실행 중인 `server` 컨테이너의 `N8N_INGEST_TOKEN`을 읽어 자동 생성·갱신하고
알려진 내부 API 노드에만 연결한다. 토큰은 서버 env 파일에만 설정하며 n8n 화면에서 복사할 필요가 없다.
토큰을 바꿨다면 먼저 `docker compose ... up -d --wait server`로 서버를 재생성하고 아래 `apply`를 실행한다.
일반적인 `restart`는 변경된 env 파일을 반영하지 않는다.

```sh
AIKNOW_INFRA_ENV_FILE=/opt/aiknow/infra.env \
AIKNOW_COMPOSE_FILE="$PWD/infra/docker-compose.prod.yml" \
sh infra/n8n/deploy.sh apply
```

저장소 안에 env 파일을 두었다면 위 `/opt/aiknow/infra.env` 대신 `$PWD/infra/.env.prod`를 지정한다.
이 과정에는 최초 n8n Owner 설정이 필요하다. `check`는 인증정보를 변경하지 않는다.
최초 연결/연결 복구는 워크플로를 게시되지 않은 초안으로 가져온다. 이미 연결된 워크플로는
토큰만 변경할 때 재가져오기나 게시 상태 변경 없이 Credential을 갱신한다.
관리되는 Credential 이름은 `AI_KNOW Ingestion (env managed)`이며 직접 수정하면 다음 배포 때 덮어쓴다.
별도의 OpenAI Credential은 보존한다. 배포 중 토큰은 로그·워크플로 JSON·Git에 기록하지 않는다.

수동 검증 후 스케줄 노드 활성화와 Publish를 진행한다. JSON 변경을 가져오면 초안으로 전환하므로 다시 검토·Publish해야 한다.
일반 컨테이너 재시작은 JSON을 가져오거나 유료 생성 요청을 실행하는 명령이 아니다.

## CI/CD

PR과 main/develop push에서 변경 경로를 검사한다. 서버는 Java+MySQL 마이그레이션 테스트,
n8n은 Node 회귀 테스트, 인프라는 Compose 검사와 Docker 이미지 빌드를 실행한다.
필수 체크에는 `required`를 지정한다. 해당 작업이 없는 경우도 성공/skip 여부를 종합한다.
main push의 CI가 성공하면 Docker Hub의 `<계정명>/aiknow-server`에 커밋 SHA 태그로 이미지를 게시한다.
Docker Hub에서 `aiknow-server` 저장소를 먼저 만들고, GitHub Repository secrets에 `DOCKERHUB_USERNAME`,
`DOCKERHUB_TOKEN`(Docker Hub Read & Write PAT)을 등록한다. 계정명은 Secret이 없으면 같은 이름의 Variable도 지원한다.
게시 job은 production 환경을 사용하지 않으므로 Docker Hub 설정은 Repository 범위에 등록한다.

실제 원격 배포는 기본적으로 꺼져 있다. 준비 후 GitHub 저장소 Actions의 **Repository variable**에
`DEPLOY_ENABLED=true`를 등록한다. job 조건은 실행 전에 평가되므로 이 값을 production Environment variable에만 넣으면 안 된다.
Repository secrets에는 `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `TAILSCALE_AUTHKEY`를 등록한다.
production environment에 위 값을 등록하는 것도 지원한다. 같은 이름이 양쪽에 있으면 환경 값이 우선한다.
Ethica처럼 `tailscale/github-action@v4`가 Auth key로 NAS의 tailnet에 들어간 후 일반 SSH로 배포한다.
Auth key는 Reusable·Ephemeral로 발급하고 해당 사용자/태그의 NAS TCP 22번 접근을 허용한다.
공인 SSH 포트포워딩은 필요하지 않다. NAS 일반 SSH 서비스에 대응 공개키가 등록되어 있어야 한다.
승인자 보호 규칙을 설정하면 배포 때 승인이 필요하다. 승인 없이 배포하려면 해당 규칙을 사용하지 않는다.
알려진 SSH 호스트 키를 사용하며 호스트 검증을 끄지 않는다. 배포 계정에는 Docker 접근과 `/opt/aiknow` 쓰기 권한이 필요하다.
Docker Hub 저장소가 비공개면 NAS 배포 계정에서도 Docker Hub Read 전용 PAT으로 한 번 로그인한다.
공개 저장소는 인증 없이 pull할 수 있다. NAS 다운로드 인증정보는 GitHub에서 자동 전달하지 않는다.
Docker Hub에 올려도 NAS 자동 재시작에는 위 SSH 설정이 필요하다.
현재 SSH는 22번 포트, `/opt/aiknow` 경로, Linux amd64 이미지 기준이다.
각 릴리스가 다른 디렉터리에 풀리므로 운영 env의 서버 env/비밀 파일/인증서/미디어 경로는 고정 절대 경로를 사용한다.

배포는 이전 이미지 기록 → HTTP 초기 기동·인증서 발급/재사용·갱신 서비스 시작 → DB 백업 → 이미지 교체/Flyway/헬스체크 → n8n JSON 적용 순서다.
DB 백업은 `/opt/aiknow/backups`, 이전 이미지는 `previous-server-image`에 남는다.
첫 n8n Owner/Credential 설정, UI와 Git 충돌은 자동으로 무시하지 않는다. 실패 원인을 해결하고 재배포한다.
이 자동 배포는 서버 주소·인증정보 미설정 상태에서 실행되지 않는다.

## 복구와 데이터

배포 실패 시 먼저 스키마 변경의 하위 호환성을 확인한다. 호환될 때 이전 이미지로 SERVER_IMAGE를 지정하고 서버만 교체한다.
스키마 복구가 필요하면 쓰기를 중단하고 백업 복원 절차를 수행한다. 자동으로 DB를 삭제하거나 downgrade하지 않는다.
DB 외에 n8n 데이터 볼륨과 N8N_ENCRYPTION_KEY, TLS 인증서, 미디어 파일도 보관해야 한다.
실행 중인 SQLite 파일을 단순 복사하지 말고 n8n을 정지하거나 SQLite 백업 방식을 사용한다.
볼륨 삭제(`down -v`)는 배포/복구 절차에 포함하지 않는다.
