# 이미지 선택 재실행 수정 적용

같은 기사·작업의 요청 본문이 바뀌면 서버는 중복 과금을 막기 위해 `BLOCKED`를 반환한다.
이제 이미지 검색 전에 기존 선택 기록을 확인하고, 완료된 기록의 **당시 후보 목록과 요청**을
복원하여 저장된 응답을 재사용한다. 후보만 새로 검색해서 예전 선택 결과와 섞지 않는다.
출처 페이지·라이선스 확인과 최종 초안 검증은 재개 후에도 수행한다.

## 필요한 변경

- 서버와 JSON을 함께 배포한다. V5 마이그레이션은 `context_json` nullable 열만 추가한다.
  기존 요청·응답·기사·볼륨은 삭제하지 않는다.
- 새 `image_selection` 예약은 초안과 후보 목록을 요청과 함께 원자적으로 저장한다.
- 재실행은 검색 전에 조회한다. 완료 기록은 복원, 없는 기록은 검색,
  원래 응답이 없거나 초안이 다르면 이유와 기록 ID를 표시하고 중단한다.
- 검색 HTTP 제한은 60초다. 검색 상한 20회와 이미지 관련성/라이선스 검사는 유지한다.
  아직 선택 예약에 도달하지 않은 검색 도중 중단은 검색을 처음부터 다시 할 수 있다.
- 이 수정은 새 이미지 재선택에 추가 유료 호출을 허용하지 않는다.

## 운영 적용 순서

1. 실행 중인 n8n 워크플로가 끝난 뒤 현재 워크플로를 내보내 백업한다.
2. EC2 `/opt/aiknow/infra.env`의 비밀 값은 유지하고 아래 운영 설정을 확인한다.

   ```dotenv
   AIKNOW_DRY_RUN=false
   AIKNOW_ALLOW_IMAGE_GENERATION=false
   ```

   처음 점검만 하려면 `AIKNOW_DRY_RUN=true`를 유지한다. JSON 기본값을 false로 바꾸지 않는다.
   실행 모드는 env를 통해 반영한다. OpenAI Credential은 다시 만들지 않는다.
3. 수정된 서버와 JSON이 포함된 커밋으로 CI/CD를 실행한다.
   예전 GitHub 실행의 Re-run은 예전 코드를 배포하므로 사용하지 않는다.
4. UI에서 `dryRun`/Timeout 등을 바꿨다면 CD의 n8n 적용이 충돌로 중단될 수 있다.
   서버가 새 버전으로 실행 중인지 확인한 뒤, 내보낸 UI 변경을 검토하고 아래처럼 적용한다.
   `<새 커밋 SHA>`는 이번 수정이 포함된 배포 커밋으로 바꾼다.

   ```sh
   cd /opt/aiknow/releases/<새 커밋 SHA>
   export SERVER_IMAGE="$(sudo docker inspect --format '{{.Config.Image}}' aiknow-prod-server-1)"
   export AIKNOW_DOCKER_SUDO=true
   export AIKNOW_INFRA_ENV_FILE=/opt/aiknow/infra.env
   export AIKNOW_COMPOSE_FILE="$PWD/infra/docker-compose.prod.yml"
   sh infra/n8n/deploy.sh apply ai-know-news-draft.json --replace
   ```

   `--replace`는 검토한 UI 편집을 새 JSON으로 대체한다. 적용 전 기존 JSON을 자동 백업하며,
   같은 노드의 OpenAI Credential 및 내부 API 인증정보는 보존한다.
   가져온 워크플로는 게시되지 않은 초안이다. 수동 검증 후 필요한 경우 게시한다.

## 기존 기록 4번 복구

이전 버전의 기록에는 후보 목록이 없으므로 배포만으로 복원할 수 없다.
위와 같은 EC2 터미널/릴리스 경로/환경에서, 다른 실행이 없는 동안 실행한다.

```sh
sh infra/n8n/recover-selection.sh 4
```

도구는 n8n을 잠시 중지한 뒤 SQLite 실행 이력을 읽기 전용으로 검사한다.
생성 기록 4번을 최초 예약한 `CALL` 또는 동일 요청의 `REPLAY` 입력만 선택한다.
나중에 막힌 `BLOCKED` 실행은 복구 원본으로 사용하지 않는다.
서버가 원래 요청의 해시를 대조한 뒤 후보 목록만 첨부한다.
기존 후보 목록이 이미 있으면 같은 내용만 허용하며 덮어쓰지 않는다.
모델을 호출하거나 기존 생성 기록을 삭제하지 않는다. 성공/실패 후 실행 중이던 n8n을 다시 시작한다.

- `contextRecovered:true`: 후보 복원 완료. n8n 새로고침 후 전체 워크플로를 한 번 수동 실행한다.
- 원래 실행 이력이 삭제됨: 자동 복구 불가. 보관한 원래 실행 데이터가 필요하다.
- 기존 모델 응답이 없음: 후보를 복구해도 유료 재호출은 차단된다. 원래 실행의 응답을 확인한 뒤
  기존 관리자 복구 절차를 사용한다. 기록 삭제나 임의 예약 해제로 우회하지 않는다.
- 이미 검수 DB에 저장된 기사: 다시 생성하지 않고 기존 기사 제외 단계에서 건너뛴다.

단순 `Success`가 아니라 `저장 확인 후 URL 기록`의 HTTP 202와 검수 상태 `PENDING`을 확인한다.
`blocked`/`skipped`는 기사 저장 성공과 다르다. 기존 기록 4번의 운영 복구는 실제 EC2에서 위 명령을
실행하기 전까지 확인된 것으로 간주하지 않는다.
