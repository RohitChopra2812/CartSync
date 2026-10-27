CREATE TABLE carts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT,
    session_id VARCHAR(128),
    created_at TIMESTAMP DEFAULT NOW(),
    version    INT NOT NULL DEFAULT 0
);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT NOT NULL REFERENCES carts(id),
    product_id BIGINT NOT NULL,
    quantity   INT NOT NULL CHECK (quantity > 0),
    version    INT NOT NULL DEFAULT 0
);