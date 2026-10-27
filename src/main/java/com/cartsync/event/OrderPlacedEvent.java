package com.cartsync.event;

public record OrderPlacedEvent(
        Long orderId,
        Long userId,
        Long cartId) {
        }