
CREATE TABLE outbox_events(
      id UUID PRIMARY KEY,
      aggregate_id UUID NOT NULL,
      type VARCHAR(200) NOT NULL,
      payload JSONB NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;