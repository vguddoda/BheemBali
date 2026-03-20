import org.apache.kafka.clients.consumer.*;
import java.time.Duration;
import java.util.*;

public class ConsumerGroup {
    public static void main(String[] args) {
        String id = args.length > 0 ? args[0] : "consumer-1";
        
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("group.id", "my-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("test-topic"));
        
        System.out.println(id + " listening...");
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("[%s] Got: %s (partition %d)%n",
                    id, record.value(), record.partition());
            }
        }
    }
}

