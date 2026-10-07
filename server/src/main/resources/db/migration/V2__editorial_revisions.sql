ALTER TABLE news_submission ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN edited_payload MEDIUMTEXT NULL;
ALTER TABLE card_news ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    MODIFY COLUMN publication_status ENUM('HIDDEN','PUBLISHED','READY') NOT NULL;
CREATE TABLE editorial_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    before_json MEDIUMTEXT NULL,
    after_json MEDIUMTEXT NULL,
    INDEX idx_editorial_resource (resource_type, resource_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
