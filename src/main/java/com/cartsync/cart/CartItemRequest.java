package com.cartsync.cart;

public record CartItemRequest(Long productId, Integer quantity) {
}