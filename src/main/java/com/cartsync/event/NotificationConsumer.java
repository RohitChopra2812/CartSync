package com.cartsync.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @RetryableTopic(
        attempts = "4",
        backOff = @BackOff(delay = 1000, multiplier = 2),
        dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "order.placed", groupId = "notification-svc",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(OrderPlacedEvent event) {
        log.info("NotificationConsumer received event orderId={} userId={} — sending email",
                event.orderId(), event.userId());
    }
}