SET TIME ZONE 'UTC';

-- Authentication and authorization schema for assignment 5, added on top of the previous exercises'
-- migrations (V1-V6).

-- BCrypt via the delegating encoder stores a prefixed hash ("{bcrypt}$2a$10$...", ~68 chars), so the
-- column is text. It is nullable because a User can be constructed before a hash is set; the API
-- requires a password through the DTO instead.
ALTER TABLE users ADD COLUMN password_hash text;

-- The set of roles a user holds, backing the @ElementCollection of the Role enum on UserEntity. The
-- CHECK keeps the stored strings in step with the enum. Roles are an independent set of capabilities
-- (USER is the base; MODERATOR and ADMIN are orthogonal grants), with no implied rank.
CREATE TABLE user_roles (
    user_id uuid NOT NULL,
    role varchar(20) NOT NULL CHECK (role IN ('USER', 'MODERATOR', 'ADMIN')),
    PRIMARY KEY (user_id, role),
    -- named so deleting a user cascades to their roles
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Records who approved a review (its own surrogate id mirrors the other tables). The named unique
-- constraint on (review_id, user_id) is the authoritative "one approval per user per review" guard; the
-- application maps a violation to a 409.
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
