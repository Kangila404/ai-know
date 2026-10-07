# TrueNAS 이미지 저장

로컬 개발은 NAS 없이 별도 `local` 모드를 사용합니다. [환경별 설정](ENVIRONMENTS.md)을 먼저 참고하세요.

특정 TrueNAS 버전이나 관리 API에 의존하지 않습니다. NAS 소유자가 제공한 SMB 또는 NFS 공유를
Spring 실행 호스트에 마운트합니다. n8n은 Spring API에 파일을 보내고, Spring은 마운트된 폴더에 저장합니다.
NAS 관리 페이지 주소나 관리 API 키를 넣는 방식이 아닙니다.

```text
n8n 생성 이미지 → 인증된 Spring 업로드 API → SMB/NFS로 마운트한 TrueNAS 폴더
                                          ↓
                               영구 HTTPS 이미지 URL → 검수 대기 DB
```

## NAS 소유자에게 받을 정보

- VPN/내부망에서 연결 가능한 NAS 호스트와 SMB 공유 이름 또는 NFS export 경로
- 해당 폴더의 읽기·쓰기·파일 이름 변경 권한 (SMB 계정 또는 NFS 허용 호스트/UID/GID)
- 서버가 접근할 수 있는 네트워크/VPN 경로

TrueNAS 소유자는 전용 dataset/공유를 만들면 됩니다. 파일별 관리 API나 S3 서비스는 필요 없습니다.
SMB/NFS를 인터넷에 직접 공개하지 않고 서버와 NAS 사이의 연결을 사용합니다.
공식 [SMB 연결](https://www.truenas.com/docs/scale/shares/smb/addmanagesmbshares/),
[NFS 연결](https://www.truenas.com/docs/scale/shares/nfs/addingnfsshares/).

## 1. 운영체제에서 공유 폴더 마운트

Linux는 NAS 소유자가 제공한 정보를 사용해 `/mnt/aiknow-media` 같은 경로에 마운트합니다.
NFS 예시의 대괄호는 실제 값으로 바꿉니다.

```sh
sudo mount -t nfs '[NAS호스트]:[export경로]' /mnt/aiknow-media
```

SMB는 `mount -t cifs`와 OS의 credentials 파일을 사용합니다. 비밀번호를 워크플로 JSON이나
명령 인자에 넣지 마세요. Windows는 Spring 실행 계정에서 SMB 공유에 연결하고
`Z:/aiknow-media` 또는 UNC 경로를 사용할 수 있습니다. 서비스 계정은 로그인 사용자와 드라이브 매핑이 다를 수 있습니다.
서버 재부팅 때도 자동 마운트되고 Spring이 마운트 이후 시작되도록 호스트에 설정합니다.

Docker의 Spring 컨테이너라면 **호스트의 실제 마운트 경로를 컨테이너에 bind mount**합니다.
현재 기본 Compose에는 Spring 서비스가 없으므로, 아래 항목은 실제 Spring 서비스 정의에 합칩니다.

```yaml
environment:
  MEDIA_STORAGE_MODE: ${MEDIA_STORAGE_MODE}
  MEDIA_STORAGE_ENABLED: ${MEDIA_STORAGE_ENABLED}
  MEDIA_ROOT: ${MEDIA_ROOT}
  MEDIA_PUBLIC_BASE_URL: ${MEDIA_PUBLIC_BASE_URL}
  MEDIA_VOLUME_ID: ${MEDIA_VOLUME_ID}
volumes:
  - type: bind
    source: ${MEDIA_HOST_PATH}
    target: ${MEDIA_ROOT}
    bind:
      create_host_path: false
```

이때 Compose가 읽는 `.env`에 `MEDIA_HOST_PATH=/mnt/aiknow-media`, `MEDIA_ROOT=/var/aiknow-media`처럼
호스트 경로와 컨테이너 경로를 구분합니다. n8n 컨테이너에는 NAS 마운트가 필요 없습니다.

## 2. 저장소 표식과 서버 환경변수

**NAS가 실제 마운트된 것을 확인한 후**, 저장할 폴더에 `.aiknow-storage-id` 파일을 한 번 만듭니다.
내용은 `aiknow-media-prod` 같은 고유 식별자 한 줄입니다. 비밀키가 아닙니다.
마운트 전 로컬의 빈 폴더에 이 파일을 만들면 연결 해제 감지 목적을 잃습니다.

`server/.env` (또는 서버 프로세스에 주입하는 환경변수):

```dotenv
MEDIA_STORAGE_ENABLED=true
MEDIA_STORAGE_MODE=mounted
MEDIA_ROOT=/mnt/aiknow-media
MEDIA_PUBLIC_BASE_URL=https://실제-API-도메인/media/generated
MEDIA_VOLUME_ID=aiknow-media-prod
```

- `MEDIA_ROOT`: Spring 프로세스가 보는 실제 공유 폴더 경로. 폴더는 자동으로 만들지 않습니다.
- `MEDIA_PUBLIC_BASE_URL`: 앱에서 접근할 HTTPS 주소. 위 예시는 실제 도메인으로 반드시 교체합니다.
  기존 HTTPS 리버스 프록시에서 `/media/generated/*`를 Spring에 전달하면 됩니다.
  NAS 관리 UI 주소가 아닙니다. 프록시 업로드 크기도 multipart 포함 11 MiB 이상을 허용합니다.
- `MEDIA_VOLUME_ID`: 공유 폴더의 `.aiknow-storage-id` 내용과 일치해야 합니다.
- 미설정 기본값은 비활성화입니다. `.env`는 자동으로 Spring에 로드되지 않으므로 실행 환경에 주입하고 재시작합니다.
- NAS 비밀번호는 호스트의 마운트 설정에만 둡니다. n8n과 Spring에는 NAS 비밀번호가 필요 없습니다.

NAS를 바꿀 때 파일과 표식 파일을 새 NAS로 옮긴 뒤 마운트 경로/환경변수를 변경합니다.
기존 DB URL을 유지하려면 **공개 이미지 도메인과 URL 경로는 유지**해야 합니다.

## 3. n8n 연결

`AI_KNOW Ingestion` Header Auth Credential (`Authorization: Bearer ...`)을 기존 수집 API와 공유합니다.

1. 이미지 생성이 켜진 실제 실행은 `GET /internal/v1/images/storage`로 NAS 표식과 쓰기 권한부터 점검합니다.
   미설정/연결 실패면 503으로 종료하고 유료 API는 호출하지 않습니다. dryRun은 이 점검을 건너뜁니다.
2. 초안 검증 후 생성 이미지 Binary만 `POST /internal/v1/images`에 multipart `file`로 전송합니다.
3. 업로드 완료 응답의 URL을 슬라이드에 넣고 `GENERATED`, `AI 생성 이미지`를 기록합니다.
4. 모든 업로드가 성공해야 기존 검수 대기 수집 API를 호출합니다. 저장 성공 후에만 URL 처리 이력을 기록합니다.

PNG 최대 10 MiB / 4096×4096을 허용합니다. 원본 파일명과 MIME 주장 대신 실제 PNG를 검사합니다.
파일 내용의 SHA-256 이름과 같은 폴더 안의 atomic rename을 사용해 동일 바이트 재전송은 같은 URL을 반환합니다.
공유 폴더는 atomic rename을 지원해야 합니다. 이미지 생성 API 오류·업로드 오류는 자동으로 유료 재시도하지 않습니다.
중간에 일부 파일만 업로드된 뒤 DB 저장이 실패하면 업로드된 파일은 남습니다. 재시도에 재사용할 수 있으며 자동 삭제하지 않습니다.

이미지 주소는 공개 GET/HEAD입니다. 검수 전 이미지는 URL을 가진 사람이 접근할 수 있으며,
뉴스 본문과 승인/게시 권한은 기존 검수 정책을 유지합니다.

서버 주소는 해당 환경의 `infra/.env` 또는 지정한 env 파일에 설정하고 워크플로에 적용합니다.

```powershell
.\infra\n8n\deploy.ps1 apply -EnvFile infra/.env.prod
```

다른 Docker Compose의 Spring 서비스라면 같은 네트워크에서 `http://server:8080` 등 실제 서비스 이름을 사용합니다.
외부 서버는 HTTPS를 사용합니다. 워크플로의 이미지 점검·업로드·뉴스 저장 URL이 함께 변경됩니다.
UI에서 편집한 내용이 있다면 기존 배포 절차에 따라 먼저 소스로 반영합니다.

## 검증

`ImageStorageTests`는 인증, 파일 저장/중복/조회, 경로 제한, PNG 검증, 용량 제한, NAS 표식 불일치를 검사합니다.
`image-upload-nodes.test.mjs`는 이미지 공유/매핑, 출처 이미지 유지, 부분 업로드 실패를 검사합니다.

실제 n8n HTTP 노드 검사 도구 `verify-image-upload.mjs <Spring URL>`은 기존 수집 Credential을 사용합니다.
배포 도구와 같은 방식으로 n8n을 정지한 상태에서 `n8n-workflows` 컨테이너로 실행합니다.
이 도구는 작은 테스트 PNG를 업로드하고 **검수 대기 테스트 초안 1건을 남깁니다**. OpenAI는 호출하지 않습니다.
NAS 연결 정보가 없는 환경에서 로컬 폴더로 통과한 검사는 실제 NAS 접근 가능 여부를 보장하지 않습니다.
