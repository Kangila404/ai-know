# 운영 배포: Docker Compose + Nginx

Linux x86_64를 기본으로 준비한다. 실제 서버/도메인은 미정이며 이 파일의 예제는 배포 완료를 뜻하지 않는다.
Spring·MySQL·n8n은 같은 Docker 네트워크를 사용한다. MySQL과 Spring 포트는 호스트에 공개하지 않는다.
외부 요청은 Nginx 80/443으로만 받고, n8n 관리 UI는 localhost 바인딩과 SSH 터널로 접근한다.
운영에는 Assistant 샌드박스를 띄우지 않는다. 저장된 워크플로 실행에는 해당 샌드박스가 필요하지 않다.

## 서버 준비

1. Docker/Compose를 설치하고 `/opt/aiknow`에 배포 계정의 작업 권한을 준다.
2. `infra/.env.prod.example`을 `/opt/aiknow/infra.env`, `server/.env.prod.example`을 `/opt/aiknow/server.env`로 작성한다.
   예시 도메인·암호·이미지 태그를 실제 값으로 바꾼다. 실제 env는 Git에 올리지 않고 권한을 600으로 제한한다.
3. `/opt/aiknow/secrets`를 만들고 Firebase 준비 시 JSON을 둔다. Spring 컨테이너 UID/GID는 10001이다.
4. 미디어 마운트 경로를 준비한다. NAS 연결을 보류하는 동안 `MEDIA_STORAGE_ENABLED=false`를 유지한다.
   Compose bind mount 경로 자체는 존재해야 한다. `mounted` 모드에는 임의 로컬 폴더에 NAS 표식을 만들지 않는다.
5. 인증서 발급 후 `/opt/aiknow/tls/fullchain.pem`, `privkey.pem`을 실제 파일로 둔다.
   Let's Encrypt symlink가 마운트 밖을 가리키지 않도록 인증서 체인과 키를 전용 디렉터리로 복사한다.
6. [DB 전환 절차](../server/docs/migrations.md)를 확인한다. 기존 DB에 자동 baseline하지 않는다.

## HTTPS 인증서

Nginx는 제공된 인증서를 읽는다. ACME/Certbot 또는 기존 인증서 관리 시스템의 발급·갱신을 사용한다.
DNS와 80/443 연결을 먼저 준비하고 최초 인증서는 Nginx 기동 전에 발급한다.
갱신 후 전용 TLS 디렉터리의 두 파일을 교체하고 다음 명령으로 설정 검사 후 reload한다.

```sh
sh infra/reload-nginx.sh /opt/aiknow/infra.env
```

Certbot 사용 시 이 동작을 인증서 갱신 deploy hook에 연결한다.
`/.well-known/acme-challenge/`는 `acme-webroot` 볼륨을 사용하므로 webroot 갱신 프로세스도 같은 볼륨을 공유해야 한다.
인증서 발급 자체는 실제 도메인이 정해진 후 구성·검증한다.

## 최초 설치

검증한 커밋의 서버 이미지를 빌드하거나 CI가 게시한 태그로 `SERVER_IMAGE`를 지정한다.

```sh
docker compose --env-file /opt/aiknow/infra.env -f infra/docker-compose.prod.yml up -d --wait
```

SSH 터널 `ssh -L 5678:127.0.0.1:5678 <배포계정>@<서버>`로 n8n Owner를 설정하고,
다음 명령으로 JSON을 반영한 뒤 OpenAI와 Ingestion Credential을 연결한다.

```sh
AIKNOW_INFRA_ENV_FILE=/opt/aiknow/infra.env \
AIKNOW_COMPOSE_FILE="$PWD/infra/docker-compose.prod.yml" \
sh infra/n8n/deploy.sh apply
```

수동 검증 후 스케줄 노드 활성화와 Publish를 진행한다. JSON 변경을 가져오면 초안으로 전환하므로 다시 검토·Publish해야 한다.
일반 컨테이너 재시작은 JSON을 가져오거나 유료 생성 요청을 실행하는 명령이 아니다.

## CI/CD

PR과 main/develop push에서 변경 경로를 검사한다. 서버는 Java+MySQL 마이그레이션 테스트,
n8n은 Node 회귀 테스트, 인프라는 Compose 검사와 Docker 이미지 빌드를 실행한다.
필수 체크에는 `required`를 지정한다. 해당 작업이 없는 경우도 성공/skip 여부를 종합한다.
main push의 CI가 성공하면 GHCR에 커밋 SHA 태그로 이미지를 게시한다.

실제 원격 배포는 기본적으로 꺼져 있다. 준비 후 GitHub의 production environment에 보호 규칙을 설정하고,
`DEPLOY_ENABLED=true` 변수 및 `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` secrets를 등록한다.
알려진 SSH 호스트 키를 사용하며 호스트 검증을 끄지 않는다. 배포 계정에는 Docker 접근과 `/opt/aiknow` 쓰기 권한이 필요하다.
GHCR 패키지가 비공개면 서버에서도 읽기 권한으로 로그인해 둔다.

배포는 이전 이미지 기록 → DB 백업 → 이미지 교체/Flyway/헬스체크 → n8n JSON 적용 순서다.
DB 백업은 `/opt/aiknow/backups`, 이전 이미지는 `previous-server-image`에 남는다.
첫 n8n Owner/Credential 설정, UI와 Git 충돌은 자동으로 무시하지 않는다. 실패 원인을 해결하고 재배포한다.
이 자동 배포는 서버 주소·인증정보 미설정 상태에서 실행되지 않는다.

## 복구와 데이터

배포 실패 시 먼저 스키마 변경의 하위 호환성을 확인한다. 호환될 때 이전 이미지로 SERVER_IMAGE를 지정하고 서버만 교체한다.
스키마 복구가 필요하면 쓰기를 중단하고 백업 복원 절차를 수행한다. 자동으로 DB를 삭제하거나 downgrade하지 않는다.
DB 외에 n8n 데이터 볼륨과 N8N_ENCRYPTION_KEY, TLS 인증서, 미디어 파일도 보관해야 한다.
실행 중인 SQLite 파일을 단순 복사하지 말고 n8n을 정지하거나 SQLite 백업 방식을 사용한다.
볼륨 삭제(`down -v`)는 배포/복구 절차에 포함하지 않는다.
