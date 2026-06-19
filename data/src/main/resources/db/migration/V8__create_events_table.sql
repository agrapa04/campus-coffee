SET TIME ZONE 'UTC';

-- Append-only event log for the optional event-sourcing persistence mode
-- (campus-coffee.persistence.mode = event-sourcing). The table is always created and the EventEntity is
-- always mapped; in the default relational mode nothing writes to it. When event sourcing is on, the log
-- is the source of truth and the other tables are a read model projected from it.
CREATE TABLE events (
    -- The event's own identity, an application-assigned UUID like every other entity (from the IdGenerator
    -- port). It is NOT monotonic, so it does not define the replay order; seq does.
    id uuid NOT NULL PRIMARY KEY,
    -- Append order: a strictly increasing counter the database assigns on insert (may have gaps from
    -- rolled-back inserts), so the log can be replayed in the order the events were appended (the UUID id
    -- cannot give that order).
    seq bigint NOT NULL GENERATED ALWAYS AS IDENTITY,
    -- INSERT, UPDATE, or DELETE (the ChangeType enum, stored as its name).
    change_type varchar(16) NOT NULL CHECK (change_type IN ('INSERT', 'UPDATE', 'DELETE')),
    -- The simple name of the domain type the event concerns (Pos, User, Review, ReviewApproval).
    entity_type varchar(255) NOT NULL,
    -- The event payload schema version, reserved for evolving the body format.
    entity_version bigint NOT NULL,
    -- The full state of the domain object as JSON (the domain object's own id lives inside the body; a
    -- DELETE carries only the id). jsonb so the column is validated and queryable.
    body jsonb NOT NULL,
    created_at timestamp NOT NULL
);

-- The log is read back in append order on a rebuild (events -> data).
CREATE INDEX idx_events_seq ON events (seq);

-- Look up the events of one domain object by the id embedded in the body (e.g., to inspect an entity's
-- history); also speeds up checking whether a domain type already has events (so the import can skip it).
CREATE INDEX idx_events_entity_type ON events (entity_type);
CREATE INDEX idx_events_body_id ON events ((body ->> 'id'));
