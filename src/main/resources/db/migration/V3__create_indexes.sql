CREATE INDEX idx_carts_user_session  ON carts(user_id, session_id);
CREATE INDEX idx_cart_items_cart     ON cart_items(cart_id);
CREATE INDEX idx_orders_idempotency  ON orders(idempotency_key);