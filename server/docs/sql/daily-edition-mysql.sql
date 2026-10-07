-- Apply ONCE after news-features-mysql.sql, with application writers stopped.
-- Also applies to databases that already ran the original PR #10 schema.
-- Existing approved articles were public under the old behavior: preserve them as PUBLISHED.
-- Back up first. MySQL DDL auto-commits; do not rerun blindly after partial failure.

ALTER TABLE card_news
    MODIFY COLUMN publication_date DATE NULL,
    ADD COLUMN publication_status ENUM('PUBLISHED','READY') NOT NULL DEFAULT 'READY',
    ADD COLUMN content_type ENUM('AI_THEORY','NEWS') NOT NULL DEFAULT 'NEWS',
    ADD COLUMN approved_at DATETIME(6) NULL,
    ADD COLUMN first_used_at DATETIME(6) NULL,
    ADD COLUMN last_used_at DATETIME(6) NULL,
    ADD INDEX idx_news_selection (inspection_status, content_type, publication_status, approved_at);

UPDATE card_news
SET publication_status = 'PUBLISHED', approved_at = created_at,
    first_used_at = created_at, last_used_at = created_at
WHERE inspection_status = 'APPROVED';

CREATE TABLE daily_news_edition (
    delivery_date DATE NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL,
    card_news_id BIGINT NULL,
    selected_at DATETIME(6) NOT NULL,
    approval_cutoff DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Old queued deliveries were selected by publication date, not by a fixed daily edition.
-- Cancel these at cutover rather than accidentally sending a different article on the same date.
UPDATE news_delivery SET status = 'CANCELLED' WHERE status IN ('PENDING','PROCESSING');
