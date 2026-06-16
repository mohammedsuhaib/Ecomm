-- shared module: domain event types / value types have no persistent tables of
-- their own, but the Spring Modulith event publication registry (transactional
-- outbox) lives in the shared `events` schema so every module can publish
-- through it without owning the metadata.
CREATE SCHEMA IF NOT EXISTS events;

-- Spring Modulith JDBC event publication table (Postgres dialect).
-- Created here (rather than via modulith.events.jdbc.schema-initialization) so
-- the outbox is versioned alongside the rest of the schema.
CREATE TABLE IF NOT EXISTS events.event_publication (
    id               UUID NOT NULL,
    listener_id      TEXT NOT NULL,
    event_type       TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON events.event_publication (event_type, listener_id);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON events.event_publication (completion_date);
