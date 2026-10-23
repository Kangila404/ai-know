-- Existing approved theory must be available in the archive without waiting for a push.
-- The production editorial calendar uses Asia/Seoul (UTC+09:00).
-- Preserve first/last delivery timestamps: archive publication is not notification delivery.
UPDATE card_news
SET publication_status = 'PUBLISHED',
    publication_date = COALESCE(publication_date, DATE(DATE_ADD(COALESCE(approved_at, UTC_TIMESTAMP(6)), INTERVAL 9 HOUR))),
    updated_at = UTC_TIMESTAMP(6),
    version = version + 1
WHERE content_type = 'AI_THEORY'
  AND inspection_status = 'APPROVED'
  AND publication_status = 'READY';
