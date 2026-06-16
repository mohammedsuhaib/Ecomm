-- shared module: domain event types / value types have no persistent tables of
-- their own. The Spring Modulith event publication registry (transactional
-- outbox) is created here so the outbox is versioned alongside the rest of the
-- schema.
--
-- NOTE: Spring Modulith's JPA `JpaEventPublication` entity is NOT schema-
-- qualified, so it resolves to the app connection's default schema (`public`).
-- This migration runs with Flyway's default schema set to `flyway`, so the
-- table is qualified with `public` explicitly — otherwise it would be created
-- in the `flyway` schema and Hibernate `validate` (and the outbox at runtime)
-- would not find it. Keep it in `public` unless Modulith/Hibernate's default
-- schema is reconfigured to match.
CREATE TABLE IF NOT EXISTS public.event_publication (
    id               UUID NOT NULL,
    listener_id      TEXT NOT NULL,
    event_type       TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON public.event_publication (event_type, listener_id);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON public.event_publication (completion_date);
