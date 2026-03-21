import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Windowed Aggregation Example
 * 
 * Counts events per 1-minute tumbling window
 * 
 * Reads from "events-input" topic
 * Groups by key and counts in 1-minute windows
 * Writes to "windowed-counts" topic
 * 
 * Run:
 * 1. Create topics first (see README.md)
 * 2. mvn clean package
 * 3. mvn exec:java -Dexec.mainClass="WindowedCount"
 */
public class WindowedCount {
    
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "windowed-count-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        
        StreamsBuilder builder = new StreamsBuilder();
        
        KStream<String, String> events = builder.stream("events-input");
        
        // Count events per 1-minute tumbling window
        TimeWindowedKStream<String, String> windowedStream = events
            .peek((key, value) -> 
                System.out.println("Event: key=" + key + ", value=" + value))
            .groupByKey()
            .windowedBy(TimeWindows.of(Duration.ofMinutes(1)));
        
        KTable<Windowed<String>, Long> windowedCounts = windowedStream.count();
        
        // Convert windowed result to regular stream
        windowedCounts.toStream()
            .map((windowedKey, count) -> {
                String key = windowedKey.key();
                long windowStart = windowedKey.window().start();
                long windowEnd = windowedKey.window().end();
                
                String result = String.format(
                    "Key: %s | Window: [%d - %d] | Count: %d",
                    key, windowStart, windowEnd, count
                );
                
                System.out.println("✓ " + result);
                
                return KeyValue.pair(key + "@" + windowStart, count);
            })
            .to("windowed-counts", Produced.with(Serdes.String(), Serdes.Long()));
        
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
            System.out.println("\n🚀 Starting Windowed Count Stream...");
            System.out.println("Window size: 1 minute (tumbling)");
            System.out.println("Reading from: events-input");
            System.out.println("Writing to: windowed-counts");
            System.out.println("Press Ctrl+C to stop\n");
            
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}

