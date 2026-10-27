package com.cartsync.cart;

public interface CartService {

    Cart getCart(Long cartId);

    CartItem addOrUpdateItem(Long cartId, Long productId, Integer quantity);

    void removeItem(Long cartId, Long itemId);

    void clearCart(Long cartId);
}