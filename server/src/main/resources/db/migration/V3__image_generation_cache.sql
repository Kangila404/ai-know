ALTER TABLE news_generation_record MODIFY COLUMN task VARCHAR(64) NOT NULL;
ALTER TABLE news_generation_record ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN recovered_response_json MEDIUMTEXT NULL;
