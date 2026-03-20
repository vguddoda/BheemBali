package com.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration with different concurrency levels
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    /**
     * Base consumer configuration
     */
    private Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Performance tuning
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1048576);      // 1 MB
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);     // 30 sec
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);  // 5 min
        
        return props;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    /**
     * Default listener with auto-commit
     * Concurrency: 3 (3 concurrent consumers)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);  // 3 concurrent consumers
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }

    /**
     * Manual commit listener
     * Concurrency: 5 (5 concurrent consumers)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> manualCommitContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        
        Map<String, Object> props = consumerConfigs();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        
        factory.setConcurrency(5);  // 5 concurrent consumers
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        return factory;
    }

    /**
     * Batch listener
     * Concurrency: 10 (10 concurrent batch consumers)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        
        Map<String, Object> props = consumerConfigs();
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);  // Larger batch
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        
        factory.setConcurrency(10);  // 10 concurrent consumers
        factory.setBatchListener(true);  // Enable batch mode
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        
        return factory;
    }

    /**
     * High-throughput listener
     * Concurrency: 20 (20 concurrent consumers for maximum throughput)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> highThroughputContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        
        Map<String, Object> props = consumerConfigs();
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 5242880);      // 5 MB
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 2000);
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        
        factory.setConcurrency(20);  // 20 concurrent consumers - maximum throughput
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        
        return factory;
    }
}

