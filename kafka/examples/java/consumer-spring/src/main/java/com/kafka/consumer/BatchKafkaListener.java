package com.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Batch processing with KafkaListener
 * Receives and processes multiple messages at once
 */
@Component
public class BatchKafkaListener {

    /**
     * Batch consumer - receives list of messages
     * Configured for 10 concurrent batch consumers
     */
    @KafkaListener(
        topics = "test-topic",
        groupId = "batch-group",
        containerFactory = "batchContainerFactory",
        concurrency = "10"
    )
    public void listenBatch(
        @Payload List<String> messages,
        @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
        @Header(KafkaHeaders.OFFSET) List<Long> offsets
    ) {
        String threadName = Thread.currentThread().getName();
        
        System.out.printf("[%s] Batch received: %d messages%n", 
            threadName, messages.size());
        
        // Process batch
        for (int i = 0; i < messages.size(); i++) {
            System.out.printf("[%s] %d. %s [partition=%d, offset=%d]%n",
                threadName, i + 1, messages.get(i), 
                partitions.get(i), offsets.get(i));
        }
        
        // Batch database insert example
        saveBatchToDatabase(messages);
        
        System.out.printf("[%s] ✓ Batch processed successfully%n", threadName);
    }
    
    private void saveBatchToDatabase(List<String> messages) {
        // Simulate batch DB insert
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

