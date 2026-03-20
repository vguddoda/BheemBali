package com.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Basic KafkaListener with concurrency
 * Concurrency level set in application.properties
 */
@Component
public class ConcurrentKafkaListener {

    /**
     * Single listener, multiple threads consuming in parallel
     * Concurrency configured via spring.kafka.listener.concurrency property
     */
    @KafkaListener(
        topics = "test-topic",
        groupId = "concurrent-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String threadName = Thread.currentThread().getName();
        System.out.printf("[%s] Received: %s [partition=%d, offset=%d]%n",
            threadName, message, partition, offset);
        
        // Simulate processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

