CREATE TABLE admin_alert (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(200) NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    submission_id BIGINT NULL,
    card_news_id BIGINT NULL,
    delivery_date DATE NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_admin_alert_event UNIQUE (event_key)
);
CREATE TABLE admin_alert_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    device_token_id BIGINT NOT NULL,
    status ENUM('PENDING','PROCESSING','SENT','FAILED','CANCELLED') NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    last_error VARCHAR(100) NULL,
    CONSTRAINT uk_admin_alert_device UNIQUE (alert_id, device_token_id),
    INDEX idx_admin_alert_due (status,next_attempt_at)
);
