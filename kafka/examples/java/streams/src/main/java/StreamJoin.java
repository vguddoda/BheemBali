import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Stream-Stream Join Example
 * 
 * Joins orders with payments within 5-minute window
 * 
 * Reads from "orders" and "payments" topics
 * Joins matching orders and payments
 * Writes to "order-payment-joined" topic
 * 
 * Run:
 * 1. Create topics first (see README.md)
 * 2. mvn clean package
 * 3. mvn exec:java -Dexec.mainClass="StreamJoin"
 */
public class StreamJoin {
    
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-join-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        
        StreamsBuilder builder = new StreamsBuilder();
        
        // Orders stream (keyed by orderId)
        KStream<String, String> orders = builder.stream("orders");
        
        // Payments stream (keyed by orderId)
        KStream<String, String> payments = builder.stream("payments");
        
        // Join within 5-minute window
        KStream<String, String> joined = orders.join(
            payments,
            (orderValue, paymentValue) -> {
                String result = "Order: " + orderValue + " | Payment: " + paymentValue;
                System.out.println("✓ Joined: " + result);
                return result;
            },
            JoinWindows.of(Duration.ofMinutes(5)),
            StreamJoined.with(
                Serdes.String(),
                Serdes.String(),
                Serdes.String()
            )
        );
        
        // Write joined results
        joined.to("order-payment-joined");
        
        // Also handle left join (keep orders even if payment missing)
        KStream<String, String> leftJoined = orders.leftJoin(
            payments,
            (orderValue, paymentValue) -> {
                if (paymentValue != null) {
                    return "PAID: " + orderValue + " with " + paymentValue;
                } else {
                    return "UNPAID: " + orderValue;
                }
            },
            JoinWindows.of(Duration.ofMinutes(5)),
            StreamJoined.with(
                Serdes.String(),
                Serdes.String(),
                Serdes.String()
            )
        );
        
        leftJoined
            .peek((key, value) -> System.out.println("Left Join: " + value))
            .to("order-payment-left-joined");
        
        final KafkaStreams streams = new KafkaStreams(builder.build(), props);
        final CountDownLatch latch = new CountDownLatch(1);
        
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });
        
        try {
            System.out.println("\n🚀 Starting Stream Join...");
            System.out.println("Join window: 5 minutes");
            System.out.println("Reading from: orders, payments");
            System.out.println("Writing to: order-payment-joined, order-payment-left-joined");
            System.out.println("Press Ctrl+C to stop\n");
            
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}

