CREATE TABLE card_read (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    card_news_id BIGINT NOT NULL,
    read_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_card_read_user_news UNIQUE (user_id, card_news_id)
) ENGINE=InnoDB;
