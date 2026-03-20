import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Properties;

public class ThreePartitionProducer {
    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:19092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        try {
            for (int i = 0; i < 100; i++) {
                int partition = i % 3;
                String key = "p" + partition + "-key-" + i;
                String value = "partition-msg-" + i;

                ProducerRecord<String, String> record =
                    new ProducerRecord<>("vk-topic", partition, key, value);
                Thread.sleep(3500);
                producer.send(record, (RecordMetadata metadata, Exception e) -> {
                    if (e == null) {
                        System.out.printf("Sent to partition=%d, offset=%d, value=%s%n",
                            metadata.partition(), metadata.offset(), value);
                    } else {
                        System.err.printf("Failed for value=%s, error=%s%n", value, e.getMessage());
                    }
                });
            }

            producer.flush();
            System.out.println("Done! Sent messages to partitions 0, 1, and 2.");
        } finally {
            producer.close();
        }
    }
}

