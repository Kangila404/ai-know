ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN suspended BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE card_view (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    card_news_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL,
    last_viewed_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_card_view_user_news UNIQUE (user_id, card_news_id),
    INDEX idx_card_view_news (card_news_id)
);
