package com.cartsync.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);

    @KafkaListener(topics = "order.placed.DLT", groupId = "dlt-handler", containerFactory = "kafkaListenerContainerFactory")
    public void handle(OrderPlacedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("DLT message received from topic={} orderId={} — manual replay required",
                topic, event.orderId());
    }
}