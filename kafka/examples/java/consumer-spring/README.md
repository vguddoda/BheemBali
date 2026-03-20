# Spring Kafka Consumer with @KafkaListener and Concurrency

Spring Kafka example showing concurrent message processing with @KafkaListener annotation.

## Features

✅ **Multiple Concurrency Examples:**
1. Basic concurrent listener (3 threads)
2. Manual commit listener (5 threads)
3. Batch processing listener (10 threads)
4. High-throughput listener (20 threads)

✅ **Configuration Options:**
- Concurrency levels
- Manual vs auto-commit
- Batch vs single message
- Performance tuning

---

## Build & Run

```bash
cd consumer-spring
mvn clean package
mvn spring-boot:run
```

Related doc: `consumer-spring/REBALANCING_SHORT_GUIDE.md`

Deep dive: `consumer-spring/HOW_THREADS_GET_PARTITIONS.md`

---

## Examples Included

### 1. ConcurrentKafkaListener.java
- Basic @KafkaListener with 3 concurrent consumers
- Auto-commit mode
- Demonstrates thread-based parallelism

### 2. AdvancedKafkaListener.java
- Manual acknowledgment
- 5 concurrent consumers
- Error handling with retry

### 3. BatchKafkaListener.java
- Batch processing (list of messages)
- 10 concurrent consumers
- Efficient for bulk operations

### 4. KafkaConsumerConfig.java
- All configuration in one place
- Multiple container factories
- Different concurrency levels

---

## Understanding Concurrency

### What `concurrency = 3` Means:

```
Single @KafkaListener with concurrency=3:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@KafkaListener
  ├── Thread-1 → Partition 0
  ├── Thread-2 → Partition 1
  └── Thread-3 → Partition 2
```

Each thread gets assigned partitions and processes independently.

### Concurrency vs Partitions:

| Partitions | Concurrency | Active Threads | Utilization |
|------------|-------------|----------------|-------------|
| 3 | 3 | 3 | 100% |
| 3 | 5 | 3 | 60% (2 idle) |
| 10 | 3 | 3 | 30% |
| 10 | 10 | 10 | 100% |

**Rule:** Max useful concurrency = number of partitions

Simple rule of thumb:
- One partition is read by only one thread in the same consumer group at a time
- One thread can own one or more partitions
- If concurrency is higher than partition count, the extra threads stay idle

Example:
```
1 pod, concurrency=10, topic has 2 partitions

Result:
- 2 threads active
- 8 threads idle
- One thread reads partition 0
- One thread reads partition 1
```

---

## Configuration Breakdown

### Basic Configuration (Auto-commit):
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> 
    kafkaListenerContainerFactory() {
    factory.setConcurrency(3);  // 3 threads
    factory.getContainerProperties()
        .setAckMode(ContainerProperties.AckMode.BATCH);
    return factory;
}
```

`AckMode.BATCH` means Spring commits after the listener finishes processing the records returned by the last poll. It is simpler because you do not manually call acknowledge.

### Manual Commit Configuration:
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> 
    manualCommitContainerFactory() {
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    factory.setConcurrency(5);  // 5 threads
    factory.getContainerProperties()
        .setAckMode(ContainerProperties.AckMode.MANUAL);
    return factory;
}
```

`AckMode.MANUAL` means your listener decides when to commit by calling `ack.acknowledge()`. This is useful when you want to commit only after DB save, API call, or other business logic succeeds.

### Batch Processing Configuration:
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> 
    batchContainerFactory() {
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);
    factory.setConcurrency(10);  // 10 threads
    factory.setBatchListener(true);
    return factory;
}
```

---

## Usage Examples

### Example 1: Basic Concurrent Listener
```java
@KafkaListener(
    topics = "test-topic",
    groupId = "my-group",
    containerFactory = "kafkaListenerContainerFactory"
)
public void listen(String message) {
    System.out.println("Received: " + message);
}
```

### Example 2: With Custom Concurrency
```java
@KafkaListener(
    topics = "test-topic",
    groupId = "my-group",
    concurrency = "10"  // Override default
)
public void listen(String message) {
    System.out.println("Received: " + message);
}
```

### Example 3: Manual Acknowledgment
```java
@KafkaListener(
    topics = "test-topic",
    containerFactory = "manualCommitContainerFactory"
)
public void listen(String message, Acknowledgment ack) {
    try {
        process(message);
        ack.acknowledge();  // Manual commit
    } catch (Exception e) {
        // Don't ack - will retry
    }
}
```

### Example 4: Batch Processing
```java
@KafkaListener(
    topics = "test-topic",
    containerFactory = "batchContainerFactory"
)
public void listenBatch(List<String> messages) {
    System.out.println("Batch size: " + messages.size());
    database.batchInsert(messages);
}
```

---

## Performance Tuning Parameters

### Consumer Properties:
```properties
# Fetch more data per request
spring.kafka.consumer.fetch-min-size=1048576      # 1 MB
spring.kafka.consumer.fetch-max-wait=500ms

# Poll more records
spring.kafka.consumer.max-poll-records=500

# Avoid rebalancing
spring.kafka.consumer.session-timeout=30000ms
spring.kafka.consumer.max-poll-interval=300000ms

# Concurrency
spring.kafka.listener.concurrency=10
```

---

## Testing Concurrent Processing

### Terminal 1 - Start Consumer:
```bash
mvn spring-boot:run
```

### Terminal 2 - Send Messages:
```bash
cd ../producer
mvn exec:java -Dexec.mainClass="SimpleProducer"
```

**Watch output:**
- See different thread names processing messages
- Multiple messages processed simultaneously
- Load balanced across threads

---

## Interview Questions You Can Answer

### 1. "How does concurrency work in Spring Kafka?"
**Answer:**
- `concurrency=N` creates N consumer threads
- Each thread polls from assigned partitions
- Threads process independently in parallel
- Max concurrency = number of partitions

### 2. "When to use batch vs single message processing?"
**Answer:**
- Batch: Database operations (bulk insert), aggregations
- Single: Real-time processing, complex per-message logic
- Batch has higher throughput, single has lower latency

### 3. "How to ensure ordering with concurrency?"
**Answer:**
- Each thread gets specific partitions
- Order maintained within partition
- Same key = same partition = same thread
- Across partitions: no ordering guarantee

### 4. "What's the trade-off with high concurrency?"
**Answer:**
- **Pro:** Higher throughput, better CPU utilization
- **Con:** More memory, complexity, potential out-of-order if not careful
- **Limit:** Max = partition count

---

## Common Concurrency Patterns

### Pattern 1: High-Volume Real-Time
```java
concurrency = partition_count
max.poll.records = 500
ack.mode = BATCH
```

### Pattern 2: Bulk Database Operations
```java
concurrency = 10
batch.listener = true
max.poll.records = 1000
```

### Pattern 3: At-Least-Once Guarantee
```java
concurrency = 5
ack.mode = MANUAL
enable.auto.commit = false
```

---

## Key Differences from Plain Kafka Consumer

| Feature | Plain Consumer | Spring @KafkaListener |
|---------|---------------|----------------------|
| Setup | Manual configuration | Annotation-based |
| Concurrency | Manual threads | Built-in `concurrency` |
| Error Handling | Manual try-catch | RetryTemplate, ErrorHandler |
| Batch | Manual batching | `@KafkaListener(batch=true)` |
| Metrics | Manual | Built-in actuator metrics |

---

## Monitoring

### Check Active Threads:
```bash
# See thread count in application
jps -l
jstack <pid> | grep "kafka-listener"
```

### Check Consumer Groups:
```bash
docker exec kafka-broker-1 kafka-consumer-groups \
  --bootstrap-server localhost:19092 \
  --group spring-concurrent-group --describe
```

You'll see multiple consumers in same group (one per thread)!

---

## Troubleshooting

### Too Many Threads?
- Reduce concurrency
- Check partition count
- Monitor CPU usage

### Messages Not Processing?
- Check consumer group status
- Verify partition assignment
- Look for rebalancing in logs

### Out of Order Messages?
- Ensure same key goes to same partition
- Check thread assignments
- Reduce concurrency if needed

---

## Complete Working Example

Run this to see concurrent processing in action:

```bash
# 1. Start Kafka
cd /Users/vishalkumarbg/Documents/Bheembali/kafka
./setup.sh

# 2. Create topic with 10 partitions
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic test-topic --partitions 10 --replication-factor 2

# 3. Start Spring Consumer (10 concurrent threads)
cd examples/java/consumer-spring
mvn spring-boot:run

# 4. Send messages (new terminal)
cd examples/java/producer
mvn exec:java -Dexec.mainClass="SimpleProducer"

# Watch multiple threads process in parallel!
```

---

## Summary

✅ **Concurrency Levels:** 3, 5, 10, 20 examples
✅ **Manual Commit:** With acknowledgment
✅ **Batch Processing:** List-based consumption
✅ **Performance Tuned:** Optimized settings
✅ **Production Ready:** Error handling, monitoring

**Now you can demo Spring Kafka with concurrent consumers in interviews!** 🚀

