# EC2 전용 배포

`.github/workflows/server-release-ec2.yml`은 Tailscale 없이 EC2의 공인 IP/DNS로 SSH 접속한다.
`main` push의 Server CI 성공 → Docker Hub에 SHA 이미지 게시 → EC2 준비 검사 → 릴리스 전송 → Compose 배포 순서다.
컨테이너 구성과 인증서 자동 발급은 기존 `infra/deploy-server.sh`를 사용한다.

## GitHub 설정

Settings → Secrets and variables → Actions에서 다음을 등록한다.

| 종류 | 이름 | 값 |
|---|---|---|
| Repository variable | `DEPLOY_TARGET` | `ec2` |
| Repository variable | `DEPLOY_ENABLED` | 준비가 끝나면 `true`, 준비 중에는 `false` |
| Secret | `EC2_HOST` | 고정 공인 IP 또는 DNS 이름. 프로토콜·포트 제외 |
| Secret | `EC2_USER` | Ubuntu 기본 계정 사용 시 `ubuntu` |
| Secret | `EC2_SSH_KEY` | 해당 계정에 등록된 공개키와 짝인 개인키 전체 |
| Secret | `EC2_KNOWN_HOSTS` | `EC2_HOST`에 대한 검증된 SSH known_hosts 항목 |

`DOCKERHUB_USERNAME`과 `DOCKERHUB_TOKEN`은 기존 값을 사용한다. `production` GitHub Environment도 유지한다.
SSH 호스트 키의 지문은 EC2의 신뢰할 수 있는 콘솔/시스템 로그와 비교한 뒤 등록한다.
`DEPLOY_TARGET=ec2`이면 기존 NAS 워크플로는 이미지 게시와 배포를 모두 건너뛴다.
변수가 없으면 기존 NAS 워크플로 동작을 유지한다. EC2 전용 Secrets는 NAS용 `DEPLOY_*` Secrets와 분리한다.
두 워크플로는 동일한 production concurrency 그룹을 사용한다.

## EC2 준비

- 현재 이미지는 Linux amd64 기준이다. x86_64 EC2에서 Docker Engine과 Compose 플러그인을 설치한다.
- 배포 계정으로 `sudo -n /usr/bin/docker info`와 `sudo -n /usr/bin/docker compose version`이 성공해야 한다.
- `/opt/aiknow`에 배포 계정의 쓰기 권한을 부여한다.
- 로컬 `infra/.env.prod` → `/opt/aiknow/infra.env`, `server/.env.prod` → `/opt/aiknow/server.env`로 전송한다.
  두 파일은 배포 계정이 읽을 수 있도록 소유자를 맞추고 권한을 600으로 설정한다. 실제 env와 개인키는 Git에 올리지 않는다.
- `SERVER_ENV_FILE`, `SERVER_SECRETS_PATH`, `MEDIA_HOST_PATH`를 EC2의 실제 절대 경로로 맞춘다.
  secrets와 media bind mount 원본 폴더는 기능 사용 여부와 관계없이 존재해야 한다.
  Firebase 기능을 켜면 Spring UID 10001이 필요한 파일을 읽을 수 있게 권한을 설정한다.
- `HTTP_BIND_ADDRESS=0.0.0.0`, `HTTP_PORT=80`, `HTTPS_PORT=443`을 확인한다.
- `API_DOMAIN`의 공개 DNS A 레코드는 EC2의 고정 공인 IP를 가리켜야 한다.
  기존 NAS를 가리키는 AAAA 레코드가 있다면 EC2 IPv6 구성에 맞게 수정하거나 제거한다.
- `ACME_EMAIL`, 동의한 `ACME_AGREE_TOS=true`와 서비스 도메인/OAuth URL을 확인한다.
  기존 `kro.kr` 도메인의 인증서 공유 발급 한도는 EC2로 이동해도 적용될 수 있다.
- t3.small의 RAM은 2GiB다. 현재 Compose에는 서비스별 메모리 제한이 없으므로 실제 워크로드에 대한
  Java/MySQL/n8n 메모리 조정과 부하 확인이 배포 전 별도로 필요하다.

## 보안 그룹과 SSH

HTTP 80과 HTTPS 443은 사용자 및 인증서 검증 서버에서 접근 가능해야 한다.
SSH 22의 `내 IP` 규칙은 로컬 관리자 접속만 허용하며 GitHub 호스팅 러너 접속은 허용하지 않는다.
러너가 사용하는 공인 IP의 `/32` 규칙을 별도로 허용해야 직접 SSH 배포가 가능하다.
이 워크플로는 AWS API를 호출하거나 보안 그룹 규칙을 자동 변경하지 않는다.
러너 IP별 자동 허용·회수 기능을 추가하려면 별도의 AWS OIDC 역할과 보안 그룹 관리 권한 구성이 필요하다.
전체 공개 `0.0.0.0/0`을 잠시 사용할 경우 배포 실패 시에도 종료 후 제한된 규칙으로 되돌려야 한다.

## 실행과 완료 확인

배포 사전 검사에서 운영 경로, Docker 데이터 경로와 `/var/lib/containerd`에 각각 최소 2GiB의 여유 공간이 있어야 한다.
이 값은 이미지 압축 해제를 위한 최소 검사이며 DB·미디어·백업 증가분까지 보장하지 않는다.
공간이 부족하면 EBS 확장 후 파티션과 파일시스템도 확장하고 `df -h`로 확인한다. 인증서/DB 볼륨을 삭제하지 않는다.
배포 로그는 UTC 시각과 단계 이름을 표시한다. 실패 시 마지막 단계와 종료 코드로 원인을 구분한다.
이미지(MySQL 포함)는 서비스 변경 전에 다운로드한다. MySQL은 TCP 인증 및 운영 DB 쿼리 성공 후 백업한다.
DB 준비·백업 실패 시 앱 배포를 중단하고 불완전한 백업 파일만 정리한다.

워크플로를 main에 반영하고 GitHub 설정과 EC2 준비가 끝나면, main push의 CI 성공 후
`Server release EC2`가 실행된다. 기존 NAS 릴리스 실행의 재실행으로 이 파일이 적용되지는 않는다.
서버 소스·Compose 파일은 `/opt/aiknow/releases/<SHA>`로 CD가 자동 전송한다.
배포 전 DB 백업, HTTPS 발급, 서버 health 확인, n8n 반영은 공유 스크립트에서 처리한다.
최초 n8n Owner 설정과 운영 연결 검증은 [PRODUCTION.md](PRODUCTION.md)를 참고한다.
