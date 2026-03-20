package com.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Advanced KafkaListener with manual acknowledgment and batch processing
 */
@Component
public class AdvancedKafkaListener {

    /**
     * Manual commit with 5 concurrent consumers
     * Processes messages and manually commits offsets
     */
    @KafkaListener(
        topics = "test-topic",
        groupId = "advanced-group",
        containerFactory = "manualCommitContainerFactory",
        concurrency = "5"  // 5 concurrent consumers
    )
    public void listenWithManualCommit(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        String threadName = Thread.currentThread().getName();
        
        try {
            // Process message
            System.out.printf("[%s] Processing: %s [partition=%d, offset=%d]%n",
                threadName, message, partition, offset);
            
            // Simulate processing
            processMessage(message);
            
            // Manual commit after successful processing
            acknowledgment.acknowledge();
            System.out.printf("[%s] ✓ Committed offset %d%n", threadName, offset);
            
        } catch (Exception e) {
            System.err.printf("[%s] ✗ Error processing: %s%n", threadName, e.getMessage());
            // Don't acknowledge - message will be reprocessed
        }
    }
    
    private void processMessage(String message) throws Exception {
        // Your business logic here
        Thread.sleep(50);
    }
}

