package com.cartsync.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Component;

@Component
public class InventoryConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumer.class);

    @RetryableTopic(
        attempts = "4",
        backOff = @BackOff(delay = 1000, multiplier = 2),
        dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "order.placed", groupId = "inventory-svc",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(OrderPlacedEvent event) {
        log.info("InventoryConsumer received event orderId={} cartId={} — decrementing stock",
                event.orderId(), event.cartId());
        // throw new RuntimeException("Simulated failure — testing DLT");
    }
}