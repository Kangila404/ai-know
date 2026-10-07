-- Apply before deploying generation-cache endpoints when Hibernate DDL is disabled.
-- Local profile uses ddl-auto=update. Existing news_submission data is not changed.
CREATE TABLE IF NOT EXISTS news_generation_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_key VARCHAR(64) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    task VARCHAR(32) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    request_json MEDIUMTEXT NOT NULL,
    response_json MEDIUMTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_generation_attempt UNIQUE (attempt_key)
);
