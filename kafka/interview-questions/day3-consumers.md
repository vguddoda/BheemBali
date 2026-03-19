# Day 3: Kafka Consumers - Interview Questions

## Consumer Basics

### 1. How does a Kafka consumer work?
**Answer:**
1. Consumer subscribes to topic(s)
2. Joins consumer group (or reads alone)
3. Gets assigned partitions
4. Polls for messages in batches
5. Processes messages
6. Commits offsets (auto or manual)

### 2. What is a consumer group?
**Answer:**
A consumer group is multiple consumers working together to read from a topic. Each partition is assigned to only ONE consumer in the group.

**Benefits:**
- Load balancing
- Fault tolerance
- Scalability

**Example:**
```java
props.put("group.id", "my-group");  // All consumers with same group.id share work
```

### 3. How many consumers can read from a topic?
**Answer:**
- **Max useful consumers = number of partitions**
- Topic with 3 partitions → max 3 consumers in same group
- Extra consumers sit idle
- Different groups can all read same messages

### 4. What is offset?
**Answer:**
Position of consumer in partition. Like a bookmark.

**Types:**
- Current offset: Last read message
- Committed offset: Last saved position
- If consumer crashes, restarts from committed offset

### 5. What is auto.offset.reset?
**Answer:**
What to do when no offset exists (new consumer).

- `earliest` - Read from beginning
- `latest` - Read only new messages (default)
- `none` - Throw error

```java
props.put("auto.offset.reset", "earliest");
```

## Offset Management

### 6. What is offset commit?
**Answer:**
Saving consumer's position in partition.

**Auto commit:**
```java
props.put("enable.auto.commit", "true");  // Default
props.put("auto.commit.interval.ms", "5000");  // Every 5 sec
```

**Manual commit:**
```java
props.put("enable.auto.commit", "false");
consumer.commitSync();  // After processing
```

### 7. When to use manual commit?
**Answer:**
When you need **at-least-once** or **exactly-once** processing.

**Example:**
```java
ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

for (ConsumerRecord<String, String> record : records) {
    processMessage(record);  // Save to database
}

consumer.commitSync();  // Only commit after processing
```

If you crash before commit → reprocess messages (at-least-once)

### 8. commitSync vs commitAsync?
**Answer:**

| commitSync | commitAsync |
|------------|-------------|
| Blocks until done | Returns immediately |
| Retries on failure | No retry |
| Lower throughput | Higher throughput |
| Use for critical data | Use for high-volume |

### 9. What happens if consumer crashes?
**Answer:**
1. Consumer stops sending heartbeats
2. Group coordinator detects failure
3. Triggers rebalance
4. Remaining consumers take over partitions
5. Read from last committed offset

### 10. What is consumer lag?
**Answer:**
How far behind consumer is from latest message.

```
Lag = Latest Offset - Consumer Offset
```

**Check lag:**
```bash
kafka-consumer-groups --bootstrap-server localhost:19092 \
  --group my-group --describe
```

High lag = consumer too slow

## Consumer Groups

### 11. What is rebalancing?
**Answer:**
Reassigning partitions when consumers join/leave group.

**Triggers:**
- New consumer joins
- Consumer crashes
- Consumer leaves
- Partitions added to topic

**During rebalance:** No messages consumed (stop-the-world)

### 12. What is partition assignment strategy?
**Answer:**
How partitions are distributed to consumers.

**Range (default):**
- Divides partitions by range
- Can be uneven

**RoundRobin:**
- Even distribution
- All consumers subscribe to same topics

**Sticky:**
- Minimizes partition movement during rebalance
- Better performance

```java
props.put("partition.assignment.strategy", "org.apache.kafka.clients.consumer.RoundRobinAssignor");
```

### 13. What is session.timeout.ms?
**Answer:**
How long broker waits before declaring consumer dead.

```java
props.put("session.timeout.ms", "10000");  // 10 sec
props.put("heartbeat.interval.ms", "3000");  // Send heartbeat every 3 sec
```

Too low → false positives (unnecessary rebalances)
Too high → slow failure detection

### 14. What is max.poll.interval.ms?
**Answer:**
Max time between poll() calls. If exceeded → consumer kicked out.

```java
props.put("max.poll.interval.ms", "300000");  // 5 min
```

Use when processing takes time:
```java
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    for (ConsumerRecord<String, String> record : records) {
        slowProcess(record);  // Takes 2 min
    }
    // Must call poll() again within 5 min
}
```

### 15. Can one consumer read from multiple topics?
**Answer:**
Yes!

```java
consumer.subscribe(Arrays.asList("topic1", "topic2", "topic3"));
```

Or pattern:
```java
consumer.subscribe(Pattern.compile("user-.*"));  // All topics starting with user-
```

## Performance & Tuning

### 16. What is fetch.min.bytes?
**Answer:**
Minimum data broker sends to consumer. Reduces network calls.

```java
props.put("fetch.min.bytes", "1048576");  // Wait for 1 MB
props.put("fetch.max.wait.ms", "500");  // Or wait max 500ms
```

Trade-off: Throughput vs Latency

### 17. What is max.poll.records?
**Answer:**
Max records returned in single poll().

```java
props.put("max.poll.records", "500");  // Default 500
```

Lower value → More frequent polls → Lower chance of timeout
Higher value → Fewer polls → Better throughput

### 18. How to handle slow processing?
**Answer:**
**Option 1:** Pause/Resume
```java
consumer.pause(consumer.assignment());
// Process slowly
consumer.resume(consumer.assignment());
```

**Option 2:** Increase timeout
```java
props.put("max.poll.interval.ms", "600000");  // 10 min
```

**Option 3:** Multi-threaded processing
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
for (ConsumerRecord<String, String> record : records) {
    executor.submit(() -> process(record));
}
```

### 19. What is deserialization error handling?
**Answer:**
If message can't be deserialized → consumer stuck!

**Solution:**
```java
props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

// Or custom deserializer with error handling
public class SafeDeserializer implements Deserializer<String> {
    public String deserialize(String topic, byte[] data) {
        try {
            return new String(data);
        } catch (Exception e) {
            return null;  // Or log and return default
        }
    }
}
```

### 20. How to seek to specific offset?
**Answer:**
```java
// Seek to beginning
consumer.seekToBeginning(consumer.assignment());

// Seek to end
consumer.seekToEnd(consumer.assignment());

// Seek to specific offset
TopicPartition partition = new TopicPartition("my-topic", 0);
consumer.seek(partition, 100);  // Start from offset 100
```

## Advanced Scenarios

### 21. Can I read from specific partition?
**Answer:**
Yes! Use assign instead of subscribe.

```java
TopicPartition partition0 = new TopicPartition("my-topic", 0);
TopicPartition partition1 = new TopicPartition("my-topic", 1);

consumer.assign(Arrays.asList(partition0, partition1));

// No consumer group, no rebalancing
```

### 22. What's the difference between subscribe vs assign?
**Answer:**

| subscribe | assign |
|-----------|--------|
| Uses consumer group | No consumer group |
| Auto rebalancing | Manual partition management |
| Dynamic partition assignment | Fixed partitions |
| Can't control which partitions | Full control |

### 23. How to process exactly once?
**Answer:**
**Option 1:** Idempotent processing
```java
// Save to database with unique key
saveToDatabase(record.key(), record.value());  // Key prevents duplicates
```

**Option 2:** Transactional processing
```java
// Store offset with processed data in same transaction
db.beginTransaction();
db.save(record.value());
db.saveOffset(record.partition(), record.offset());
db.commit();
```

### 24. What is isolation.level?
**Answer:**
Controls which messages consumer sees.

```java
props.put("isolation.level", "read_committed");  // Only committed transactions
// Or
props.put("isolation.level", "read_uncommitted");  // All messages (default)
```

Use with transactional producers.

### 25. How to handle poison pill messages?
**Answer:**
Message that crashes consumer on every retry.

**Solution:**
```java
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    for (ConsumerRecord<String, String> record : records) {
        try {
            process(record);
        } catch (Exception e) {
            // Send to dead letter queue
            sendToDeadLetterQueue(record);
            // Log error
            logger.error("Failed to process: " + record, e);
        }
    }
    
    consumer.commitSync();  // Commit even if processing failed
}
```

### 26. How to design a fast consumer for high throughput?
**Answer:**

**All steps for fast consumer processing:**

---

**Step 1: Optimize Fetch Settings**
```java
// Fetch more data per request
props.put("fetch.min.bytes", "1048576");      // Wait for 1 MB (default: 1 byte)
props.put("fetch.max.wait.ms", "500");        // Or wait max 500ms
props.put("max.partition.fetch.bytes", "5242880"); // 5 MB per partition
```

**How it helps:**
- Fetch 1MB instead of small chunks → Fewer network roundtrips
- Batching reduces overhead (like bulk buying vs single items)
- 1MB in one call vs 1000 calls of 1KB each

---

**Step 2: Increase Poll Batch Size**
```java
props.put("max.poll.records", "1000");  // Default: 500
```

**How it helps:**
- Process 1000 messages per poll() instead of 500
- Amortize poll() overhead across more messages
- Better batch processing efficiency

**Trade-off:** Must process within `max.poll.interval.ms` or consumer gets kicked out

---

**Step 3: Multi-threaded Processing**
```java
ExecutorService executor = Executors.newFixedThreadPool(10);

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    List<Future<?>> futures = new ArrayList<>();
    for (ConsumerRecord<String, String> record : records) {
        futures.add(executor.submit(() -> process(record)));
    }
    
    // Wait for all to complete
    for (Future<?> future : futures) {
        future.get();
    }
    
    consumer.commitSync();
}
```

**How it helps:**
- 10 threads process in parallel vs 1 thread sequential
- 10x faster processing
- CPU fully utilized

---

**Step 4: Async Commit for Higher Throughput**
```java
props.put("enable.auto.commit", "false");

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    processRecords(records);
    
    // Async commit - don't wait
    consumer.commitAsync((offsets, exception) -> {
        if (exception != null) {
            logger.error("Commit failed", exception);
        }
    });
}
```

**How it helps:**
- No blocking on commit → Keep processing
- commitSync blocks 5-10ms per commit
- commitAsync returns immediately

**Trade-off:** Risk of duplicate processing on failure (no retry)

---

**Step 5: Tune Timeouts to Avoid Rebalancing**
```java
props.put("session.timeout.ms", "30000");        // 30 sec (default: 10 sec)
props.put("heartbeat.interval.ms", "3000");      // 3 sec
props.put("max.poll.interval.ms", "600000");     // 10 min (default: 5 min)
```

**How it helps:**
- Longer timeouts → Less chance of false-positive rebalance
- Rebalancing stops all processing (stop-the-world)
- Avoid rebalances during slow processing spikes

---

**Step 6: Multiple Consumer Instances**
```java
// Instead of 1 consumer, run 10 instances
// Each gets different partitions
// 10x parallelism
```

**How it helps:**
- Topic with 10 partitions + 10 consumers = full parallelism
- Each consumer processes independently
- Linear scaling

**Rule:** Max consumers = number of partitions

---

**Step 7: Disable Auto-commit (Use Manual)**
```java
props.put("enable.auto.commit", "false");
```

**How it helps:**
- Auto-commit happens every 5 seconds regardless of processing
- Can commit partial batches → Inefficient
- Manual commit after full batch = better control

---

**Step 8: Optimize Deserialization**
```java
// Use efficient deserializer
props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

// Or use Avro/Protobuf with schema registry
// Faster than JSON parsing
```

**How it helps:**
- ByteArray = no deserialization overhead
- Avro/Protobuf 5-10x faster than JSON
- Less CPU = more throughput

---

**Step 9: Batch Database Operations**
```java
List<Record> batch = new ArrayList<>();

for (ConsumerRecord<String, String> record : records) {
    batch.add(convert(record));
    
    if (batch.size() >= 1000) {
        database.batchInsert(batch);  // Insert 1000 at once
        batch.clear();
    }
}

if (!batch.isEmpty()) {
    database.batchInsert(batch);
}

consumer.commitSync();
```

**How it helps:**
- 1 DB call for 1000 records vs 1000 DB calls
- 100x faster database operations
- Reduces network + DB overhead

---

**Step 10: Use Read Committed for Transactional Data**
```java
props.put("isolation.level", "read_committed");
```

**How it helps:**
- Skip uncommitted/aborted transactions
- Don't waste time processing data that will be rolled back

---

**Complete Fast Consumer Configuration:**

```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:19092");
props.put("group.id", "fast-consumer-group");
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

// Fetch optimization
props.put("fetch.min.bytes", "1048576");           // 1 MB
props.put("fetch.max.wait.ms", "500");             // 500ms
props.put("max.partition.fetch.bytes", "5242880"); // 5 MB

// Poll optimization
props.put("max.poll.records", "1000");             // 1000 records

// Timeout optimization
props.put("session.timeout.ms", "30000");          // 30 sec
props.put("heartbeat.interval.ms", "3000");        // 3 sec
props.put("max.poll.interval.ms", "600000");       // 10 min

// Manual commit
props.put("enable.auto.commit", "false");

// Transactional
props.put("isolation.level", "read_committed");
```

---

**Implementation Pattern:**

```java
KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("high-volume-topic"));

ExecutorService executor = Executors.newFixedThreadPool(10);
List<Record> batchBuffer = new ArrayList<>();

while (true) {
    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
    
    // Multi-threaded processing
    CountDownLatch latch = new CountDownLatch(records.count());
    
    for (ConsumerRecord<String, byte[]> record : records) {
        executor.submit(() -> {
            try {
                Record processed = process(record);
                synchronized (batchBuffer) {
                    batchBuffer.add(processed);
                }
            } finally {
                latch.countDown();
            }
        });
    }
    
    // Wait for all threads
    latch.await();
    
    // Batch database insert
    if (batchBuffer.size() >= 1000) {
        database.batchInsert(batchBuffer);
        batchBuffer.clear();
        consumer.commitAsync();
    }
}
```

---

**Performance Gains:**

| Optimization | Improvement | Reason |
|--------------|-------------|--------|
| fetch.min.bytes=1MB | 10x | Fewer network calls |
| max.poll.records=1000 | 2x | Larger batches |
| Multi-threading (10 threads) | 10x | Parallel processing |
| commitAsync | 1.5x | No blocking |
| Batch DB operations | 100x | Bulk inserts |
| Multiple consumers | Nx | N = partition count |

**Total realistic improvement: 50-100x over default settings!**

---

**Monitoring:**
```bash
# Check consumer lag
kafka-consumer-groups --bootstrap-server localhost:19092 \
  --group fast-consumer-group --describe

# Should see:
# - LAG = 0 (keeping up)
# - CURRENT-OFFSET increasing steadily
```

---

**Common Bottlenecks:**

1. **Slow DB writes** → Batch operations
2. **CPU-bound processing** → Multi-threading
3. **Network latency** → Increase fetch sizes
4. **Too few consumers** → Add more (up to partition count)
5. **Frequent rebalancing** → Tune timeouts

---

**Trade-offs:**

- **Memory:** Larger fetches + batching = more RAM
- **Latency:** Batching adds delay
- **Complexity:** Multi-threading harder to debug
- **Risk:** Async commit can lose offsets on crash

---

## Quick Code Examples

### Basic Consumer
```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:19092");
props.put("group.id", "my-group");
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("my-topic"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        System.out.printf("offset=%d, key=%s, value=%s%n", 
            record.offset(), record.key(), record.value());
    }
}
```

### Manual Commit
```java
props.put("enable.auto.commit", "false");

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);
    }
    
    consumer.commitSync();
}
```

### At-Least-Once Processing
```java
props.put("enable.auto.commit", "false");

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    for (ConsumerRecord<String, String> record : records) {
        saveToDatabase(record);  // Process first
    }
    
    consumer.commitSync();  // Then commit
}
// If crash before commit → reprocess
```

## Common Interview Questions

1. **What happens if you have more consumers than partitions?**
   - Extra consumers sit idle

2. **How to reset consumer to read from beginning?**
   - `consumer.seekToBeginning()` or delete consumer group

3. **What causes consumer lag?**
   - Slow processing, network issues, under-provisioned consumers

4. **How to reduce rebalance time?**
   - Use sticky assignment, tune session.timeout.ms

5. **Can different consumer groups read same data?**
   - Yes! Each group maintains separate offsets

6. **What if consumer crashes during processing?**
   - Depends on commit strategy (auto vs manual)

---

## Practice Tasks

1. Run `SimpleConsumer` - see basic consumption
2. Run 2 `ConsumerGroup` instances - see load balancing
3. Kill one consumer - see rebalance
4. Use `ManualCommit` - see offset control
5. Check consumer lag with `kafka-consumer-groups`

See `examples/java/consumer/` for working code!

