import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Stream-Table Join Example (Enrichment Pattern)
 * 
 * Enriches order stream with customer data from table
 * 
 * Reads from "orders-stream" and "customers-table" topics
 * Looks up customer info for each order
 * Writes enriched orders to "enriched-orders" topic
 * 
 * Run:
 * 1. Create topics first (see README.md)
 * 2. mvn clean package
 * 3. mvn exec:java -Dexec.mainClass="StreamTableJoin"
 */
public class StreamTableJoin {
    
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-table-join-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        
        StreamsBuilder builder = new StreamsBuilder();
        
        // Customer table (latest state per customer)
        // Topic should be compacted
        KTable<String, String> customers = builder.table("customers-table");
        
        // Orders stream (keyed by customerId)
        KStream<String, String> orders = builder.stream("orders-stream");
        
        // Enrich orders with customer data
        KStream<String, String> enriched = orders
            .peek((customerId, order) -> 
                System.out.println("Processing order: " + order + " for customer: " + customerId))
            .join(
                customers,
                (orderValue, customerValue) -> {
                    String enrichedOrder = String.format(
                        "Order: %s | Customer: %s", 
                        orderValue, customerValue
                    );
                    System.out.println("✓ Enriched: " + enrichedOrder);
                    return enrichedOrder;
                }
            );
        
        enriched.to("enriched-orders");
        
        // Left join - keep orders even if customer not found
        KStream<String, String> enrichedOrUnknown = orders.leftJoin(
            customers,
            (orderValue, customerValue) -> {
                if (customerValue != null) {
                    return "Order: " + orderValue + " | Customer: " + customerValue;
                } else {
                    return "Order: " + orderValue + " | Customer: UNKNOWN";
                }
            }
        );
        
        enrichedOrUnknown
            .peek((key, value) -> System.out.println("Left Join: " + value))
            .to("enriched-orders-with-unknown");
        
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
            System.out.println("\n🚀 Starting Stream-Table Join (Enrichment)...");
            System.out.println("Stream: orders-stream (keyed by customerId)");
            System.out.println("Table: customers-table (customer profiles)");
            System.out.println("Output: enriched-orders");
            System.out.println("Press Ctrl+C to stop\n");
            
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}

