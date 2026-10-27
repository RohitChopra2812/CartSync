package com.cartsync.order;

public interface OrderService {

    Order placeOrder(String idempotencyKey, Long userId, Long cartId);

    Order getOrder(Long orderId);
}