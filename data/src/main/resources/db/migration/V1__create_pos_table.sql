SET TIME ZONE 'UTC';

CREATE TABLE pos (
    id uuid NOT NULL PRIMARY KEY,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    name varchar(255) NOT NULL CHECK (name <> ''),
    description text CHECK (description <> ''),
    type varchar(255) NOT NULL,
    campus varchar(255) NOT NULL,
    street varchar(255) NOT NULL CHECK (street <> ''),
    house_number int NOT NULL,
    house_number_suffix char(1),
    postal_code int NOT NULL,
    city varchar(255) NOT NULL CHECK (city <> ''),
    CONSTRAINT uq_pos_name UNIQUE (name)
);
