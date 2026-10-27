CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'PLACED',
    idempotency_key  VARCHAR(128) UNIQUE NOT NULL,
    created_at       TIMESTAMP DEFAULT NOW()
);