import org.apache.kafka.clients.consumer.*;
import java.time.Duration;
import java.util.*;

public class ManualCommit {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("group.id", "manual-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("test-topic"));
        
        System.out.println("Manual commit mode...");
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, String> record : records) {
                System.out.println("Processing: " + record.value());
            }
            
            if (!records.isEmpty()) {
                consumer.commitSync();
                System.out.println("✓ Committed " + records.count() + " msgs");
            }
        }
    }
}

