CREATE TABLE processed_events (
    event_id       VARCHAR(128) NOT NULL,
    consumer_group VARCHAR(64)  NOT NULL,
    processed_at   TIMESTAMP    DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer_group)
);