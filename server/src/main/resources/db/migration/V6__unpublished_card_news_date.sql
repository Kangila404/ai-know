-- Approval creates READY content. The first delivery assigns its publication date.
-- Also safe when the same correction was applied as an operational hotfix.
ALTER TABLE card_news MODIFY COLUMN publication_date DATE NULL;
