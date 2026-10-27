package com.cartsync.order;

import com.cartsync.event.OrderEventProducer;
import com.cartsync.event.OrderPlacedEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;

@Service
public class OrderServiceImpl implements OrderService {

    private static final String IDEMPOTENCY_PREFIX = "order:";
    private static final long TTL_SECONDS = 86400;

    private final OrderRepository orderRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final OrderEventProducer orderEventProducer;

    public OrderServiceImpl(OrderRepository orderRepository,
            RedisTemplate<String, String> redisTemplate,
            OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.redisTemplate = redisTemplate;
        this.orderEventProducer = orderEventProducer;
    }

    @Override
    @Transactional
    public Order placeOrder(String idempotencyKey, Long userId, Long cartId) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        // Redis check — primary dedup
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PLACED", Duration.ofSeconds(TTL_SECONDS));

        if (Boolean.FALSE.equals(isNew)) {
            // already processed — return existing order
            return orderRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new RuntimeException("Order not found for key: " + idempotencyKey));
        }

        // DB check — fallback if Redis was down
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> {
                    Order order = new Order();
                    order.setUserId(userId);
                    order.setIdempotencyKey(idempotencyKey);
                    Order saved = orderRepository.save(order);
                    orderEventProducer.publish(new OrderPlacedEvent(saved.getId(), userId, cartId));
                    return saved;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
    }
}