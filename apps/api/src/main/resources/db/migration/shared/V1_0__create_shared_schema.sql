-- shared module: domain event types / value types have no persistent tables of
-- their own. The Spring Modulith event publication registry (transactional
-- outbox) is created here so the outbox is versioned alongside the rest of the
-- schema.
--
-- NOTE: Spring Modulith's JPA `JpaEventPublication` entity is NOT schema-
-- qualified, so it resolves to the connection's default schema (public). The
-- table must live there for both Hibernate `validate` and the outbox at runtime
-- to find it — do not move it into a named schema without also configuring
-- Modulith/Hibernate's default schema to match.
CREATE TABLE IF NOT EXISTS event_publication (
    id               UUID NOT NULL,
    listener_id      TEXT NOT NULL,
    event_type       TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication (event_type, listener_id);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);
