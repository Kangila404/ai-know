# 운영 DB 마이그레이션

운영은 Flyway를 먼저 실행한 뒤 Hibernate `validate`로 검사한다. `clean`과 자동 baseline은 비활성이다.
로컬 기본 설정은 기존 `ddl-auto=update`를 유지하며 Flyway를 실행하지 않는다.

## 신규 DB

빈 MySQL 8 데이터베이스와 DB 계정을 준비하고 prod 프로필로 기동한다.
`db/migration/V1__initial_schema.sql`은 앱 테이블과 Spring Batch 메타데이터/시퀀스를 생성한다.
V2는 검수 편집/공개 상태/변경 이력, V3는 생성 이미지 재사용과 복구 기록을 추가한다.
적용 이력·체크섬은 `flyway_schema_history`에 남는다. 적용한 SQL 파일은 수정하지 않고 다음 버전을 추가한다.

## 이미 데이터가 있는 DB

1. 서버·배치·n8n의 DB 쓰기를 멈추고 백업한다. 복구 가능한 백업인지 먼저 확인한다.
2. 기존 스키마가 어느 단계인지 확인한다. 예전 develop 스키마라면 `docs/sql/news-features-mysql.sql`,
   `daily-edition-mysql.sql`, `infra/n8n/generation-cache.mysql.sql` 중 **아직 적용하지 않은 변경만** 순서대로 적용한다.
3. Batch 테이블이 없다면 `docs/sql/spring-batch-mysql.sql`을 적용한다. 이미 있으면 다시 생성하지 않는다.
4. V1의 테이블·컬럼·인덱스·제약과 대조한다. 구버전의 공개 상태와 당일 발송 데이터 보정은 기존 SQL 지침을 따른다.
5. V1과 동일한 스키마임을 확인한 DB에만 Flyway CLI/API로 **baseline version 1**을 명시해 기록한다.
6. 새 서버를 시작하면 V2 이후가 적용된다. 원본 데이터·검수 상태·발송 이력이 유지되는지 확인한다.

현재 로컬처럼 Hibernate가 이미 V2/V3 변경까지 만든 DB를 version 1로 baseline하면 중복 DDL이 발생한다.
이 경우 최신 버전까지 실제 스키마를 검증한 뒤 해당 버전으로 채택하거나, 별도 신규 DB에서 검증한다.
프로그램이 기존 운영 스키마를 추측해 자동 baseline하지 않는다.

MySQL DDL은 일부 실패해도 이전 문장이 커밋될 수 있다. 실패했다고 `repair`부터 실행하거나 같은 SQL을 반복하지 않는다.
실패 문장·현재 스키마·백업을 확인하고 복구한 뒤 이력을 정리한다. 구 이미지로 되돌리는 것만으로 DB가 되돌아가지는 않는다.

[Spring Boot Flyway 초기화](https://docs.spring.io/spring-boot/how-to/data-initialization.html),
[Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline).
