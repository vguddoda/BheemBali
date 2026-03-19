import org.apache.kafka.clients.producer.*;
import java.util.Properties;

public class IdempotentProducer {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("enable.idempotence", "true");
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        for (int i = 0; i < 5; i++) {
            producer.send(new ProducerRecord<>("test-topic", "exact-once-" + i));
            System.out.println("Sent: exact-once-" + i);
        }
        
        producer.close();
    }
}

