ALTER TABLE reviews ALTER COLUMN pos_id SET NOT NULL;
ALTER TABLE reviews ALTER COLUMN author_id SET NOT NULL;
ALTER TABLE reviews ADD CONSTRAINT uq_reviews_pos_author UNIQUE (pos_id, author_id);
