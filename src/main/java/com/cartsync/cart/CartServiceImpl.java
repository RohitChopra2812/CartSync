package com.cartsync.cart;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.OptimisticLockException;
import java.util.Optional;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Cart getCart(Long cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
    }

    @Override
    @Transactional
    @Retryable(retryFor = OptimisticLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2))
    public CartItem addOrUpdateItem(Long cartId, Long productId, Integer quantity) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));

        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cartId, productId);

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(quantity);
            return cartItemRepository.save(item);
        }

        CartItem newItem = new CartItem();
        newItem.setCart(cart);
        newItem.setProductId(productId);
        newItem.setQuantity(quantity);
        return cartItemRepository.save(newItem);
    }

    @Override
    @Transactional
    public void removeItem(Long cartId, Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
        cartItemRepository.delete(item);
    }

    @Override
    @Transactional
    public void clearCart(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
        cart.getItems().clear();
        cartRepository.save(cart);
    }
}