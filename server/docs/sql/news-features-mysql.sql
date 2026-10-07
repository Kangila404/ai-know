-- Apply ONCE to the develop schema before this feature branch, with application writers stopped.
-- MySQL 8.0. Back up first. DDL auto-commits: inspect existing schema before retrying a partial run.
-- Skip this script on databases already updated by Hibernate ddl-auto=update.
-- Apply spring-batch-mysql.sql separately when Batch metadata tables do not yet exist.

ALTER TABLE device_token
    ADD COLUMN installation_id VARCHAR(36) NULL,
    ADD CONSTRAINT uk_device_installation UNIQUE (platform, installation_id),
    ADD INDEX idx_device_user_active (user_id, active);
ALTER TABLE notification_setting ADD INDEX idx_notification_due (is_allowed, setting_time, id);

CREATE TABLE news_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_token_id BIGINT NOT NULL,
    card_news_id BIGINT NOT NULL,
    delivery_date DATE NOT NULL,
    status ENUM('CANCELLED','FAILED','PENDING','PROCESSING','SENT') NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    last_error VARCHAR(100) NULL,
    CONSTRAINT uk_news_delivery_day UNIQUE (user_id, device_token_id, delivery_date),
    INDEX idx_news_delivery_due (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notice (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    published BIT(1) NOT NULL,
    published_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_notice_published (published, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE news_submission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_hash VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    status ENUM('APPROVED','DENIED','PENDING') NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    review_note VARCHAR(2000) NULL,
    card_news_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_submission_source UNIQUE (source_hash),
    INDEX idx_submission_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE card_news
    MODIFY COLUMN title VARCHAR(200) NOT NULL,
    MODIFY COLUMN title_img_url VARCHAR(2048) NULL,
    ADD COLUMN source_title VARCHAR(500) NULL,
    ADD COLUMN source_url VARCHAR(2048) NULL,
    ADD COLUMN source_published_at DATETIME(6) NULL,
    ADD COLUMN summary VARCHAR(5000) NULL,
    ADD COLUMN title_image_source_url VARCHAR(2048) NULL,
    ADD COLUMN title_image_credit VARCHAR(500) NULL,
    ADD COLUMN title_image_origin VARCHAR(20) NULL;
ALTER TABLE card_news_key_points MODIFY COLUMN key_points VARCHAR(1000) NOT NULL;
ALTER TABLE card_slide
    MODIFY COLUMN title VARCHAR(200) NOT NULL,
    MODIFY COLUMN content VARCHAR(5000) NOT NULL,
    MODIFY COLUMN img_url VARCHAR(2048) NULL,
    ADD COLUMN layout VARCHAR(100) NULL,
    ADD COLUMN image_source_url VARCHAR(2048) NULL,
    ADD COLUMN image_credit VARCHAR(500) NULL,
    ADD COLUMN image_origin VARCHAR(20) NULL;
