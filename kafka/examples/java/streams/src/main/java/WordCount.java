import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Classic Word Count Example
 * 
 * Reads from "streams-plaintext-input" topic
 * Counts words
 * Writes to "streams-wordcount-output" topic
 * 
 * Run:
 * 1. Create topics first (see README.md)
 * 2. mvn clean package
 * 3. mvn exec:java -Dexec.mainClass="WordCount"
 */
public class WordCount {
    
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        
        // Disable cache for immediate results (good for demo)
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        
        StreamsBuilder builder = new StreamsBuilder();
        
        // Read from input topic
        KStream<String, String> textLines = builder.stream("streams-plaintext-input");
        
        // Word count logic
        KTable<String, Long> wordCounts = textLines
            // Print each line
            .peek((key, value) -> System.out.println("Read line: " + value))
            
            // Split line into words (stateless)
            .flatMapValues(value -> Arrays.asList(value.toLowerCase().split("\\s+")))
            
            // Print each word
            .peek((key, word) -> System.out.println("  Word: " + word))
            
            // Group by word (triggers repartition if needed)
            .groupBy((key, word) -> word)
            
            // Count occurrences (stateful - uses RocksDB)
            .count(Materialized.as("counts-store"));
        
        // Convert KTable to KStream and write to output
        wordCounts.toStream()
            .peek((word, count) -> 
                System.out.println("✓ Word: '" + word + "' Count: " + count))
            .to("streams-wordcount-output", 
                Produced.with(Serdes.String(), Serdes.Long()));
        
        // Build and start
        final Topology topology = builder.build();
        System.out.println(topology.describe());
        
        final KafkaStreams streams = new KafkaStreams(topology, props);
        final CountDownLatch latch = new CountDownLatch(1);
        
        // Attach shutdown handler
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });
        
        try {
            System.out.println("\n🚀 Starting Word Count Stream...");
            System.out.println("Reading from: streams-plaintext-input");
            System.out.println("Writing to: streams-wordcount-output");
            System.out.println("Press Ctrl+C to stop\n");
            
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}

