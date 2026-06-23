SET TIME ZONE 'UTC';

ALTER TABLE users ADD COLUMN password_hash text;

CREATE TABLE user_roles (
    user_id uuid NOT NULL,
    role varchar(20) NOT NULL CHECK (role IN ('USER', 'MODERATOR', 'ADMIN')),
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- grant any pre-existing user the base role (a no-op on a fresh database)
INSERT INTO user_roles (user_id, role) SELECT id, 'USER' FROM users;

CREATE TABLE review_approvals (
    id uuid NOT NULL PRIMARY KEY,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    review_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT uq_review_approvals_review_user UNIQUE (review_id, user_id),
    CONSTRAINT fk_review_approvals_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_approvals_user FOREIGN KEY (user_id) REFERENCES users(id)
);
