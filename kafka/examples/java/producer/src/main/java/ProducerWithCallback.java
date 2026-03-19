import org.apache.kafka.clients.producer.*;
import java.util.Properties;

public class ProducerWithCallback {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<>("test-topic", "key-" + i, "msg-" + i), 
                (metadata, e) -> {
                    if (e == null) {
                        System.out.printf("✓ Partition: %d, Offset: %d%n", 
                            metadata.partition(), metadata.offset());
                    }
                });
        }
        
        producer.close();
    }
}

