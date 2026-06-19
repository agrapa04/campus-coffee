SET TIME ZONE 'UTC';

CREATE TABLE reviews (
    id uuid NOT NULL PRIMARY KEY,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    pos_id uuid REFERENCES pos(id),
    author_id uuid REFERENCES users(id),
    review text NOT NULL CHECK (length(review) > 0),
    approval_count int NOT NULL CHECK (approval_count >= 0),
    approved boolean NOT NULL
);
